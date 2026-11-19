package com.example.jylos.tests;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static com.example.jylos.tests.FxTestSupport.runFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.event.EventBus;
import com.example.jylos.service.NoteService;
import com.example.jylos.ui.controller.EditorController;
import com.example.jylos.ui.web.JavaFxHtmlView;

import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * The reading view tells a link to a note of the vault from a link to a note to create, with
 * the vault's link index.
 */
class PreviewLinkExistenceTest {

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void aLinkToANoteOfTheVaultIsNotShownAsANoteToCreate(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.writeString(vault.resolve("Fuente.md"), "Ver [[Destino]] y [[Falta]]\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("Destino.md"), "x\n", StandardCharsets.UTF_8);
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        List<String> opened = new CopyOnWriteArrayList<>();
        AtomicReference<EditorController> controller = new AtomicReference<>();
        AtomicReference<JavaFxHtmlView> webView = new AtomicReference<>();
        AtomicReference<Stage> stage = new AtomicReference<>();
        runFx(() -> {
            EditorController editor = new EditorController();
            JavaFxHtmlView view = new JavaFxHtmlView();
            VBox pane = new VBox(view);
            set(editor, "previewWebView", view);
            set(editor, "previewPane", pane);
            set(editor, "noteService", notes);
            set(editor, "currentNote", notes.getNoteById("Fuente.md").orElseThrow());
            set(editor, "wikiLinkHandler", (EditorController.WikiLinkHandler) (title, newTab) -> opened.add(title));
            Stage window = new Stage();
            window.setScene(new Scene(pane, 600, 400));
            window.show();
            editor.refreshPreview(false);
            controller.set(editor);
            webView.set(view);
            stage.set(window);
        });
        try {
            String script = "Array.from(document.querySelectorAll('a.wikilink'))"
                    + ".map(a => a.textContent + ':' + a.className).join(',')";
            String classes = "";
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline && classes.isEmpty()) {
                Thread.sleep(100);
                classes = String.valueOf(onFx(() -> webView.get().evaluate(script)));
            }
            assertEquals("Destino:wikilink,Falta:wikilink wikilink-new", classes);

            // A click on a link reaches the app through the page's bridge.
            runFx(() -> webView.get().evaluate("document.querySelector('a.wikilink').click()"));
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < until && opened.isEmpty()) {
                Thread.sleep(50);
            }
            assertEquals(List.of("Destino"), opened, "the clicked link opens its note");
        } finally {
            runFx(() -> {
                controller.get().teardown();
                stage.get().close();
            });
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = EditorController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
