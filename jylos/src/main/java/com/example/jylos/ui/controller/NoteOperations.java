package com.example.jylos.ui.controller;

import java.io.File;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;

/**
 * Note creation logic, free of controller/UI state.
 *
 * <p>Uses explicit note/folder services supplied at composition time; the controller
 * only supplies the desired title and target folder and reacts to the returned result.</p>
 */
class NoteOperations {

    private static final Logger logger = LoggerConfig.getLogger(NoteOperations.class);
    private static final String ROOT_ID = "ROOT";
    private static final String ALL_NOTES_VIRTUAL_ID = "ALL_NOTES_VIRTUAL";
    private final NoteService noteService;
    private final FolderService folderService;

    NoteOperations(NoteService noteService, FolderService folderService) {
        this.noteService = noteService;
        this.folderService = folderService;
    }

    record NoteCreationResult(boolean success, Note note, String errorMessage) {
    }

    NoteCreationResult createNewNote(String title, Folder currentFolder, boolean isFileSystem) {
        return createNewNote(title, "", currentFolder, isFileSystem);
    }

    /** Creates a note with initial {@code content} (used by daily notes and templates). */
    NoteCreationResult createNewNote(String title, String content, Folder currentFolder, boolean isFileSystem) {
        if (noteService == null) {
            return new NoteCreationResult(false, null, "NoteService is null");
        }
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

            Note createdNote = noteService.createNote(newNote);
            if (createdNote == null || createdNote.getId() == null || createdNote.getId().isBlank()) {
                return new NoteCreationResult(false, null, "Created note has null/blank ID");
            }

            newNote.setId(createdNote.getId());
            if (isConcreteFolder(currentFolder)) {
                if (folderService == null) {
                    return new NoteCreationResult(false, null, "FolderService is null");
                }
                folderService.addNoteToFolder(currentFolder, newNote);
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
