package com.example.jylos.data.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.models.VaultSettings;
import com.example.jylos.data.models.VaultSettings.DailyNotes;
import com.example.jylos.data.models.VaultSettings.FolderTemplate;
import com.example.jylos.data.models.VaultSettings.NewNoteLocation;
import com.example.jylos.data.models.VaultSettings.NewNotes;
import com.example.jylos.data.models.VaultSettings.Startup;
import com.example.jylos.data.models.VaultSettings.StartupFront;
import com.example.jylos.data.models.VaultSettings.StartupTabs;
import com.example.jylos.data.models.VaultSettings.Templater;
import com.example.jylos.data.models.VaultSettings.Templates;

/** A vault's own settings (templates, daily note, new notes, Templater), kept in a file in it. */
class VaultSettingsStoreTest {

    @Test
    void withNoFileTheSettingsAreTheDefaultsAndNothingIsWritten(@TempDir Path dir) {
        Path file = dir.resolve(".jylos/settings.json");

        VaultSettings settings = new VaultSettingsStore(file).get();

        assertEquals(new Templates("", "YYYY-MM-DD", "HH:mm"), settings.templates());
        assertEquals(new DailyNotes("", "YYYY-MM-DD", ""), settings.daily());
        assertEquals(new NewNotes(NewNoteLocation.CURRENT, ""), settings.newNotes());
        assertEquals(new Templater(false, false, List.of()), settings.templater());
        assertFalse(settings.obsidianImportAsked());
        assertFalse(Files.exists(file), "reading writes nothing");
    }

    @Test
    void theSettingsAreKeptForNextTime(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(".jylos/settings.json");
        VaultSettings chosen = VaultSettings.defaults()
                .withTemplates(new Templates("Plantillas", "DD/MM/YYYY", "H:mm"))
                .withDaily(new DailyNotes("Diario", "YYYY/MM/DD", "Plantillas/diaria.md"))
                .withNewNotes(new NewNotes(NewNoteLocation.FOLDER, "Entrada"))
                .withTemplater(new Templater(true, true, List.of(new FolderTemplate("crea", "Plantillas/diaria.md"))))
                .withObsidianImportAsked(true);

        new VaultSettingsStore(file).set(chosen);

        assertEquals(chosen, new VaultSettingsStore(file).get(), "read back by a new store");
        String json = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"templates\"") && json.contains("\"Plantillas\""), json);
    }

    @Test
    void aFileWithSomeSettingsGivesTheDefaultsForTheRest(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("settings.json");
        Files.writeString(file, "{\"templates\": {\"folder\": \"Modelos\"}, \"daily\": {}}", StandardCharsets.UTF_8);

        VaultSettings settings = new VaultSettingsStore(file).get();

        assertEquals(new Templates("Modelos", "YYYY-MM-DD", "HH:mm"), settings.templates());
        assertEquals(new DailyNotes("", "YYYY-MM-DD", ""), settings.daily());
        assertEquals(new NewNotes(NewNoteLocation.CURRENT, ""), settings.newNotes());
    }

    /** What opens with the vault: by default the last session, as before; a file of an older version reads so. */
    @Test
    void whatOpensWithTheVaultIsKeptAndAnOlderFileReadsAsTheLastSession(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("settings.json");
        Files.writeString(file, "{\"templates\": {\"folder\": \"Modelos\"}}", StandardCharsets.UTF_8);
        assertEquals(new Startup("", StartupTabs.LAST_SESSION, StartupFront.LAST_ACTIVE),
                new VaultSettingsStore(file).get().startup());

        new VaultSettingsStore(file).set(VaultSettings.defaults()
                .withStartup(new Startup("Inicio/Panel.canvas", StartupTabs.PINNED, StartupFront.HOME)));

        assertEquals(new Startup("Inicio/Panel.canvas", StartupTabs.PINNED, StartupFront.HOME),
                new VaultSettingsStore(file).get().startup());
    }

    @Test
    void anUnreadableFileIsIgnoredAndReplacedOnTheNextChange(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("settings.json");
        Files.writeString(file, "{ esto no es json", StandardCharsets.UTF_8);
        VaultSettingsStore store = new VaultSettingsStore(file);
        assertEquals(VaultSettings.defaults(), store.get());

        store.set(store.get().withObsidianImportAsked(true));

        assertTrue(new VaultSettingsStore(file).get().obsidianImportAsked());
    }

    @Test
    void aFolderThatMovesTakesItsSettingsAlong(@TempDir Path dir) {
        Path file = dir.resolve("settings.json");
        VaultSettingsStore store = new VaultSettingsStore(file);
        store.set(VaultSettings.defaults()
                .withTemplates(new Templates("Plantillas", "YYYY-MM-DD", "HH:mm"))
                .withDaily(new DailyNotes("Plantillas viejas", "YYYY-MM-DD", "Plantillas/Sub/diaria.md"))
                .withNewNotes(new NewNotes(NewNoteLocation.FOLDER, "Plantillas"))
                .withTemplater(new Templater(true, true, List.of(new FolderTemplate("Plantillas/Sub", "x.md"))))
                .withStartup(new Startup("Plantillas/Inicio.md", StartupTabs.LAST_SESSION, StartupFront.HOME)));

        store.movePath("Plantillas", "Archivo/Modelos");

        VaultSettings moved = new VaultSettingsStore(file).get();
        assertEquals("Archivo/Modelos", moved.templates().folder());
        assertEquals("Plantillas viejas", moved.daily().folder(), "another folder that only starts the same");
        assertEquals("Archivo/Modelos/Sub/diaria.md", moved.daily().template());
        assertEquals("Archivo/Modelos", moved.newNotes().folder());
        assertEquals(List.of(new FolderTemplate("Archivo/Modelos/Sub", "x.md")), moved.templater().folderTemplates());
        assertEquals("Archivo/Modelos/Inicio.md", moved.startup().home());
    }
}
