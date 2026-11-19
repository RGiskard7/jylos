package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.VaultSettingsStore;
import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.VaultSettings;
import com.example.jylos.data.models.VaultSettings.DailyNotes;
import com.example.jylos.data.models.VaultSettings.FolderTemplate;
import com.example.jylos.data.models.VaultSettings.Templater;
import com.example.jylos.data.models.VaultSettings.Templates;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.tests.FxTestSupport;

/**
 * Templater in the vault, as Templater does it: "New from template" writes its tags when the
 * vault understands them and leaves them otherwise; a note made empty in a folder with a
 * template gets it, from the nearest folder up, when Templater writes the notes made; the daily
 * note's own tags are written then too; and a template Templater stops makes nothing.
 */
class TemplaterIntegrationTest {

    @TempDir
    Path vault;

    private final List<String> opened = new ArrayList<>();
    /** The interface's language, which names the months and days: Spanish, whatever the machine's is. */
    private static final ResourceBundle ES =
            ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es"));

    private final List<String> statuses = new ArrayList<>();
    private final Folder[] inView = {null};

    private NoteCreationSupport creation(VaultSettings value) throws Exception {
        Files.createDirectories(vault.resolve("Plantillas"));
        VaultSettingsStore store = new VaultSettingsStore(vault.resolve(".jylos/settings.json"));
        store.set(value);
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        FolderService folders = new FolderService(new FolderDAOFileSystem(vault.toString()),
                new NoteDAOFileSystem(vault.toString()));
        NoteCreationSupport creation = new NoteCreationSupport();
        creation.wire(notes, new NoteOperations(notes, folders), () -> true,
                key -> "app.untitled".equals(key) ? "Sin título" : key.startsWith("templater.status.unsupported")
                        || key.endsWith("_missing") ? key + " {0}" : key,
                statuses::add, () -> null, () -> inView[0], note -> opened.add(note.getId()), note -> { }, id -> { },
                () -> { }, () -> { });
        creation.useVault(() -> store, () -> null, () -> null, () -> ES);
        creation.useClock(() -> OffsetDateTime.parse("2026-11-19T14:05:09+01:00").atZoneSameInstant(ZoneId.of("Europe/Madrid")));
        return creation;
    }

    private static VaultSettings templater(boolean enabled, boolean onCreate, FolderTemplate... byFolder) {
        return VaultSettings.defaults().withTemplates(new Templates("Plantillas", null, null))
                .withTemplater(new Templater(enabled, onCreate, List.of(byFolder)));
    }

    private void template(String name, String text) throws Exception {
        Files.createDirectories(vault.resolve("Plantillas"));
        Files.writeString(vault.resolve("Plantillas/" + name), text, StandardCharsets.UTF_8);
    }

    private String file(String path) throws Exception {
        return Files.readString(vault.resolve(path), StandardCharsets.UTF_8);
    }

    @Test
    void newFromTemplateWritesItsTagsWhenTheVaultUnderstandsThem() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        template("acta.md", "---\nsemana: <% tp.date.now(\"W\") %>\n---\n# <% tp.file.title %> <%* tR += 'x' %>\n");
        NoteCreationSupport on = creation(templater(true, false));
        onFx(() -> {
            on.createFromTemplate(on.templates().listing().find("acta").orElseThrow());
            return null;
        });

        assertEquals("---\nsemana: 47\n---\n# acta <%* tR += 'x' %>\n", file("acta.md"));
        assertEquals("templater.status.unsupported <%*", statuses.get(statuses.size() - 1), "said last, after it was made");
    }

    @Test
    void withoutTemplaterTheTagsAreLeft() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        template("acta.md", "# <% tp.file.title %> {{title}}\n");
        NoteCreationSupport off = creation(templater(false, false));
        onFx(() -> {
            off.createFromTemplate(off.templates().listing().find("acta").orElseThrow());
            return null;
        });

        assertEquals("# <% tp.file.title %> acta\n", file("acta.md"));
    }

    @Test
    void aNoteMadeEmptyGetsTheTemplateOfItsFolderOrTheNearestAbove() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        template("diaria.md", "# <% tp.file.title %> en <% tp.file.folder() %>\n");
        Files.createDirectories(vault.resolve("crea/sub"));
        Files.createDirectories(vault.resolve("otra"));
        NoteCreationSupport creation = creation(templater(true, true, new FolderTemplate("crea", "Plantillas/diaria")));

        onFx(() -> {
            inView[0] = new Folder("crea", "crea");
            creation.createNewNote();
            inView[0] = new Folder("crea/sub", "sub");
            creation.createNewNote();
            inView[0] = new Folder("otra", "otra");
            creation.createNewNote();
            return null;
        });

        assertEquals(List.of("crea/Sin título.md", "crea/sub/Sin título.md", "otra/Sin título.md"), opened);
        assertEquals("# Sin título en crea\n", file("crea/Sin título.md"));
        assertEquals("# Sin título en sub\n", file("crea/sub/Sin título.md"));
        assertEquals("", file("otra/Sin título.md"));
    }

    @Test
    void folderTemplatesNeedTemplaterToWriteTheNotesMade() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        template("diaria.md", "# <% tp.file.title %>\n");
        Files.createDirectories(vault.resolve("crea"));
        NoteCreationSupport creation = creation(templater(true, false, new FolderTemplate("crea", "Plantillas/diaria.md")));

        onFx(() -> {
            inView[0] = new Folder("crea", "crea");
            creation.createNewNote();
            return null;
        });

        assertEquals("", file("crea/Sin título.md"));
    }

    @Test
    void theDailyNotesOwnTagsAreWrittenWhenTemplaterWritesTheNotesMade() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        template("diaria.md", "---\nfecha: <% tp.date.now(\"YYYY-MM-DD\") %>\ndía: <% tp.date.now(\"dddd\") %>\n---\n# {{title}}\n");
        NoteCreationSupport on = creation(templater(true, true).withDaily(new DailyNotes("", "", "Plantillas/diaria")));
        onFx(() -> {
            on.openDailyNote();
            return null;
        });
        assertEquals("---\nfecha: 2026-11-19\ndía: jueves\n---\n# 2026-11-19\n", file("2026-11-19.md"));

        Files.delete(vault.resolve("2026-11-19.md"));
        NoteCreationSupport off = creation(templater(true, false).withDaily(new DailyNotes("", "", "Plantillas/diaria")));
        onFx(() -> {
            off.openDailyNote();
            return null;
        });
        assertEquals("---\nfecha: <% tp.date.now(\"YYYY-MM-DD\") %>\ndía: <% tp.date.now(\"dddd\") %>\n---\n# 2026-11-19\n",
                file("2026-11-19.md"), "as Obsidian's daily note, unless Templater writes the notes made");
    }

    @Test
    void aTemplateTemplaterStopsMakesNothing() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        template("mal.md", "<% tp.date.now(\"YYYY\", 0, \"no es fecha\", \"YYYY-MM-DD\") %>\n");
        NoteCreationSupport creation = creation(templater(true, false));
        onFx(() -> {
            creation.createFromTemplate(creation.templates().listing().find("mal").orElseThrow());
            return null;
        });

        assertEquals(List.of("templater.status.failed.reference_date"), statuses);
        assertFalse(Files.exists(vault.resolve("mal.md")));
    }

    @Test
    void aMissingFolderTemplateIsSaidAndTheNoteIsMadeEmpty() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.createDirectories(vault.resolve("crea"));
        NoteCreationSupport creation = creation(templater(true, true, new FolderTemplate("crea", "Plantillas/no-está.md")));
        onFx(() -> {
            inView[0] = new Folder("crea", "crea");
            creation.createNewNote();
            return null;
        });

        assertEquals("", file("crea/Sin título.md"));
        assertEquals("templater.status.folder_template_missing Plantillas/no-está.md", statuses.get(0));
    }
}
