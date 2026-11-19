package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * The status bar's transient message ("Note loaded: &lt;title&gt;") grows to fill the bar.
 * With a long note title it asked for the whole width and JavaFX shrank every other label
 * (storage, note count, words, characters, save state, editor mode) down to "...". The
 * message must take only the space left over and ellipsise itself instead.
 *
 * <p>Loads the real status bar from {@code MainView.fxml} (without its click handlers,
 * which need the whole MainController).</p>
 */
class StatusBarLongMessageTest {

    private static final String LONG_TITLE = "Nota cargada: (9) Prajwal Tomar en X \"3 The new standard Project "
            + "Rules If Cursor doesn't understand your product, it will hallucinate. That's why I generate 7 "
            + ".mdc docs before I write a single line of code\"";

    @Test
    void aLongStatusMessageNeverSqueezesTheOtherLabels() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        String statusBarFxml = extractStatusBar();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<String> truncated = new ArrayList<>();
        double[] messageWidth = new double[1];
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages",
                        Locale.forLanguageTag("es"));
                FXMLLoader loader = new FXMLLoader();
                loader.setResources(bundle);
                HBox bar = loader.load(new ByteArrayInputStream(statusBarFxml.getBytes(StandardCharsets.UTF_8)));
                Map<String, Object> ids = loader.getNamespace();

                ((Label) ids.get("storageLabel")).setText("Bóveda: Obsidian Vault");
                ((Label) ids.get("noteCountLabel")).setText("3.369 notas");
                for (String id : List.of("wordCountLabel", "charCountLabel", "statsSeparator",
                        "wordCharSeparator", "editorIndicatorsBox")) {
                    Node node = (Node) ids.get(id);
                    node.setVisible(true);
                    node.setManaged(true);
                }
                ((Label) ids.get("wordCountLabel")).setText("343 palabras");
                ((Label) ids.get("charCountLabel")).setText("3.097 caracteres");
                ((Label) ids.get("saveStateLabel")).setText("Sin guardar");
                ((Label) ids.get("editorModeLabel")).setText("Vista previa en vivo");
                Label status = (Label) ids.get("statusLabel");
                status.setText(LONG_TITLE);

                Scene scene = new Scene(bar, 1100, 40);
                scene.getStylesheets().add(StatusBarLongMessageTest.class.getClassLoader()
                        .getResource("com/example/jylos/ui/css/modern-theme.css").toExternalForm());
                Stage stage = new Stage();
                stage.setScene(scene);
                stage.show();
                bar.applyCss();
                bar.layout();

                for (String id : List.of("storageLabel", "noteCountLabel", "wordCountLabel", "charCountLabel",
                        "saveStateLabel", "editorModeLabel")) {
                    Label label = (Label) ids.get(id);
                    double needed = label.prefWidth(-1);
                    if (label.getWidth() + 0.5 < needed) {
                        truncated.add(id + " " + Math.round(label.getWidth()) + "<" + Math.round(needed));
                    }
                    double right = label.localToScene(label.getBoundsInLocal()).getMaxX();
                    if (right > scene.getWidth() + 0.5) {
                        truncated.add(id + " off-screen at x=" + Math.round(right));
                    }
                }
                messageWidth[0] = status.getWidth();
                stage.close();
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
        assertEquals(List.of(), truncated,
                "these status-bar labels were cut short or pushed out of the bar by the long message");
        // The message still gets the leftover space (it is not hidden or squeezed to nothing).
        assertTrue(messageWidth[0] > 100, "the message should keep the leftover width, was " + messageWidth[0]);
    }

    /** The {@code <bottom>} HBox of MainView.fxml as a standalone document, without event handlers. */
    private static String extractStatusBar() throws Exception {
        String fxml;
        try (InputStream in = StatusBarLongMessageTest.class.getClassLoader()
                .getResourceAsStream("com/example/jylos/ui/view/MainView.fxml")) {
            fxml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        StringBuilder imports = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        Matcher importMatcher = Pattern.compile("<\\?import [^?]+\\?>").matcher(fxml);
        while (importMatcher.find()) {
            imports.append(importMatcher.group()).append('\n');
        }
        Matcher bar = Pattern.compile("(?s)<bottom>\\s*(<HBox fx:id=\"statusBar\".*?)</bottom>").matcher(fxml);
        assertTrue(bar.find(), "status bar not found in MainView.fxml");
        String body = bar.group(1)
                .replaceFirst("<HBox fx:id=\"statusBar\"",
                        "<HBox xmlns=\"http://javafx.com/javafx/21\" xmlns:fx=\"http://javafx.com/fxml/1\" fx:id=\"statusBar\"")
                .replaceAll("\\s+onMouseClicked=\"#\\w+\"", "");
        return imports + body;
    }
}
