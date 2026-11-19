package com.example.jylos.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.util.AttachmentType;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

class FileViewerTest {

    private static boolean fxRuntimeAvailable;

    @BeforeAll
    static void initFxRuntime() {
        fxRuntimeAvailable = FxTestSupport.isFxRuntimeAvailable();
    }

    @Test
    void pdfViewerExposesPageNavigationAndThumbnailPanel(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(fxRuntimeAvailable);
        Path pdf = tempDir.resolve("sample.pdf");
        writePdf(pdf, 3);

        Region viewer = onFx(() -> {
            ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.ENGLISH);
            Region region = FileViewer.forAttachment(pdf, AttachmentType.PDF, bundle);
            Pane host = new Pane(region);
            host.resize(900, 700);
            new Scene(host, 900, 700);
            host.applyCss();
            host.layout();
            return region;
        });

        assertTrue(awaitFx(() -> pageTotal(viewer) != null && "/ 3".equals(pageTotal(viewer).getText()), 5),
                "PDF viewer should expose the total page count once loaded.");

        TextField pageField = onFx(() -> findByStyleClass(viewer, TextField.class, "viewer-page-field"));
        assertNotNull(pageField);
        assertEquals("1", onFx(pageField::getText));

        ScrollPane thumbnailScroll = onFx(() ->
                findByStyleClass(viewer, ScrollPane.class, "viewer-thumbnail-scroll"));
        assertNotNull(thumbnailScroll);
        assertTrue(!onFx(thumbnailScroll::isVisible), "Thumbnail rail should start hidden.");
        assertTrue(!onFx(thumbnailScroll::isManaged), "Hidden thumbnail rail should not take layout space.");

        ToggleButton thumbnailToggle = onFx(() -> findFirst(viewer, ToggleButton.class));
        assertNotNull(thumbnailToggle);
        onFx(() -> {
            thumbnailToggle.fire();
            return null;
        });

        assertTrue(awaitFx(() -> thumbnailScroll.isVisible() && thumbnailScroll.isManaged(), 2),
                "Thumbnail toggle should show the thumbnail rail.");
        VBox thumbnailList = onFx(() -> findByStyleClass(viewer, VBox.class, "viewer-thumbnail-list"));
        assertNotNull(thumbnailList);
        assertEquals(3, onFx(() -> thumbnailList.getChildren().size()),
                "Thumbnail rail should contain one entry per PDF page.");

        Button nextButton = onFx(() -> findButtonByText(viewer, "›"));
        assertNotNull(nextButton);
        onFx(() -> {
            nextButton.fire();
            return null;
        });
        assertEquals("2", onFx(pageField::getText),
                "Next-page button should update the current page field.");

        onFx(() -> {
            if (viewer.getParent() instanceof Pane pane) {
                pane.getChildren().remove(viewer);
            }
            return null;
        });
    }

    /** A WebP in the vault opens in the image viewer, though JavaFX's Image cannot decode WebP. */
    @Test
    void aWebpImageIsShown(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(fxRuntimeAvailable);
        Path webp = tempDir.resolve("foto.webp");
        // 40x30, lossless.
        Files.write(webp, Base64.getDecoder().decode(
                "UklGRioAAABXRUJQVlA4TB4AAAAvJ0AHAA8wHvfwvRD3/EcLhASEB/6v9UCO6H+UMAQ="));

        Region viewer = onFx(() -> {
            ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.ENGLISH);
            Region region = FileViewer.forAttachment(webp, AttachmentType.fromName(webp.toString()), bundle);
            new Scene(new Pane(region), 600, 400);
            return region;
        });

        assertTrue(awaitFx(() -> {
            ImageView view = findFirst(viewer, ImageView.class);
            return view != null && view.getImage() != null && view.getImage().getWidth() == 40;
        }, 10), "the picture is shown at its own width");
    }

    private static Label pageTotal(Node root) {
        return findLabels(root).stream()
                .filter(label -> label.getStyleClass().contains("viewer-info"))
                .filter(label -> label.getText() != null && label.getText().startsWith("/ "))
                .findFirst()
                .orElse(null);
    }

    private static List<Label> findLabels(Node root) {
        ArrayList<Label> labels = new ArrayList<>();
        collect(root, Label.class, labels);
        return labels;
    }

    private static Button findButtonByText(Node root, String text) {
        ArrayList<Button> buttons = new ArrayList<>();
        collect(root, Button.class, buttons);
        return buttons.stream()
                .filter(button -> text.equals(button.getText()))
                .findFirst()
                .orElse(null);
    }

    private static <T extends Node> T findByStyleClass(Node root, Class<T> type, String styleClass) {
        ArrayList<T> nodes = new ArrayList<>();
        collect(root, type, nodes);
        return nodes.stream()
                .filter(node -> node.getStyleClass().contains(styleClass))
                .findFirst()
                .orElse(null);
    }

    private static <T extends Node> T findFirst(Node root, Class<T> type) {
        ArrayList<T> nodes = new ArrayList<>();
        collect(root, type, nodes);
        return nodes.isEmpty() ? null : nodes.get(0);
    }

    private static <T extends Node> void collect(Node node, Class<T> type, List<T> found) {
        if (type.isInstance(node)) {
            found.add(type.cast(node));
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collect(child, type, found);
            }
        }
        if (node instanceof ScrollPane scrollPane && scrollPane.getContent() != null) {
            collect(scrollPane.getContent(), type, found);
        }
    }

    private static void writePdf(Path path, int pages) throws Exception {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage());
            }
            document.save(path.toFile());
        }
    }

    private static boolean awaitFx(BooleanSupplier condition, int timeoutSeconds) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            if (onFx(condition::getAsBoolean)) {
                return true;
            }
            Thread.sleep(25);
        }
        return onFx(condition::getAsBoolean);
    }
}
