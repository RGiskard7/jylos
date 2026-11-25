package com.example.jylos.ui;

import java.util.List;

import javafx.collections.ListChangeListener;
import javafx.event.Event;
import javafx.event.EventTarget;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.input.InputMethodTextRun;
import javafx.stage.Window;

/**
 * Makes a dead key pressed again and again type one mark per press in every text input of the
 * app, as in any other app: the grave accent three times opens a code block on a Spanish
 * keyboard. It covers the editor and the reading view (web pages) and every JavaFX
 * {@code TextField} and {@code TextArea}, in every window.
 *
 * <p>On macOS a dead key is typed as an input method composition: the mark is pending until
 * the next key. Pressing it again should commit the pending mark and leave a new one pending,
 * but JavaFX delivers every other such press as a new composition with nothing committed, and
 * the control replaces the pending mark with it: that press is lost (recorded from a real
 * keyboard: five presses typed three marks, in a web page and in a {@code TextField} alike).
 * When a composition of a single dead-key mark arrives while that very mark is pending and
 * nothing was committed, the pending one is committed first. Compositions of any other text (an
 * input method for Japanese, say, which recomposes as it goes) pass through untouched.</p>
 *
 * <p>The bug is in JavaFX. The report for JavaFX, a stand-alone test case and when to remove this
 * class are in {@code docs/upstream/javafx-macos-dead-keys}.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class DeadKeyRepeats {

    /** What the page has pending (composed, not committed), as far as the events said. */
    private String pending = "";
    private boolean replaying;

    private DeadKeyRepeats() {
    }

    private static boolean installed;

    /**
     * Watches every window of the app, those already open and those opened later (dialogs,
     * pop-ups): every text input in them, a web page's or a JavaFX field's, gets one mark per
     * press. Call it once on the JavaFX Application Thread; calling it again does nothing.
     */
    public static void installEverywhere() {
        if (installed) {
            return;
        }
        installed = true;
        Window.getWindows().forEach(DeadKeyRepeats::install);
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                change.getAddedSubList().forEach(DeadKeyRepeats::install);
            }
        });
    }

    private static void install(Window window) {
        DeadKeyRepeats repeats = new DeadKeyRepeats();
        window.addEventFilter(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, repeats::filter);
    }

    private void filter(InputMethodEvent event) {
        String composed = text(event.getComposed());
        if (!replaying && event.getCommitted().isEmpty() && isMark(composed) && composed.equals(pending)) {
            event.consume();
            EventTarget target = event.getTarget();
            replaying = true;
            try {
                Event.fireEvent(target, new InputMethodEvent(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                        List.of(), pending, 0));
                Event.fireEvent(target, new InputMethodEvent(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                        event.getComposed(), "", event.getCaretPosition()));
            } finally {
                replaying = false;
            }
            return;
        }
        pending = composed;
    }

    /**
     * A spacing accent, as a dead key composes it: a Unicode modifier symbol (grave, acute,
     * diaeresis, macron, cedilla and the rest), a spacing modifier letter (the circumflex and
     * caron macOS composes, U+02B0 to U+02FF), the ASCII tilde, which Unicode files as a math
     * symbol, or the apostrophe and the double quote, dead keys on layouts such as U.S.
     * International. Other modifier letters, such as the Japanese long vowel mark, are text.
     */
    private static boolean isMark(String text) {
        if (text.length() != 1) {
            return false;
        }
        char c = text.charAt(0);
        return Character.getType(c) == Character.MODIFIER_SYMBOL || c == '~' || c == '\'' || c == '"'
                || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.SPACING_MODIFIER_LETTERS;
    }

    private static String text(List<InputMethodTextRun> runs) {
        StringBuilder text = new StringBuilder();
        runs.forEach(run -> text.append(run.getText()));
        return text.toString();
    }
}
