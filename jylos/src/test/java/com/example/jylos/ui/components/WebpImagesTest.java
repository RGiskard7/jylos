package com.example.jylos.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.util.MarkdownPreview;

import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

/**
 * A WebP image is shown in the editor and in the reading view, as in Obsidian, although
 * JavaFX's WebKit cannot decode WebP: the image that fails is converted to PNG and drawn.
 *
 * <p>Public, as is {@link Bridge}: JavaScript in a WebView only reaches methods of a class
 * that is public all the way out.</p>
 */
public class WebpImagesTest {

    /** 40×30 lossy WebP: red on the left half, blue on the right. */
    private static final byte[] WEBP = Base64.getDecoder().decode(
            "UklGRnQAAABXRUJQVlA4IGgAAADQBACdASooAB4APm0skkWkIqGYBABABsSgDBCoB8oFKA/ACu/9wAAveYM/tUZ2AAD+7ylH/sWctgSg"
                    + "//c4H2Gfi1/h09py7pqmxMf7NbCx6OP/t67lDyvN0ztBqTgw/Gz1doJZsAAAAA==");

    @Test
    void theEditorShowsAWebpImage(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.write(vault.resolve("foto.webp"), WEBP);

        MarkdownEditorView editor = onFx(() -> {
            MarkdownEditorView view = new MarkdownEditorView();
            view.setImageSourceResolver(source -> MarkdownPreview.resolveImageSource(source, vault));
            show(view);
            view.setText("Texto\n\n![](foto.webp)\n\nfin");
            return view;
        });

        WebEngine engine = editor.getWebView().getEngine();
        assertEquals(40, widthOnceDrawn(engine, ".cm-live-image img"), "the picture is drawn at its own width");
        assertEquals(Boolean.TRUE, onFx(() -> engine.executeScript(
                "document.querySelector('.cm-live-image-fallback').hidden")), "not its file name");
    }

    @Test
    void theReadingViewShowsAWebpImage(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.write(vault.resolve("foto.webp"), WEBP);
        String html = MarkdownPreview.buildPreviewHtml("Texto\n\n![](foto.webp)\n", false, List.of(), vault);

        WebView webView = onFx(() -> {
            WebView view = new WebView();
            WebEngine engine = view.getEngine();
            // What the reading view does once its page has loaded.
            engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
                if (state == Worker.State.SUCCEEDED) {
                    ((JSObject) engine.executeScript("window")).setMember("javaApp", new Bridge(engine));
                    WebViewImages.bridgeReady(engine);
                }
            });
            show(view);
            engine.loadContent(html);
            return view;
        });

        assertEquals(40, widthOnceDrawn(webView.getEngine(), "img"), "the picture is drawn at its own width");
    }

    /** The reading view's {@code window.javaApp}, reduced to what images use. */
    public static final class Bridge {
        private final WebEngine engine;

        Bridge(WebEngine engine) {
            this.engine = engine;
        }

        public void convertImage(String id, String source) {
            WebViewImages.convert(engine, WebViewImages.READING_VIEW, id, source);
        }
    }

    private static void show(javafx.scene.Parent content) {
        Stage stage = new Stage();
        stage.setScene(new Scene(new StackPane(content), 800, 600));
        stage.show();
    }

    /** The natural width of the image, once it is above zero; zero if it never is. */
    private static int widthOnceDrawn(WebEngine engine, String selector) throws Exception {
        String script = "(function(){var i=document.querySelector('" + selector + "');return i?i.naturalWidth:-1;})()";
        int width = 0;
        for (int attempt = 0; attempt < 75 && width <= 0; attempt++) {
            Thread.sleep(200);
            width = ((Number) onFx(() -> engine.executeScript(script))).intValue();
        }
        assertTrue(width >= 0, "the image is on the page");
        return width;
    }
}
