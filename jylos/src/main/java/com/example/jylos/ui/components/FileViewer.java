package com.example.jylos.ui.components;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.util.AttachmentType;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Builds native viewers for vault attachments:
 * <ul>
 *   <li><b>Images</b> — JavaFX {@link Image}/{@link ImageView} with fit / zoom controls.</li>
 *   <li><b>PDF</b> — pages rasterised with Apache PDFBox (off the FX thread) and shown
 *       as a vertical, scrollable list of pages with zoom controls.</li>
 * </ul>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.6.0
 */
public final class FileViewer {

    private static final Logger logger = LoggerConfig.getLogger(FileViewer.class);

    /** DPI used to rasterise PDF pages — a balance between sharpness and memory. */
    private static final float PDF_RENDER_DPI = 120f;
    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 5.0;
    private static final double ZOOM_STEP = 1.2;

    private FileViewer() {
    }

    /** Builds the appropriate viewer for {@code path} given its {@code type}. */
    public static Region forAttachment(Path path, AttachmentType type, java.util.ResourceBundle bundle) {
        if (type == AttachmentType.PDF) {
            return pdfViewer(path, bundle);
        }
        return imageViewer(path);
    }

    private static String tr(java.util.ResourceBundle bundle, String key, String fallback) {
        try {
            return bundle != null ? bundle.getString(key) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    // ── Image viewer ──────────────────────────────────────────────────────────

    private static Region imageViewer(Path path) {
        ImageView view = new ImageView();
        view.setPreserveRatio(true);
        view.setSmooth(true);

        StackPane holder = new StackPane(view);
        holder.getStyleClass().add("viewer-canvas");
        ScrollPane scroll = new ScrollPane(holder);
        scroll.setPannable(true);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.getStyleClass().add("viewer-scroll");

        Image image = new Image(path.toUri().toString(), true); // background load
        view.setImage(image);

        // Default: fit the image to the viewport width.
        Runnable fit = () -> view.setFitWidth(Math.max(1, scroll.getViewportBounds().getWidth() - 24));
        double[] zoom = { 0 }; // 0 = "fit", otherwise an explicit fitWidth in px
        Runnable apply = () -> {
            if (zoom[0] <= 0) {
                fit.run();
            } else {
                view.setFitWidth(zoom[0]);
            }
        };
        scroll.viewportBoundsProperty().addListener((o, a, b) -> {
            if (zoom[0] <= 0) {
                fit.run();
            }
        });
        image.progressProperty().addListener((o, a, p) -> {
            if (p.doubleValue() >= 1.0) {
                Platform.runLater(apply);
            }
        });

        HBox toolbar = viewerToolbar();
        Button zoomOut = toolBtn("−");
        Button zoomIn = toolBtn("+");
        Button fitBtn = toolBtn("Fit");
        Button fullBtn = toolBtn("100%");
        zoomOut.setOnAction(e -> {
            double base = zoom[0] > 0 ? zoom[0] : view.getBoundsInLocal().getWidth();
            zoom[0] = Math.max(image.getWidth() * MIN_ZOOM, base / ZOOM_STEP);
            apply.run();
        });
        zoomIn.setOnAction(e -> {
            double base = zoom[0] > 0 ? zoom[0] : view.getBoundsInLocal().getWidth();
            zoom[0] = Math.min(image.getWidth() * MAX_ZOOM, base * ZOOM_STEP);
            apply.run();
        });
        fitBtn.setOnAction(e -> { zoom[0] = 0; apply.run(); });
        fullBtn.setOnAction(e -> { zoom[0] = image.getWidth(); apply.run(); });
        toolbar.getChildren().addAll(zoomOut, zoomIn, fitBtn, fullBtn);

        BorderPane root = new BorderPane(scroll);
        root.setTop(toolbar);
        root.getStyleClass().add("file-viewer");
        return root;
    }

    // ── PDF viewer ──────────────────────────────────────────────────────────

    private static Region pdfViewer(Path path, java.util.ResourceBundle bundle) {
        VBox pages = new VBox(12);
        pages.setAlignment(Pos.TOP_CENTER);
        pages.getStyleClass().add("viewer-canvas");
        pages.setFillWidth(false);

        ScrollPane scroll = new ScrollPane(pages);
        scroll.setPannable(true);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("viewer-scroll");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(48, 48);
        StackPane loading = new StackPane(spinner);
        loading.getStyleClass().add("viewer-canvas");

        HBox toolbar = viewerToolbar();
        Label pageInfo = new Label();
        pageInfo.getStyleClass().add("viewer-info");
        Button zoomOut = toolBtn("−");
        Button zoomIn = toolBtn("+");
        Button fitBtn = toolBtn("Fit");
        toolbar.getChildren().addAll(pageInfo, zoomOut, zoomIn, fitBtn);

        BorderPane root = new BorderPane(loading);
        root.setTop(toolbar);
        root.getStyleClass().add("file-viewer");

        // Render the PDF pages off the FX thread; show a spinner meanwhile.
        Task<List<Image>> task = new Task<>() {
            @Override
            protected List<Image> call() throws Exception {
                List<Image> rendered = new ArrayList<>();
                try (PDDocument document = PDDocument.load(path.toFile())) {
                    PDFRenderer renderer = new PDFRenderer(document);
                    int count = document.getNumberOfPages();
                    for (int i = 0; i < count; i++) {
                        BufferedImage page = renderer.renderImageWithDPI(i, PDF_RENDER_DPI, ImageType.RGB);
                        rendered.add(toFxImage(page));
                        updateMessage((i + 1) + " / " + count);
                    }
                }
                return rendered;
            }
        };
        task.setOnSucceeded(e -> {
            List<Image> rendered = task.getValue();
            double[] zoom = { 0 }; // 0 = fit width
            List<ImageView> views = new ArrayList<>();
            for (Image img : rendered) {
                ImageView iv = new ImageView(img);
                iv.setPreserveRatio(true);
                iv.setSmooth(true);
                iv.getStyleClass().add("viewer-pdf-page");
                views.add(iv);
                pages.getChildren().add(iv);
            }
            Runnable apply = () -> {
                double w = zoom[0] > 0 ? zoom[0] : Math.max(1, scroll.getViewportBounds().getWidth() - 32);
                for (ImageView iv : views) {
                    iv.setFitWidth(w);
                }
            };
            scroll.viewportBoundsProperty().addListener((o, a, b) -> {
                if (zoom[0] <= 0) {
                    apply.run();
                }
            });
            zoomOut.setOnAction(ev -> {
                double base = zoom[0] > 0 ? zoom[0] : scroll.getViewportBounds().getWidth() - 32;
                zoom[0] = Math.max(120, base / ZOOM_STEP);
                apply.run();
            });
            zoomIn.setOnAction(ev -> {
                double base = zoom[0] > 0 ? zoom[0] : scroll.getViewportBounds().getWidth() - 32;
                zoom[0] = base * ZOOM_STEP;
                apply.run();
            });
            fitBtn.setOnAction(ev -> { zoom[0] = 0; apply.run(); });
            pageInfo.textProperty().unbind();
            String pagesWord = tr(bundle, rendered.size() == 1 ? "viewer.page" : "viewer.pages",
                    rendered.size() == 1 ? "page" : "pages");
            pageInfo.setText(rendered.size() + " " + pagesWord);
            root.setCenter(scroll);
            Platform.runLater(apply);
        });
        task.setOnFailed(e -> {
            logger.log(Level.WARNING, "Failed to render PDF: " + path, task.getException());
            Label error = new Label(tr(bundle, "viewer.pdf_error", "Could not render PDF")
                    + (task.getException() != null ? ": " + task.getException().getMessage() : ""));
            error.getStyleClass().add("viewer-info");
            root.setCenter(new StackPane(error));
        });
        pageInfo.textProperty().bind(task.messageProperty());
        Thread thread = new Thread(task, "pdf-render");
        thread.setDaemon(true);
        thread.start();
        return root;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Image toFxImage(BufferedImage image) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return new Image(new ByteArrayInputStream(baos.toByteArray()));
        }
    }

    private static HBox viewerToolbar() {
        HBox bar = new HBox(6);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("viewer-toolbar");
        return bar;
    }

    private static Button toolBtn(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("viewer-btn");
        return button;
    }
}
