package com.example.jylos.compat.obsidian;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.models.VaultSettings;
import com.example.jylos.data.models.VaultSettings.DailyNotes;
import com.example.jylos.data.models.VaultSettings.FolderTemplate;
import com.example.jylos.data.models.VaultSettings.NewNoteLocation;
import com.example.jylos.data.models.VaultSettings.NewNotes;
import com.example.jylos.data.models.VaultSettings.Templater;
import com.example.jylos.data.models.VaultSettings.Templates;

/**
 * Obsidian's settings of a vault, read from {@code .obsidian} and never written, as Jylos'
 * settings: the templates folder and formats, the daily note, where new notes go and
 * Templater's, each as Obsidian has it (its defaults where a file or a key is missing), only
 * for the plugins Obsidian has on; with what would change from Jylos' settings now.
 */
class ObsidianSettingsTest {

    @TempDir
    Path vault;

    private void write(String name, String json) throws Exception {
        Path file = vault.resolve(".obsidian").resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, json, StandardCharsets.UTF_8);
    }

    /** A vault set up as the one Jylos is tried with: Templates and Daily notes on, Templater with a folder template. */
    private void obsidianAsTried() throws Exception {
        write("core-plugins.json", "{\"file-explorer\": true, \"daily-notes\": true, \"templates\": true, \"canvas\": true}");
        write("community-plugins.json", "[\"dataview\", \"templater-obsidian\"]");
        write("templates.json", "{\"folder\": \"Creación de notas/Plantillas\"}");
        write("app.json", "{\"alwaysUpdateLinks\": true, \"attachmentFolderPath\": \"/\"}");
        write("plugins/templater-obsidian/data.json", "{\"templates_folder\": \"\", \"trigger_on_file_creation\": true,"
                + " \"enable_folder_templates\": true, \"folder_templates\": [{\"folder\": \"Creación de notas/Plantillas\","
                + " \"template\": \"\"}, {\"folder\": \"crea\", \"template\": \"Creación de notas/Plantillas/daily-template.md\"},"
                + " {\"folder\": \"\", \"template\": \"\"}]}");
    }

    @Test
    void obsidiansSettingsAreJylossOwn() throws Exception {
        obsidianAsTried();

        ObsidianSettings.Proposal proposal = ObsidianSettings.propose(vault, VaultSettings.defaults()).orElseThrow();

        assertEquals(new Templates("Creación de notas/Plantillas", "YYYY-MM-DD", "HH:mm"), proposal.settings().templates());
        assertEquals(new DailyNotes("", "YYYY-MM-DD", ""), proposal.settings().daily(), "no daily-notes.json: its defaults");
        assertEquals(new NewNotes(NewNoteLocation.ROOT, ""), proposal.settings().newNotes(), "Obsidian's default: the root");
        assertEquals(new Templater(true, true, List.of(new FolderTemplate("crea",
                "Creación de notas/Plantillas/daily-template.md"))), proposal.settings().templater(),
                "only the folder templates with a template");
        assertEquals(List.of("templates.folder", "newNotes.location", "templater.enabled", "templater.triggerOnCreate",
                "templater.folderTemplates"), proposal.changes().stream().map(ObsidianSettings.Change::setting).toList(),
                "what differs from Jylos' settings now");
    }

    @Test
    void theirFormatsAndFoldersAreTakenAsObsidianHasThem() throws Exception {
        write("core-plugins.json", "[\"templates\", \"daily-notes\"]");
        write("templates.json", "{\"folder\": \"/Modelos/\", \"dateFormat\": \"DD/MM/YYYY\", \"timeFormat\": \"H:mm\"}");
        write("daily-notes.json", "{\"folder\": \"Diario\", \"format\": \"YYYY/MM/DD\", \"template\": \"Modelos/diaria\"}");
        write("app.json", "{\"newFileLocation\": \"folder\", \"newFileFolderPath\": \"Entrada\"}");

        VaultSettings settings = ObsidianSettings.propose(vault, VaultSettings.defaults()).orElseThrow().settings();

        assertEquals(new Templates("Modelos", "DD/MM/YYYY", "H:mm"), settings.templates(), "an older core-plugins.json: a list");
        assertEquals(new DailyNotes("Diario", "YYYY/MM/DD", "Modelos/diaria"), settings.daily());
        assertEquals(new NewNotes(NewNoteLocation.FOLDER, "Entrada"), settings.newNotes());
        assertEquals(VaultSettings.defaults().templater(), settings.templater(), "Templater is not on: left as it was");
    }

    @Test
    void aPluginObsidianHasOffIsNotTaken() throws Exception {
        write("core-plugins.json", "{\"templates\": false, \"daily-notes\": false}");
        write("templates.json", "{\"folder\": \"Modelos\"}");
        write("daily-notes.json", "{\"folder\": \"Diario\"}");
        VaultSettings mine = VaultSettings.defaults().withTemplates(new Templates("Mías", null, null));

        VaultSettings settings = ObsidianSettings.propose(vault, mine).orElseThrow().settings();

        assertEquals(mine.templates(), settings.templates());
        assertEquals(mine.daily(), settings.daily());
    }

    @Test
    void withoutObsidiansFolderThereIsNothing() {
        assertTrue(ObsidianSettings.propose(vault, VaultSettings.defaults()).isEmpty());
    }

    @Test
    void aFileNotInObsidiansShapeIsRefused() throws Exception {
        write("templates.json", "{ esto no es json");

        assertThrows(IllegalArgumentException.class, () -> ObsidianSettings.propose(vault, VaultSettings.defaults()));
    }

    @Test
    void obsidiansFilesAreNotChanged() throws Exception {
        obsidianAsTried();
        List<String> before = snapshot();

        ObsidianSettings.propose(vault, VaultSettings.defaults());

        assertEquals(before, snapshot());
    }

    /** Each file of {@code .obsidian}: its path, bytes and time. */
    private List<String> snapshot() throws Exception {
        List<String> files = new ArrayList<>();
        try (Stream<Path> all = Files.walk(vault.resolve(".obsidian"))) {
            for (Path file : all.filter(Files::isRegularFile).sorted().toList()) {
                FileTime time = Files.getLastModifiedTime(file);
                files.add(vault.relativize(file) + " " + Files.readString(file) + " " + time);
            }
        }
        return files;
    }
}
