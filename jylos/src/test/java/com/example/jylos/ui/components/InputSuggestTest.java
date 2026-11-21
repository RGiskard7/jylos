package com.example.jylos.ui.components;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Suggestions under a text field as it is typed in, as Obsidian's {@code AbstractInputSuggest}:
 * what matches what is typed (fuzzy, as the quick switcher), the best first and at most a
 * limit; arrows move, Enter or Tab picks, Escape closes; what is typed may also be kept as it
 * is.
 */
class InputSuggestTest {

    private static final List<String> PATHS = List.of("Diario/hoy.md", "Inicio.md", "Inicio/Panel.canvas",
            "Plantillas/diaria.md", "Proyectos/Panel de control.md");

    private static TextField fieldInWindow() {
        TextField field = new TextField();
        Stage stage = new Stage();
        stage.setScene(new Scene(new StackPane(field), 400, 200));
        stage.show();
        return field;
    }

    @Test
    void whatIsTypedNarrowsTheSuggestionsTheBestFirst() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<List<String>> seen = onFx(() -> {
            TextField field = fieldInWindow();
            InputSuggest suggest = new InputSuggest(field, InputSuggest.fuzzy(() -> PATHS));
            List<List<String>> out = new ArrayList<>();
            suggest.show();
            out.add(suggest.shown());
            field.setText("panel");
            out.add(suggest.shown());
            field.setText("dria");
            out.add(suggest.shown());
            field.setText("zzz");
            out.add(List.of(String.valueOf(suggest.isShowing())));
            return out;
        });

        assertEquals(List.of(PATHS,
                List.of("Inicio/Panel.canvas", "Proyectos/Panel de control.md"),
                List.of("Plantillas/diaria.md"),
                List.of("false")), seen);
    }

    /** The best match first: one that starts with what is typed beats one that has it further in. */
    @Test
    void theBestMatchComesFirstWhateverTheOrderOfTheItems() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            TextField field = fieldInWindow();
            InputSuggest suggest = new InputSuggest(field,
                    InputSuggest.fuzzy(() -> List.of("Proyectos/notas de inicio.md", "Inicio.md")));
            suggest.show();
            field.setText("inicio");
            return suggest.shown();
        });

        assertEquals(List.of("Inicio.md", "Proyectos/notas de inicio.md"), seen);
    }

    @Test
    void atMostTheLimitIsShown() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            InputSuggest suggest = new InputSuggest(fieldInWindow(), InputSuggest.fuzzy(() -> PATHS)).limit(2);
            suggest.show();
            return suggest.shown();
        });

        assertEquals(List.of("Diario/hoy.md", "Inicio.md"), seen);
    }

    @Test
    void arrowsMoveEnterPicksAndEscapeKeepsWhatIsTyped() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            TextField field = fieldInWindow();
            List<String> picked = new ArrayList<>();
            InputSuggest suggest = new InputSuggest(field, InputSuggest.fuzzy(() -> PATHS)).onSelect(picked::add);
            List<String> out = new ArrayList<>();
            suggest.show();
            field.setText("inicio");
            key(field, KeyCode.DOWN);
            key(field, KeyCode.ENTER);
            out.add(field.getText() + " | " + picked + " | showing " + suggest.isShowing());

            field.setText("Panel");
            suggest.show();
            boolean before = suggest.isShowing();
            // Escape closes the suggestions only, not the window they are in (the Preferences).
            List<String> reachedTheWindow = new ArrayList<>();
            field.getScene().addEventHandler(KeyEvent.KEY_PRESSED, e -> reachedTheWindow.add(e.getCode().toString()));
            key(field, KeyCode.ESCAPE);
            out.add(field.getText() + " | showing " + before + " then " + suggest.isShowing()
                    + " | window got " + reachedTheWindow);
            key(field, KeyCode.ESCAPE);
            out.add("closed: the next Escape reaches the window " + reachedTheWindow);
            return out;
        });

        assertEquals(List.of("Inicio/Panel.canvas | [Inicio/Panel.canvas] | showing false",
                "Panel | showing true then false | window got []",
                "closed: the next Escape reaches the window [ESCAPE]"), seen);
    }

    private static void key(TextField field, KeyCode code) {
        Event.fireEvent(field, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }
}
