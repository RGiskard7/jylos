package com.example.jylos.ui.components;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.web.JavaFxHtmlView;
import com.example.jylos.util.MarkdownPreview;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Following a note's link in a new tab, as everywhere else in the app. In the reading view a
 * plain click opens it in the active tab, and Cmd/Ctrl+click or the middle button in a new
 * one. In the editor a plain click is for writing and Cmd/Ctrl+click follows the link in the
 * active tab, so a new tab is the middle button or Cmd/Ctrl+Shift+click.
 */
class LinkNewTabClicksTest {

    @AfterEach
    void closeWindows() throws Exception {
        if (FxTestSupport.isFxRuntimeAvailable()) {
            onFx(() -> {
                new ArrayList<>(Window.getWindows()).forEach(Window::hide);
                return null;
            });
        }
    }

    /** The reading view's {@code window.javaApp}, reduced to following links. */
    public static final class Bridge {
        final List<String> opened = new CopyOnWriteArrayList<>();

        public void openNote(String title) {
            opened.add(title + " in the active tab");
        }

        public void openNoteInNewTab(String title) {
            opened.add(title + " in a new tab");
        }
    }

    @Test
    void inTheReadingViewCmdCtrlOrTheMiddleButtonOpenALinkInANewTab() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Bridge bridge = new Bridge();
        CountDownLatch loaded = new CountDownLatch(1);
        JavaFxHtmlView page = onFx(() -> {
            JavaFxHtmlView view = new JavaFxHtmlView();
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(view), 600, 400));
            stage.show();
            view.exposeBridge("javaApp", bridge);
            view.whenLoaded(loaded::countDown);
            view.loadHtml(MarkdownPreview.buildPreviewHtml("Ver [[Beta]] y [[Gamma#Uso]]\n", false, List.of()));
            return view;
        });
        assertTrue(loaded.await(30, TimeUnit.SECONDS), "the page never loaded");

        onFx(() -> {
            String links = "var a = document.querySelectorAll('a[href^=\"jylos://open-note/\"]');";
            page.run(links + "a[0].dispatchEvent(new MouseEvent('click', {bubbles: true, cancelable: true, button: 0}));");
            page.run(links + "a[0].dispatchEvent(new MouseEvent('click', {bubbles: true, cancelable: true, metaKey: true}));");
            page.run(links + "a[1].dispatchEvent(new MouseEvent('click', {bubbles: true, cancelable: true, ctrlKey: true}));");
            page.run(links + "a[1].dispatchEvent(new MouseEvent('mousedown', {bubbles: true, cancelable: true, button: 1}));"
                    + "a[1].dispatchEvent(new MouseEvent('mouseup', {bubbles: true, cancelable: true, button: 1}));"
                    + "a[1].dispatchEvent(new MouseEvent('click', {bubbles: true, cancelable: true, button: 1}));");
            return null;
        });

        assertEquals(List.of("Beta in the active tab", "Beta in a new tab", "Gamma#Uso in a new tab",
                "Gamma#Uso in a new tab"), bridge.opened);
    }

    @Test
    void inTheEditorTheMiddleButtonOrCmdCtrlShiftOpenALinkInANewTab() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> opened = new CopyOnWriteArrayList<>();
        CountDownLatch ready = new CountDownLatch(1);
        MarkdownEditorView editor = onFx(() -> {
            MarkdownEditorView view = new MarkdownEditorView();
            view.setEditable(true);
            view.setLinkHandlers((title, newTab) -> opened.add(title + (newTab ? " in a new tab" : " in the active tab")),
                    url -> { });
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(view), 800, 600));
            stage.show();
            view.whenReady(ready::countDown);
            return view;
        });
        assertTrue(ready.await(30, TimeUnit.SECONDS), "the editor never loaded");

        for (boolean livePreview : new boolean[] {false, true}) {
            onFx(() -> {
                editor.setLivePreviewEnabled(livePreview);
                editor.setText("Primera línea\n\nVer [[Beta]] fin");
                return null;
            });
            Thread.sleep(400);
            onFx(() -> {
                // The middle of "Beta" in the source, or of the drawn link in Live Preview.
                String at = "var link = document.querySelector('.cm-live-wikilink');"
                        + "var r; if (link) { r = link.getBoundingClientRect(); } else {"
                        + " var walk = document.createTreeWalker(document.querySelector('.cm-content'), NodeFilter.SHOW_TEXT);"
                        + " var text = walk.nextNode(); while (text && text.textContent.indexOf('Beta') < 0) { text = walk.nextNode(); }"
                        + " var range = document.createRange(); var i = text.textContent.indexOf('Beta');"
                        + " range.setStart(text, i); range.setEnd(text, i + 4); r = range.getBoundingClientRect(); }"
                        + "var x = r.left + r.width / 2, y = r.top + r.height / 2;"
                        + "var target = document.elementFromPoint(x, y);"
                        + "function fire(type, init) { init.bubbles = true; init.cancelable = true; init.clientX = x;"
                        + " init.clientY = y; target.dispatchEvent(new MouseEvent(type, init)); }";
                editor.page().run(at + "fire('mousedown', {button: 0, metaKey: true}); fire('mouseup', {button: 0, metaKey: true});"
                        + "fire('click', {button: 0, metaKey: true});");
                editor.page().run(at + "fire('mousedown', {button: 0, metaKey: true, shiftKey: true});"
                        + "fire('mouseup', {button: 0, metaKey: true, shiftKey: true});"
                        + "fire('click', {button: 0, metaKey: true, shiftKey: true});");
                editor.page().run(at + "fire('mousedown', {button: 1}); fire('mouseup', {button: 1});");
                return null;
            });
            Thread.sleep(300);
            onFx(() -> null); // the opens run later on the FX thread
            assertEquals(List.of("Beta in the active tab", "Beta in a new tab", "Beta in a new tab"), opened,
                    livePreview ? "Live Preview" : "source");
            opened.clear();
        }
    }
}
