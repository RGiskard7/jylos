package com.example.jylos.ui.controller;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.prefs.Preferences;

import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.event.events.SystemActionEvent.ActionType;
import com.example.jylos.ui.components.LinksPanel;
import com.example.jylos.ui.components.SideDock;

import javafx.scene.Node;

/**
 * The right panel, as Obsidian's: its own views, the backlinks and the outgoing links of the
 * open note, come first; plugins add theirs after ({@link PluginUi}). The view chosen, the
 * order of the tabs and the views closed are kept for the next start, a plugin's too (applied
 * when the plugin comes). The links are worked out only while one of their views is in sight,
 * and when one is brought into sight. "Show backlinks" and "Show outgoing links" open the
 * panel on them.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.1
 */
final class RightPanelSupport {

    static final String BACKLINKS = "backlinks";
    static final String OUTGOING = "outgoing-links";
    /** Preference: the view chosen last. */
    static final String VIEW_KEY = "ui.right_panel.view";
    /** Preference: the order of the tabs, one view id per line. */
    static final String ORDER_KEY = "ui.right_panel.order";
    /** Preference: the views closed, one id per line. */
    static final String CLOSED_KEY = "ui.right_panel.closed";

    private SideDock dock;
    private BooleanSupplier panelOpen = () -> false;
    private Runnable togglePanel = () -> {
    };

    /**
     * @param links        the backlinks and outgoing links views, with their actions
     * @param panelOpen    whether the right panel is open
     * @param refreshLinks works the links of the open note out (one of their views was chosen)
     * @param togglePanel  opens the right panel when it is closed (and closes it when open)
     */
    void wire(SideDock dock, Preferences prefs, Function<String, String> i18n, LinksPanel links,
            BooleanSupplier panelOpen, Runnable refreshLinks, Runnable togglePanel) {
        this.dock = dock;
        this.panelOpen = panelOpen != null ? panelOpen : () -> false;
        this.togglePanel = togglePanel != null ? togglePanel : () -> {
        };
        dock.setEmptyText(i18n.apply("side.empty"));
        dock.setCloseText(i18n.apply("side.close"));
        dock.setPreferredOrder(lines(prefs.get(ORDER_KEY, "")));
        lines(prefs.get(CLOSED_KEY, "")).forEach(id -> dock.setClosed(id, true));
        dock.setOnOrderChanged(ids -> prefs.put(ORDER_KEY, String.join("\n", ids)));
        dock.setOnClosedChanged(ids -> prefs.put(CLOSED_KEY, String.join("\n", ids)));
        dock.addView(BACKLINKS, i18n.apply("side.backlinks"), icon("fth-arrow-down-left"), links,
                links.backlinksActions());
        dock.addView(OUTGOING, i18n.apply("links.outgoing"), icon("fth-arrow-up-right"), links.outgoingView(),
                links.outgoingActions());
        dock.select(prefs.get(VIEW_KEY, BACKLINKS));
        dock.setOnSelected(id -> {
            prefs.put(VIEW_KEY, id);
            if (isLinks(id) && refreshLinks != null) {
                refreshLinks.run();
            }
        });
    }

    void register(Map<ActionType, Runnable> handlers) {
        handlers.put(ActionType.SHOW_BACKLINKS, () -> show(BACKLINKS));
        handlers.put(ActionType.SHOW_OUTGOING_LINKS, () -> show(OUTGOING));
    }

    /** Opens the panel, if closed, on the view {@code id}, opening the view if it was closed. */
    void show(String id) {
        if (dock == null) {
            return;
        }
        dock.setClosed(id, false);
        dock.select(id);
        if (!panelOpen.getAsBoolean()) {
            togglePanel.run();
        }
    }

    private static List<String> lines(String text) {
        return text.isBlank() ? List.of() : List.of(text.split("\n"));
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
