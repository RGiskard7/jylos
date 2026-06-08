package com.example.jylos.ui.controller;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.example.jylos.service.EncryptionService;
import com.example.jylos.ui.UiDialogs;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;

/**
 * Master-password plumbing for private (encrypted) notes: the unlock and first-time
 * setup prompts and the small error dialogs. Extracted from {@code MainController} so
 * the shell only keeps the orchestration (toggle private / lock), delegating the
 * password UX here.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
final class PrivacySupport {

    private Function<String, String> i18n;
    private Consumer<String> status;
    private Supplier<Scene> sceneSupplier;

    void wire(Function<String, String> i18n, Consumer<String> status, Supplier<Scene> sceneSupplier) {
        this.i18n = i18n;
        this.status = status;
        this.sceneSupplier = sceneSupplier;
    }

    /** Ensures the session is unlocked when notes are encrypted; prompts if needed. */
    boolean ensureUnlocked() {
        EncryptionService enc = EncryptionService.getInstance();
        if (!enc.isConfigured() || enc.isUnlocked()) {
            return true;
        }
        return promptUnlock();
    }

    /** Prompts for the master password and unlocks the session. */
    boolean promptUnlock() {
        String pw = promptPassword(getString("dialog.unlock.title"), getString("dialog.unlock.header"));
        if (pw == null) {
            return false;
        }
        if (EncryptionService.getInstance().unlock(pw.toCharArray())) {
            updateStatus(getString("status.unlocked"));
            return true;
        }
        showError(getString("dialog.unlock.title"), getString("status.unlock_failed"));
        return false;
    }

    /** First-time setup of the master password (entered twice). */
    boolean setupMasterPassword() {
        String pw = promptPassword(getString("dialog.setup_password.title"), getString("dialog.setup_password.header"));
        if (pw == null || pw.isEmpty()) {
            return false;
        }
        String confirm = promptPassword(getString("dialog.setup_password.title"),
                getString("dialog.setup_password.confirm"));
        if (confirm == null) {
            return false;
        }
        if (!pw.equals(confirm)) {
            showError(getString("dialog.setup_password.title"), getString("status.password_mismatch"));
            return false;
        }
        EncryptionService.getInstance().configure(pw.toCharArray());
        updateStatus(getString("status.unlocked"));
        return true;
    }

    private String promptPassword(String title, String header) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        Scene scene = sceneSupplier != null ? sceneSupplier.get() : null;
        if (scene != null) {
            dialog.initOwner(scene.getWindow());
        }
        UiDialogs.apply(dialog);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        PasswordField field = new PasswordField();
        VBox box = new VBox(field);
        box.setPadding(new Insets(16));
        dialog.getDialogPane().setContent(box);
        Platform.runLater(field::requestFocus);
        dialog.setResultConverter(b -> b == ButtonType.OK ? field.getText() : null);
        return UiDialogs.show(dialog).orElse(null);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        Scene scene = sceneSupplier != null ? sceneSupplier.get() : null;
        if (scene != null) {
            alert.initOwner(scene.getWindow());
        }
        UiDialogs.show(alert);
    }

    private String getString(String key) {
        return i18n != null ? i18n.apply(key) : key;
    }

    private void updateStatus(String message) {
        if (status != null) {
            status.accept(message);
        }
    }
}
