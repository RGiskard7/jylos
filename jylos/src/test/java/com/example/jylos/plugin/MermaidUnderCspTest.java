package com.example.jylos.plugin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.plugin.mermaid.MermaidPlugin;
import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.util.MarkdownPreview;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * The reading preview runs under a nonce-based Content-Security-Policy. Mermaid is the
 * heaviest script the preview ships (a bundled library plus an init script injected by
 * the built-in plugin), so this checks in the real WebKit that a diagram still renders to
 * an SVG with that policy in place.
 */
class MermaidUnderCspTest {

    @Test
    void mermaidDiagramStillRendersUnderThePreviewPolicy() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<PreviewEnhancer> enhancers = new ArrayList<>();
        PreviewEnhancerRegistry capture = new PreviewEnhancerRegistry() {
            @Override
            public void registerPreviewEnhancer(String pluginId, PreviewEnhancer enhancer) {
                enhancers.add(enhancer);
            }

            @Override
            public void unregisterPreviewEnhancer(String pluginId) {
                enhancers.clear();
            }
        };
        MermaidPlugin plugin = new MermaidPlugin();
        plugin.initialize(new PluginContext(plugin.getId(), null, null, null, null, null, null, null, capture,
                null, null, null, null, null, null));
        assertTrue(!enhancers.isEmpty(), "Mermaid must register its preview enhancer");

        String html = MarkdownPreview.buildPreviewHtml("```mermaid\ngraph TD\n    A --> B\n```\n", false, enhancers);

        AtomicReference<String> svgFound = new AtomicReference<>("false");
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            Stage stage = new Stage();
            WebView view = new WebView();
            stage.setScene(new Scene(view, 800, 600));
            stage.show();
            view.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState != Worker.State.SUCCEEDED) {
                    return;
                }
                // Mermaid renders asynchronously after load: poll for the SVG for a while.
                Timeline poll = new Timeline();
                int[] attempts = { 0 };
                poll.getKeyFrames().add(new KeyFrame(Duration.millis(200), e -> {
                    Object found = view.getEngine().executeScript(
                            "String(document.querySelector('.mermaid svg') !== null)");
                    attempts[0]++;
                    if ("true".equals(String.valueOf(found)) || attempts[0] >= 50) {
                        svgFound.set(String.valueOf(found));
                        poll.stop();
                        stage.close();
                        done.countDown();
                    }
                }));
                poll.setCycleCount(Timeline.INDEFINITE);
                poll.play();
            });
            view.getEngine().loadContent(html, "text/html");
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "preview page never finished loading");
        assertTrue("true".equals(svgFound.get()), "the Mermaid diagram should render to an SVG under the CSP");
    }
}
