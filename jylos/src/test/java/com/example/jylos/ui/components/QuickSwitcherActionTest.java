package com.example.jylos.ui.components;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.tests.FxTestSupport;

import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * The switcher can offer one more thing at its foot (the board picker's "New board"): it runs
 * it and closes. Its search field can say what is listed.
 */
class QuickSwitcherActionTest {

    @Test
    void theFootActionRunsAndCloses() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        Stage[] shownStage = new Stage[1];
        List<String> seen = onFx(() -> {
            Stage owner = new Stage();
            QuickSwitcher switcher = new QuickSwitcher(owner);
            List<String> ran = new ArrayList<>();
            switcher.setPlaceholder("Buscar tablero…");
            switcher.setFooterAction("+ Nuevo tablero", () -> ran.add("new board"));
            switcher.setNotes(List.of(new Note("Tablero", "")));
            switcher.show();
            Stage shown = (Stage) Window.getWindows().stream()
                    .filter(w -> w instanceof Stage s && s.getOwner() == owner).findFirst().orElseThrow();
            Button action = (Button) shown.getScene().lookup(".overlay-footer-action");
            String placeholder = ((TextField) shown.getScene().lookup(".overlay-search-field")).getPromptText();
            List<String> out = new ArrayList<>(List.of(action.getText(), placeholder));
            action.fire();
            out.add(String.valueOf(ran));
            shownStage[0] = shown;
            return out;
        });
        long until = System.currentTimeMillis() + 5_000; // it closes with a short animation
        while (onFx(shownStage[0]::isShowing) && System.currentTimeMillis() < until) {
            Thread.sleep(25);
        }

        assertEquals(List.of("+ Nuevo tablero", "Buscar tablero…", "[new board]"), seen);
        assertEquals(false, onFx(shownStage[0]::isShowing), "the switcher closes");
    }
}
