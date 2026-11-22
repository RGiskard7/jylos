package com.example.jylos.ui.controller;

import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.prefs.Preferences;

import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.ui.components.SideDock;

import javafx.scene.Node;

/**
 * The right panel, as Obsidian's: its own views, the backlinks and the outgoing links of the
 * open note, come first; plugins add theirs after ({@link PluginUi}). The view chosen is
 * remembered for the next start, a plugin's too (chosen when the plugin comes). The links are
 * worked out only while one of their views is in sight, and when one is brought into sight.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.1
 */
final class RightPanelSupport {

    static final String BACKLINKS = "backlinks";
    static final String OUTGOING = "outgoing-links";
    /** Preference: the view chosen last. */
    static final String VIEW_KEY = "ui.right_panel.view";

    private SideDock dock;
    private BooleanSupplier panelOpen = () -> false;

    /**
     * @param backlinks    the backlinks view
     * @param outgoing     the outgoing links view
     * @param panelOpen    whether the right panel is open
     * @param refreshLinks works the links of the open note out (one of their views was chosen)
     */
    void wire(SideDock dock, Preferences prefs, Function<String, String> i18n, Node backlinks, Node outgoing,
            BooleanSupplier panelOpen, Runnable refreshLinks) {
        this.dock = dock;
        this.panelOpen = panelOpen != null ? panelOpen : () -> false;
        dock.setEmptyText(i18n.apply("side.empty"));
        dock.addView(BACKLINKS, i18n.apply("side.backlinks"), icon("fth-arrow-down-left"), backlinks);
        dock.addView(OUTGOING, i18n.apply("links.outgoing"), icon("fth-arrow-up-right"), outgoing);
        dock.select(prefs.get(VIEW_KEY, BACKLINKS));
        dock.setOnSelected(id -> {
            prefs.put(VIEW_KEY, id);
            if (isLinks(id) && refreshLinks != null) {
                refreshLinks.run();
            }
        });
    }

    /** Whether the backlinks or the outgoing links are in sight: the panel is open on one of them. */
    boolean linksInSight() {
        return dock != null && panelOpen.getAsBoolean()
                && dock.selected().map(RightPanelSupport::isLinks).orElse(false);
    }

    private static boolean isLinks(String id) {
        return BACKLINKS.equals(id) || OUTGOING.equals(id);
    }

    private static Node icon(String literal) {
        FontIcon icon = new FontIcon(literal);
        icon.setIconSize(16); // as the left panel's tabs
        icon.getStyleClass().add("feather-icon");
        return icon;
    }
}
