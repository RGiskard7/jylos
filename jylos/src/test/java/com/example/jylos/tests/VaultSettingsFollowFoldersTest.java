package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.VaultSettingsStore;
import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.sqlite.FolderDAOSQLite;
import com.example.jylos.data.dao.sqlite.NoteDAOSQLite;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.VaultSettings;
import com.example.jylos.data.models.VaultSettings.DailyNotes;
import com.example.jylos.data.models.VaultSettings.Templates;
import com.example.jylos.service.FolderService;

/**
 * The vault's settings name folders by their path; renaming or moving one of them in Jylos
 * changes the settings with it, in a folder vault and with the database alike.
 */
class VaultSettingsFollowFoldersTest {

    @TempDir
    Path dir;

    @Test
    void inAFolderVault() throws Exception {
        Path vault = Files.createDirectories(dir.resolve("vault"));
        Files.createDirectories(vault.resolve("Plantillas"));
        Files.createDirectories(vault.resolve("Archivo"));
        VaultSettingsStore settings = new VaultSettingsStore(vault.resolve(".jylos/settings.json"));
        FolderDAOFileSystem folderDAO = new FolderDAOFileSystem(vault.toString());
        FolderService folders = new FolderService(folderDAO, new NoteDAOFileSystem(vault.toString()), null, settings);
        settings.set(VaultSettings.defaults().withTemplates(new Templates("Plantillas", "YYYY-MM-DD", "HH:mm"))
                .withDaily(new DailyNotes("", "YYYY-MM-DD", "Plantillas/diaria.md")));

        Folder plantillas = folderDAO.getFolderById("Plantillas");
        folders.renameFolder(plantillas, "Modelos");
        assertEquals("Modelos", settings.get().templates().folder());
        assertEquals("Modelos/diaria.md", settings.get().daily().template());

        folders.moveFolderToFolder(folderDAO.getFolderById("Modelos"), folderDAO.getFolderById("Archivo"));
        assertEquals("Archivo/Modelos", settings.get().templates().folder());
        assertEquals("Archivo/Modelos/diaria.md", settings.get().daily().template());
    }

    @Test
    void withTheDatabase() throws Exception {
        SQLiteTestSupport.configureFreshDatabase(dir.resolve("settings.db"));
        Connection connection = SQLiteTestSupport.openConnection();
        try {
            VaultSettingsStore settings = new VaultSettingsStore(dir.resolve("vault-settings.json"));
            FolderDAOSQLite folderDAO = new FolderDAOSQLite(connection);
            FolderService folders = new FolderService(folderDAO, new NoteDAOSQLite(connection), null, settings);
            Folder plantillas = folders.createFolder("Plantillas");
            Folder archivo = folders.createFolder("Archivo");
            settings.set(VaultSettings.defaults().withTemplates(new Templates("Plantillas", "YYYY-MM-DD", "HH:mm")));

            folders.renameFolder(plantillas, "Modelos");
            assertEquals("Modelos", settings.get().templates().folder());

            folders.moveFolderToFolder(folderDAO.getFolderById(plantillas.getId()), archivo);
            assertEquals("Archivo/Modelos", settings.get().templates().folder());
        } finally {
            SQLiteTestSupport.closeAndReset(connection);
        }
    }
}
