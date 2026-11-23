package com.example.jylos.ui;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.input.InputMethodHighlight;
import javafx.scene.input.InputMethodTextRun;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * A dead key pressed again and again types one mark per press in every text field of every
 * window, dialogs included, not only in the editor. The events are the ones JavaFX gave a
 * TextField on macOS with a real keyboard (Spanish layout, the grave accent five times): every
 * other press arrives as a new composition with nothing committed, and the field replaced the
 * pending mark (three marks for five presses).
 */
class DeadKeyRepeatsTest {

    private static InputMethodEvent im(String composed, String committed) {
        List<InputMethodTextRun> runs = composed.isEmpty() ? List.of()
                : List.of(new InputMethodTextRun(composed, InputMethodHighlight.UNSELECTED_RAW));
        return new InputMethodEvent(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, runs, committed, composed.length());
    }

    /** Five presses of the grave accent, as recorded; the fifth mark is still pending (shown). */
    private static final List<InputMethodEvent> FIVE_PRESSES = List.of(
            im("`", ""),
            im("", "`"), im("`", ""),
            im("`", ""),
            im("", "`"), im("`", ""),
            im("`", ""));

    private static String typeInto(TextInputControl control, List<InputMethodEvent> events) throws Exception {
        return onFx(() -> {
            Stage stage = new Stage(); // a window opened after the app started, as a dialog is
            stage.setScene(new Scene(new VBox(control), 300, 200));
            stage.show();
            control.requestFocus();
            events.forEach(event -> Event.fireEvent(control, event.copyFor(control, control)));
            String typed = control.getText();
            stage.close();
            return typed;
        });
    }

    @Test
    void aDeadKeyPressedAgainAndAgainTypesOneMarkPerPressInAnyTextField() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        onFx(() -> {
            DeadKeyRepeats.installEverywhere();
            return null;
        });

        assertEquals(List.of("`````", "`````"),
                List.of(typeInto(new TextField(), FIVE_PRESSES), typeInto(new TextArea(), FIVE_PRESSES)));
    }

    /** Input that an input method composes and recomposes (Japanese) is left as it is. */
    @Test
    void aCompositionOfOtherTextIsLeftAsItIs() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        onFx(() -> {
            DeadKeyRepeats.installEverywhere();
            return null;
        });

        assertEquals("缶", typeInto(new TextField(),
                List.of(im("k", ""), im("か", ""), im("かん", ""), im("かん", ""), im("", "缶"))));
    }
}
