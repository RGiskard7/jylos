package com.example.jylos.ui.controller;

import java.io.File;
import java.util.logging.Logger;

import com.example.jylos.config.AppContext;
import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.dao.interfaces.FolderDAO;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;

/**
 * Folder creation logic, free of controller/UI state.
 *
 * <p>Pure persistence orchestration: results are returned as records and the
 * controller decides how to reflect them in the UI.</p>
 */
class FolderOperations {

    private static final Logger logger = LoggerConfig.getLogger(FolderOperations.class);

    record FolderCreationResult(boolean success, Folder folder, String errorMessage) {
    }

    /** Creates a folder in root or inside {@code currentFolder} depending on {@code createInRoot}. */
    FolderCreationResult createFolder(FolderDAO folderDAO, String folderName, Folder currentFolder,
            boolean createInRoot) {
        if (folderDAO == null) {
            return new FolderCreationResult(false, null, "FolderDAO is null");
        }
        if (folderName == null || folderName.isBlank()) {
            return new FolderCreationResult(false, null, "Folder name is empty");
        }

        try {
            Folder newFolder = new Folder(folderName.trim());
            if (!createInRoot && currentFolder != null) {
                newFolder.setParent(currentFolder);
            }

            String folderId = folderDAO.createFolder(newFolder);
            if (folderId == null || folderId.isBlank()) {
                return new FolderCreationResult(false, null, "Folder ID is null/blank");
            }

            newFolder.setId(folderId);

            if (!createInRoot && currentFolder != null) {
                folderDAO.addSubFolder(currentFolder, newFolder);
            }

            return new FolderCreationResult(true, newFolder, null);
        } catch (Exception e) {
            logger.warning("Failed to create folder: " + e.getMessage());
            return new FolderCreationResult(false, null, e.getMessage());
        }
    }
}

/**
 * Note creation logic, free of controller/UI state.
 *
 * <p>Pulls the note/folder services from {@link AppContext}; the controller only
 * supplies the desired title and target folder and reacts to the returned result.</p>
 */
class NoteOperations {

    private static final Logger logger = LoggerConfig.getLogger(NoteOperations.class);
    private static final String ROOT_ID = "ROOT";
    private static final String ALL_NOTES_VIRTUAL_ID = "ALL_NOTES_VIRTUAL";

    record NoteCreationResult(boolean success, Note note, String errorMessage) {
    }

    NoteCreationResult createNewNote(String title, Folder currentFolder, boolean isFileSystem) {
        return createNewNote(title, "", currentFolder, isFileSystem);
    }

    /** Creates a note with initial {@code content} (used by daily notes and templates). */
    NoteCreationResult createNewNote(String title, String content, Folder currentFolder, boolean isFileSystem) {
        try {
            String safeTitle = (title == null || title.isBlank()) ? "New Note" : title.trim();
            Note newNote = new Note(safeTitle, content != null ? content : "");

            if (isConcreteFolder(currentFolder)) {
                newNote.setParent(currentFolder);
            }

            // FileSystem DAO relies on a pre-seeded ID to infer the parent folder path.
            if (isFileSystem && isConcreteFolder(currentFolder)) {
                String folderPath = currentFolder.getId();
                String sanitizedTitle = safeTitle.replaceAll("[^\\p{L}\\p{N}\\.\\-_ ]", "_");
                newNote.setId(folderPath + File.separator + sanitizedTitle);
            }

            Note createdNote = AppContext.getNoteService().createNote(newNote);
            if (createdNote == null || createdNote.getId() == null || createdNote.getId().isBlank()) {
                return new NoteCreationResult(false, null, "Created note has null/blank ID");
            }

            newNote.setId(createdNote.getId());
            if (isConcreteFolder(currentFolder)) {
                AppContext.getFolderService().addNoteToFolder(currentFolder, newNote);
            }

            return new NoteCreationResult(true, newNote, null);
        } catch (Exception e) {
            logger.warning("Failed to create note: " + e.getMessage());
            return new NoteCreationResult(false, null, e.getMessage());
        }
    }

    private boolean isConcreteFolder(Folder folder) {
        return folder != null
                && folder.getId() != null
                && !folder.getId().isBlank()
                && !ROOT_ID.equals(folder.getId())
                && !ALL_NOTES_VIRTUAL_ID.equals(folder.getId());
    }
}
