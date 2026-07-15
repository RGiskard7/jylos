package com.example.jylos.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.util.AttachmentType;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

class FileViewerTest {

    private static boolean fxRuntimeAvailable;

    @BeforeAll
    static void initFxRuntime() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
            fxRuntimeAvailable = latch.await(2, TimeUnit.SECONDS);
        } catch (IllegalStateException e) {
            fxRuntimeAvailable = true;
        } catch (Exception e) {
            fxRuntimeAvailable = false;
        }
    }

    @Test
    void pdfViewerExposesPageNavigationAndThumbnailPanel(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(fxRuntimeAvailable);
        Path pdf = tempDir.resolve("sample.pdf");
        writePdf(pdf, 3);

        Region viewer = runOnFx(() -> {
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

        TextField pageField = runOnFx(() -> findByStyleClass(viewer, TextField.class, "viewer-page-field"));
        assertNotNull(pageField);
        assertEquals("1", runOnFx(pageField::getText));

        ScrollPane thumbnailScroll = runOnFx(() ->
                findByStyleClass(viewer, ScrollPane.class, "viewer-thumbnail-scroll"));
        assertNotNull(thumbnailScroll);
        assertTrue(!runOnFx(thumbnailScroll::isVisible), "Thumbnail rail should start hidden.");
        assertTrue(!runOnFx(thumbnailScroll::isManaged), "Hidden thumbnail rail should not take layout space.");

        ToggleButton thumbnailToggle = runOnFx(() -> findFirst(viewer, ToggleButton.class));
        assertNotNull(thumbnailToggle);
        runOnFx(() -> {
            thumbnailToggle.fire();
            return null;
        });

        assertTrue(awaitFx(() -> thumbnailScroll.isVisible() && thumbnailScroll.isManaged(), 2),
                "Thumbnail toggle should show the thumbnail rail.");
        VBox thumbnailList = runOnFx(() -> findByStyleClass(viewer, VBox.class, "viewer-thumbnail-list"));
        assertNotNull(thumbnailList);
        assertEquals(3, runOnFx(() -> thumbnailList.getChildren().size()),
                "Thumbnail rail should contain one entry per PDF page.");

        Button nextButton = runOnFx(() -> findButtonByText(viewer, "›"));
        assertNotNull(nextButton);
        runOnFx(() -> {
            nextButton.fire();
            return null;
        });
        assertEquals("2", runOnFx(pageField::getText),
                "Next-page button should update the current page field.");

        runOnFx(() -> {
            if (viewer.getParent() instanceof Pane pane) {
                pane.getChildren().remove(viewer);
            }
            return null;
        });
    }

    private static Label pageTotal(Node root) {
        return findLabels(root).stream()
                .filter(label -> label.getStyleClass().contains("viewer-info"))
                .filter(label -> label.getText() != null && label.getText().startsWith("/ "))
                .findFirst()
                .orElse(null);
    }

    private static java.util.List<Label> findLabels(Node root) {
        java.util.ArrayList<Label> labels = new java.util.ArrayList<>();
        collect(root, Label.class, labels);
        return labels;
    }

    private static Button findButtonByText(Node root, String text) {
        java.util.ArrayList<Button> buttons = new java.util.ArrayList<>();
        collect(root, Button.class, buttons);
        return buttons.stream()
                .filter(button -> text.equals(button.getText()))
                .findFirst()
                .orElse(null);
    }

    private static <T extends Node> T findByStyleClass(Node root, Class<T> type, String styleClass) {
        java.util.ArrayList<T> nodes = new java.util.ArrayList<>();
        collect(root, type, nodes);
        return nodes.stream()
                .filter(node -> node.getStyleClass().contains(styleClass))
                .findFirst()
                .orElse(null);
    }

    private static <T extends Node> T findFirst(Node root, Class<T> type) {
        java.util.ArrayList<T> nodes = new java.util.ArrayList<>();
        collect(root, type, nodes);
        return nodes.isEmpty() ? null : nodes.get(0);
    }

    private static <T extends Node> void collect(Node node, Class<T> type, java.util.List<T> found) {
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
            if (runOnFx(condition::getAsBoolean)) {
                return true;
            }
            Thread.sleep(25);
        }
        return runOnFx(condition::getAsBoolean);
    }

    private static <T> T runOnFx(Supplier<T> supplier) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return supplier.get();
        }
        CountDownLatch latch = new CountDownLatch(1);
        Object[] value = new Object[1];
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try {
                value[0] = supplier.get();
            } catch (Throwable t) {
                failure[0] = t;
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for JavaFX thread.");
        if (failure[0] != null) {
            throw new AssertionError(failure[0]);
        }
        return (T) value[0];
    }
}
