package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.VaultSettingsStore;
import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.VaultSettings;
import com.example.jylos.data.models.VaultSettings.DailyNotes;
import com.example.jylos.data.models.VaultSettings.NewNoteLocation;
import com.example.jylos.data.models.VaultSettings.NewNotes;
import com.example.jylos.data.models.VaultSettings.Templates;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.tests.FxTestSupport;

/**
 * Today's daily note and where new notes go, as in Obsidian: the daily note is made at its path
 * with its template (whose {@code {{date}}} is Obsidian's fixed format, not the templates'),
 * opened when it is there, made in the folders a format with slashes names, and not made when
 * its folder or template is missing; a new note goes to the folder in view, the root or a
 * folder chosen.
 */
class DailyNoteTest {

    @TempDir
    Path vault;

    private final List<String> opened = new ArrayList<>();
    /** The interface's language, which names the months and days: Spanish, whatever the machine's is. */
    private static final ResourceBundle ES =
            ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es"));

    private final List<String> statuses = new ArrayList<>();
    private VaultSettingsStore settings;
    private final Folder[] inView = {null};

    private NoteCreationSupport creation() {
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        FolderService folders = new FolderService(new FolderDAOFileSystem(vault.toString()),
                new NoteDAOFileSystem(vault.toString()));
        VaultSettingsStore store = settings != null ? settings
                : new VaultSettingsStore(vault.resolve(".jylos/settings.json"));
        NoteCreationSupport creation = new NoteCreationSupport();
        creation.wire(notes, new NoteOperations(notes, folders), () -> true,
                key -> key.startsWith("daily.status.") ? key + " {0}" : "app.untitled".equals(key) ? "Sin título" : key,
                statuses::add, () -> null, () -> inView[0], note -> opened.add(note.getId()), note -> { }, id -> { },
                () -> { }, () -> { });
        creation.useVault(() -> store, () -> null, () -> null, () -> ES);
        creation.useClock(() -> LocalDateTime.parse("2026-11-19T09:30:00").atZone(ZoneId.of("Europe/Madrid")));
        return creation;
    }

    private void settings(VaultSettings value) {
        settings = new VaultSettingsStore(vault.resolve(".jylos/settings.json"));
        settings.set(value);
    }

    private String file(String path) throws Exception {
        return Files.readString(vault.resolve(path), StandardCharsets.UTF_8);
    }

    @Test
    void itIsMadeAtItsPathWithItsTemplate() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.createDirectories(vault.resolve("Diario"));
        Files.createDirectories(vault.resolve("Plantillas"));
        Files.writeString(vault.resolve("Plantillas/diaria.md"), "# {{title}}\n{{date}} {{time}} {{date:dddd}}\n",
                StandardCharsets.UTF_8);
        settings(VaultSettings.defaults().withTemplates(new Templates("Plantillas", "DD/MM/YYYY", "H.mm"))
                .withDaily(new DailyNotes("Diario", "DD-MM-YYYY", "Plantillas/diaria")));
        NoteCreationSupport creation = creation();

        onFx(() -> {
            creation.openDailyNote();
            return null;
        });

        assertEquals(List.of("Diario/19-11-2026.md"), opened);
        assertEquals("# 19-11-2026\n2026-11-19 09:30 jueves\n", file("Diario/19-11-2026.md"),
                "{{date}} and {{time}} in Obsidian's fixed formats, not the templates'");
    }

    @Test
    void whenItIsThereItIsOpened() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.createDirectories(vault.resolve("Diario"));
        Files.writeString(vault.resolve("Diario/2026-11-19.md"), "ya escrita\n", StandardCharsets.UTF_8);
        Files.createDirectories(vault.resolve("Otra"));
        settings(VaultSettings.defaults().withDaily(new DailyNotes("Diario", "", "")));
        NoteCreationSupport creation = creation();

        onFx(() -> {
            creation.openDailyNote();
            return null;
        });

        assertEquals(List.of("Diario/2026-11-19.md"), opened);
        assertEquals("ya escrita\n", file("Diario/2026-11-19.md"));
    }

    @Test
    void aFormatWithSlashesMakesItsFolders() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.createDirectories(vault.resolve("Diario/2026"));
        settings(VaultSettings.defaults().withDaily(new DailyNotes("Diario", "YYYY/MM/DD", "")));
        NoteCreationSupport creation = creation();

        onFx(() -> {
            creation.openDailyNote();
            return null;
        });

        assertEquals(List.of("Diario/2026/11/19.md"), opened);
        assertEquals("", file("Diario/2026/11/19.md"), "without a template it is empty, as in Obsidian");
    }

    @Test
    void withoutItsFolderOrItsTemplateNothingIsMade() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        settings(VaultSettings.defaults().withDaily(new DailyNotes("Diario", "", "")));
        NoteCreationSupport creation = creation();
        onFx(() -> {
            creation.openDailyNote();
            return null;
        });
        settings(VaultSettings.defaults().withDaily(new DailyNotes("", "", "Plantillas/no-está")));
        NoteCreationSupport again = creation();
        onFx(() -> {
            again.openDailyNote();
            return null;
        });

        assertEquals(List.of(), opened);
        assertEquals(List.of("daily.status.folder_not_found Diario", "daily.status.template_not_found Plantillas/no-está.md"),
                statuses);
        try (Stream<Path> files = Files.list(vault)) {
            assertFalse(files.anyMatch(p -> p.toString().endsWith(".md")), "no note made");
        }
    }

    @Test
    void aNewNoteGoesWhereTheVaultSays() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.createDirectories(vault.resolve("Proyectos"));
        Files.createDirectories(vault.resolve("Entrada"));
        inView[0] = new Folder("Proyectos", "Proyectos");

        settings(VaultSettings.defaults());
        NoteCreationSupport inFolderInView = creation();
        settings(VaultSettings.defaults().withNewNotes(new NewNotes(NewNoteLocation.ROOT, "")));
        NoteCreationSupport atRoot = creation();
        settings(VaultSettings.defaults().withNewNotes(new NewNotes(NewNoteLocation.FOLDER, "Entrada")));
        NoteCreationSupport inEntrada = creation();
        settings(VaultSettings.defaults().withNewNotes(new NewNotes(NewNoteLocation.FOLDER, "No existe")));
        NoteCreationSupport missing = creation();

        onFx(() -> {
            inFolderInView.createNewNote();
            atRoot.createNewNote();
            inEntrada.createNoteWithTitle("Enlazada");
            missing.createNoteWithTitle("Huérfana");
            return null;
        });

        assertEquals(List.of("Proyectos/Sin título.md", "Sin título.md", "Entrada/Enlazada.md", "Huérfana.md"), opened);
    }
}
