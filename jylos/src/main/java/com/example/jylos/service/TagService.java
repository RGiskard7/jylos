package com.example.jylos.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.dao.interfaces.TagDAO;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;

/**
 * Service layer for tag-related business logic.
 * Provides a clean API for tag operations and owns note-tag relationships.
 * 
 * <p>
 * This service handles:
 * </p>
 * <ul>
 * <li>CRUD operations for tags</li>
 * <li>Tag-note relationship management</li>
 * <li>Tag search and filtering</li>
 * <li>Tag usage statistics</li>
 * </ul>
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.1.0
 */
public class TagService {

    private static final Logger logger = LoggerConfig.getLogger(TagService.class);

    private final TagDAO tagDAO;
    private final NoteDAO noteDAO;

    /**
     * Creates a new TagService with the required DAOs.
     * 
     * @param tagDAO  Data access object for tags
     * @param noteDAO Data access object for notes
     */
    public TagService(TagDAO tagDAO, NoteDAO noteDAO) {
        this.tagDAO = tagDAO;
        this.noteDAO = noteDAO;
        logger.info("TagService initialized");
    }

    // ==================== CRUD Operations ====================

    /**
     * Creates a new tag.
     * 
     * @param title The tag title
     * @return The created tag with its generated ID
     * @throws IllegalArgumentException if a tag with this name already exists
     */
    /**
     * Creates a new tag.
     * 
     * @param title The tag title
     * @return The created tag with its generated ID
     * @throws IllegalArgumentException if a tag with this name already exists
     */
    public Tag createTag(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Tag title cannot be null or empty");
        }

        String trimmedTitle = title.trim();

        // Check if tag already exists
        if (tagExists(trimmedTitle)) {
            throw new IllegalArgumentException("Tag '" + trimmedTitle + "' already exists");
        }

        Tag tag = new Tag(trimmedTitle);
        String tagId = tagDAO.createTag(tag);
        tag.setId(tagId);
        logger.info("Created tag: " + trimmedTitle + " (ID: " + tagId + ")");
        return tag;
    }

    /**
     * Gets or creates a tag by title.
     * If the tag exists, returns it. If not, creates a new one.
     * 
     * @param title The tag title
     * @return The existing or newly created tag
     */
    public Tag getOrCreateTag(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Tag title cannot be null or empty");
        }

        String trimmedTitle = title.trim();
        Optional<Tag> existing = getTagByTitle(trimmedTitle);

        if (existing.isPresent()) {
            return existing.get();
        }

        return createTag(trimmedTitle);
    }

    /**
     * Retrieves a tag by its ID.
     * 
     * @param id The tag ID
     * @return Optional containing the tag if found
     */
    public Optional<Tag> getTagById(String id) {
        Tag tag = tagDAO.getTagById(id);
        return Optional.ofNullable(tag);
    }

    /**
     * Retrieves a tag by its title.
     * 
     * @param title The tag title
     * @return Optional containing the tag if found
     */
    public Optional<Tag> getTagByTitle(String title) {
        if (title == null) {
            return Optional.empty();
        }

        return getAllTags().stream()
                .filter(t -> t.getTitle().equalsIgnoreCase(title.trim()))
                .findFirst();
    }

    /**
     * Updates an existing tag.
     * 
     * @param tag The tag with updated data
     */
    public void updateTag(Tag tag) {
        if (tag == null || tag.getId() == null) {
            throw new IllegalArgumentException("Tag or tag ID cannot be null");
        }
        tagDAO.updateTag(tag);
        logger.info("Updated tag: " + tag.getTitle());
    }

    /**
     * Renames a tag.
     * 
     * @param tag     The tag to rename
     * @param newName The new name
     * @throws IllegalArgumentException if the new name already exists
     */
    public void renameTag(Tag tag, String newName) {
        if (tag == null || newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("Tag and new name cannot be null or empty");
        }

        String trimmedName = newName.trim();

        // Check if new name already exists (excluding current tag)
        Optional<Tag> existing = getTagByTitle(trimmedName);
        if (existing.isPresent() && !existing.get().getId().equals(tag.getId())) {
            throw new IllegalArgumentException("Tag '" + trimmedName + "' already exists");
        }

        tag.setTitle(trimmedName);
        updateTag(tag);
    }

    /**
     * Deletes a tag by its ID.
     * This will remove the tag from all notes.
     * 
     * @param tagId The ID of the tag to delete
     */
    public void deleteTag(String tagId) {
        tagDAO.deleteTag(tagId);
        logger.info("Deleted tag ID: " + tagId);
    }

    // ==================== Retrieval Methods ====================

    /**
     * Fetches all tags.
     * 
     * @return List of all tags
     */
    public List<Tag> getAllTags() {
        return tagDAO.fetchAllTags();
    }

    /**
     * Fetches all tags sorted by title.
     * 
     * @return Sorted list of tags
     */
    public List<Tag> getAllTagsSorted() {
        return getAllTags().stream()
                .sorted((a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()))
                .collect(Collectors.toList());
    }

    /**
     * Searches tags by title prefix.
     * 
     * @param prefix The prefix to search for
     * @return List of matching tags
     */
    public List<Tag> searchTags(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return getAllTags();
        }

        String searchLower = prefix.toLowerCase().trim();
        return getAllTags().stream()
                .filter(t -> t.getTitle().toLowerCase().startsWith(searchLower))
                .collect(Collectors.toList());
    }

    // ==================== Note-Tag Relationships ====================

    /**
     * Gets all notes that have a specific tag.
     * 
     * @param tag The tag
     * @return List of notes with the tag
     */
    public List<Note> getNotesWithTag(Tag tag) {
        if (tag == null) {
            return new ArrayList<>();
        }
        // Vault (filesystem) tags have no persistent id — they live as frontmatter
        // strings — so fall back to the title, which the DAO also matches on.
        String key = (tag.getId() != null && !tag.getId().isBlank()) ? tag.getId() : tag.getTitle();
        if (key == null || key.isBlank()) {
            return new ArrayList<>();
        }
        return tagDAO.fetchAllNotesWithTag(key);
    }

    /**
     * Gets all tags for a specific note.
     * 
     * @param note The note
     * @return List of tags on the note
     */
    public List<Tag> getTagsForNote(Note note) {
        if (note == null || note.getId() == null) {
            return new ArrayList<>();
        }
        List<Tag> noteTags = note.getTags();
        if (noteTags != null && !noteTags.isEmpty()) {
            return noteTags;
        }
        return noteDAO.fetchTags(note.getId());
    }

    /**
     * Gets tags that are NOT assigned to a note (for adding new tags).
     * 
     * @param note The note
     * @return List of available tags
     */
    public List<Tag> getAvailableTagsForNote(Note note) {
        List<Tag> allTags = getAllTags();
        List<Tag> noteTags = getTagsForNote(note);

        return allTags.stream()
                .filter(tag -> noteTags.stream().noneMatch(nt -> nt.getId().equals(tag.getId())))
                .sorted((a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()))
                .collect(Collectors.toList());
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

        // Check if already has tag
        List<Tag> currentTags = getTagsForNote(note);
        if (currentTags.stream().anyMatch(t -> t.getId().equals(tag.getId()))) {
            logger.info("Note already has tag: " + tag.getTitle());
            return;
        }

        noteDAO.addTag(note, tag);
        logger.info("Added tag '" + tag.getTitle() + "' to note: " + note.getTitle());
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

    // ==================== Statistics ====================

    /**
     * Gets the total count of tags.
     * 
     * @return Total tag count
     */
    public int getTagCount() {
        return getAllTags().size();
    }

    /**
     * Gets the count of notes that have a specific tag.
     * 
     * @param tag The tag
     * @return Note count with this tag
     */
    public int getNoteCountForTag(Tag tag) {
        return getNotesWithTag(tag).size();
    }

    /**
     * Gets tags sorted by usage (most used first).
     * 
     * @return List of tags sorted by usage count
     */
    public List<Tag> getTagsByUsage() {
        List<Tag> tags = new ArrayList<>(getAllTags());
        tags.sort((a, b) -> getNoteCountForTag(b) - getNoteCountForTag(a));
        return tags;
    }

    /**
     * Gets the most used tags.
     * 
     * @param limit Maximum number of tags to return
     * @return List of most used tags
     */
    public List<Tag> getMostUsedTags(int limit) {
        return getTagsByUsage().stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    // ==================== Utility Methods ====================

    /**
     * Checks if a tag with the given title exists.
     * 
     * @param title The tag title to check
     * @return true if a tag with this title exists
     */
    public boolean tagExists(String title) {
        return tagDAO.existsByTitle(title);
    }

    /**
     * Validates a tag title.
     * 
     * @param title The title to validate
     * @return true if the title is valid
     */
    public boolean isValidTagTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return false;
        }
        // Tag titles should not be too long
        return title.trim().length() <= 50;
    }
}
