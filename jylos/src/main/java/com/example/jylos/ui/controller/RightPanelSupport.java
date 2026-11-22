package com.example.jylos.ui.controller;

import java.util.ArrayList;
import java.util.HashMap;
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
 * open note, come first; plugins add theirs after ({@link PluginUi}). The view chosen in
 * each group, the order of the tabs, the views closed, the groups the panel is split in and
 * how tall they are, are kept for the next start, a plugin's too (applied when the plugin
 * comes). The links are worked out only while one of their views is in sight, in any group,
 * and when one is brought into sight. "Show backlinks" and "Show outgoing links" open the
 * panel on them.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.1
 */
final class RightPanelSupport {

    static final String BACKLINKS = "backlinks";
    static final String OUTGOING = "outgoing-links";
    /** Preference: the view chosen in each group, top to bottom, one per line. */
    static final String VIEW_KEY = "ui.right_panel.view";
    /** Preference: the order of the tabs, one view id per line. */
    static final String ORDER_KEY = "ui.right_panel.order";
    /** Preference: the views closed, one id per line. */
    static final String CLOSED_KEY = "ui.right_panel.closed";
    /** Preference: the group of each view not in the top one, a {@code id<TAB>group} per line. */
    static final String GROUPS_KEY = "ui.right_panel.groups";
    /** Preference: where the dividers between the groups are, from 0 to 1, comma-separated. */
    static final String DIVIDERS_KEY = "ui.right_panel.dividers";

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
        dock.setSplitText(i18n.apply("side.split_down"));
        dock.setMoveTexts(i18n.apply("side.move_up"), i18n.apply("side.move_down"));
        dock.setPreferredOrder(lines(prefs.get(ORDER_KEY, "")));
        dock.setPreferredGroups(groups(prefs.get(GROUPS_KEY, "")));
        dock.setPreferredDividers(dividers(prefs.get(DIVIDERS_KEY, "")));
        lines(prefs.get(CLOSED_KEY, "")).forEach(id -> dock.setClosed(id, true));
        dock.setOnOrderChanged(ids -> prefs.put(ORDER_KEY, String.join("\n", ids)));
        dock.setOnClosedChanged(ids -> prefs.put(CLOSED_KEY, String.join("\n", ids)));
        dock.setOnGroupsChanged(groups -> prefs.put(GROUPS_KEY, String.join("\n", groups.entrySet().stream()
                .filter(entry -> entry.getValue() != 0).map(entry -> entry.getKey() + "\t" + entry.getValue())
                .toList())));
        dock.setOnDividersChanged(positions -> prefs.put(DIVIDERS_KEY,
                String.join(",", positions.stream().map(String::valueOf).toList())));
        dock.addView(BACKLINKS, i18n.apply("side.backlinks"), icon("fth-arrow-down-left"), links,
                links.backlinksActions());
        dock.addView(OUTGOING, i18n.apply("links.outgoing"), icon("fth-arrow-up-right"), links.outgoingView(),
                links.outgoingActions());
        lines(prefs.get(VIEW_KEY, BACKLINKS)).forEach(dock::select);
        dock.setOnSelected(id -> {
            prefs.put(VIEW_KEY, String.join("\n", dock.shown()));
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

    /** {@code id<TAB>group} per line; a line that does not read so is left out. */
    private static Map<String, Integer> groups(String text) {
        Map<String, Integer> groups = new HashMap<>();
        for (String line : lines(text)) {
            int tab = line.lastIndexOf('\t');
            if (tab <= 0) {
                continue;
            }
            try {
                groups.put(line.substring(0, tab), Integer.parseInt(line.substring(tab + 1).strip()));
            } catch (NumberFormatException notAGroup) {
                // a hand-edited preference: the view goes to the top group
            }
        }
        return groups;
    }

    /** Positions between 0 and 1, comma-separated; any that does not read so leaves them all out. */
    private static List<Double> dividers(String text) {
        List<Double> positions = new ArrayList<>();
        try {
            for (String part : text.isBlank() ? new String[0] : text.split(",")) {
                double position = Double.parseDouble(part.strip());
                if (position <= 0 || position >= 1) {
                    return List.of();
                }
                positions.add(position);
            }
        } catch (NumberFormatException notANumber) {
            return List.of();
        }
        return positions;
    }

    /** Whether the backlinks or the outgoing links are in sight: the panel is open on one of them, in any group. */
    boolean linksInSight() {
        return dock != null && panelOpen.getAsBoolean() && dock.shown().stream().anyMatch(RightPanelSupport::isLinks);
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
