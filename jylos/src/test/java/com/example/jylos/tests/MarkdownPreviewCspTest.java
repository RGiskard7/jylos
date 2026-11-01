package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.plugin.PreviewEnhancer;
import com.example.jylos.util.MarkdownPreview;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/**
 * Notes may carry raw HTML (Obsidian parity), and notes can come from outside the user's
 * own hands: imported vaults, {@code git pull}, an MCP agent. Script embedded in a note
 * must therefore never run in the preview, while the scripts Jylos itself ships
 * (highlight.js, KaTeX, the link handler) and those injected by plugins keep working.
 * Checked in the real bundled WebKit, not by inspecting the HTML string.
 */
class MarkdownPreviewCspTest {

    private static final String HOSTILE_NOTE = """
            # Hostile note

            <script>window.__noteScriptRan = 'yes';</script>

            <img src="does-not-exist.png" onerror="window.__noteHandlerRan = 'yes'">

            ```java
            int answer = 42;
            ```
            """;

    @Test
    void scriptsFromTheNoteDoNotRunButAppAndPluginScriptsDo() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        PreviewEnhancer pluginEnhancer = new PreviewEnhancer() {
            @Override
            public String getBodyInjections() {
                return "<script>window.__pluginScriptRan = 'yes';</script>";
            }
        };
        String html = MarkdownPreview.buildPreviewHtml(HOSTILE_NOTE, false, List.of(pluginEnhancer));

        Map<String, String> results = evaluateInWebView(html,
                "noteScript", "String(window.__noteScriptRan)",
                "noteHandler", "String(window.__noteHandlerRan)",
                "pluginScript", "String(window.__pluginScriptRan)",
                "highlighted", "String(document.querySelector('pre code').classList.contains('hljs'))");

        assertEquals("undefined", results.get("noteScript"), "a <script> written in the note must not run");
        assertEquals("undefined", results.get("noteHandler"), "an inline event handler in the note must not run");
        assertEquals("yes", results.get("pluginScript"), "a script injected by a plugin must still run");
        assertEquals("true", results.get("highlighted"), "Jylos' own highlight.js must still run");
    }

    /**
     * A note that ends in an unterminated {@code <script ... a=} tag would otherwise swallow
     * the start of the next tag in the page — Jylos' own nonce-carrying script — and run
     * attacker-chosen code under that nonce.
     */
    @Test
    void anUnterminatedTagInTheNoteCannotStealTheNonceOfTheNextScript() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        String note = "Plain text first.\n\n<script src=\"data:text/javascript,window.__hijackRan='yes'\" a=";
        String html = MarkdownPreview.buildPreviewHtml(note, false, null);

        Map<String, String> results = evaluateInWebView(html,
                "hijack", "String(window.__hijackRan)",
                "linkHandlerStillRegistered", "String(document.body.textContent.indexOf('Plain text first.') >= 0)");

        assertEquals("undefined", results.get("hijack"), "a dangling tag in the note must not inherit the nonce");
        assertEquals("true", results.get("linkHandlerStillRegistered"), "the note itself must still render");
    }

    @Test
    void previewDeclaresAContentSecurityPolicyWithAFreshNoncePerRender() {
        String first = MarkdownPreview.buildPreviewHtml("text", false, null);
        String second = MarkdownPreview.buildPreviewHtml("text", false, null);
        String firstNonce = nonceOf(first);
        String secondNonce = nonceOf(second);
        assertTrue(firstNonce.length() >= 16, "nonce must be present and non-trivial: " + firstNonce);
        assertTrue(!firstNonce.equals(secondNonce), "each render must use a fresh nonce");
        assertTrue(first.contains("connect-src 'none'"), "the preview must not be able to open connections");
    }

    private static String nonceOf(String html) {
        int start = html.indexOf("'nonce-");
        if (start < 0) {
            return "";
        }
        int end = html.indexOf('\'', start + 7);
        return html.substring(start + 7, end);
    }

    /** Loads {@code html} in a real WebView and evaluates each named JS expression once loaded. */
    private static Map<String, String> evaluateInWebView(String html, String... namedExpressions) throws Exception {
        Map<String, String> results = new ConcurrentHashMap<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            Stage stage = new Stage();
            WebView view = new WebView();
            stage.setScene(new Scene(view, 800, 600));
            stage.show();
            view.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == Worker.State.SUCCEEDED || newState == Worker.State.FAILED) {
                    for (int i = 0; i + 1 < namedExpressions.length; i += 2) {
                        Object value = view.getEngine().executeScript(namedExpressions[i + 1]);
                        results.put(namedExpressions[i], String.valueOf(value));
                    }
                    stage.close();
                    done.countDown();
                }
            });
            view.getEngine().loadContent(html, "text/html");
        });
        assertTrue(done.await(30, TimeUnit.SECONDS), "preview page never finished loading");
        return results;
    }
}
