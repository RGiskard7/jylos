package com.example.jylos.ui.components;

import com.example.jylos.ui.web.HtmlView;

import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;

/**
 * The right-click menu of a page that is read, not edited (the reading view, the release
 * notes): Copy and Select all, in the app's language, closing as the app's menus do. It
 * replaces the engine's own menu, which is in English and offers to reload the page or open
 * links in windows the app does not have.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class PageTextMenu {

    private static final String KEY = PageTextMenu.class.getName();

    private PageTextMenu() {
    }

    /** Gives {@code page} this menu, or relabels the one it has. */
    public static void install(HtmlView page, String copyLabel, String selectAllLabel) {
        if (page == null) {
            return;
        }
        Node node = page.node();
        if (node.getProperties().get(KEY) instanceof ContextMenu menu) {
            menu.getItems().get(0).setText(copyLabel);
            menu.getItems().get(1).setText(selectAllLabel);
            return;
        }
        page.setContextMenuEnabled(false);
        MenuItem copy = new MenuItem(copyLabel);
        copy.setOnAction(event -> copySelection(page));
        MenuItem selectAll = new MenuItem(selectAllLabel);
        selectAll.setOnAction(event -> page.run("window.getSelection().selectAllChildren(document.body)"));
        ContextMenu menu = new ContextMenu(copy, selectAll);
        node.getProperties().put(KEY, menu);
        node.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            copy.setDisable(selectedText(page).isEmpty());
            ContextMenus.show(menu, node, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    private static String selectedText(HtmlView page) {
        return page.evaluate("window.getSelection().toString()") instanceof String text ? text : "";
    }

    private static void copySelection(HtmlView page) {
        String text = selectedText(page);
        if (!text.isEmpty()) {
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            Clipboard.getSystemClipboard().setContent(content);
        }
    }
}
