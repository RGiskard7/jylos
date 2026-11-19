package com.example.jylos.workspace;

import java.util.List;

/**
 * What one tab showed before and after what it shows now: its back and forward, oldest
 * first, as notes ids or view ids (the graph).
 *
 * @param back    what back goes through, the last one first reached
 * @param forward what forward goes through, the last one first reached
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public record TabHistory(List<String> back, List<String> forward) {

    /** A tab with nothing to go back or forward to. */
    public static final TabHistory NONE = new TabHistory(List.of(), List.of());

    public TabHistory {
        back = back != null ? List.copyOf(back) : List.of();
        forward = forward != null ? List.copyOf(forward) : List.of();
    }
}
