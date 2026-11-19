package com.example.jylos.ui.controller;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import com.example.jylos.data.models.Folder;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * The context menu of a folder, shared by the classic folder tree and the Explorer tree.
 *
 * <p>Entries, in order: new note, new canvas, new subfolder, a separator, rename, colour and
 * move (not for the vault root), reveal in files (folder vaults only) and delete.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class FolderContextMenuSupport {

    /** What each entry does with the folder it was opened on. */
    public record Actions(Consumer<Folder> newNote, Consumer<Folder> newCanvas, Consumer<Folder> newBoard,
            Consumer<Folder> newSubfolder,
            Consumer<Folder> rename, Consumer<Folder> move, Consumer<Folder> reveal, Consumer<Folder> delete,
            BiConsumer<Folder, String> color) {
    }

    /** The colours a folder can be given (Obsidian's canvas colours), by the key of their name. */
    private static final List<String[]> COLORS = List.of(
            new String[] {"canvas.color.red", "#e93147"},
            new String[] {"canvas.color.orange", "#ec7500"},
            new String[] {"canvas.color.yellow", "#e0ac00"},
            new String[] {"canvas.color.green", "#08b94e"},
            new String[] {"canvas.color.cyan", "#00bfbc"},
            new String[] {"canvas.color.purple", "#7852ee"});

    private FolderContextMenuSupport() {
    }

    /** Builds the menu for {@code folder}. */
    public static ContextMenu create(Folder folder, Actions actions, boolean fileVault,
            Function<String, String> i18n) {
        ContextMenu menu = new ContextMenu();
        menu.getItems().addAll(
                item(i18n.apply("action.new_note"), folder, actions.newNote()),
                item(i18n.apply("action.new_canvas"), folder, actions.newCanvas()),
                item(i18n.apply("action.new_kanban_board"), folder, actions.newBoard()),
                item(i18n.apply("action.new_subfolder"), folder, actions.newSubfolder()),
                new SeparatorMenuItem(),
                item(i18n.apply("action.rename"), folder, actions.rename()));
        if (canMove(folder)) {
            menu.getItems().add(colorMenu(folder, actions.color(), i18n));
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

    /** The colours, each with its swatch, then "No colour". */
    private static Menu colorMenu(Folder folder, BiConsumer<Folder, String> action, Function<String, String> i18n) {
        Menu menu = new Menu(i18n.apply("action.folder_color"));
        for (String[] color : COLORS) {
            MenuItem item = new MenuItem(i18n.apply(color[0]), new Rectangle(12, 12, Color.web(color[1])));
            item.setOnAction(e -> action.accept(folder, color[1]));
            menu.getItems().add(item);
        }
        MenuItem none = new MenuItem(i18n.apply("canvas.color.none"));
        none.setOnAction(e -> action.accept(folder, null));
        menu.getItems().addAll(new SeparatorMenuItem(), none);
        return menu;
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
