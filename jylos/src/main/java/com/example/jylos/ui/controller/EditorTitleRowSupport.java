package com.example.jylos.ui.controller;

import java.util.ResourceBundle;

import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.data.models.Note;
import com.example.jylos.service.EncryptionService;
import com.example.jylos.ui.UiNodes;
import com.example.jylos.util.AttachmentType;

import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;

/**
 * What the editor's title row says about the open note besides its title: the folders it is
 * in ({@code folder / sub /}), which reveal it in the Explorer tree when clicked, and a lock
 * when it is private.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
final class EditorTitleRowSupport {

    private Label pathLabel;
    private Region row;
    private Label lock;
    private FontIcon lockIcon;
    private Tooltip lockTip;
    private ResourceBundle bundle;
    /** Reveals the open note in the Explorer tree; {@code null} where there is no tree. */
    private Runnable pathRevealAction;

    /** The title row's nodes, from {@code EditorView.fxml}. */
    void wire(Label pathLabel, Region row, Label lock, FontIcon lockIcon, Tooltip lockTip) {
        this.pathLabel = pathLabel;
        this.row = row;
        this.lock = lock;
        this.lockIcon = lockIcon;
        this.lockTip = lockTip;
        if (pathLabel != null) {
            pathLabel.setOnMouseClicked(e -> {
                if (pathRevealAction != null) {
                    pathRevealAction.run();
                }
            });
        }
    }

    void setBundle(ResourceBundle bundle) {
        this.bundle = bundle;
    }

    /**
     * What clicking the folder path does (the Explorer layout reveals the note in its tree).
     * {@code null} makes the path plain text, not a link.
     */
    void setPathRevealAction(Runnable action) {
        this.pathRevealAction = action;
        if (pathLabel != null) {
            pathLabel.getStyleClass().remove("note-path-link");
            if (action != null) {
                pathLabel.getStyleClass().add("note-path-link");
            }
            pathLabel.setCursor(action != null ? Cursor.HAND : Cursor.DEFAULT);
        }
    }

    /**
     * Shows the note's folders ({@code folder / sub /}) in the title row, before the title;
     * hidden for a note at the root of the vault. The tooltip has the full file path.
     */
    void showPath(Note note) {
        if (pathLabel == null || note == null) {
            return;
        }
        String id = note.getId();
        String path;
        if (id != null && (id.contains("/") || id.contains("\\"))) {
            path = id.replace('\\', '/');
            // Keep the real extension for attachments; only assume .md when there is none.
            if (AttachmentType.extensionOf(path).isEmpty()) {
                path = path + ".md";
            }
        } else {
            String title = note.getTitle() != null ? note.getTitle() : "";
            path = title.isBlank() ? "" : (AttachmentType.extensionOf(title).isEmpty() ? title + ".md" : title);
        }
        int lastSlash = path.lastIndexOf('/');
        String folders = lastSlash > 0 ? path.substring(0, lastSlash).replace("/", " / ") + " /" : "";
        pathLabel.setText(folders);
        pathLabel.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
        if (row != null && !pathLabel.maxWidthProperty().isBound()) {
            // A deep path never takes more than 40% of the row; the title keeps the rest.
            pathLabel.maxWidthProperty().bind(row.widthProperty().multiply(0.4));
        }
        pathLabel.setTooltip(path.isBlank() ? null : new Tooltip(path));
        UiNodes.show(pathLabel, !folders.isEmpty());
    }

    /**
     * Shows a lock badge next to the title for private notes: a closed lock when the body
     * is still locked (🔒 placeholder), an open lock when it is readable this session.
     * Hidden for normal notes and attachments.
     */
    void showLock(Note note, boolean attachment) {
        if (lock == null) {
            return;
        }
        boolean isPrivate = note != null && note.isPrivate() && !attachment;
        UiNodes.show(lock, isPrivate);
        if (!isPrivate) {
            return;
        }
        boolean readable = EncryptionService.getInstance().canRead(note.getId());
        if (lockIcon != null) {
            lockIcon.setIconLiteral(readable ? "fth-unlock" : "fth-lock");
        }
        if (lockTip != null && bundle != null) {
            String key = readable ? "tooltip.note_private_unlocked" : "tooltip.note_private_locked";
            if (bundle.containsKey(key)) {
                lockTip.setText(bundle.getString(key));
            }
        }
    }
}
