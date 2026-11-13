package com.example.jylos.ui.controller;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.example.jylos.data.models.Note;
import com.example.jylos.plugin.NoteContextMenuEntry;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

/**
 * The context menu of a note, the same wherever the note is shown: notes list, grid card
 * and Explorer tree.
 *
 * <p>Entries, in order: open, open in new tab, pin, favorite, private, rename, move, reveal
 * in files (folder vaults only), export, then the move-to-trash entry behind a separator, and
 * finally any plugin entries behind another separator. A private note cannot be moved to the
 * trash (it has to be made normal first), so that entry is disabled for it.</p>
 */
public final class NoteContextMenuSupport {

    /** What each entry does with the note it was opened on. */
    public record Actions(Consumer<Note> open, Consumer<Note> openInNewTab, Consumer<Note> togglePin,
            Consumer<Note> toggleFavorite, Consumer<Note> togglePrivacy, Consumer<Note> rename,
            Consumer<Note> move, Consumer<Note> reveal, Consumer<Note> export, Consumer<Note> moveToTrash,
            Consumer<Note> duplicate, Consumer<Note> copyPath, Consumer<Note> openInDefaultApp) {

        /** The same actions with a different "Open". */
        public Actions withOpen(Consumer<Note> otherOpen) {
            return new Actions(otherOpen, openInNewTab, togglePin, toggleFavorite, togglePrivacy, rename, move,
                    reveal, export, moveToTrash, duplicate, copyPath, openInDefaultApp);
        }

        /** The same actions with a different "Rename" (the Explorer renames in the row itself). */
        public Actions withRename(Consumer<Note> otherRename) {
            return new Actions(open, openInNewTab, togglePin, toggleFavorite, togglePrivacy, otherRename, move,
                    reveal, export, moveToTrash, duplicate, copyPath, openInDefaultApp);
        }
    }

    private NoteContextMenuSupport() {
    }

    /**
     * Builds the menu for {@code note}. Labels that depend on the note's state (pin,
     * favorite, private) are resolved now, so build the menu again when that state changes.
     */
    public static ContextMenu create(Note note, Actions actions, boolean fileVault, Function<String, String> i18n,
            List<NoteContextMenuEntry> pluginEntries) {
        ContextMenu menu = new ContextMenu();
        menu.getItems().addAll(
                item(i18n.apply("action.open"), note, actions.open()),
                item(i18n.apply("action.open_in_new_tab"), note, actions.openInNewTab()),
                item(i18n.apply(note.isPinned() ? "action.unpin_note" : "action.pin_note"), note, actions.togglePin()),
                item(i18n.apply(note.isFavorite() ? "action.remove_favorite" : "action.add_favorite"), note,
                        actions.toggleFavorite()),
                item(i18n.apply(note.isPrivate() ? "action.make_normal" : "action.make_private"), note,
                        actions.togglePrivacy()),
                item(i18n.apply("action.rename"), note, actions.rename()),
                item(i18n.apply("action.move_to"), note, actions.move()));
        // Images and PDFs are files, not notes: only notes and canvases are copied.
        String title = note.getTitle() != null ? note.getTitle() : "";
        if (!com.example.jylos.util.AttachmentType.isAttachment(title)
                || title.toLowerCase(java.util.Locale.ROOT).endsWith(".canvas")) {
            menu.getItems().add(item(i18n.apply("action.duplicate"), note, actions.duplicate()));
        }
        if (fileVault) {
            menu.getItems().addAll(
                    item(i18n.apply("action.copy_path"), note, actions.copyPath()),
                    item(i18n.apply("action.open_default_app"), note, actions.openInDefaultApp()),
                    item(i18n.apply("action.reveal_in_files"), note, actions.reveal()));
        }
        MenuItem moveToTrash = item(i18n.apply("action.move_to_trash"), note, actions.moveToTrash());
        moveToTrash.setDisable(note.isPrivate());
        menu.getItems().addAll(item(i18n.apply("action.export_note"), note, actions.export()),
                new SeparatorMenuItem(), moveToTrash);

        if (pluginEntries != null && !pluginEntries.isEmpty()) {
            menu.getItems().add(new SeparatorMenuItem());
            for (NoteContextMenuEntry entry : pluginEntries) {
                menu.getItems().add(item(entry.label(), note, entry.action()));
            }
        }
        return menu;
    }

    private static MenuItem item(String label, Note note, Consumer<Note> action) {
        MenuItem item = new MenuItem(label);
        item.setOnAction(e -> {
            if (action != null) {
                action.accept(note);
            }
        });
        return item;
    }
}
