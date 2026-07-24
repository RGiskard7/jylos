package com.example.jylos.data.dao.interfaces;

import java.util.List;

import com.example.jylos.data.models.Tag;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Folder;

/**
 * This interface defines the contract for data access operations related to
 * notes.
 * It provides methods for creating, retrieving, updating, and deleting notes,
 * as well as managing their relationships with folders and tags.
 */
public interface NoteDAO {

    // CRUD Operations
    /**
     * Creates a new note in the database.
     *
     * @param note The note to be created.
     * @return The generated ID of the created note.
     */
    public String createNote(Note note);

    /**
     * Retrieves a note by its unique ID.
     *
     * @param id The ID of the note.
     * @return The corresponding Note object, or null if not found.
     */
    public Note getNoteById(String id);

    /**
     * Updates an existing note.
     *
     * @param note The note containing updated data.
     */
    public void updateNote(Note note);

    /**
     * Deletes a note by its ID (soft delete, moves to trash).
     *
     * @param id The ID of the note to be deleted.
     */
    public void deleteNote(String id);

    /**
     * Permanently deletes a note by its ID.
     * 
     * @param id The ID of the note to be deleted permanently.
     */
    public void permanentlyDeleteNote(String id);

    /**
     * Restores a note from the trash.
     * 
     * @param id The ID of the note to be restored.
     */
    public void restoreNote(String id);

    /**
     * Fetches all notes that are in the trash (soft deleted).
     * 
     * @return A list of notes in the trash.
     */
    public List<Note> fetchTrashNotes();

    // Retrieval Methods
    /**
     * Fetches all notes from the database.
     *
     * @return A list of all notes.
     */
    public List<Note> fetchAllNotes();

    /**
     * Fetches all notes that belong to a specific folder.
     *
     * @param folderId The ID of the folder. If null, empty, or "ROOT", notes at
     *                 root level are returned.
     * @return A list of notes inside the specified folder.
     */
    public List<Note> fetchNotesByFolderId(String folderId);

    /**
     * Loads all notes that belong to a specific folder into the folder object.
     *
     * @param folder The folder whose notes should be loaded.
     */
    public void fetchNotesByFolderId(Folder folder);

    /**
     * Retrieves the folder that contains a specific note.
     *
     * @param noteId The ID of the note.
     * @return The Folder object that contains the given note, or null if the note
     *         is not inside a folder.
     */
    public Folder getFolderOfNote(String noteId);

    // Tag Management
    /**
     * Assigns a tag to a note using their respective IDs.
     *
     * @param noteId The ID of the note.
     * @param tagId  The ID of the tag to be assigned.
     */
    public void addTag(String noteId, String tagId);

    /**
     * Assigns a tag to a note.
     *
     * @param note The note to which the tag should be assigned.
     * @param tag  The tag to be assigned.
     */
    public void addTag(Note note, Tag tag);

    /**
     * Removes a tag from a note using their respective IDs.
     *
     * @param noteId The ID of the note.
     * @param tagId  The ID of the tag to be removed.
     */
    public void removeTag(String noteId, String tagId);

    /**
     * Removes a tag from a note.
     *
     * @param note The note from which the tag should be removed.
     * @param tag  The tag to be removed.
     */
    public void removeTag(Note note, Tag tag);

    /**
     * Fetches all tags associated with a given note.
     *
     * @param noteId The ID of the note.
     * @return A list of tags assigned to the note.
     */
    public List<Tag> fetchTags(String noteId);

    /**
     * Loads all tags assigned to a specific note into the note object.
     *
     * @param note The note whose tags should be loaded.
     */
    public void loadTags(Note note);

    /**
     * Fetches all notes that have a specific tag.
     *
     * @param tagId The ID of the tag.
     * @return A list of notes that have the specified tag.
     */
    public List<Note> fetchNotesByTagId(String tagId);

    /**
     * Refreshes the internal cache if the DAO implementation uses one.
     * Default implementation does nothing (e.g., SQLite does not need it).
     */
    default void refreshCache() {
        // Do nothing by default
    }

    /**
     * Re-indexes a note after another DAO moved its backing document.
     * Default implementation does nothing because not all storage backends keep a
     * path-based note cache.
     *
     * @param previousNoteId note id before the move
     * @param movedNote note instance after the move
     */
    default void reindexMovedNote(String previousNoteId, Note movedNote) {
        // Do nothing by default
    }

    /**
     * Registers a callback invoked once any <em>deferred</em> background content load
     * finishes (file-based storage that builds its cache lazily). Implementations that
     * load synchronously have nothing to defer, so the default is a no-op.
     *
     * @param callback action to run on completion (may be {@code null} to clear)
     */
    default void setOnContentLoaded(Runnable callback) {
        // No-op: synchronous backends are already fully loaded.
    }

    /**
     * Resolves the on-disk file backing a note, when the storage is file-based.
     *
     * @param id note id
     * @return the note's file path, or empty for storage backends without files
     *         (e.g. SQLite)
     */
    default java.util.Optional<java.nio.file.Path> resolveFilePath(String id) {
        return java.util.Optional.empty();
    }
}
