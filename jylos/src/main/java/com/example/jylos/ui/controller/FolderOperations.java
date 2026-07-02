package com.example.jylos.ui.controller;

import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.dao.interfaces.FolderDAO;
import com.example.jylos.data.models.Folder;

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
