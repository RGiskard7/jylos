package com.example.jylos.ui.controller;

import java.util.function.Consumer;
import java.util.function.Function;

import com.example.jylos.data.models.Folder;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

/**
 * The context menu of a folder, shared by the classic folder tree and the Explorer tree.
 *
 * <p>Entries, in order: new note, new canvas, new subfolder, a separator, rename, move
 * (not for the vault root), reveal in files (folder vaults only) and delete.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class FolderContextMenuSupport {

    /** What each entry does with the folder it was opened on. */
    public record Actions(Consumer<Folder> newNote, Consumer<Folder> newCanvas, Consumer<Folder> newSubfolder,
            Consumer<Folder> rename, Consumer<Folder> move, Consumer<Folder> reveal, Consumer<Folder> delete) {
    }

    private FolderContextMenuSupport() {
    }

    /** Builds the menu for {@code folder}. */
    public static ContextMenu create(Folder folder, Actions actions, boolean fileVault,
            Function<String, String> i18n) {
        ContextMenu menu = new ContextMenu();
        menu.getItems().addAll(
                item(i18n.apply("action.new_note"), folder, actions.newNote()),
                item(i18n.apply("action.new_canvas"), folder, actions.newCanvas()),
                item(i18n.apply("action.new_subfolder"), folder, actions.newSubfolder()),
                new SeparatorMenuItem(),
                item(i18n.apply("action.rename"), folder, actions.rename()));
        if (canMove(folder)) {
            menu.getItems().add(item(i18n.apply("action.move_to"), folder, actions.move()));
        }
        if (fileVault) {
            menu.getItems().add(item(i18n.apply("action.reveal_in_files"), folder, actions.reveal()));
        }
        menu.getItems().add(item(i18n.apply("action.delete"), folder, actions.delete()));
        return menu;
    }

    /** The vault root (and the tree's invisible root above it) cannot be moved. */
    public static boolean canMove(Folder folder) {
        if (folder == null || folder.getId() == null) {
            return false;
        }
        String id = FolderDropSupport.normalizeId(folder.getId());
        return !"ROOT".equals(id) && !"INVISIBLE_ROOT".equals(id);
    }

    private static MenuItem item(String label, Folder folder, Consumer<Folder> action) {
        MenuItem item = new MenuItem(label);
        item.setOnAction(e -> {
            if (action != null) {
                action.accept(folder);
            }
        });
        return item;
    }
}
