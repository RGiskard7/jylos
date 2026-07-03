package com.example.jylos.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.dao.interfaces.FolderDAO;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;

/**
 * Service layer for note-related business logic.
 * Provides a clean API for note operations, separating business logic from UI
 * controllers.
 * 
 * <p>
 * This service handles:
 * </p>
 * <ul>
 * <li>CRUD operations for notes</li>
 * <li>Search and filtering</li>
 * <li>Sorting and ordering</li>
 * <li>Tag management for notes</li>
 * <li>Favorites management</li>
 * </ul>
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.1.0
 */
public class NoteService {

    private static final Logger logger = LoggerConfig.getLogger(NoteService.class);

    private final NoteDAO noteDAO;
    private final FolderDAO folderDAO;
    private NoteTitleIndex noteTitleIndex;

    /**
     * Sorting options for notes list.
     */
    public enum SortOption {
        TITLE_ASC("Title (A-Z)"),
        TITLE_DESC("Title (Z-A)"),
        CREATED_NEWEST("Created (Newest)"),
        CREATED_OLDEST("Created (Oldest)"),
        MODIFIED_NEWEST("Modified (Newest)"),
        MODIFIED_OLDEST("Modified (Oldest)");

        private final String displayName;

        SortOption(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static SortOption fromDisplayName(String name) {
            for (SortOption option : values()) {
                if (option.displayName.equals(name)) {
                    return option;
                }
            }
            return MODIFIED_NEWEST; // Default
        }
    }

    /**
     * Creates a new NoteService with the required DAOs.
     *
     * @param noteDAO   Data access object for notes
     * @param folderDAO Data access object for folders
     */
    public NoteService(NoteDAO noteDAO, FolderDAO folderDAO) {
        this.noteDAO = noteDAO;
        this.folderDAO = folderDAO;

        logger.info("NoteService initialized");
    }

    public void setNoteTitleIndex(NoteTitleIndex noteTitleIndex) {
        this.noteTitleIndex = noteTitleIndex;
    }

    // ==================== CRUD Operations ====================

    /**
     * Creates a new note with the given title and content.
     *
     * @param title   The note title
     * @param content The note content (Markdown)
     * @return The created note with its generated ID
     */
    public Note createNote(String title, String content) {
        Note note = new Note(title, content);
        return createNote(note);
    }

    /**
     * Creates a new note from an existing Note object.
     * Useful when pre-setting the ID for specific DAOs (like FileSystem).
     * 
     * @param note The note to create
     * @return The created note with its generated ID
     */
    public Note createNote(Note note) {
        String plain = note.getContent();
        boolean encrypted = encryptForWrite(note);
        String noteId;
        try {
            noteId = noteDAO.createNote(note);
        } finally {
            if (encrypted) {
                note.setContent(plain); // keep the in-memory note as plaintext
            }
        }
        note.setId(noteId);
        logger.info("Created note: " + note.getTitle() + " (ID: " + noteId + ")");
        return note;
    }

    /**
     * Creates a new note in a specific folder.
     * 
     * @param title   The note title
     * @param content The note content
     * @param folder  The parent folder (can be null for root)
     * @return The created note
     */
    public Note createNoteInFolder(String title, String content, Folder folder) {
        Note note = createNote(title, content);
        if (folder != null && folder.getId() != null) {
            folderDAO.addNote(folder, note);
            logger.info("Added note to folder: " + folder.getTitle());
        }
        return note;
    }

    /**
     * Retrieves a note by its ID.
     * 
     * @param id The note ID
     * @return Optional containing the note if found
     */
    public Optional<Note> getNoteById(String id) {
        Note note = noteDAO.getNoteById(id);
        if (note != null) {
            decryptForRead(note);
        }
        return Optional.ofNullable(note);
    }

    /**
     * Resolves the on-disk file backing a note (file-based storage only).
     *
     * @param id note id
     * @return the note's file path, or empty when storage has no files (SQLite)
     */
    public Optional<java.nio.file.Path> getNoteFilePath(String id) {
        return noteDAO.resolveFilePath(id);
    }

    /**
     * Updates an existing note.
     * 
     * @param note The note with updated data
     */
    public void updateNote(Note note) {
        if (note == null || note.getId() == null) {
            throw new IllegalArgumentException("Note or note ID cannot be null");
        }
        // Never overwrite an encrypted private note with plaintext when we hold no key
        // (it could not be re-encrypted, so saving would expose/lose the content).
        if (note.isPrivate() && !EncryptionService.isEncrypted(note.getContent())
                && !EncryptionService.getInstance().hasKey()) {
            logger.warning("Skipping save of private note while locked: " + note.getTitle());
            return;
        }
        // Version history: capture the previously *stored* content (raw from the DAO,
        // i.e. ciphertext for private notes — history must never leak plaintext).
        if (historyService != null) {
            try {
                Note stored = noteDAO.getNoteById(note.getId());
                if (stored != null && stored.getContent() != null) {
                    historyService.snapshot(note.getId(), stored.getContent());
                }
            } catch (Exception e) {
                logger.fine("History snapshot skipped: " + e.getMessage());
            }
        }
        String plain = note.getContent();
        boolean encrypted = encryptForWrite(note);
        try {
            noteDAO.updateNote(note);
        } finally {
            if (encrypted) {
                note.setContent(plain);
            }
        }
        logger.info("Updated note: " + note.getTitle());
    }

    /** Optional local version-history recorder (see {@link NoteHistoryService}). */
    private NoteHistoryService historyService;

    public void setHistoryService(NoteHistoryService historyService) {
        this.historyService = historyService;
    }

    public NoteHistoryService getHistoryService() {
        return historyService;
    }

    // ------------------------------------------------------------------
    // Encryption hooks (private notes) — see EncryptionService
    // ------------------------------------------------------------------

    /**
     * Encrypts the note body in place when it is private, the session is unlocked, and
     * the body isn't already encrypted. Returns {@code true} if the content was changed
     * (so the caller can restore the plaintext on the in-memory note afterwards).
     */
    private boolean encryptForWrite(Note note) {
        if (note == null || !note.isPrivate()) {
            return false;
        }
        String content = note.getContent();
        if (EncryptionService.isEncrypted(content)) {
            return false; // already ciphertext
        }
        EncryptionService enc = EncryptionService.getInstance();
        if (!enc.hasKey()) {
            return false; // can't encrypt without the key (write blocked upstream)
        }
        note.setContent(enc.encrypt(content));
        return true;
    }

    /**
     * Decrypts an encrypted body when unlocked; otherwise replaces it with a locked
     * placeholder so the UI can show 🔒 without exposing (or losing) the ciphertext.
     */
    private void decryptForRead(Note note) {
        String content = note != null ? note.getContent() : null;
        if (!EncryptionService.isEncrypted(content)) {
            return;
        }
        EncryptionService enc = EncryptionService.getInstance();
        if (enc.canRead(note.getId())) {
            try {
                note.setContent(enc.decrypt(content));
                return;
            } catch (Exception e) {
                logger.warning("Could not decrypt note " + note.getId() + ": " + e.getMessage());
            }
        }
        note.setContent(LOCKED_PLACEHOLDER);
    }

    /** Shown in place of a private note's body while the session is locked. */
    public static final String LOCKED_PLACEHOLDER = "🔒";

    /**
     * Moves a note to the trash (soft delete).
     * 
     * @param noteId The ID of the note to move to trash
     */
    public void moveToTrash(String noteId) {
        noteDAO.deleteNote(noteId);
        logger.fine("Moved note to trash, ID: " + noteId);
    }

    /**
     * Deletes a note permanently from the database.
     * 
     * @param noteId The ID of the note to delete permanently
     */
    public void permanentlyDeleteNote(String noteId) {
        noteDAO.permanentlyDeleteNote(noteId);
        logger.fine("Permanently deleted note ID: " + noteId);
    }

    /**
     * Restores a note from the trash.
     * 
     * @param noteId The ID of the note to restore
     */
    public void restoreNote(String noteId) {
        noteDAO.restoreNote(noteId);
        noteDAO.refreshCache();
        if (folderDAO != null) {
            folderDAO.refreshCache();
        }
        logger.fine("Restored note from trash, ID: " + noteId);
    }

    /**
     * Fetches all notes currently in the trash.
     * 
     * @return List of notes in trash
     */
    public List<Note> getTrashNotes() {
        return noteDAO.fetchTrashNotes();
    }

    /**
     * Empties the trash by permanently deleting all notes in it.
     */
    public void emptyTrash() {
        List<Note> trash = getTrashNotes();
        for (Note note : trash) {
            permanentlyDeleteNote(note.getId());
        }
        logger.info("Trash emptied: " + trash.size() + " notes deleted permanently");
    }

    // ==================== Retrieval Methods ====================

    /**
     * Fetches all notes from the database.
     * 
     * @return List of all notes
     */
    public List<Note> getAllNotes() {
        return scrubEncryptedForList(noteDAO.fetchAllNotes());
    }

    /**
     * Replaces encrypted bodies with the lock placeholder for any list of notes, so list
     * previews (all-notes, folder, tag, search) never expose ciphertext. These are fresh
     * list copies from the DAO, so the change is display-only and never persisted.
     */
    private List<Note> scrubEncryptedForList(List<Note> notes) {
        if (notes == null) {
            return new ArrayList<>();
        }
        for (Note n : notes) {
            if (n != null && EncryptionService.isEncrypted(n.getContent())) {
                n.setContent(LOCKED_PLACEHOLDER);
            }
        }
        return notes;
    }

    /**
     * Fetches notes for a specific folder.
     * 
     * @param folder The folder to get notes from
     * @return List of notes in the folder
     */
    public List<Note> getNotesByFolder(Folder folder) {
        if (folder == null || folder.getId() == null) {
            return getAllNotes();
        }
        return scrubEncryptedForList(noteDAO.fetchNotesByFolderId(folder.getId()));
    }

    /**
     * Fetches notes that have a specific tag.
     * 
     * @param tag The tag to filter by
     * @return List of notes with the tag
     */
    public List<Note> getNotesByTag(Tag tag) {
        if (tag == null || tag.getId() == null) {
            return new ArrayList<>();
        }
        return scrubEncryptedForList(noteDAO.fetchNotesByTagId(tag.getId()));
    }

    /**
     * Fetches all favorite notes.
     * 
     * @return List of favorite notes
     */
    public List<Note> getFavoriteNotes() {
        return getAllNotes().stream()
                .filter(Note::isFavorite)
                .collect(Collectors.toList());
    }

    /**
     * Fetches recent notes, sorted by modification date.
     * 
     * @param limit Maximum number of notes to return
     * @return List of recent notes
     */
    public List<Note> getRecentNotes(int limit) {
        List<Note> notes = getAllNotes();
        return sortNotes(notes, SortOption.MODIFIED_NEWEST).stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    // ==================== WikiLinks Support ====================

    /**
     * Finds a note by its exact title (case-insensitive).
     * Used by WikiLinks resolution to navigate to linked notes.
     *
     * @param title The exact note title to search for
     * @return Optional containing the note if found
     */
    public Optional<Note> findNoteByTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return Optional.empty();
        }
        String target = title.trim();
        if (noteTitleIndex != null) {
            Optional<String> indexedId = noteTitleIndex.findNoteIdByTitle(target);
            if (indexedId.isPresent()) {
                Optional<Note> indexed = getNoteById(indexedId.get());
                if (indexed.isPresent()) {
                    return indexed;
                }
                noteTitleIndex.invalidate();
            }
        }
        return getAllNotes().stream()
                .filter(n -> target.equalsIgnoreCase(n.getTitle()))
                .findFirst();
    }

    // ==================== Search Methods ====================

    /**
     * Searches notes by title and content.
     * 
     * @param query The search query
     * @return List of matching notes
     */
    public List<Note> searchNotes(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllNotes();
        }

        String searchLower = query.toLowerCase().trim();
        return getAllNotes().stream()
                .filter(note -> matchesSearch(note, searchLower))
                .collect(Collectors.toList());
    }

    /**
     * Searches notes within a specific folder.
     * 
     * @param query  The search query
     * @param folder The folder to search in (null for all notes)
     * @return List of matching notes
     */
    public List<Note> searchNotesInFolder(String query, Folder folder) {
        List<Note> notes = folder != null ? getNotesByFolder(folder) : getAllNotes();

        if (query == null || query.trim().isEmpty()) {
            return notes;
        }

        String searchLower = query.toLowerCase().trim();
        return notes.stream()
                .filter(note -> matchesSearch(note, searchLower))
                .collect(Collectors.toList());
    }

    /**
     * Checks if a note matches a search query.
     * 
     * @param note        The note to check
     * @param searchLower The lowercase search query
     * @return true if the note matches
     */
    private boolean matchesSearch(Note note, String searchLower) {
        String title = note.getTitle() != null ? note.getTitle().toLowerCase() : "";
        String content = note.getContent() != null ? note.getContent().toLowerCase() : "";
        return title.contains(searchLower) || content.contains(searchLower);
    }

    // ==================== Sorting Methods ====================

    /**
     * Sorts a list of notes according to the specified option.
     * 
     * @param notes  The notes to sort
     * @param option The sorting option
     * @return Sorted list of notes
     */
    public List<Note> sortNotes(List<Note> notes, SortOption option) {
        if (notes == null || notes.isEmpty()) {
            return new ArrayList<>();
        }

        List<Note> sorted = new ArrayList<>(notes);
        Comparator<Note> comparator = getComparator(option);
        sorted.sort(comparator);
        return sorted;
    }

    /**
     * Gets the comparator for a sort option.
     * 
     * @param option The sort option
     * @return The appropriate comparator
     */
    private Comparator<Note> getComparator(SortOption option) {
        switch (option) {
            case TITLE_ASC:
                return Comparator.comparing(
                        n -> n.getTitle() != null ? n.getTitle().toLowerCase() : "",
                        Comparator.nullsLast(String::compareTo));
            case TITLE_DESC:
                return Comparator.comparing(
                        (Note n) -> n.getTitle() != null ? n.getTitle().toLowerCase() : "",
                        Comparator.nullsLast(String::compareTo)).reversed();
            case CREATED_NEWEST:
                return Comparator.comparing(
                        (Note n) -> n.getCreatedDate() != null ? n.getCreatedDate() : "",
                        Comparator.nullsLast(String::compareTo)).reversed();
            case CREATED_OLDEST:
                return Comparator.comparing(
                        n -> n.getCreatedDate() != null ? n.getCreatedDate() : "",
                        Comparator.nullsLast(String::compareTo));
            case MODIFIED_NEWEST:
                return Comparator.comparing(
                        (Note n) -> getEffectiveModifiedDate(n),
                        Comparator.nullsLast(String::compareTo)).reversed();
            case MODIFIED_OLDEST:
                return Comparator.comparing(
                        n -> getEffectiveModifiedDate(n),
                        Comparator.nullsLast(String::compareTo));
            default:
                return Comparator.comparing(
                        (Note n) -> getEffectiveModifiedDate(n),
                        Comparator.nullsLast(String::compareTo)).reversed();
        }
    }

    /**
     * Gets the effective modified date (falls back to created date if null).
     * 
     * @param note The note
     * @return The effective date string
     */
    private String getEffectiveModifiedDate(Note note) {
        if (note.getModifiedDate() != null) {
            return note.getModifiedDate();
        }
        return note.getCreatedDate() != null ? note.getCreatedDate() : "";
    }

    // ==================== Tag Management ====================

    /**
     * Gets all tags assigned to a note.
     * 
     * @param note The note
     * @return List of tags
     */
    public List<Tag> getNoteTags(Note note) {
        if (note == null || note.getId() == null) {
            return new ArrayList<>();
        }
        return noteDAO.fetchTags(note.getId());
    }

    /**
     * Alias aligned with the service-boundary cleanup naming used by the UI layer.
     *
     * @param note The note
     * @return List of tags
     */
    public List<Tag> getTagsForNote(Note note) {
        return getNoteTags(note);
    }

    /**
     * Adds a tag to a note.
     * 
     * @param note The note
     * @param tag  The tag to add
     */
    public void addTagToNote(Note note, Tag tag) {
        if (note == null || tag == null) {
            throw new IllegalArgumentException("Note and tag cannot be null");
        }

        // Check if tag already exists on note
        List<Tag> currentTags = getNoteTags(note);
        boolean alreadyHasTag = currentTags.stream()
                .anyMatch(t -> t.getId().equals(tag.getId()));

        if (!alreadyHasTag) {
            noteDAO.addTag(note, tag);
            logger.info("Added tag '" + tag.getTitle() + "' to note: " + note.getTitle());
        }
    }

    /**
     * Removes a tag from a note.
     * 
     * @param note The note
     * @param tag  The tag to remove
     */
    public void removeTagFromNote(Note note, Tag tag) {
        if (note == null || tag == null) {
            throw new IllegalArgumentException("Note and tag cannot be null");
        }
        noteDAO.removeTag(note, tag);
        logger.info("Removed tag '" + tag.getTitle() + "' from note: " + note.getTitle());
    }

    // ==================== Favorites Management ====================

    /**
     * Toggles the favorite status of a note.
     * 
     * @param note The note
     * @return The new favorite status
     */
    public boolean toggleFavorite(Note note) {
        if (note == null) {
            throw new IllegalArgumentException("Note cannot be null");
        }

        boolean newStatus = !note.isFavorite();
        note.setFavorite(newStatus);
        updateNote(note);
        logger.info("Note '" + note.getTitle() + "' favorite status: " + newStatus);
        return newStatus;
    }

    // ==================== Utility Methods ====================

    /**
     * Counts words in text content.
     * 
     * @param text The text to count
     * @return Word count
     */
    public int countWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    /**
     * Counts characters in text content.
     * 
     * @param text The text to count
     * @return Character count
     */
    public int countCharacters(String text) {
        return text != null ? text.length() : 0;
    }
}
