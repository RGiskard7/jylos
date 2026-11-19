package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.VaultSettings;
import com.example.jylos.data.models.VaultSettings.DailyNotes;
import com.example.jylos.data.models.VaultSettings.FolderTemplate;
import com.example.jylos.data.models.VaultSettings.NewNoteLocation;
import com.example.jylos.data.models.VaultSettings.NewNotes;
import com.example.jylos.data.models.VaultSettings.Templater;
import com.example.jylos.data.models.VaultSettings.Templates;
import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.components.SettingsPane;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

/**
 * Preferences > Vault: the templates folder and formats, the daily note, where new notes go
 * and Templater, as the vault has them; what is changed is what is saved; date formats show
 * what they write; and its Reset puts back the defaults.
 */
class VaultSettingsSupportTest {

    private static final ResourceBundle ES =
            ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es"));
    private static final VaultSettings CURRENT = VaultSettings.defaults()
            .withTemplates(new Templates("Plantillas", "YYYY-MM-DD", "HH:mm"))
            .withDaily(new DailyNotes("Diario", "YYYY-MM-DD", "Plantillas/diaria.md"))
            .withTemplater(new Templater(true, true, List.of(new FolderTemplate("crea", "Plantillas/diaria.md"))))
            .withObsidianImportAsked(true);

    @Test
    void itShowsTheVaultsSettingsAndSavesWhatIsChanged() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        VaultSettings saved = onFx(() -> {
            VaultSettingsSupport section = section();
            Parent page = page(section);
            assertEquals("Plantillas", text(page, "pref.vault.templates_folder"));
            assertEquals("Plantillas/diaria.md", text(page, "pref.vault.daily_template"));

            field(page, "pref.vault.templates_folder").setText("Modelos");
            field(page, "pref.vault.date_format").setText("DD/MM/YYYY");
            field(page, "pref.vault.daily_folder").setText("");
            choice(page, "pref.vault.new_location").getSelectionModel().select(2);
            field(page, "pref.vault.new_folder").setText("Entrada");
            ((CheckBox) control(page, "pref.vault.templater_trigger")).setSelected(false);
            return section.edited();
        });

        assertEquals(VaultSettings.defaults()
                .withTemplates(new Templates("Modelos", "DD/MM/YYYY", "HH:mm"))
                .withDaily(new DailyNotes("", "YYYY-MM-DD", "Plantillas/diaria.md"))
                .withNewNotes(new NewNotes(NewNoteLocation.FOLDER, "Entrada"))
                .withTemplater(new Templater(true, false, List.of(new FolderTemplate("crea", "Plantillas/diaria.md"))))
                .withObsidianImportAsked(true), saved);
    }

    @Test
    void aDateFormatShowsWhatItWrites() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> previews = onFx(() -> {
            Parent page = page(section());
            String before = preview(page, "pref.vault.daily_format");
            field(page, "pref.vault.daily_format").setText("dddd D [de] MMMM");
            return List.of(before, preview(page, "pref.vault.daily_format"));
        });

        assertEquals(List.of("Vista previa: 2026-11-19", "Vista previa: jueves 19 de noviembre"), previews);
    }

    @Test
    void aFolderTemplateIsAddedAndRemoved() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<List<FolderTemplate>> seen = onFx(() -> {
            VaultSettingsSupport section = section();
            Parent page = page(section);
            button(page, ES.getString("pref.vault.folder_templates_add")).fire();
            List<TextField> fields = page.lookupAll(".vault-folder-template .combo-box-base").stream()
                    .map(node -> ((ComboBox<?>) node).getEditor()).toList();
            assertEquals(4, fields.size(), "two rows, a folder and a template each");
            fields.get(2).setText("Reuniones");
            fields.get(3).setText("Plantillas/acta.md");
            List<FolderTemplate> added = section.edited().templater().folderTemplates();
            button(page, ES.getString("pref.vault.folder_templates_remove")).fire();
            return List.of(added, section.edited().templater().folderTemplates());
        });

        assertEquals(List.of(new FolderTemplate("crea", "Plantillas/diaria.md"),
                new FolderTemplate("Reuniones", "Plantillas/acta.md")), seen.get(0));
        assertEquals(List.of(new FolderTemplate("Reuniones", "Plantillas/acta.md")), seen.get(1), "the first removed");
    }

    @Test
    void resetPutsBackTheDefaults() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        VaultSettings reset = onFx(() -> {
            VaultSettingsSupport section = section();
            Parent page = page(section);
            ((Button) page.lookup(".settings-reset-btn")).fire();
            return section.edited();
        });

        assertEquals(VaultSettings.defaults().withObsidianImportAsked(true), reset,
                "everything but whether importing from Obsidian was offered");
    }

    private static VaultSettingsSupport section() {
        return new VaultSettingsSupport(CURRENT, List.of("Plantillas", "Diario", "crea"),
                List.of("Plantillas/diaria.md"), ES::getString, Locale.forLanguageTag("es"),
                () -> LocalDateTime.parse("2026-11-19T14:05:00").atZone(ZoneId.of("Europe/Madrid")));
    }

    private static Parent page(VaultSettingsSupport section) {
        SettingsPane pane = new SettingsPane();
        section.addTo(pane, ES.getString("dialog.preferences.reset"));
        pane.showSection(0);
        Scene scene = new Scene(new StackPane(pane), 900, 700);
        scene.getRoot().applyCss();
        ((Pane) scene.getRoot()).layout();
        return pane;
    }

    /** The control on the row of the setting named by {@code key}. */
    private static Node control(Parent root, String key) {
        String name = ES.getString(key);
        for (Node node : root.lookupAll(".settings-row-name")) {
            if (node instanceof Label label && name.equals(label.getText())) {
                HBox row = (HBox) label.getParent().getParent();
                return row.getChildren().get(row.getChildren().size() - 1);
            }
        }
        throw new AssertionError("no setting named " + name);
    }

    private static TextField field(Parent root, String key) {
        Node control = control(root, key);
        if (control instanceof ComboBox<?> combo) {
            return combo.getEditor();
        }
        return (TextField) control.lookup("TextField");
    }

    private static String text(Parent root, String key) {
        return field(root, key).getText();
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<String> choice(Parent root, String key) {
        return (ComboBox<String>) control(root, key);
    }

    private static String preview(Parent root, String key) {
        Label label = (Label) control(root, key).lookup(".vault-format-preview");
        assertTrue(label != null, "a preview under " + key);
        return label.getText();
    }

    private static Button button(Parent root, String text) {
        for (Node node : root.lookupAll(".button")) {
            if (node instanceof Button b && text.equals(b.getText())) {
                return b;
            }
        }
        throw new AssertionError("no button " + text);
    }
}
