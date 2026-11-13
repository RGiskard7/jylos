package com.example.jylos.ui.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Folder;
import com.example.jylos.service.FolderService;

/**
 * Folder creation logic, free of controller/UI state.
 *
 * <p>Pure persistence orchestration: results are returned as records and the
 * controller decides how to reflect them in the UI.</p>
 */
class FolderOperations {

    private static final Logger logger = LoggerConfig.getLogger(FolderOperations.class);
    private FolderService folderService;

    record FolderCreationResult(boolean success, Folder folder, String errorMessage) {
    }

    void wire(FolderService folderService) {
        this.folderService = folderService;
    }

    /** Creates a folder in root or inside {@code currentFolder} depending on {@code createInRoot}. */
    FolderCreationResult createFolder(String folderName, Folder currentFolder, boolean createInRoot) {
        if (folderService == null) {
            return new FolderCreationResult(false, null, "FolderService is null");
        }
        if (folderName == null || folderName.isBlank()) {
            return new FolderCreationResult(false, null, "Folder name is empty");
        }

        try {
            Folder createdFolder = createInRoot || currentFolder == null
                    ? folderService.createFolder(folderName.trim())
                    : folderService.createSubfolder(folderName.trim(), currentFolder);
            if (createdFolder == null || createdFolder.getId() == null || createdFolder.getId().isBlank()) {
                return new FolderCreationResult(false, null, "Folder ID is null/blank");
            }
            return new FolderCreationResult(true, createdFolder, null);
        } catch (Exception e) {
            logger.warning("Failed to create folder: " + e.getMessage());
            return new FolderCreationResult(false, null, e.getMessage());
        }
    }

    /**
     * Creates a folder named {@code base} ("Sin nombre"), or "base 1", "base 2", ... the first
     * name free among the folders next to it, as Obsidian does before letting it be renamed.
     */
    FolderCreationResult createUntitledFolder(String base, Folder parent, boolean inRoot) {
        if (folderService == null) {
            return new FolderCreationResult(false, null, "FolderService is null");
        }
        boolean root = inRoot || parent == null;
        List<String> taken = new ArrayList<>();
        try {
            Folder where = root ? new Folder("ROOT", "") : parent;
            for (Folder sibling : folderService.getSubfolders(where)) {
                taken.add(sibling.getTitle());
            }
        } catch (RuntimeException e) {
            logger.warning("Could not list folders before creating one: " + e.getMessage());
        }
        return createFolder(com.example.jylos.util.UntitledNames.next(base, taken), parent, root);
    }
}
