package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.VaultSettingsStore;
import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.VaultSettings;
import com.example.jylos.data.models.VaultSettings.Templates;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.templates.TemplateLibrary;

/**
 * The templates of the open vault, as its settings say: which notes they are, their text as
 * it is in the file, and that text written with the vault's date and time formats.
 */
class TemplatesSupportTest {

    @TempDir
    Path vault;

    private TemplatesSupport support(VaultSettingsStore settings) {
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        return new TemplatesSupport(() -> notes, () -> settings, () -> Locale.forLanguageTag("es"),
                () -> LocalDateTime.parse("2026-11-19T14:05:00").atZone(ZoneId.of("Europe/Madrid")));
    }

    @Test
    void theTemplatesAreThoseOfTheFolderInTheVaultsSettings() throws Exception {
        Files.createDirectories(vault.resolve("Plantillas/Trabajo"));
        Files.writeString(vault.resolve("Plantillas/diaria.md"), "# {{title}}\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("Plantillas/Trabajo/acta.md"), "## Acta\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("Inicio.md"), "hola\n", StandardCharsets.UTF_8);
        VaultSettingsStore settings = new VaultSettingsStore(vault.resolve(".jylos/settings.json"));
        settings.set(VaultSettings.defaults().withTemplates(new Templates("Plantillas", "DD/MM/YYYY", "H:mm")));

        TemplateLibrary.Listing listing = support(settings).listing();

        assertEquals(List.of("diaria", "Trabajo/acta"),
                listing.templates().stream().map(TemplateLibrary.Template::name).toList(),
                "by name, case aside, as Obsidian sorts them");
    }

    @Test
    void aTemplatesTextIsTheFileAsItIsAndIsWrittenWithTheVaultsFormats() throws Exception {
        Files.createDirectories(vault.resolve("Plantillas"));
        String file = "---\n# una nota del autor\nestado:   borrador\n---\n\n# {{title}}\n{{date}} {{time}}\n";
        Files.writeString(vault.resolve("Plantillas/diaria.md"), file, StandardCharsets.UTF_8);
        VaultSettingsStore settings = new VaultSettingsStore(vault.resolve(".jylos/settings.json"));
        settings.set(VaultSettings.defaults().withTemplates(new Templates("Plantillas", "DD/MM/YYYY", "H:mm")));
        TemplatesSupport support = support(settings);
        TemplateLibrary.Template template = support.listing().find("diaria").orElseThrow();

        String text = support.text(template);

        assertEquals(file, text, "comments and spacing kept");
        assertEquals("---\n# una nota del autor\nestado:   borrador\n---\n\n# Acta\n19/11/2026 14:05\n",
                support.render(text, "Acta"));
        assertEquals("19/11/2026", support.date());
        assertEquals("14:05", support.time());
    }
}
