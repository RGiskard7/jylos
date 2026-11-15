package com.example.jylos.ui.components;

import java.util.ArrayList;
import java.util.List;

/**
 * Order and active state of the editor tabs, without any UI. Tabs behave like VS Code:
 * <ul>
 *   <li>a plain open ({@link OpenTarget#CURRENT_TAB}) replaces the note shown in the active
 *       tab, in the same position;</li>
 *   <li>a note that already has a tab is activated, never duplicated;</li>
 *   <li>{@link OpenTarget#NEW_TAB} (Cmd/Ctrl+click, middle click, "Open in new tab")
 *       inserts a tab right after the active one;</li>
 *   <li>with "always open in a new tab" every open behaves like {@code NEW_TAB}.</li>
 * </ul>
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

    private final List<String> ids = new ArrayList<>();
    private String activeId;
    private boolean alwaysNewTab;

    /** Opens {@code id} following the rules above and makes it the active tab. */
    public void open(String id, OpenTarget target) {
        if (id == null) {
            return;
        }
        if (!ids.contains(id)) {
            int activeIndex = activeId != null ? ids.indexOf(activeId) : -1;
            boolean replace = target == OpenTarget.CURRENT_TAB && !alwaysNewTab && activeIndex >= 0;
            if (replace) {
                ids.set(activeIndex, id);
            } else if (activeIndex >= 0) {
                ids.add(activeIndex + 1, id);
            } else {
                ids.add(id);
            }
        }
        activeId = id;
    }

    /** "Always open in a new tab" preference. */
    public void setAlwaysNewTab(boolean alwaysNewTab) {
        this.alwaysNewTab = alwaysNewTab;
    }

    /** Removes the tab; when it was the active one, no tab is active afterwards. */
    public void remove(String id) {
        ids.remove(id);
        if (id != null && id.equals(activeId)) {
            activeId = null;
        }
    }

    /**
     * Gives the tab {@code oldId} the id {@code newId} in the same position (a renamed
     * file-backed note). If {@code newId} already has a tab, {@code oldId}'s tab is dropped.
     */
    public void rebind(String oldId, String newId) {
        if (oldId == null || newId == null || oldId.equals(newId)) {
            return;
        }
        int index = ids.indexOf(oldId);
        if (index < 0) {
            return;
        }
        if (ids.contains(newId)) {
            ids.remove(index);
        } else {
            ids.set(index, newId);
        }
        if (oldId.equals(activeId)) {
            activeId = newId;
        }
    }

    public void clear() {
        ids.clear();
        activeId = null;
    }

    /**
     * The tab to activate after closing {@code id}: the previous one, or the next one for
     * the first tab, or {@code null} when it is the only tab or not open.
     */
    public String neighborOf(String id) {
        int index = ids.indexOf(id);
        if (index < 0) {
            return null;
        }
        if (index > 0) {
            return ids.get(index - 1);
        }
        return ids.size() > 1 ? ids.get(1) : null;
    }

    public boolean isOpen(String id) {
        return id != null && ids.contains(id);
    }

    /** Tab ids in display order (a copy). */
    public List<String> getIds() {
        return List.copyOf(ids);
    }

    public String getActiveId() {
        return activeId;
    }
}
