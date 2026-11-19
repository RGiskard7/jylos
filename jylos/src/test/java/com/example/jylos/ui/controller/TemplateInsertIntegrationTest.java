package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.VaultSettingsStore;
import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.VaultSettings;
import com.example.jylos.data.models.VaultSettings.Templates;
import com.example.jylos.event.EventBus;
import com.example.jylos.service.NoteService;
import com.example.jylos.tests.FxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;

/**
 * Inserting a template in the open note, in the real editor: its text goes where the cursor
 * is, its properties join the note's, and saving writes both to the note's file.
 */
class TemplateInsertIntegrationTest {

    private static final ResourceBundle ES =
            ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es"));

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void theTemplateIsInsertedAndSavedWithTheNotesProperties(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.createDirectories(vault.resolve("Plantillas"));
        Files.writeString(vault.resolve("Plantillas/acta.md"), "---\ntags: [reunión]\nlugar: sala\n---\n## {{title}}\n",
                StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("Nota.md"), "---\ntags:\n  - proyecto\n---\nTexto\n", StandardCharsets.UTF_8);
        VaultSettingsStore settings = new VaultSettingsStore(vault.resolve(".jylos/settings.json"));
        settings.set(VaultSettings.defaults().withTemplates(new Templates("Plantillas", null, null)));
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        List<String> statuses = new CopyOnWriteArrayList<>();

        EditorController editor = onFx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/EditorView.fxml"), ES);
            new Scene(new Pane((Parent) loader.load()), 900, 600);
            EditorController controller = loader.getController();
            controller.wire(EventBus.getInstance(), notes, null, loader.getResources(), note -> { }, status -> { },
                    List::of, false, enabled -> { }, null, null, null);
            controller.loadNote(notes.getNoteById("Nota.md").orElseThrow());
            return controller;
        });
        waitUntil(() -> onFx(() -> "Texto\n".equals(editor.getCurrentContent())));

        onFx(() -> {
            NoteCreationSupport creation = new NoteCreationSupport();
            creation.wire(notes, null, () -> true, ES::getString, statuses::add, () -> null, () -> null,
                    note -> { }, note -> { }, id -> { }, () -> { }, () -> { });
            creation.useVault(() -> settings, () -> editor, () -> null, () -> ES);
            creation.insertTemplate(creation.templates().listing().find("acta").orElseThrow());
            return null;
        });
        waitUntil(() -> onFx(() -> editor.getCurrentContent().contains("## Nota")));
        onFx(() -> {
            editor.handleSave();
            return null;
        });

        assertEquals("---\ntags:\n  - proyecto\n  - reunión\nlugar: sala\n---\n## Nota\nTexto\n",
                Files.readString(vault.resolve("Nota.md"), StandardCharsets.UTF_8));
        assertEquals(List.of(), statuses, "nothing to report");
    }

    /** A template's {@code title:} is a property, as in Obsidian: the note keeps its name and file. */
    @Test
    void aTitlePropertyDoesNotRenameTheNote(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.createDirectories(vault.resolve("Plantillas"));
        Files.writeString(vault.resolve("Plantillas/cabecera.md"),
                "---\ntitle: Título de la Nota\nauthor:\n  - Edu\n---\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("Nota.md"), "Texto\n", StandardCharsets.UTF_8);
        VaultSettingsStore settings = new VaultSettingsStore(vault.resolve(".jylos/settings.json"));
        settings.set(VaultSettings.defaults().withTemplates(new Templates("Plantillas", null, null)));
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));

        EditorController editor = onFx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/EditorView.fxml"), ES);
            new Scene(new Pane((Parent) loader.load()), 900, 600);
            EditorController controller = loader.getController();
            controller.wire(EventBus.getInstance(), notes, null, loader.getResources(), note -> { }, status -> { },
                    List::of, false, enabled -> { }, null, null, null);
            controller.loadNote(notes.getNoteById("Nota.md").orElseThrow());
            return controller;
        });
        waitUntil(() -> onFx(() -> "Texto\n".equals(editor.getCurrentContent())));
        onFx(() -> {
            NoteCreationSupport creation = new NoteCreationSupport();
            creation.wire(notes, null, () -> true, ES::getString, status -> { }, () -> null, () -> null,
                    note -> { }, note -> { }, id -> { }, () -> { }, () -> { });
            creation.useVault(() -> settings, () -> editor, () -> null, () -> ES);
            creation.insertTemplate(creation.templates().listing().find("cabecera").orElseThrow());
            return null;
        });
        onFx(() -> {
            editor.handleSave();
            return null;
        });

        assertTrue(Files.exists(vault.resolve("Nota.md")), "the note keeps its file");
        assertEquals("---\ntitle: Título de la Nota\nauthor:\n  - Edu\n---\nTexto\n",
                Files.readString(vault.resolve("Nota.md"), StandardCharsets.UTF_8));
    }

    private interface Check {
        boolean holds() throws Exception;
    }

    private static void waitUntil(Check check) throws Exception {
        long end = System.currentTimeMillis() + 15_000;
        while (!check.holds() && System.currentTimeMillis() < end) {
            Thread.sleep(50);
        }
        assertTrue(check.holds(), "timed out");
    }
}
