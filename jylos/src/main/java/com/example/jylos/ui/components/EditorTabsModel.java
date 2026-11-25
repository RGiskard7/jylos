package com.example.jylos.ui.components;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Order, active state and history of the editor tabs, without any UI. A tab shows a note, a
 * view such as the graph, or nothing yet (a new tab, see {@link ViewTabs}); what it shows is
 * an id. The rules, as in Obsidian except where noted:
 * <ul>
 *   <li>a plain open ({@link OpenTarget#CURRENT_TAB}) shows the id in the active tab, which
 *       keeps what it showed before in its own history;</li>
 *   <li>{@link OpenTarget#NEW_TAB} (Cmd/Ctrl+click, middle click, "Open in new tab") inserts
 *       a tab right after the active one, or fills the active one while it is a new, empty
 *       tab;</li>
 *   <li>a new empty tab (the +) is always added, at the end;</li>
 *   <li>unlike Obsidian, an id is never in two tabs: opening one that already has a tab
 *       activates it, and going back to one takes it from the tab that had it;</li>
 *   <li>with "always open in a new tab" every open behaves like {@code NEW_TAB}.</li>
 *   <li>a pinned tab, as in Obsidian, keeps what it shows: what is opened from it goes to a
 *       new tab. Pinned tabs go first, in the order they were pinned, and a new tab never
 *       goes among them; closing "the others" leaves them.</li>
 * </ul>
 * Back and forward go through what the active tab showed, up to {@value #MAX_HISTORY} steps.
 * Each step keeps where its note was left ({@link NotePosition}), so going back or forward, or
 * bringing a tab to the front, shows the note there again; a note opened in a tab starts at
 * the top. Two visits to the same note are two steps, each with its own position.
 *
 * @since 2.6.0
 * @author Edu Díaz (RGiskard7)
 */
public final class EditorTabsModel {

    /** Where a note should open. */
    public enum OpenTarget {
        CURRENT_TAB,
        NEW_TAB
    }

    private static final int MAX_HISTORY = 20;

    /** A step of a tab's history: what it showed, and where its note was left. */
    private record Entry(String id, NotePosition position) {
    }

    /** A tab: what it shows and where, and what it showed before and after it. */
    private static final class Tab {
        String id;
        NotePosition position;
        boolean pinned;
        final Deque<Entry> back = new ArrayDeque<>();
        final Deque<Entry> forward = new ArrayDeque<>();

        Tab(String id) {
            this.id = id;
        }
    }

    private final List<Tab> tabs = new ArrayList<>();
    private Tab active;
    private boolean alwaysNewTab;

    /** Opens {@code id} following the rules above and makes its tab the active one. */
    public void open(String id, OpenTarget target) {
        if (id == null) {
            return;
        }
        Tab existing = find(id);
        if (existing != null) {
            active = existing;
            return;
        }
        if (ViewTabs.isEmptyTab(id)) {
            Tab tab = new Tab(id);
            tabs.add(tab);
            active = tab;
            return;
        }
        boolean activeIsEmpty = active != null && ViewTabs.isEmptyTab(active.id);
        boolean inPlace = target == OpenTarget.CURRENT_TAB && !alwaysNewTab && !(active != null && active.pinned);
        if (active != null && (inPlace || activeIsEmpty)) {
            show(active, id);
            return;
        }
        Tab tab = new Tab(id);
        int activeIndex = active != null ? tabs.indexOf(active) : -1;
        tabs.add(activeIndex >= 0 ? Math.max(activeIndex + 1, pinnedCount()) : tabs.size(), tab);
        active = tab;
    }

    /** The active tab goes back to what it showed before; returns it, or {@code null} when there is nothing. */
    public String back() {
        return step(true);
    }

    /** The active tab goes forward again; returns what it shows, or {@code null} when there is nothing. */
    public String forward() {
        return step(false);
    }

    private String step(boolean back) {
        if (active == null) {
            return null;
        }
        Deque<Entry> from = back ? active.back : active.forward;
        Deque<Entry> to = back ? active.forward : active.back;
        if (from.isEmpty()) {
            return null;
        }
        Entry target = from.peekLast();
        Tab other = find(target.id());
        if (other != null && other.pinned) {
            active = other; // a pinned tab keeps what it shows: it comes forward instead
            return target.id();
        }
        from.pollLast();
        push(to, new Entry(active.id, active.position));
        NotePosition position = target.position();
        if (other != null) {
            tabs.remove(other); // an id is in one tab only, and it was left there last
            position = other.position;
        }
        active.id = target.id();
        active.position = position;
        return target.id();
    }

    public boolean canGoBack() {
        return active != null && !active.back.isEmpty();
    }

    public boolean canGoForward() {
        return active != null && !active.forward.isEmpty();
    }

    /** "Always open in a new tab" preference. */
    public void setAlwaysNewTab(boolean alwaysNewTab) {
        this.alwaysNewTab = alwaysNewTab;
    }

    /**
     * Pins {@code id}'s tab: it goes after the tabs already pinned. A new, empty tab is not
     * pinned (it shows nothing to keep).
     */
    public void pin(String id) {
        Tab tab = find(id);
        if (tab == null || tab.pinned || ViewTabs.isEmptyTab(tab.id)) {
            return;
        }
        tabs.remove(tab);
        tabs.add(pinnedCount(), tab);
        tab.pinned = true;
    }

    /** Unpins {@code id}'s tab: it goes first among the tabs not pinned. */
    public void unpin(String id) {
        Tab tab = find(id);
        if (tab == null || !tab.pinned) {
            return;
        }
        tabs.remove(tab);
        tab.pinned = false;
        tabs.add(pinnedCount(), tab);
    }

    public boolean isPinned(String id) {
        Tab tab = find(id);
        return tab != null && tab.pinned;
    }

    /** The pinned tabs, in order. */
    public List<String> pinnedIds() {
        return tabs.stream().filter(tab -> tab.pinned).map(tab -> tab.id).toList();
    }

    /** What "close the others" closes from {@code id}'s tab: every tab but it and the pinned ones. */
    public List<String> othersToClose(String id) {
        return tabs.stream().filter(tab -> !tab.pinned && !Objects.equals(tab.id, id)).map(tab -> tab.id).toList();
    }

    private int pinnedCount() {
        return (int) tabs.stream().filter(tab -> tab.pinned).count();
    }

    /** Removes the tab; when it was the active one, no tab is active afterwards. */
    public void remove(String id) {
        Tab tab = find(id);
        if (tab == null) {
            return;
        }
        tabs.remove(tab);
        if (tab == active) {
            active = null;
        }
    }

    /** A note that no longer exists leaves every tab's history. */
    public void forget(String id) {
        for (Tab tab : tabs) {
            tab.back.removeIf(entry -> entry.id().equals(id));
            tab.forward.removeIf(entry -> entry.id().equals(id));
        }
    }

    /**
     * Gives {@code oldId} the id {@code newId} in its tab and in every history (a renamed
     * file-backed note), where it keeps its positions. If {@code newId} already has a tab,
     * {@code oldId}'s tab is dropped.
     */
    public void rebind(String oldId, String newId) {
        if (oldId == null || newId == null || oldId.equals(newId)) {
            return;
        }
        for (Tab tab : tabs) {
            replaceIn(tab.back, oldId, newId);
            replaceIn(tab.forward, oldId, newId);
        }
        Tab tab = find(oldId);
        if (tab == null) {
            return;
        }
        if (find(newId) != null) {
            tabs.remove(tab);
            if (tab == active) {
                active = find(newId);
            }
        } else {
            tab.id = newId;
            if (tab.position != null) {
                tab.position = renamed(tab.position, newId);
            }
        }
    }

    public void clear() {
        tabs.clear();
        active = null;
    }

    /**
     * The tab to activate after closing {@code id}: the previous one, or the next one for
     * the first tab, or {@code null} when it is the only tab or not open.
     */
    public String neighborOf(String id) {
        Tab tab = find(id);
        if (tab == null) {
            return null;
        }
        int index = tabs.indexOf(tab);
        if (index > 0) {
            return tabs.get(index - 1).id;
        }
        return tabs.size() > 1 ? tabs.get(1).id : null;
    }

    public boolean isOpen(String id) {
        return id != null && find(id) != null;
    }

    /** What the tabs show, in display order (a copy). */
    public List<String> getIds() {
        return tabs.stream().map(tab -> tab.id).toList();
    }

    public String getActiveId() {
        return active != null ? active.id : null;
    }

    /** What back goes through in {@code id}'s tab, the last one first reached; empty when it is not open. */
    public List<String> backOf(String id) {
        Tab tab = find(id);
        return tab != null ? ids(tab.back) : List.of();
    }

    /** What forward goes through in {@code id}'s tab, the last one first reached. */
    public List<String> forwardOf(String id) {
        Tab tab = find(id);
        return tab != null ? ids(tab.forward) : List.of();
    }

    /**
     * Gives {@code id}'s tab this back and forward (a workspace or the last session reopening
     * them), keeping the last {@value #MAX_HISTORY} of each; their notes start at the top.
     * Nothing happens when it is not open.
     */
    public void setHistory(String id, List<String> back, List<String> forward) {
        Tab tab = find(id);
        if (tab == null) {
            return;
        }
        tab.back.clear();
        tab.forward.clear();
        back.forEach(entry -> push(tab.back, new Entry(entry, null)));
        forward.forEach(entry -> push(tab.forward, new Entry(entry, null)));
    }

    /**
     * Keeps where the note in {@code position} was left, in the tab that shows it. Nothing
     * happens when no tab shows it (it was already left, or nothing was in sight).
     */
    public void remember(NotePosition position) {
        Tab tab = position != null ? find(position.noteId()) : null;
        if (tab != null) {
            tab.position = position;
        }
    }

    /**
     * Where the note {@code id}'s tab shows was left, or {@code null} when it has to start at
     * the top (it was just opened) or no tab shows it.
     */
    public NotePosition positionOf(String id) {
        Tab tab = id != null ? find(id) : null;
        return tab != null ? tab.position : null;
    }

    private Tab find(String id) {
        for (Tab tab : tabs) {
            if (Objects.equals(tab.id, id)) {
                return tab;
            }
        }
        return null;
    }

    /** The tab shows {@code id}; what it showed goes back, and what was forward is forgotten. */
    private static void show(Tab tab, String id) {
        push(tab.back, new Entry(tab.id, tab.position));
        tab.forward.clear();
        tab.id = id;
        tab.position = null; // a note opened starts at the top
    }

    private static void push(Deque<Entry> stack, Entry entry) {
        if (stack.size() >= MAX_HISTORY) {
            stack.pollFirst();
        }
        stack.addLast(entry);
    }

    private static List<String> ids(Deque<Entry> stack) {
        return stack.stream().map(Entry::id).toList();
    }

    private static void replaceIn(Deque<Entry> stack, String oldId, String newId) {
        List<Entry> entries = new ArrayList<>(stack);
        stack.clear();
        entries.forEach(entry -> stack.addLast(entry.id().equals(oldId)
                ? new Entry(newId, entry.position() != null ? renamed(entry.position(), newId) : null)
                : entry));
    }

    private static NotePosition renamed(NotePosition position, String newId) {
        return new NotePosition(newId, position.editorTop(), position.editorOffset(), position.selectionAnchor(),
                position.selectionHead(), position.readingY());
    }
}
