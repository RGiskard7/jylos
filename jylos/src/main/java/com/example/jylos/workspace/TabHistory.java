package com.example.jylos.workspace;

import java.util.List;

/**
 * What one tab keeps between sessions: what it showed before and after what it shows now (its
 * back and forward, oldest first, as notes ids or view ids such as the graph), and whether it
 * is pinned.
 *
 * @param back    what back goes through, the last one first reached
 * @param forward what forward goes through, the last one first reached
 * @param pinned  whether the tab is pinned (see {@code EditorTabsModel})
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public record TabHistory(List<String> back, List<String> forward, boolean pinned) {

    /** A tab with nothing to go back or forward to. */
    public static final TabHistory NONE = new TabHistory(List.of(), List.of(), false);

    public TabHistory {
        back = back != null ? List.copyOf(back) : List.of();
        forward = forward != null ? List.copyOf(forward) : List.of();
    }

    /** A tab that is not pinned. */
    public TabHistory(List<String> back, List<String> forward) {
        this(back, forward, false);
    }
}
