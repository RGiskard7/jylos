package com.example.jylos.ui.controller;

import java.text.MessageFormat;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.function.Supplier;

import com.example.jylos.data.dao.VaultSettingsStore;
import com.example.jylos.data.models.VaultSettings;
import com.example.jylos.data.models.VaultSettings.DailyNotes;
import com.example.jylos.data.models.VaultSettings.FolderTemplate;
import com.example.jylos.data.models.VaultSettings.NewNoteLocation;
import com.example.jylos.data.models.VaultSettings.NewNotes;
import com.example.jylos.data.models.VaultSettings.Templater;
import com.example.jylos.data.models.VaultSettings.Templates;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.LinkIndex;
import com.example.jylos.service.NoteService;
import com.example.jylos.ui.components.SettingsPane;
import com.example.jylos.util.MomentFormat;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Preferences > Vault: the open vault's own settings ({@link VaultSettings}): its templates
 * folder and their date and time formats, its daily note, where new notes go, and Templater.
 * A folder or a note is typed or chosen from the vault's; a date format shows what it writes
 * today. Nothing is kept until the window is saved: {@link #edited} gives what it shows.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
final class VaultSettingsSupport {

    private final VaultSettings current;
    private final List<String> folders;
    private final List<String> notes;
    private final Function<String, String> i18n;
    private final Locale language;
    private final Supplier<ZonedDateTime> now;

    private final ComboBox<String> templatesFolder;
    private final TextField dateFormat = new TextField();
    private final TextField timeFormat = new TextField();
    private final TextField dailyFormat = new TextField();
    private final ComboBox<String> dailyFolder;
    private final ComboBox<String> dailyTemplate;
    private final ComboBox<String> newLocation = new ComboBox<>();
    private final ComboBox<String> newFolder;
    private final CheckBox templaterEnabled = new CheckBox();
    private final CheckBox templaterTrigger = new CheckBox();
    private final VBox folderTemplates = new VBox();
    /** The folder and template fields of each row of {@link #folderTemplates}, in order. */
    private final List<FolderTemplateRow> folderTemplateRows = new ArrayList<>();

    /**
     * @param current  the vault's settings now
     * @param folders  the vault's folders, by path, to choose from
     * @param notes    the vault's notes, by path, to choose a template from
     * @param i18n     the interface's texts
     * @param language the interface's language, for the previews
     * @param now      the moment the previews write
     */
    VaultSettingsSupport(VaultSettings current, List<String> folders, List<String> notes,
            Function<String, String> i18n, Locale language, Supplier<ZonedDateTime> now) {
        this.current = current != null ? current : VaultSettings.defaults();
        this.folders = folders != null ? folders : List.of();
        this.notes = notes != null ? notes : List.of();
        this.i18n = i18n;
        this.language = language;
        this.now = now;
        templatesFolder = choosing(this.folders);
        dailyFolder = choosing(this.folders);
        dailyTemplate = choosing(this.notes);
        newFolder = choosing(this.folders);
        newLocation.getItems().setAll(i18n.apply("pref.vault.new_location.current"),
                i18n.apply("pref.vault.new_location.root"), i18n.apply("pref.vault.new_location.folder"));
        newLocation.getSelectionModel().selectedIndexProperty()
                .addListener((o, was, index) -> newFolder.setDisable(index.intValue() != 2));
        show(this.current);
    }

    /**
     * Preferences > Vault for the open vault, with its folders and notes to choose from, or
     * {@code null} with no vault open.
     */
    static VaultSettingsSupport forVault(VaultSettingsStore store, FolderService folders, NoteService notes,
            Function<String, String> i18n, ResourceBundle resources) {
        if (store == null || folders == null || notes == null) {
            return null;
        }
        List<String> folderPaths = folders.getAllFolders().stream()
                .map(folder -> notes.folderPath(folder.getId()))
                .filter(path -> !path.isEmpty()).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        LinkIndex links = notes.linkIndex();
        List<String> notePaths = notes.getAllNotes().stream()
                .map(note -> links.pathOf(note.getId()))
                .filter(path -> path != null && path.endsWith(".md")).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        Locale language = resources != null ? resources.getLocale() : Locale.getDefault();
        return new VaultSettingsSupport(store.get(), folderPaths, notePaths, i18n, language, ZonedDateTime::now);
    }

    /** Keeps {@code edited} as the vault's settings, when there is a vault and the section was shown. */
    static void save(VaultSettingsStore store, VaultSettings edited) {
        if (store != null && edited != null) {
            store.set(edited);
        }
    }

    /** Adds the section to {@code pane}, with its Reset labelled {@code resetLabel}. */
    void addTo(SettingsPane pane, String resetLabel) {
        Button add = new Button(i18n.apply("pref.vault.folder_templates_add"));
        add.setOnAction(e -> addFolderTemplate(new FolderTemplate("", "")));
        pane.addSection(i18n.apply("dialog.preferences.section.vault"))
                .resettable(resetLabel, () -> show(VaultSettings.defaults()))
                .subheading(i18n.apply("pref.vault.templates"))
                .row(i18n.apply("pref.vault.templates_folder"), i18n.apply("pref.vault.templates_folder_desc"),
                        templatesFolder)
                .row(i18n.apply("pref.vault.date_format"), i18n.apply("pref.vault.date_format_desc"),
                        withPreview(dateFormat))
                .row(i18n.apply("pref.vault.time_format"), i18n.apply("pref.vault.time_format_desc"),
                        withPreview(timeFormat))
                .subheading(i18n.apply("pref.vault.daily"))
                .row(i18n.apply("pref.vault.daily_format"), i18n.apply("pref.vault.daily_format_desc"),
                        withPreview(dailyFormat))
                .row(i18n.apply("pref.vault.daily_folder"), i18n.apply("pref.vault.daily_folder_desc"), dailyFolder)
                .row(i18n.apply("pref.vault.daily_template"), i18n.apply("pref.vault.daily_template_desc"),
                        dailyTemplate)
                .subheading(i18n.apply("pref.vault.new_notes"))
                .row(i18n.apply("pref.vault.new_location"), null, newLocation)
                .row(i18n.apply("pref.vault.new_folder"), null, newFolder)
                .subheading(i18n.apply("pref.vault.templater"))
                .row(i18n.apply("pref.vault.templater_enabled"), i18n.apply("pref.vault.templater_enabled_desc"),
                        templaterEnabled)
                .row(i18n.apply("pref.vault.templater_trigger"), i18n.apply("pref.vault.templater_trigger_desc"),
                        templaterTrigger)
                .subheading(i18n.apply("pref.vault.folder_templates"), add)
                .block(folderTemplates);
    }

    /** The settings as the section shows them. */
    VaultSettings edited() {
        List<FolderTemplate> byFolder = new ArrayList<>();
        for (FolderTemplateRow row : folderTemplateRows) {
            String folder = value(row.folder());
            String template = value(row.template());
            if (!folder.isEmpty() || !template.isEmpty()) {
                byFolder.add(new FolderTemplate(folder, template));
            }
        }
        NewNoteLocation location = NewNoteLocation.values()[Math.max(0,
                newLocation.getSelectionModel().getSelectedIndex())];
        return current
                .withTemplates(new Templates(value(templatesFolder), dateFormat.getText().strip(),
                        timeFormat.getText().strip()))
                .withDaily(new DailyNotes(value(dailyFolder), dailyFormat.getText().strip(), value(dailyTemplate)))
                .withNewNotes(new NewNotes(location, value(newFolder)))
                .withTemplater(new Templater(templaterEnabled.isSelected(), templaterTrigger.isSelected(), byFolder));
    }

    private void show(VaultSettings settings) {
        templatesFolder.getEditor().setText(settings.templates().folder());
        dateFormat.setText(settings.templates().dateFormat());
        timeFormat.setText(settings.templates().timeFormat());
        dailyFormat.setText(settings.daily().format());
        dailyFolder.getEditor().setText(settings.daily().folder());
        dailyTemplate.getEditor().setText(settings.daily().template());
        newLocation.getSelectionModel().select(settings.newNotes().location().ordinal());
        newFolder.getEditor().setText(settings.newNotes().folder());
        newFolder.setDisable(settings.newNotes().location() != NewNoteLocation.FOLDER);
        templaterEnabled.setSelected(settings.templater().enabled());
        templaterTrigger.setSelected(settings.templater().triggerOnCreate());
        folderTemplates.getChildren().clear();
        folderTemplateRows.clear();
        settings.templater().folderTemplates().forEach(this::addFolderTemplate);
    }

    private void addFolderTemplate(FolderTemplate template) {
        ComboBox<String> folder = choosing(folders);
        folder.setPromptText(i18n.apply("pref.vault.folder_templates_folder"));
        folder.getEditor().setText(template.folder());
        ComboBox<String> note = choosing(notes);
        note.setPromptText(i18n.apply("pref.vault.folder_templates_template"));
        note.getEditor().setText(template.template());
        HBox.setHgrow(folder, Priority.ALWAYS);
        HBox.setHgrow(note, Priority.ALWAYS);
        folder.setMaxWidth(Double.MAX_VALUE);
        note.setMaxWidth(Double.MAX_VALUE);
        Button remove = new Button(i18n.apply("pref.vault.folder_templates_remove"));
        remove.setMinWidth(Region.USE_PREF_SIZE);
        HBox row = SettingsPane.controls(folder, note, remove);
        row.getStyleClass().add("vault-folder-template");
        FolderTemplateRow fields = new FolderTemplateRow(folder, note);
        remove.setOnAction(e -> {
            folderTemplates.getChildren().remove(row);
            folderTemplateRows.remove(fields);
        });
        folderTemplates.getChildren().add(row);
        folderTemplateRows.add(fields);
    }

    /** One row of the folder templates: its folder and its template. */
    private record FolderTemplateRow(ComboBox<String> folder, ComboBox<String> template) {
    }

    /** {@code format} with what it writes now under it, kept up to date as it is typed. */
    private Node withPreview(TextField format) {
        Label preview = new Label();
        preview.getStyleClass().addAll("settings-row-description", "vault-format-preview");
        Runnable update = () -> preview.setText(MessageFormat.format(i18n.apply("pref.vault.preview"),
                MomentFormat.format(now.get(), format.getText(), language)));
        format.textProperty().addListener((o, was, text) -> update.run());
        update.run();
        VBox box = new VBox(format, preview);
        box.getStyleClass().add("vault-format");
        return box;
    }

    /** A field to type a path in or choose one of {@code choices}. */
    private static ComboBox<String> choosing(List<String> choices) {
        ComboBox<String> combo = new ComboBox<>();
        combo.setEditable(true);
        combo.getItems().setAll(choices);
        combo.setVisibleRowCount(12);
        combo.getStyleClass().add("vault-path");
        return combo;
    }

    private static String value(ComboBox<String> combo) {
        String text = combo.getEditor().getText();
        return text != null ? text.strip() : "";
    }
}
