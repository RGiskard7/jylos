package com.example.jylos.ui.controller;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.data.models.Note;
import com.example.jylos.event.events.SystemActionEvent.ActionType;
import com.example.jylos.ui.components.LinksPanel;
import com.example.jylos.ui.components.SideDock;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The right panel, as Obsidian's: its own views, the backlinks and the outgoing links of the
 * open note, come first; plugins add theirs after ({@link PluginUi}). The view chosen in
 * each group, the order of the tabs, the views closed, the groups the panel is split in and
 * how tall they are, are kept for the next start, a plugin's too (applied when the plugin
 * comes). The links are worked out only while one of their views is in sight, in any group,
 * and when one is brought into sight. "Show backlinks" and "Show outgoing links" open the
 * panel on them.
 *
 * <p>A links view can be pinned, from its tab's menu, to the note in sight: a copy of it, in a
 * group of its own under it, shows that note's links whatever note is open, and follows the
 * note when it is renamed. The copies are kept for the next start; closing one takes it away.
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
    /** Preference: the copies of the links views pinned to a note, a {@code id<TAB>view<TAB>note id} per line. */
    static final String PINNED_KEY = "ui.right_panel.pinned";

    private SideDock dock;
    private Preferences prefs;
    private Function<String, String> i18n = key -> key;
    private BacklinksSupport pins;
    private Supplier<Note> noteInSight = () -> null;
    /** The pinned copies: the view each copies, and the line over it naming its note. */
    private final Map<String, Copy> copies = new LinkedHashMap<>();

    private record Copy(String kind, Label pinnedTo) {
    }

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
        this.prefs = prefs;
        this.i18n = i18n;
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
            if (pins != null && copies.containsKey(id)) {
                pins.refreshPinned(id);
            }
        });
    }

    /**
     * Lets the links views be pinned to the note in sight ({@code noteInSight}, {@code null} for
     * none), as copies fed by {@code links}, and brings back the copies kept from last time.
     */
    void pinWith(BacklinksSupport links, Supplier<Note> noteInSight) {
        if (dock == null || links == null) {
            return;
        }
        pins = links;
        this.noteInSight = noteInSight != null ? noteInSight : () -> null;
        dock.setMenuExtras(id -> isLinks(id) ? List.of(pinItem(id)) : List.of());
        dock.setOnDiscarded(id -> {
            links.forget(id);
            copies.remove(id);
            savePins();
        });
        links.setOnPinnedShown(this::pinnedShown);
        links.setOnPinnedRebound((copy, noteId) -> {
            savePins();
            name(copy, titleOf(noteId), true);
        });
        for (String line : lines(prefs.get(PINNED_KEY, ""))) {
            String[] parts = line.split("\t", -1);
            if (parts.length == 3 && isLinks(parts[1]) && !parts[2].isBlank()) {
                addCopy(parts[0], parts[1], parts[2], false);
            }
        }
    }

    /** The panel was opened: the pinned copies in sight work their links out. */
    void panelOpened() {
        if (pins != null) {
            List.copyOf(copies.keySet()).forEach(pins::refreshPinned);
        }
    }

    /** "Pin a copy to «note»", for the links view {@code kind}: unusable with no note in sight. */
    private MenuItem pinItem(String kind) {
        Note note = noteInSight.get();
        MenuItem item = new MenuItem(MessageFormat.format(i18n.apply("side.pin_copy"),
                note != null ? note.getTitle() : ""));
        item.setDisable(note == null || note.getId() == null);
        item.setOnAction(e -> {
            int next = 1;
            while (dock.viewIds().contains(kind + "@" + next) || copies.containsKey(kind + "@" + next)) {
                next++;
            }
            addCopy(kind + "@" + next, kind, note.getId(), true);
            savePins();
        });
        return item;
    }

    /**
     * Adds the copy {@code copyId} of the links view {@code kind}, pinned to {@code noteId}: a new
     * one goes in a group of its own under the view it copies, one kept from last time where it was.
     */
    private void addCopy(String copyId, String kind, String noteId, boolean fresh) {
        LinksPanel panel = pins.pinnedPanel(copyId, kind, noteId,
                () -> panelOpen.getAsBoolean() && dock.shown().contains(copyId));
        Label pinnedTo = new Label(MessageFormat.format(i18n.apply("side.pinned_to"), titleOf(noteId)));
        pinnedTo.getStyleClass().addAll("side-dock-pinned-to", "info-value-small");
        pinnedTo.setCursor(Cursor.HAND);
        pinnedTo.setOnMouseClicked(e -> pins.openPinned(copyId));
        copies.put(copyId, new Copy(kind, pinnedTo));
        boolean backlinks = BACKLINKS.equals(kind);
        Node content = backlinks ? panel : panel.outgoingView();
        VBox actions = new VBox(pinnedTo, backlinks ? panel.backlinksActions() : panel.outgoingActions());
        HBox icon = new HBox(1, icon(backlinks ? "fth-arrow-down-left" : "fth-arrow-up-right"), pinBadge());
        icon.setAlignment(Pos.CENTER);
        String title = copyTitle(kind, titleOf(noteId));
        if (fresh) {
            dock.addViewInNewGroupBelow(kind, copyId, title, icon, content, actions);
        } else {
            dock.addView(copyId, title, icon, content, actions);
        }
        dock.setDisposable(copyId);
    }

    /** A copy was worked out: the line over it and its tab say which note it shows, or that it is not there. */
    private void pinnedShown(String copyId, Note note) {
        name(copyId, note != null ? note.getTitle() : titleOf(pins.pinnedNoteId(copyId)), note != null);
    }

    /** The line over a copy and its tab name its note ({@code found}) or say it is not there. */
    private void name(String copyId, String title, boolean found) {
        Copy copy = copies.get(copyId);
        if (copy == null) {
            return;
        }
        copy.pinnedTo().setText(MessageFormat.format(i18n.apply(found ? "side.pinned_to" : "side.pinned_missing"), title));
        dock.setTitle(copyId, copyTitle(copy.kind(), title));
    }

    private String copyTitle(String kind, String noteTitle) {
        return i18n.apply(BACKLINKS.equals(kind) ? "side.backlinks" : "links.outgoing") + " · " + noteTitle;
    }

    private void savePins() {
        List<String> lines = new ArrayList<>();
        copies.forEach((copyId, copy) -> lines.add(copyId + "\t" + copy.kind() + "\t" + pins.pinnedNoteId(copyId)));
        prefs.put(PINNED_KEY, String.join("\n", lines));
    }

    /** A note's name from its id (its path): the file's name without its extension. */
    private static String titleOf(String noteId) {
        if (noteId == null) {
            return "";
        }
        String name = noteId.substring(noteId.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
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

    /** A small pin beside a copy's icon. */
    private static Node pinBadge() {
        FontIcon pin = new FontIcon("bi-pin-angle");
        pin.setIconSize(11);
        pin.getStyleClass().addAll("feather-icon", "side-dock-pin-badge");
        return pin;
    }

    private static Node icon(String literal) {
        FontIcon icon = new FontIcon(literal);
        icon.setIconSize(16); // as the left panel's tabs
        icon.getStyleClass().add("feather-icon");
        return icon;
    }
}
