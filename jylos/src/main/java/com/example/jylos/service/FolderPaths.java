package com.example.jylos.service;

import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.dao.interfaces.FolderDAO;

/**
 * The path of a folder in the vault, as links and settings write it ({@code Trabajo/Reuniones}):
 * its id in a folder vault, the chain of folder names with the database.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
final class FolderPaths {

    private static final Logger logger = LoggerConfig.getLogger(FolderPaths.class);

    private FolderPaths() {
    }

    /** The path of the folder {@code folderId}, or {@code ""} when it has none. */
    static String of(FolderDAO folders, boolean folderVault, String folderId) {
        if (folderId == null || folderId.isBlank() || "ROOT".equals(folderId)) {
            return "";
        }
        if (folderVault) {
            return folderId.replace('\\', '/');
        }
        if (folders == null) {
            return "";
        }
        try {
            String path = folders.getPathFolder(folderId);
            if (path == null) {
                return "";
            }
            String clean = path.replace('\\', '/');
            while (clean.startsWith("/")) {
                clean = clean.substring(1);
            }
            return clean;
        } catch (RuntimeException e) {
            logger.fine("No folder path for " + folderId + ": " + e.getMessage());
            return "";
        }
    }
}
