package com.example.jylos.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.dao.interfaces.FolderDAO;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.interfaces.Component;

/**
 * Service layer for folder-related business logic.
 * Provides a clean API for folder operations, including hierarchical
 * management.
 * 
 * <p>
 * This service handles:
 * </p>
 * <ul>
 * <li>CRUD operations for folders</li>
 * <li>Folder hierarchy management (parent-child relationships)</li>
 * <li>Note assignment to folders</li>
 * <li>Folder tree traversal</li>
 * </ul>
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.1.0
 */
public class FolderService {

    private static final Logger logger = LoggerConfig.getLogger(FolderService.class);

    private final FolderDAO folderDAO;
    private final NoteDAO noteDAO;

    /**
     * Creates a new FolderService with the required DAOs.
     * 
     * @param folderDAO Data access object for folders
     * @param noteDAO   Data access object for notes
     */
    public FolderService(FolderDAO folderDAO, NoteDAO noteDAO) {
        this.folderDAO = folderDAO;
        this.noteDAO = noteDAO;
        logger.info("FolderService initialized");
    }

    // ==================== CRUD Operations ====================

    /**
     * Creates a new folder in the root.
     * 
     * @param title The folder title
     * @return The created folder with its generated ID
     */
    /**
     * Creates a new folder in the root.
     * 
     * @param title The folder title
     * @return The created folder with its generated ID
     */
    public Folder createFolder(String title) {
        Folder folder = new Folder(title);
        String folderId = folderDAO.createFolder(folder);
        folder.setId(folderId);
        logger.info("Created folder: " + title + " (ID: " + folderId + ")");
        return folder;
    }

    /**
     * Creates a new subfolder under a parent folder.
     * 
     * @param title        The subfolder title
     * @param parentFolder The parent folder
     * @return The created subfolder
     */
    public Folder createSubfolder(String title, Folder parentFolder) {
        Folder subfolder = createFolder(title);
        if (parentFolder != null && parentFolder.getId() != null) {
            folderDAO.addSubFolder(parentFolder, subfolder);
            logger.info("Added subfolder '" + title + "' to parent: " + parentFolder.getTitle());
        }
        return subfolder;
    }

    /**
     * Retrieves a folder by its ID.
     * 
     * @param id The folder ID
     * @return Optional containing the folder if found
     */
    public Optional<Folder> getFolderById(String id) {
        Folder folder = folderDAO.getFolderById(id);
        return Optional.ofNullable(folder);
    }

    /**
     * Updates an existing folder.
     * 
     * @param folder The folder with updated data
     */
    public void updateFolder(Folder folder) {
        if (folder == null || folder.getId() == null) {
            throw new IllegalArgumentException("Folder or folder ID cannot be null");
        }
        folderDAO.updateFolder(folder);
        logger.info("Updated folder: " + folder.getTitle());
    }

    /**
     * Renames a folder.
     * 
     * @param folder  The folder to rename
     * @param newName The new name
     */
    public void renameFolder(Folder folder, String newName) {
        if (folder == null || newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("Folder and new name cannot be null or empty");
        }
        folder.setTitle(newName.trim());
        updateFolder(folder);
    }

    /**
     * Deletes a folder by its ID.
     * Notes in the folder will be moved to root (no parent).
     * 
     * @param folderId The ID of the folder to delete
     */
    public void deleteFolder(String folderId) {
        folderDAO.deleteFolder(folderId);
        folderDAO.refreshCache();
        noteDAO.refreshCache();
        logger.info("Deleted folder ID: " + folderId);
    }

    /**
     * Permanently deletes a folder.
     * 
     * @param folderId The ID of the folder to delete
     */
    public void permanentlyDeleteFolder(String folderId) {
        folderDAO.permanentlyDeleteFolder(folderId);
        folderDAO.refreshCache();
        noteDAO.refreshCache();
        logger.info("Permanently deleted folder ID: " + folderId);
    }

    /**
     * Restores a deleted folder from the trash.
     * 
     * @param folderId The ID of the folder to restore
     */
    public void restoreFolder(String folderId) {
        folderDAO.restoreFolder(folderId);
        folderDAO.refreshCache();
        noteDAO.refreshCache();
        logger.info("Restored folder ID: " + folderId);
    }

    // ==================== Retrieval Methods ====================

    /**
     * Fetches all folders as a flat list.
     * 
     * @return List of all folders
     */
    public List<Folder> getAllFolders() {
        return folderDAO.fetchAllFoldersAsList();
    }

    /**
     * Fetches only root folders (folders without a parent).
     * 
     * @return List of root folders
     */
    public List<Folder> getRootFolders() {
        List<Folder> allFolders = getAllFolders();
        return allFolders.stream()
                .filter(folder -> {
                    Folder parent = folderDAO.getParentFolder(folder.getId());
                    return parent == null;
                })
                .collect(Collectors.toList());
    }

    /**
     * Fetches all deleted folders (the trash root).
     * 
     * @return The root folder of the trash containing deleted subfolders
     */
    public Folder getTrashFolders() {
        return folderDAO.fetchTrashFolders();
    }

    /**
     * Fetches subfolders of a parent folder.
     * 
     * @param parentFolder The parent folder
     * @return List of subfolders
     */
    public List<Folder> getSubfolders(Folder parentFolder) {
        if (parentFolder == null || parentFolder.getId() == null) {
            return new ArrayList<>();
        }

        folderDAO.loadSubFolders(parentFolder);
        return parentFolder.getChildren().stream()
                .filter(c -> c instanceof Folder)
                .map(c -> (Folder) c)
                .collect(Collectors.toList());
    }

    /**
     * Loads subfolders of a parent folder recursively up to a certain depth.
     * 
     * @param parentFolder The parent folder
     * @param depth        The depth to load
     */
    public void loadSubfolders(Folder parentFolder, int depth) {
        if (parentFolder != null && parentFolder.getId() != null) {
            folderDAO.loadSubFolders(parentFolder, depth);
        }
    }

    /**
     * Gets the parent folder of a folder.
     * 
     * @param folder The folder
     * @return Optional containing the parent folder if it exists
     */
    public Optional<Folder> getParentFolder(Folder folder) {
        if (folder == null || folder.getId() == null) {
            return Optional.empty();
        }
        Folder parent = folderDAO.getParentFolder(folder.getId());
        return Optional.ofNullable(parent);
    }

    /**
     * Loads the complete folder hierarchy starting from root.
     * 
     * @return List of root folders with loaded subfolders
     */
    public List<Folder> loadFolderHierarchy() {
        List<Folder> rootFolders = getRootFolders();
        for (Folder root : rootFolders) {
            loadSubfoldersRecursively(root);
        }
        return rootFolders;
    }

    /**
     * Recursively loads subfolders.
     * 
     * @param folder The folder to load subfolders for
     */
    private void loadSubfoldersRecursively(Folder folder) {
        folderDAO.loadSubFolders(folder);
        for (Component child : folder.getChildren()) {
            if (child instanceof Folder) {
                loadSubfoldersRecursively((Folder) child);
            }
        }
    }

    // ==================== Note Management ====================

    /**
     * Gets all notes in a folder.
     * 
     * @param folder The folder
     * @return List of notes in the folder
     */
    public List<Note> getNotesInFolder(Folder folder) {
        if (folder == null || folder.getId() == null) {
            return new ArrayList<>();
        }
        return noteDAO.fetchNotesByFolderId(folder.getId());
    }

    /**
     * Adds a note to a folder.
     * 
     * @param folder The target folder
     * @param note   The note to add
     */
    public void addNoteToFolder(Folder folder, Note note) {
        if (folder == null || note == null) {
            throw new IllegalArgumentException("Folder and note cannot be null");
        }
        folderDAO.addNote(folder, note);
        logger.info("Added note '" + note.getTitle() + "' to folder: " + folder.getTitle());
    }

    /**
     * Moves a note to a different folder.
     * 
     * @param note      The note to move
     * @param newFolder The target folder (null for root)
     */
    public void moveNoteToFolder(Note note, Folder newFolder) {
        if (note == null) {
            throw new IllegalArgumentException("Note cannot be null");
        }

        // Remove from current folder and add to new one
        if (newFolder != null) {
            folderDAO.addNote(newFolder, note);
            logger.info("Moved note '" + note.getTitle() + "' to folder: " + newFolder.getTitle());
        } else {
            // Moving to root - would need a removeNoteFromFolder method in DAO
            logger.info("Moved note '" + note.getTitle() + "' to root");
        }
    }

    /**
     * Moves a folder under a new parent folder.
     *
     * @param folder      Folder to move
     * @param targetParent Destination parent folder
     */
    public void moveFolderToFolder(Folder folder, Folder targetParent) {
        if (folder == null || folder.getId() == null) {
            throw new IllegalArgumentException("Folder cannot be null");
        }
        if (targetParent == null || targetParent.getId() == null) {
            throw new IllegalArgumentException("Target parent cannot be null");
        }
        if (folder.getId().equals(targetParent.getId())) {
            throw new IllegalArgumentException("Folder cannot be moved into itself");
        }
        folderDAO.addSubFolder(targetParent, folder);
        folderDAO.refreshCache();
        noteDAO.refreshCache();
        logger.info("Moved folder '" + folder.getTitle() + "' to folder: " + targetParent.getTitle());
    }

    /**
     * Validates whether a folder can be moved under a target parent.
     */
    public boolean canMoveFolder(Folder folder, Folder targetParent) {
        if (folder == null || targetParent == null || folder.getId() == null || targetParent.getId() == null) {
            return false;
        }
        String folderId = folder.getId();
        String targetId = targetParent.getId();
        if (folderId.equals(targetId)) {
            return false;
        }
        Folder cursor = targetParent;
        int safety = 0;
        while (cursor != null && cursor.getId() != null && safety++ < 512) {
            if (folderId.equals(cursor.getId())) {
                return false;
            }
            cursor = folderDAO.getParentFolder(cursor.getId());
        }
        return true;
    }

    // ==================== Path Methods ====================

    /**
     * Gets the full path of a folder (e.g., "/Parent/Child/Current").
     * 
     * @param folder The folder
     * @return The folder path
     */
    public String getFolderPath(Folder folder) {
        if (folder == null) {
            return "/";
        }
        return folder.getPath();
    }

    /**
     * Gets the breadcrumb path as a list of folders from root to current.
     * 
     * @param folder The folder
     * @return List of folders in the path
     */
    public List<Folder> getBreadcrumbPath(Folder folder) {
        List<Folder> path = new ArrayList<>();
        Folder current = folder;

        while (current != null) {
            path.add(0, current);
            current = folderDAO.getParentFolder(current.getId());
        }

        return path;
    }

    // ==================== Utility Methods ====================

    /**
     * Checks if a folder name already exists at root level.
     * 
     * @param name The folder name to check
     * @return true if a folder with this name exists at root
     */
    public boolean folderExistsAtRoot(String name) {
        return getRootFolders().stream()
                .anyMatch(f -> f.getTitle().equalsIgnoreCase(name));
    }

    /**
     * Checks if a subfolder name already exists under a parent.
     * 
     * @param name   The subfolder name to check
     * @param parent The parent folder
     * @return true if a subfolder with this name exists
     */
    public boolean subfolderExists(String name, Folder parent) {
        return getSubfolders(parent).stream()
                .anyMatch(f -> f.getTitle().equalsIgnoreCase(name));
    }

    /**
     * Gets the total count of folders.
     * 
     * @return Total folder count
     */
    public int getFolderCount() {
        return getAllFolders().size();
    }

    /**
     * Gets the count of notes in a folder.
     * 
     * @param folder The folder
     * @return Note count in the folder
     */
    public int getNoteCount(Folder folder) {
        return getNotesInFolder(folder).size();
    }
}
