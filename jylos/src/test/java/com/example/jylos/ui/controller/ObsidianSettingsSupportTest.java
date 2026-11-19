package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.compat.obsidian.ObsidianSettings;
import com.example.jylos.data.dao.VaultSettingsStore;
import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.VaultSettings;
import com.example.jylos.event.events.SystemActionEvent.ActionType;
import com.example.jylos.service.NoteService;

/**
 * Tools > "Import Obsidian settings" shows what would change and takes Obsidian's settings
 * only when the user says so; and a vault with Obsidian's folder is offered it once, the first
 * time it is opened.
 */
class ObsidianSettingsSupportTest {

    @TempDir
    Path vault;

    private final List<String> statuses = new ArrayList<>();
    private final List<List<String>> asked = new ArrayList<>();
    private VaultSettingsStore settings;

    private ObsidianSettingsSupport support(boolean accept) {
        settings = new VaultSettingsStore(vault.resolve(".jylos/settings.json"));
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        ObsidianSettingsSupport support = new ObsidianSettingsSupport();
        Map<ActionType, Runnable> handlers = new EnumMap<>(ActionType.class);
        support.register(handlers, () -> notes, () -> settings, key -> key, statuses::add, () -> null);
        support.confirmWith((changes, firstTime) -> {
            asked.add(changes.stream().map(ObsidianSettings.Change::setting).toList());
            return accept;
        });
        return support;
    }

    private void obsidian() throws Exception {
        Files.createDirectories(vault.resolve(".obsidian"));
        Files.writeString(vault.resolve(".obsidian/templates.json"), "{\"folder\": \"Plantillas\"}", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve(".obsidian/app.json"), "{\"newFileLocation\": \"current\"}", StandardCharsets.UTF_8);
    }

    @Test
    void theCommandTakesObsidiansSettingsWhenTheUserSaysSo() throws Exception {
        obsidian();
        support(true).run();

        assertEquals(List.of(List.of("templates.folder")), asked, "what would change was shown");
        assertEquals("Plantillas", settings.get().templates().folder());
        assertTrue(settings.get().obsidianImportAsked());
        assertEquals(List.of("obsidian_settings.status.done"), statuses);
    }

    @Test
    void cancelledNothingChanges() throws Exception {
        obsidian();
        support(false).run();

        assertEquals(VaultSettings.defaults().templates(), settings.get().templates());
        assertEquals(List.of("obsidian_settings.status.cancelled"), statuses);
    }

    @Test
    void itSaysWhenThereIsNothingToTake() throws Exception {
        support(true).run();
        obsidian();
        settings.set(VaultSettings.defaults().withTemplates(new VaultSettings.Templates("Plantillas", null, null)));
        ObsidianSettingsSupport again = support(true);
        again.run();
        Files.writeString(vault.resolve(".obsidian/app.json"), "{ roto", StandardCharsets.UTF_8);
        again.run();

        assertEquals(List.of("obsidian_settings.status.none", "obsidian_settings.status.same",
                "obsidian_settings.status.not_understood"), statuses);
        assertEquals(List.of(), asked, "nothing asked");
    }

    @Test
    void aVaultWithObsidiansFolderIsOfferedItOnce() throws Exception {
        obsidian();
        ObsidianSettingsSupport support = support(false);

        support.offer();
        support.offer();

        assertEquals(1, asked.size(), "once, even when not taken");
        assertTrue(settings.get().obsidianImportAsked());
        assertEquals(VaultSettings.defaults().templates(), settings.get().templates());
    }

    @Test
    void aVaultWithoutItIsNotOffered() {
        ObsidianSettingsSupport support = support(true);

        support.offer();

        assertEquals(List.of(), asked);
        assertFalse(settings.get().obsidianImportAsked());
        assertEquals(List.of(), statuses);
    }
}
