package com.example.jylos.ui.controller;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.prefs.Preferences;

import com.example.jylos.ui.UiDialogs;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

/**
 * Decides which directory the filesystem backend opens, and asks the user what to do when
 * the configured vault cannot be found (unplugged drive, unmounted cloud folder, renamed
 * directory) instead of silently opening the default data directory — where the user would
 * see an empty or unrelated vault and keep writing new notes in the wrong place.
 */
final class VaultLocationSupport {

    /** Preference key holding the configured vault directory. */
    static final String FILESYSTEM_PATH_KEY = "filesystem_path";

    enum Kind {
        /** The configured vault exists. */
        CONFIGURED,
        /** No vault is configured: the default data directory is the vault. */
        DEFAULT,
        /** A vault is configured but cannot be found. */
        MISSING
    }

    record Resolution(Kind kind, String path) {
    }

    private Function<String, String> i18n = key -> key;

    void wire(Function<String, String> i18n) {
        this.i18n = i18n != null ? i18n : key -> key;
    }

    /**
     * Pure decision: the old default vault to keep using, when no vault is configured and
     * the data migration left notes in an older location (e.g. {@code ~/data}).
     */
    static Optional<String> legacyVaultToAdopt(String configuredPath, Optional<Path> legacyDefaultVault,
            Predicate<Path> isDirectory) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Optional.empty();
        }
        return legacyDefaultVault.filter(isDirectory).map(Path::toString);
    }

    /** Pure decision: what the configured path means, given how to test that a directory exists. */
    static Resolution resolve(String configuredPath, Predicate<Path> isDirectory) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return new Resolution(Kind.DEFAULT, null);
        }
        boolean present;
        try {
            present = isDirectory.test(Path.of(configuredPath));
        } catch (RuntimeException invalidPath) {
            present = false;
        }
        return new Resolution(present ? Kind.CONFIGURED : Kind.MISSING, configuredPath);
    }

    /**
     * Returns the vault directory to open. When the configured vault is missing, the user
     * can retry (e.g. after plugging the drive in), pick another folder (which becomes the
     * configured vault), knowingly open the default data directory for this session — the
     * configured path is kept, so the vault opens again once it is back — or quit.
     */
    String chooseVaultDirectory(Preferences prefs, String defaultDirectory, Window owner) {
        while (true) {
            Resolution resolution = resolve(prefs.get(FILESYSTEM_PATH_KEY, ""), Files::isDirectory);
            switch (resolution.kind()) {
                case CONFIGURED:
                    return resolution.path();
                case DEFAULT: {
                    // An older installed build kept its default vault where it was launched
                    // from (e.g. ~/data); keep opening it there as an explicitly chosen vault.
                    Optional<String> legacy = legacyVaultToAdopt(prefs.get(FILESYSTEM_PATH_KEY, ""),
                            com.example.jylos.AppDataDirectory.legacyDefaultVault(), Files::isDirectory);
                    if (legacy.isPresent()) {
                        prefs.put(FILESYSTEM_PATH_KEY, legacy.get());
                        return legacy.get();
                    }
                    return defaultDirectory;
                }
                default:
                    break;
            }

            ButtonType retry = new ButtonType(i18n.apply("dialog.vault_missing.retry"), ButtonBar.ButtonData.OTHER);
            ButtonType choose = new ButtonType(i18n.apply("dialog.vault_missing.choose"), ButtonBar.ButtonData.OTHER);
            ButtonType useDefault = new ButtonType(i18n.apply("dialog.vault_missing.use_default"),
                    ButtonBar.ButtonData.OTHER);
            ButtonType exit = new ButtonType(i18n.apply("dialog.vault_missing.exit"), ButtonBar.ButtonData.CANCEL_CLOSE);

            Alert alert = new Alert(Alert.AlertType.WARNING, "", retry, choose, useDefault, exit);
            alert.setTitle(i18n.apply("dialog.vault_missing.title"));
            alert.setHeaderText(i18n.apply("dialog.vault_missing.header"));
            alert.setContentText(MessageFormat.format(i18n.apply("dialog.vault_missing.content"),
                    resolution.path(), defaultDirectory));
            if (owner != null) {
                alert.initOwner(owner);
            }
            Optional<ButtonType> answer = UiDialogs.show(alert);

            if (answer.isEmpty() || answer.get() == exit) {
                Platform.exit();
                System.exit(0);
            } else if (answer.get() == useDefault) {
                return defaultDirectory;
            } else if (answer.get() == choose) {
                DirectoryChooser chooser = new DirectoryChooser();
                chooser.setTitle(i18n.apply("dialog.vault_missing.choose"));
                File chosen = chooser.showDialog(owner);
                if (chosen != null) {
                    prefs.put(FILESYSTEM_PATH_KEY, chosen.getAbsolutePath());
                }
            }
            // retry, or the chooser was cancelled: check again
        }
    }
}
