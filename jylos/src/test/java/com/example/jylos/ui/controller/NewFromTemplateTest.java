package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.VaultSettingsStore;
import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.VaultSettings;
import com.example.jylos.data.models.VaultSettings.Templates;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.tests.FxTestSupport;

/**
 * "New from template" creates a note named as the template, in the folder in view, with the
 * template written for it: its properties and text as the template has them, {@code {{title}}}
 * the new note's name; and with no templates it says why instead.
 */
class NewFromTemplateTest {

    @TempDir
    Path vault;

    @Test
    void theNewNoteIsTheTemplateWrittenForIt() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.createDirectories(vault.resolve("Plantillas/Trabajo"));
        Files.writeString(vault.resolve("Plantillas/Trabajo/acta.md"),
                "---\ntags: [reunión]\n---\n# {{title}} ({{date:[hoy]}})\n", StandardCharsets.UTF_8);
        VaultSettingsStore settings = new VaultSettingsStore(vault.resolve(".jylos/settings.json"));
        settings.set(VaultSettings.defaults().withTemplates(new Templates("Plantillas", null, null)));
        List<String> opened = new ArrayList<>();
        NoteCreationSupport creation = creation(settings, opened, new ArrayList<>());

        onFx(() -> {
            creation.createFromTemplate(creation.templates().listing().find("Trabajo/acta").orElseThrow());
            return null;
        });

        assertEquals(List.of("acta.md"), opened);
        assertEquals("---\ntags: [reunión]\n---\n# acta (hoy)\n",
                Files.readString(vault.resolve("acta.md"), StandardCharsets.UTF_8));
    }

    @Test
    void withoutTemplatesItSaysWhy() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        VaultSettingsStore settings = new VaultSettingsStore(vault.resolve(".jylos/settings.json"));
        settings.set(VaultSettings.defaults().withTemplates(new Templates("Plantillas", null, null)));
        List<String> statuses = new ArrayList<>();
        NoteCreationSupport creation = creation(settings, new ArrayList<>(), statuses);

        onFx(() -> {
            creation.createFromTemplate();
            return null;
        });

        assertEquals(List.of("templates.status.folder_not_found Plantillas"), statuses);
    }

    private NoteCreationSupport creation(VaultSettingsStore settings, List<String> opened, List<String> statuses) {
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        FolderService folders = new FolderService(new FolderDAOFileSystem(vault.toString()),
                new NoteDAOFileSystem(vault.toString()));
        NoteCreationSupport creation = new NoteCreationSupport();
        creation.wire(notes, new NoteOperations(notes, folders), () -> true,
                key -> "templates.status.folder_not_found".equals(key) ? key + " {0}" : key, statuses::add,
                () -> null, () -> null, note -> opened.add(note.getId()), note -> { }, id -> { }, () -> { }, () -> { });
        creation.useVault(() -> settings, () -> null, () -> null, () -> null);
        return creation;
    }
}
