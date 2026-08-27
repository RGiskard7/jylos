package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.ui.controller.EditorController;

import javafx.application.Platform;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;

/**
 * Reproduces a real shutdown-ordering bug: during app quit, a plugin's own
 * {@code shutdown()} can call {@code PluginContext.unregisterPreviewEnhancer}, which
 * queues {@code refreshPreview()} via {@code Platform.runLater}. That queued call always
 * lands on a *later* tick of the event loop — after {@code EditorController.teardown()}
 * has already stopped {@code previewRenderExecutor} — no matter what order teardown()
 * and plugin shutdown run in beforehand. Before the fix, this threw
 * {@code RejectedExecutionException} on the FX Application Thread for every plugin with
 * a preview enhancer, once per plugin, on every app quit.
 */
class EditorControllerPreviewShutdownTest {

    @Test
    void refreshPreviewAfterTeardownDoesNotThrowRejectedExecution() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch finished = new CountDownLatch(1);
        Throwable[] uncaught = new Throwable[1];
        Platform.runLater(() -> {
            Thread.currentThread().setUncaughtExceptionHandler((t, e) -> uncaught[0] = e);
            try {
                EditorController controller = new EditorController();
                setField(controller, "previewWebView", new WebView());
                VBox previewPane = new VBox();
                previewPane.setVisible(true);
                previewPane.setManaged(true);
                setField(controller, "previewPane", previewPane);
                setField(controller, "currentNote", new Note("Test", "Some content"));

                // Simulate app shutdown: the editor's resources are torn down...
                controller.teardown();
                // ...then a plugin's shutdown() asks for a preview refresh, same as
                // PluginContext.unregisterPreviewEnhancer's Platform.runLater callback
                // does in production, once teardown has already run.
                controller.refreshPreview(false);
            } catch (Throwable t) {
                uncaught[0] = t;
            } finally {
                finished.countDown();
            }
        });

        assertTrue(finished.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        assertNull(uncaught[0], "refreshPreview() after teardown() must not throw: " + uncaught[0]);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = EditorController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void assertTrue(boolean condition, String message) {
        org.junit.jupiter.api.Assertions.assertTrue(condition, message);
    }
}
