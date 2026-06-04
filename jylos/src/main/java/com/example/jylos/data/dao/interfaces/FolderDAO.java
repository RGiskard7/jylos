package com.example.jylos.data.dao.interfaces;

import java.util.List;

import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Folder;

/**
 * This interface defines the contract for data access operations related to
 * folders (notebooks).
 * It provides methods for creating, updating, deleting, and retrieving folders,
 * as well as managing folder hierarchy and associated notes.
 */
public interface FolderDAO {

    // CRUD Operations
    /**
     * Creates a new folder in the database.
     *
     * @param folder The folder to be created.
     * @return The generated ID of the created folder.
     */
    public String createFolder(Folder folder);

    /**
     * Updates an existing folder.
     *
     * @param folder The folder containing updated data.
     */
    public void updateFolder(Folder folder);

    /**
     * Deletes a folder by its ID.
     *
     * @param id The ID of the folder to be deleted.
     */
    public void deleteFolder(String id);

    /**
     * Retrieves a folder by its unique ID.
     *
     * @param id The ID of the folder.
     * @return The corresponding Folder object, or null if not found.
     */
    public Folder getFolderById(String id);

    // Retrieval Methods
    /**
     * Retrieves a folder that contains a specific note.
     *
     * @param noteId The ID of the note.
     * @return The Folder that contains the given note, or null if not found.
     */
    public Folder getFolderByNoteId(String noteId);

    /**
     * Fetches all folders as a flat list.
     *
     * @return A list of all folders.
     */
    public List<Folder> fetchAllFoldersAsList();

    /**
     * Fetches all folders in a hierarchical tree structure.
     *
     * @return The root folder containing all subfolders.
     */
    public Folder fetchAllFoldersAsTree();

    // Note Management
    // public void addNote(String folderId, String noteId);
    /**
     * Adds a note to a folder.
     *
     * @param folder The folder to which the note should be added.
     * @param note   The note to be added.
     */
    public void addNote(Folder folder, Note note);

    // public void removeNote(String folderId, String noteId);

    /**
     * Removes a note from a folder.
     *
     * @param folder The folder from which the note should be removed.
     * @param note   The note to be removed.
     */
    public void removeNote(Folder folder, Note note);

    /**
     * Loads all notes associated with a given folder.
     *
     * @param folder The folder whose notes should be loaded.
     */
    public void loadNotes(Folder folder);

    // public void addSubFolder(String parentId, String subFolderId);

    // Folder Hierarchy Management
    /**
     * Adds a subfolder to a parent folder.
     *
     * @param parent    The parent folder.
     * @param subFolder The subfolder to be added.
     */
    public void addSubFolder(Folder parent, Folder subFolder);

    // public void removeSubFolder(String parentId, String subFolderId);

    /**
     * Removes a subfolder from a parent folder.
     *
     * @param parentFolder The parent folder.
     * @param subFolder    The subfolder to be removed.
     */
    public void removeSubFolder(Folder parentFolder, Folder subFolder);

    /**
     * Loads all subfolders of a given folder.
     *
     * @param folder The folder whose subfolders should be loaded.
     */
    public void loadSubFolders(Folder folder);

    /**
     * Loads subfolders up to a specified depth.
     *
     * @param folder   The folder whose subfolders should be loaded.
     * @param maxDepth The maximum depth to load.
     */
    public void loadSubFolders(Folder folder, int maxDepth);

    /**
     * Loads all parent folders of a given folder up to a specified depth.
     *
     * @param folder   The folder whose parent folders should be loaded.
     * @param maxDepth The maximum depth to load.
     */
    public void loadParentFolders(Folder folder, int maxDepth);

    /**
     * Loads all parent folders of a given folder.
     *
     * @param folder The folder whose parent folders should be loaded.
     */
    public void loadParentFolders(Folder folder);

    /**
     * Loads the immediate parent folder of a given folder.
     *
     * @param folder The folder whose parent folder should be loaded.
     */
    public void loadParentFolder(Folder folder);

    // Parent Folder Retrieval
    /**
     * Retrieves the parent folder of a given folder by its ID.
     *
     * @param folderId The ID of the folder.
     * @return The parent folder, or null if there is no parent.
     */
    public Folder getParentFolder(String folderId);

    /**
     * Retrieves the parent folder of a given folder object.
     *
     * @param folder The folder whose parent is to be retrieved.
     * @return The parent folder, or null if there is no parent.
     */
    public Folder getParentFolder(Folder folder);

    // Other Utility Methods
    /**
     * Retrieves the full path of a folder based on its ID.
     *
     * @param idFolder The ID of the folder.
     * @return The full path as a string.
     */
    public String getPathFolder(String idFolder);

    /**
     * Checks whether a folder exists with a given title.
     *
     * @param title The title to check.
     * @return True if a folder with the given title exists, false otherwise.
     */
    public boolean existsByTitle(String title);

    // Trash Management
    /**
     * Fetches all deleted folders in a hierarchical tree structure.
     * 
     * @return The root folder of the trash containing deleted subfolders and notes.
     */
    public Folder fetchTrashFolders();

    /**
     * Restores a deleted folder by its ID.
     * 
     * @param id The ID of the folder to restore.
     */
    public void restoreFolder(String id);

    /**
     * Permanently deletes a folder from trash.
     * 
     * @param id The ID of the folder to permanently delete.
     */
    public void permanentlyDeleteFolder(String id);

    /**
     * Refreshes the internal cache if the DAO implementation uses one.
     * Default implementation does nothing (e.g., SQLite does not need it).
     */
    default void refreshCache() {
        // Do nothing by default
    }
}