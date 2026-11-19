package com.example.jylos.ui.controller;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.service.EncryptionService;
import com.example.jylos.service.NoteService;
import com.example.jylos.ui.UiDialogs;
import com.example.jylos.util.AttachmentType;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;

/**
 * Private (encrypted) notes: turning a note private or public, locking and unlocking them
 * all, and the master-password prompts and error dialogs these need.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
final class PrivacySupport {

    /** What turning notes private or public needs from the rest of the app. */
    interface Shell {
        /** The vault's notes; it changes when the vault does. */
        NoteService notes();

        EventBus eventBus();

        /** The note open in the editor, or {@code null}. */
        Note openNote();

        /** The editor's text of the open note, which may be newer than the stored one. */
        String openNoteText();

        /** Shows {@code note} in the editor again (after its privacy or readability changed). */
        void reloadInEditor(Note note);

        void refreshNotesList();
    }

    private Function<String, String> i18n;
    private Consumer<String> status;
    private Supplier<Scene> sceneSupplier;
    private Shell shell;

    void wire(Function<String, String> i18n, Consumer<String> status, Supplier<Scene> sceneSupplier, Shell shell) {
        this.i18n = i18n;
        this.status = status;
        this.sceneSupplier = sceneSupplier;
        this.shell = shell;
    }

    /**
     * Turns {@code target} private (encrypt its body) or public (decrypt and store as
     * plaintext). Works for any note — the open one or one picked from a list's context
     * menu — and acquires or reveals the key only as the chosen direction needs: making
     * private needs the key to encrypt; making public needs to read (reveal) the note.
     */
    void toggle(Note target) {
        NoteService notes = shell != null ? shell.notes() : null;
        if (target == null || target.getId() == null || notes == null) {
            updateStatus(getString("status.no_note_selected"));
            return;
        }
        if (!canBePrivate(target)) {
            updateStatus(getString("status.private_not_supported"));
            return;
        }
        EncryptionService enc = EncryptionService.getInstance();
        boolean makePrivate = !target.isPrivate();

        if (makePrivate) {
            if (!enc.isConfigured()) {
                if (!setupMasterPassword()) {
                    return;
                }
            } else if (!ensureKey()) {
                return; // need the key to encrypt
            }
        } else if (!enc.canRead(target.getId()) && !promptRevealNote(target.getId())) {
            return; // need to decrypt the note to turn it public
        }

        // Resolve the plaintext body. For the open note use the live editor text, but never
        // persist the 🔒 placeholder — fall back to the (now readable) stored content.
        Note open = shell.openNote();
        boolean isOpen = open != null && target.getId().equals(open.getId());
        String content = isOpen ? shell.openNoteText() : null;
        if (content == null || NoteService.LOCKED_PLACEHOLDER.equals(content)) {
            content = notes.getNoteById(target.getId()).map(Note::getContent).orElse(content);
        }
        if (content == null || NoteService.LOCKED_PLACEHOLDER.equals(content)) {
            updateStatus(getString("status.unlock_failed"));
            return;
        }

        target.setPrivate(makePrivate);
        target.setContent(content);
        notes.updateNote(target); // NoteService encrypts when private + key held
        if (makePrivate) {
            enc.reveal(target.getId()); // keep it readable this session (just created it)
        }
        if (shell.eventBus() != null) {
            shell.eventBus().publish(new NoteEvents.NoteSavedEvent(target));
        }
        // Reflect the new state in the editor when this is the open note.
        if (isOpen) {
            notes.getNoteById(target.getId()).ifPresent(shell::reloadInEditor);
        }
        shell.refreshNotesList();
        updateStatus(getString(makePrivate ? "status.note_private_on" : "status.note_private_off"));
    }

    /**
     * Whether {@code note} can be made private: notes and canvases (text) can; images and PDFs
     * are binary files that encrypting would break, as in Obsidian, which has no private files.
     */
    static boolean canBePrivate(Note note) {
        AttachmentType type =
                AttachmentType.fromName(note != null ? note.getId() : null);
        return type == AttachmentType.MARKDOWN
                || type == AttachmentType.CANVAS;
    }

    /** Asks for the master password and makes every private note readable this session. */
    void unlockAll() {
        EncryptionService enc = EncryptionService.getInstance();
        if (!enc.isConfigured()) {
            updateStatus(getString("status.no_private_notes"));
            return;
        }
        if (enc.isUnlocked()) {
            updateStatus(getString("status.unlocked"));
            return;
        }
        if (promptUnlockAll()) {
            // Show a previously locked open note decrypted.
            reloadOpenPrivateNote();
            shell.refreshNotesList();
        }
    }

    /** Locks private notes: unreadable until unlocked again. */
    void lockAll() {
        EncryptionService enc = EncryptionService.getInstance();
        if (!enc.isConfigured()) {
            updateStatus(getString("status.no_private_notes"));
            return;
        }
        enc.lock();
        // The open private note goes straight to its 🔒 placeholder (no unlock prompt).
        reloadOpenPrivateNote();
        // Full-text search keeps decrypted bodies cached while unlocked; this event makes
        // every listener drop them, so a locked note can no longer be found by its text.
        if (shell.eventBus() != null) {
            shell.eventBus().publish(new NoteEvents.NotesRefreshRequestedEvent());
        }
        shell.refreshNotesList();
        updateStatus(getString("status.notes_locked"));
    }

    private void reloadOpenPrivateNote() {
        Note open = shell.openNote();
        NoteService notes = shell.notes();
        if (open != null && open.isPrivate() && open.getId() != null && notes != null) {
            notes.getNoteById(open.getId()).ifPresent(shell::reloadInEditor);
        }
    }

    /** Prompts for the master password and unlocks <b>all</b> private notes for the session. */
    boolean promptUnlockAll() {
        char[] pw = promptPassword(getString("dialog.unlock.title"), getString("dialog.unlock.header"));
        if (pw == null) {
            return false;
        }
        try {
            if (EncryptionService.getInstance().unlock(pw)) {
                updateStatus(getString("status.unlocked"));
                return true;
            }
            showError(getString("dialog.unlock.title"), getString("status.unlock_failed"));
            return false;
        } finally {
            Arrays.fill(pw, '\0');
        }
    }

    /** Prompts for the master password and reveals only {@code noteId} (others stay 🔒). */
    boolean promptRevealNote(String noteId) {
        char[] pw = promptPassword(getString("dialog.unlock.title"), getString("dialog.unlock.header"));
        if (pw == null) {
            return false;
        }
        try {
            if (EncryptionService.getInstance().revealNote(noteId, pw)) {
                updateStatus(getString("status.note_unlocked"));
                return true;
            }
            showError(getString("dialog.unlock.title"), getString("status.unlock_failed"));
            return false;
        } finally {
            Arrays.fill(pw, '\0');
        }
    }

    /**
     * Ensures the key is held so a note can be turned private (encrypted). Prompts for the
     * master password if needed; does not reveal any existing note.
     */
    boolean ensureKey() {
        EncryptionService enc = EncryptionService.getInstance();
        if (enc.hasKey()) {
            return true;
        }
        char[] pw = promptPassword(getString("dialog.unlock.title"), getString("dialog.unlock.header"));
        if (pw == null) {
            return false;
        }
        try {
            if (enc.acquireKey(pw)) {
                return true;
            }
            showError(getString("dialog.unlock.title"), getString("status.unlock_failed"));
            return false;
        } finally {
            Arrays.fill(pw, '\0');
        }
    }

    /** First-time setup of the master password (entered twice). */
    boolean setupMasterPassword() {
        char[] pw = promptPassword(getString("dialog.setup_password.title"), getString("dialog.setup_password.header"));
        if (pw == null || pw.length == 0) {
            return false;
        }
        try {
            char[] confirm = promptPassword(getString("dialog.setup_password.title"),
                    getString("dialog.setup_password.confirm"));
            if (confirm == null) {
                return false;
            }
            try {
                if (!Arrays.equals(pw, confirm)) {
                    showError(getString("dialog.setup_password.title"), getString("status.password_mismatch"));
                    return false;
                }
                EncryptionService.getInstance().configure(pw);
                updateStatus(getString("status.unlocked"));
                return true;
            } finally {
                Arrays.fill(confirm, '\0');
            }
        } finally {
            Arrays.fill(pw, '\0');
        }
    }

    private char[] promptPassword(String title, String header) {
        Dialog<char[]> dialog = new Dialog<>();
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
        dialog.setResultConverter(b -> b == ButtonType.OK ? field.getText().toCharArray() : null);
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
