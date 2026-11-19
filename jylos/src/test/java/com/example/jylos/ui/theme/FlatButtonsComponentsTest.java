package com.example.jylos.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.components.CanvasView;
import com.example.jylos.ui.components.FileViewer;
import com.example.jylos.ui.components.KanbanBoard;
import com.example.jylos.util.AttachmentType;
import com.example.jylos.util.CanvasModel;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;

/**
 * The flat-button look also covers the views that open in place of the editor: the graph,
 * the PDF and image viewers, the Kanban board and the Canvas. Every button there has no
 * border, and no background unless toggled on. Checked with each theme's real stylesheet.
 */
class FlatButtonsComponentsTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest(name = "{0} / {1}")
    @CsvSource({
            "modern-theme.css, graph", "dark-theme.css, graph",
            "modern-theme.css, pdf", "dark-theme.css, pdf",
            "modern-theme.css, image", "dark-theme.css, image",
            "modern-theme.css, kanban", "dark-theme.css, kanban",
            "modern-theme.css, canvas", "dark-theme.css, canvas",
            "modern-theme.css, kanban-lane", "dark-theme.css, kanban-lane",
            "modern-theme.css, tags-bar", "dark-theme.css, tags-bar" })
    void buttonsAreFlat(String theme, String view) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Path pdf = tempDir.resolve("doc.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            doc.save(pdf.toFile());
        }
        Path png = tempDir.resolve("img.png");
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", png.toFile());

        AtomicReference<Pane> shown = new AtomicReference<>();
        List<String> offenders = onFx(() -> {
            ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages",
                    Locale.forLanguageTag("es"));
            Parent content = switch (view) {
                case "graph" -> new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/GraphView.fxml"),
                        bundle).load();
                case "pdf" -> FileViewer.forAttachment(pdf, AttachmentType.PDF, bundle);
                case "image" -> FileViewer.forAttachment(png, AttachmentType.IMAGE, bundle);
                case "kanban" -> new KanbanBoard(null, "Tablero.md", "%% jylos-kanban %%\n## Por hacer\n- Escribir\n",
                        (t, newTab) -> { }, md -> { }, n -> { }, k -> k);
                // A board's lane needs a stored board; this is the same structure and classes
                // the board builds for "+ Add card" / "+ Add column".
                case "kanban-lane" -> {
                    Button addCard = new Button("+ Añadir tarjeta");
                    addCard.getStyleClass().add("kanban-add-card");
                    VBox lane = new VBox(addCard);
                    lane.getStyleClass().add("kanban-lane");
                    VBox board = new VBox(lane);
                    board.getStyleClass().add("kanban-board");
                    yield board;
                }
                // The editor's tags bar, as EditorController builds it.
                case "tags-bar" -> {
                    Button add = new Button("+ Añadir etiqueta");
                    add.getStyleClass().add("add-tag-button");
                    FlowPane flow = new FlowPane(add);
                    flow.getStyleClass().add("tags-flow");
                    HBox bar = new HBox(flow);
                    bar.getStyleClass().add("tags-bar");
                    yield bar;
                }
                default -> new CanvasView(CanvasModel.Document.parse("{}"), f -> { }, f -> null, f -> f, f -> f, List::of, List::of, s -> { },
                        () -> { }, k -> k);
            };
            Pane host = new Pane(content);
            shown.set(host);
            Scene scene = new Scene(host, 1100, 700);
            scene.getStylesheets().add(getClass().getResource("/com/example/jylos/ui/css/" + theme).toExternalForm());
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();
            host.applyCss();
            host.layout();

            List<String> out = new ArrayList<>();
            int checked = 0;
            for (Node node : all(host)) {
                if (!(node instanceof ButtonBase button) || !button.isVisible()) {
                    continue;
                }
                checked++;
                String name = button.getId() != null ? button.getId()
                        : (button.getText() != null && !button.getText().isBlank() ? button.getText()
                                : String.join(",", button.getStyleClass()));
                if (visibleBorder(button)) {
                    out.add(name + " has a border");
                }
                boolean on = button instanceof Toggle t && t.isSelected()
                        || button.getStyleClass().contains("toolbar-btn-active");
                if (!on && visibleBackground(button)) {
                    out.add(name + " has a background");
                }
            }
            stage.close();
            out.add(0, "checked " + (checked > 0));
            return out;
        });

        if ("pdf".equals(view)) {
            closeWhenLoaded(shown.get());
        }

        assertEquals(List.of("checked true"), offenders);
    }

    /**
     * The PDF viewer reads its file in the background (its page count, "/ 0", changes once it
     * has) and keeps the document open while it is shown; it closes it when taken out of its
     * window. Done here once it has loaded, so the
     * file is no longer open when the temporary folder is deleted (Windows refuses to delete an
     * open file).
     */
    private static void closeWhenLoaded(Pane host) throws Exception {
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (!onFx(() -> all(host).stream().anyMatch(n -> n instanceof Label l && l.getText() != null && l.getText().matches("/ [1-9]\\d*")))) {
            assertTrue(System.nanoTime() < deadline, "the PDF viewer loaded its document");
            Thread.sleep(50);
        }
        onFx(() -> {
            host.getChildren().clear();
            return null;
        });
    }

    private static List<Node> all(Parent root) {
        List<Node> out = new ArrayList<>();
        walk(root, out);
        return out;
    }

    private static void walk(Node node, List<Node> out) {
        out.add(node);
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                walk(child, out);
            }
        }
    }

    private static boolean visibleBorder(ButtonBase button) {
        if (button.getBorder() == null) {
            return false;
        }
        for (BorderStroke stroke : button.getBorder().getStrokes()) {
            boolean width = stroke.getWidths().getTop() > 0 || stroke.getWidths().getLeft() > 0;
            for (Paint paint : List.of(stroke.getTopStroke(), stroke.getLeftStroke())) {
                if (width && !(paint instanceof Color c && c.getOpacity() == 0)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean visibleBackground(ButtonBase button) {
        if (button.getBackground() == null) {
            return false;
        }
        for (BackgroundFill fill : button.getBackground().getFills()) {
            if (!(fill.getFill() instanceof Color c && c.getOpacity() == 0)) {
                return true;
            }
        }
        return false;
    }
}
