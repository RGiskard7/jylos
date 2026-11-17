package com.example.jylos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.FolderColorStore;
import com.example.jylos.data.dao.filesystem.FactoryDAOFileSystem;
import com.example.jylos.data.models.Folder;

/**
 * In a folder vault a folder's id is its path, so it changes when the folder is renamed or
 * moved: its colour goes with it, and its subfolders' too, however the change is made.
 */
class FolderServiceColorsTest {

    @Test
    void aFoldersColourFollowsItWhenRenamedOrMovedAndGoesWithIt(@TempDir Path vault) {
        FactoryDAOFileSystem factory = new FactoryDAOFileSystem(vault.toString());
        FolderColorStore colors = new FolderColorStore(vault.resolve(".jylos/folder-colors.json"));
        FolderService service = new FolderService(factory.getFolderDAO(), factory.getNoteDAO(), colors);

        Folder work = service.createFolder("Trabajo");
        Folder minutes = service.createSubfolder("Actas", work);
        Folder archive = service.createFolder("Archivo");
        service.setFolderColor(work, "#e93147");
        service.setFolderColor(minutes, "#08b94e");
        assertEquals("#e93147", service.folderColor(work));

        Folder renamed = service.renameFolder(work, "Trabajo 2025");
        assertEquals(Map.of("Trabajo 2025", "#e93147", "Trabajo 2025/Actas", "#08b94e"), colors.all(),
                "renamed: " + renamed.getId());

        service.moveFolderToFolder(renamed, archive);
        assertEquals(Map.of("Archivo/Trabajo 2025", "#e93147", "Archivo/Trabajo 2025/Actas", "#08b94e"), colors.all(),
                "moved: " + renamed.getId());
        assertEquals("#e93147", service.folderColor(renamed));

        service.deleteFolder(renamed.getId());
        assertEquals(Map.of(), colors.all(), "a folder sent to the trash takes its colour with it");
        assertNull(service.folderColor(archive));
    }
}
