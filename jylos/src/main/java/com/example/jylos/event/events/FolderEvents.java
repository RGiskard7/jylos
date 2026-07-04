package com.example.jylos.event.events;

import com.example.jylos.data.models.Folder;
import com.example.jylos.event.AppEvent;

/**
 * Folder-related events for the application.
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.1.0
 */
public final class FolderEvents {

    private FolderEvents() {
        // Prevent instantiation
    }

    /**
     * Event fired when a folder is created.
     */
    public static class FolderCreatedEvent extends AppEvent {
        private final Folder folder;
        private final Folder parentFolder;

        public FolderCreatedEvent(Folder folder, Folder parentFolder) {
            this.folder = folder;
            this.parentFolder = parentFolder;
        }

        public Folder getFolder() {
            return folder;
        }

        public Folder getParentFolder() {
            return parentFolder;
        }
    }

    /**
     * Event fired when a folder is renamed.
     */
    public static class FolderRenamedEvent extends AppEvent {
        private final Folder folder;
        private final String oldName;
        private final String newName;

        public FolderRenamedEvent(Folder folder, String oldName, String newName) {
            this.folder = folder;
            this.oldName = oldName;
            this.newName = newName;
        }

        public Folder getFolder() {
            return folder;
        }

        public String getOldName() {
            return oldName;
        }

        public String getNewName() {
            return newName;
        }
    }

    /**
     * Event fired when a folder is deleted.
     */
    public static class FolderDeletedEvent extends AppEvent {
        private final String folderId;
        private final String folderTitle;

        public FolderDeletedEvent(String folderId, String folderTitle) {
            this.folderId = folderId;
            this.folderTitle = folderTitle;
        }

        public String getFolderId() {
            return folderId;
        }

        public String getFolderTitle() {
            return folderTitle;
        }
    }

    /**
     * Event fired when folders should be refreshed.
     */
    public static class FoldersRefreshRequestedEvent extends AppEvent {
        public FoldersRefreshRequestedEvent() {
            super();
        }
    }
}
