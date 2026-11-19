package com.example.jylos.ui.components;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tabs that hold a view instead of a note: the graph, or a new tab with nothing in it yet. Their ids live in the same
 * list as note ids ({@link EditorTabsModel}) and follow the same tab rules, under a prefix no
 * note id uses.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class ViewTabs {

    private static final String PREFIX = "jylos:view/";

    /** The graph's tab: there is only one. */
    public static final String GRAPH = PREFIX + "graph";

    private static final String EMPTY_PREFIX = PREFIX + "empty/";
    private static final AtomicInteger EMPTY_COUNT = new AtomicInteger();

    private ViewTabs() {
    }

    /** A new tab with nothing in it yet (the + after the tabs); each one has its own id. */
    public static String newEmptyTab() {
        return EMPTY_PREFIX + EMPTY_COUNT.incrementAndGet();
    }

    /** Whether {@code id} is a new tab with nothing in it yet. */
    public static boolean isEmptyTab(String id) {
        return id != null && id.startsWith(EMPTY_PREFIX);
    }

    /** Whether {@code id} is a view's tab rather than a note's. */
    public static boolean isView(String id) {
        return id != null && id.startsWith(PREFIX);
    }
}
