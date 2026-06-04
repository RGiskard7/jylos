package com.example.jylos.data.dao.interfaces;

import java.util.List;

import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;

/**
 * This interface defines the contract for data access operations related to
 * tags.
 * It provides methods for creating, retrieving, updating, and deleting tags,
 * as well as managing their relationships with notes.
 */
public interface TagDAO {

    // CRUD Operations
    /**
     * Creates a new tag in the database.
     *
     * @param tag The tag to be created.
     * @return The generated ID of the created tag.
     */
    public String createTag(Tag tag);

    /**
     * Updates an existing tag in the database.
     *
     * @param tag The tag containing updated data.
     */
    public void updateTag(Tag tag);

    /**
     * Deletes a tag by its ID.
     *
     * @param id The ID of the tag to be deleted.
     */
    public void deleteTag(String id);

    /**
     * Retrieves a tag by its unique identifier.
     *
     * @param id The ID of the tag to retrieve.
     * @return The tag with the specified ID, or null if not found.
     */
    public Tag getTagById(String id);

    // Retrieval Methods
    /**
     * Fetches all tags from the database.
     *
     * @return A list of all available tags.
     */
    public List<Tag> fetchAllTags();

    /**
     * Retrieves all notes that are associated with a given tag.
     *
     * @param tagId The ID of the tag.
     * @return A list of notes that have the specified tag.
     */
    public List<Note> fetchAllNotesWithTag(String tagId);

    // Utility Methods
    /**
     * Checks if a tag with the given title exists.
     *
     * @param title The title to check for existence.
     * @return True if a tag with the title exists, otherwise false.
     */
    public boolean existsByTitle(String title);
}