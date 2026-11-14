package com.example.jylos.ui.controller;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The back and forward arrows: the notes opened, as a browser keeps the pages visited. Opening
 * a note records the one left and forgets what was forward; going back makes the current note
 * reachable forward. The back/forward jumps themselves are not recorded.
 */
final class NavigationHistorySupport {

    private static final int MAX_HISTORY = 50;

    /** Ids of the notes visited before, most recent last. */
    private final Deque<String> back = new ArrayDeque<>();
    /** Ids of the notes left by going back, most recent last. */
    private final Deque<String> forward = new ArrayDeque<>();
    /** True while a back/forward jump opens its note, which must not be recorded. */
    private boolean jumping;

    /** The note to go back to from {@code currentId}, or {@code null}; {@code currentId} goes forward. */
    String back(String currentId) {
        return step(back, forward, currentId);
    }

    /** The note to go forward to from {@code currentId}, or {@code null}; {@code currentId} goes back. */
    String forward(String currentId) {
        return step(forward, back, currentId);
    }

    private static String step(Deque<String> from, Deque<String> to, String currentId) {
        if (from.isEmpty()) {
            return null;
        }
        String target = from.pollLast();
        if (currentId != null) {
            push(to, currentId);
        }
        return target;
    }

    /** A note was left for another one: it can be gone back to, and what was forward is forgotten. */
    void record(String previousId) {
        if (jumping || previousId == null) {
            return;
        }
        push(back, previousId);
        forward.clear();
    }

    /** Runs {@code jump}, the opening of a back/forward target, without recording it. */
    void jumping(Runnable jump) {
        jumping = true;
        try {
            jump.run();
        } finally {
            jumping = false;
        }
    }

    boolean canGoBack() {
        return !back.isEmpty();
    }

    boolean canGoForward() {
        return !forward.isEmpty();
    }

    private static void push(Deque<String> stack, String id) {
        if (stack.size() >= MAX_HISTORY) {
            stack.pollFirst();
        }
        stack.addLast(id);
    }
}
