package com.example.jylos.ui.controller;

import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.compat.obsidian.ObsidianSettings;
import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.dao.VaultSettingsStore;
import com.example.jylos.data.models.VaultSettings;
import com.example.jylos.event.events.SystemActionEvent.ActionType;
import com.example.jylos.service.NoteService;
import com.example.jylos.ui.UiDialogs;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

/**
 * Tools > "Import Obsidian settings", for a folder vault Obsidian also opens: Obsidian's
 * settings of the vault ({@link ObsidianSettings}) become Jylos' own, once the user has seen
 * what would change and said so. A vault with Obsidian's folder is offered it the first time it
 * is opened, once: whether taken or not, it is not offered again; the command stays.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
final class ObsidianSettingsSupport {

    private static final Logger logger = LoggerConfig.getLogger(ObsidianSettingsSupport.class);

    private Supplier<NoteService> notes = () -> null;
    private Supplier<VaultSettingsStore> settings = () -> null;
    private Function<String, String> i18n = key -> key;
    private Consumer<String> status = message -> {
    };
    private Supplier<Window> owner = () -> null;
    private BiPredicate<List<ObsidianSettings.Change>, Boolean> confirm = this::askToTake;

    /** Answers the command. */
    void register(Map<ActionType, Runnable> handlers, Supplier<NoteService> notes, Supplier<VaultSettingsStore> settings,
            Function<String, String> i18n, Consumer<String> status, Supplier<Window> owner) {
        this.notes = notes;
        this.settings = settings;
        this.i18n = i18n;
        this.status = status;
        this.owner = owner;
        handlers.put(ActionType.IMPORT_OBSIDIAN_SETTINGS, this::run);
    }

    /** Asks with {@code confirm} (the changes, and whether it is the offer on opening) instead of a dialog. */
    void confirmWith(BiPredicate<List<ObsidianSettings.Change>, Boolean> confirm) {
        this.confirm = confirm;
    }

    /** The command: shows what would change and takes it when the user says so. */
    void run() {
        Optional<ObsidianSettings.Proposal> proposal;
        try {
            proposal = proposal();
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Obsidian's settings were not understood", e);
            status.accept(i18n.apply("obsidian_settings.status.not_understood"));
            return;
        }
        if (proposal.isEmpty()) {
            status.accept(i18n.apply("obsidian_settings.status.none"));
            return;
        }
        if (proposal.get().changes().isEmpty()) {
            status.accept(i18n.apply("obsidian_settings.status.same"));
            return;
        }
        if (confirm.test(proposal.get().changes(), false)) {
            settings.get().set(proposal.get().settings().withObsidianImportAsked(true));
            status.accept(i18n.apply("obsidian_settings.status.done"));
        } else {
            status.accept(i18n.apply("obsidian_settings.status.cancelled"));
        }
    }

    /** The offer on opening a vault with Obsidian's folder: once, and only when something would change. */
    void offer() {
        VaultSettingsStore store = settings.get();
        if (store == null || store.get().obsidianImportAsked()) {
            return;
        }
        Optional<ObsidianSettings.Proposal> proposal;
        try {
            proposal = proposal();
        } catch (IllegalArgumentException e) {
            logger.log(Level.FINE, "Obsidian's settings were not understood", e);
            return;
        }
        if (proposal.isEmpty()) {
            return;
        }
        VaultSettings taken = !proposal.get().changes().isEmpty() && confirm.test(proposal.get().changes(), true)
                ? proposal.get().settings() : store.get();
        store.set(taken.withObsidianImportAsked(true));
    }

    private Optional<ObsidianSettings.Proposal> proposal() {
        NoteService vault = notes.get();
        VaultSettingsStore store = settings.get();
        Optional<Path> root = vault != null ? vault.getStorageRoot() : Optional.empty();
        if (root.isEmpty() || store == null) {
            return Optional.empty();
        }
        return ObsidianSettings.propose(root.get(), store.get());
    }

    private boolean askToTake(List<ObsidianSettings.Change> changes, boolean firstTime) {
        StringBuilder list = new StringBuilder();
        for (ObsidianSettings.Change change : changes) {
            list.append("• ").append(label(change.setting())).append(": ").append(value(change.current()))
                    .append(" → ").append(value(change.proposed())).append('\n');
        }
        ButtonType take = new ButtonType(i18n.apply("obsidian_settings.confirm.ok"), ButtonBar.ButtonData.OK_DONE);
        ButtonType later = new ButtonType(i18n.apply(firstTime ? "obsidian_settings.confirm.later" : "action.cancel"),
                ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                MessageFormat.format(i18n.apply("obsidian_settings.confirm.content"), list.toString().strip()), take, later);
        alert.setTitle(i18n.apply("action.import_obsidian_settings"));
        alert.setHeaderText(i18n.apply(firstTime ? "obsidian_settings.confirm.header_first" : "obsidian_settings.confirm.header"));
        Window window = owner.get();
        if (window != null) {
            alert.initOwner(window);
        }
        UiDialogs.apply(alert);
        return alert.showAndWait().filter(take::equals).isPresent();
    }

    /** The name of a setting, as Preferences > Vault shows it. */
    private String label(String setting) {
        String key = switch (setting) {
            case "templates.folder" -> "pref.vault.templates_folder";
            case "templates.dateFormat" -> "pref.vault.date_format";
            case "templates.timeFormat" -> "pref.vault.time_format";
            case "daily.folder" -> "pref.vault.daily_folder";
            case "daily.format" -> "pref.vault.daily_format";
            case "daily.template" -> "pref.vault.daily_template";
            case "newNotes.location" -> "pref.vault.new_location";
            case "newNotes.folder" -> "pref.vault.new_folder";
            case "templater.enabled" -> "pref.vault.templater_enabled";
            case "templater.triggerOnCreate" -> "pref.vault.templater_trigger";
            default -> "pref.vault.folder_templates";
        };
        return i18n.apply(key);
    }

    private String value(Object value) {
        if (value instanceof Boolean on) {
            return i18n.apply(on ? "obsidian_settings.value.yes" : "obsidian_settings.value.no");
        }
        if (value instanceof VaultSettings.NewNoteLocation location) {
            return i18n.apply("pref.vault.new_location." + location.name().toLowerCase(Locale.ROOT));
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return i18n.apply("obsidian_settings.value.none");
            }
            StringBuilder out = new StringBuilder();
            for (Object item : list) {
                if (item instanceof VaultSettings.FolderTemplate t) {
                    out.append(out.isEmpty() ? "" : "; ").append(t.folder().isEmpty() ? "/" : t.folder()).append(" → ")
                            .append(t.template());
                }
            }
            return out.toString();
        }
        String text = value != null ? value.toString() : "";
        return text.isEmpty() ? i18n.apply("obsidian_settings.value.none") : text;
    }
}
