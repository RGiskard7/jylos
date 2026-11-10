package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.controller.StatusBarSupport.EditorMode;

import javafx.application.Platform;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;

/**
 * Two users could not tell that Jylos autosaves or that the editor has a Live Preview
 * mode: both exist, but the only hints were a coloured dot and a menu entry. The status
 * bar now names the save state and the editor mode, and the mode label switches modes.
 */
class StatusBarEditorIndicatorsTest {

    private static final Map<String, String> TEXTS = Map.of(
            "status.save.saved", "Saved",
            "status.save.unsaved", "Unsaved",
            "status.save.autosave_on", "Autosave after {0} s",
            "status.save.autosave_off", "Autosave off",
            "status.mode.live_preview", "Live",
            "status.mode.source", "Source",
            "status.mode.reading", "Reading",
            "status.mode.split", "Split",
            "status.mode.tooltip", "Change mode");
    private static final Function<String, String> I18N = key -> TEXTS.getOrDefault(key, "?" + key);

    @Test
    void theEditorModeFollowsTheViewModeAndTheLivePreviewSwitch() {
        assertEquals(EditorMode.LIVE_PREVIEW, EditorMode.of(UiLayout.ViewMode.EDITOR_ONLY, true));
        assertEquals(EditorMode.SOURCE, EditorMode.of(UiLayout.ViewMode.EDITOR_ONLY, false));
        // Reading and split show the rendered note whatever the editing preference is.
        assertEquals(EditorMode.READING, EditorMode.of(UiLayout.ViewMode.PREVIEW_ONLY, true));
        assertEquals(EditorMode.READING, EditorMode.of(UiLayout.ViewMode.PREVIEW_ONLY, false));
        assertEquals(EditorMode.SPLIT, EditorMode.of(UiLayout.ViewMode.SPLIT, true));
        assertEquals(EditorMode.SPLIT, EditorMode.of(UiLayout.ViewMode.SPLIT, false));
    }

    @Test
    void theLabelsNameTheSaveStateAndModeAndHideWithoutANote() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFxThread(() -> {
            Fixture f = new Fixture(mode -> { });
            List<String> out = new ArrayList<>();

            f.support.updateEditorIndicators(true, true, EditorMode.LIVE_PREVIEW, true, 3000);
            out.add(f.save.getText() + "|" + f.save.getTooltip().getText() + "|" + f.mode.getText()
                    + "|" + f.save.isVisible() + f.mode.isVisible() + f.separator.isVisible());

            f.support.updateEditorIndicators(true, false, EditorMode.SOURCE, false, 3000);
            out.add(f.save.getText() + "|" + f.save.getTooltip().getText() + "|" + f.mode.getText());

            f.support.updateEditorIndicators(true, false, EditorMode.READING, true, 2000);
            out.add(f.mode.getText());
            f.support.updateEditorIndicators(true, false, EditorMode.SPLIT, true, 2000);
            out.add(f.mode.getText());

            f.support.updateEditorIndicators(false, false, EditorMode.LIVE_PREVIEW, true, 2000);
            out.add("" + f.save.isVisible() + f.save.isManaged() + f.mode.isVisible() + f.mode.isManaged()
                    + f.separator.isVisible());
            return out;
        });

        assertEquals(List.of(
                "Unsaved|Autosave after 3 s|Live|truetruetrue",
                "Saved|Autosave off|Source",
                "Reading",
                "Split",
                "falsefalsefalsefalsefalse"), seen);
    }

    @Test
    void theModeMenuOffersEveryModeTicksTheCurrentOneAndReportsTheChoice() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<EditorMode> chosen = new ArrayList<>();
        List<String> seen = onFxThread(() -> {
            Fixture f = new Fixture(chosen::add);
            f.support.updateEditorIndicators(true, false, EditorMode.SOURCE, true, 2000);

            ContextMenu menu = f.support.editorModeMenu();
            List<String> out = new ArrayList<>();
            for (MenuItem item : menu.getItems()) {
                out.add(item.getText() + "=" + ((CheckMenuItem) item).isSelected());
            }
            menu.getItems().get(2).fire();
            return out;
        });

        assertEquals(List.of("Live=false", "Source=true", "Reading=false", "Split=false"), seen);
        assertEquals(List.of(EditorMode.READING), chosen);
    }

    @Test
    void theSaveStateIsNotShownBeforeTheIndicatorsAreUpdated() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFxThread(() -> {
            Fixture f = new Fixture(mode -> { });
            return List.of("" + f.save.isVisible() + f.mode.isVisible());
        });

        assertEquals(List.of("falsefalse"), seen, "both labels must start hidden until a note is open");
    }

    private static final class Fixture {
        final Label save = new Label();
        final Label mode = new Label();
        final Separator separator = new Separator();
        final StatusBarSupport support = new StatusBarSupport();

        Fixture(java.util.function.Consumer<EditorMode> onModeChosen) {
            support.wire(null, null, null, null, null, null, I18N);
            support.wireEditorIndicators(save, mode, separator, onModeChosen);
        }
    }

    private static <T> T onFxThread(java.util.concurrent.Callable<T> work) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(work.call());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return result.get();
    }
}
