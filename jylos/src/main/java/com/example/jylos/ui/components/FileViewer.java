package com.example.jylos.ui.components;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.util.AttachmentType;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
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
import javafx.scene.shape.Rectangle;

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
    private static final int PDF_POSITION_CACHE_LIMIT = 64;
    private static final Map<String, Double> PDF_SCROLL_POSITIONS =
            new LinkedHashMap<>(PDF_POSITION_CACHE_LIMIT, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Double> eldest) {
                    return size() > PDF_POSITION_CACHE_LIMIT;
                }
            };

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
        String positionKey = path.toAbsolutePath().normalize().toString();
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

        Task<PdfSession> task = new Task<>() {
            @Override
            protected PdfSession call() throws Exception {
                PDDocument document = PDDocument.load(path.toFile());
                return new PdfSession(document);
            }
        };
        task.setOnSucceeded(e -> {
            PdfSession session = task.getValue();
            if (root.getScene() == null && root.getParent() == null) {
                session.close();
                return;
            }
            double[] zoom = { 0 }; // 0 = fit width
            List<PdfPageSlot> slots = session.createSlots();
            pages.getChildren().setAll(slots);

            Runnable apply = () -> {
                double w = zoom[0] > 0 ? zoom[0] : Math.max(1, scroll.getViewportBounds().getWidth() - 42);
                for (PdfPageSlot slot : slots) {
                    slot.setFitWidth(w);
                }
                session.renderAround(visiblePageIndex(scroll, pages, slots), slots);
            };
            scroll.vvalueProperty().addListener((o, a, b) -> {
                rememberPdfScrollPosition(positionKey, b.doubleValue());
                session.renderAround(visiblePageIndex(scroll, pages, slots), slots);
            });
            scroll.viewportBoundsProperty().addListener((o, a, b) -> {
                if (zoom[0] <= 0) {
                    apply.run();
                } else {
                    session.renderAround(visiblePageIndex(scroll, pages, slots), slots);
                }
            });
            pages.heightProperty().addListener((o, a, b) ->
                    session.renderAround(visiblePageIndex(scroll, pages, slots), slots));
            scroll.sceneProperty().addListener((o, oldScene, newScene) -> {
                if (newScene == null) {
                    rememberPdfScrollPosition(positionKey, scroll.getVvalue());
                    session.close();
                }
            });
            root.parentProperty().addListener((o, oldParent, newParent) -> {
                if (newParent == null) {
                    rememberPdfScrollPosition(positionKey, scroll.getVvalue());
                    session.close();
                }
            });
            zoomOut.setOnAction(ev -> {
                double base = zoom[0] > 0 ? zoom[0] : scroll.getViewportBounds().getWidth() - 42;
                zoom[0] = Math.max(120, base / ZOOM_STEP);
                apply.run();
            });
            zoomIn.setOnAction(ev -> {
                double base = zoom[0] > 0 ? zoom[0] : scroll.getViewportBounds().getWidth() - 42;
                zoom[0] = base * ZOOM_STEP;
                apply.run();
            });
            fitBtn.setOnAction(ev -> {
                zoom[0] = 0;
                apply.run();
            });
            pageInfo.textProperty().unbind();
            String pagesWord = tr(bundle, session.pageCount() == 1 ? "viewer.page" : "viewer.pages",
                    session.pageCount() == 1 ? "page" : "pages");
            pageInfo.setText(session.pageCount() + " " + pagesWord);
            root.setCenter(scroll);
            Platform.runLater(() -> {
                apply.run();
                restorePdfScrollPosition(positionKey, scroll);
                session.renderAround(visiblePageIndex(scroll, pages, slots), slots);
            });
        });
        task.setOnFailed(e -> {
            logger.log(Level.WARNING, "Failed to render PDF: " + path, task.getException());
            Label error = new Label(tr(bundle, "viewer.pdf_error", "Could not render PDF")
                    + (task.getException() != null ? ": " + task.getException().getMessage() : ""));
            error.getStyleClass().add("viewer-info");
            root.setCenter(new StackPane(error));
        });
        pageInfo.textProperty().bind(task.messageProperty());
        Thread thread = new Thread(task, "pdf-open");
        thread.setDaemon(true);
        thread.start();
        return root;
    }

    private static void rememberPdfScrollPosition(String key, double value) {
        if (key == null || Double.isNaN(value)) {
            return;
        }
        synchronized (PDF_SCROLL_POSITIONS) {
            PDF_SCROLL_POSITIONS.put(key, Math.max(0, Math.min(1, value)));
        }
    }

    private static void restorePdfScrollPosition(String key, ScrollPane scroll) {
        Double saved;
        synchronized (PDF_SCROLL_POSITIONS) {
            saved = PDF_SCROLL_POSITIONS.get(key);
        }
        if (saved == null) {
            return;
        }
        scroll.setVvalue(saved);
        Platform.runLater(() -> scroll.setVvalue(saved));
    }

    private static int visiblePageIndex(ScrollPane scroll, VBox pages, List<PdfPageSlot> slots) {
        if (slots.size() <= 1) {
            return 0;
        }
        double viewportHeight = scroll.getViewportBounds().getHeight();
        double contentHeight = pages.getBoundsInLocal().getHeight();
        if (viewportHeight <= 0 || contentHeight <= viewportHeight) {
            return 0;
        }
        double top = scroll.getVvalue() * Math.max(0, contentHeight - viewportHeight);
        double center = top + viewportHeight / 2.0;
        for (int i = 0; i < slots.size(); i++) {
            PdfPageSlot slot = slots.get(i);
            double minY = slot.getBoundsInParent().getMinY();
            double maxY = slot.getBoundsInParent().getMaxY();
            if (center >= minY && center <= maxY) {
                return i;
            }
        }
        return Math.max(0, Math.min(slots.size() - 1,
                (int) Math.round(scroll.getVvalue() * (slots.size() - 1))));
    }

    private static final class PdfSession {
        private static final int CACHE_LIMIT = 12;
        private static final int RENDER_RADIUS = 5;

        private final PDDocument document;
        private final PDFRenderer renderer;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean workerRunning = new AtomicBoolean();
        private final AtomicInteger requestedCenter = new AtomicInteger();
        private final Map<Integer, Image> cache = new LinkedHashMap<>(CACHE_LIMIT, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, Image> eldest) {
                return size() > CACHE_LIMIT;
            }
        };

        PdfSession(PDDocument document) {
            this.document = document;
            this.renderer = new PDFRenderer(document);
        }

        int pageCount() {
            return document.getNumberOfPages();
        }

        List<PdfPageSlot> createSlots() {
            List<PdfPageSlot> slots = new java.util.ArrayList<>();
            for (PDPage page : document.getPages()) {
                float width = page.getMediaBox().getWidth();
                float height = page.getMediaBox().getHeight();
                slots.add(new PdfPageSlot(height > 0 && width > 0 ? height / width : 1.414));
            }
            return slots;
        }

        void renderAround(int center, List<PdfPageSlot> slots) {
            if (closed.get() || slots.isEmpty()) {
                return;
            }
            requestedCenter.set(Math.max(0, Math.min(slots.size() - 1, center)));
            if (!workerRunning.compareAndSet(false, true)) {
                return;
            }
            Thread thread = new Thread(() -> renderLoop(slots), "pdf-render");
            thread.setDaemon(true);
            thread.start();
        }

        private void renderLoop(List<PdfPageSlot> slots) {
            try {
                while (!closed.get()) {
                    int center = requestedCenter.get();
                    renderWindow(center, slots);
                    if (requestedCenter.get() == center) {
                        return;
                    }
                }
            } finally {
                workerRunning.set(false);
                int center = requestedCenter.get();
                if (!closed.get()
                        && !isRenderedWindow(center, slots)
                        && workerRunning.compareAndSet(false, true)) {
                    Thread thread = new Thread(() -> renderLoop(slots), "pdf-render");
                    thread.setDaemon(true);
                    thread.start();
                }
            }
        }

        private void renderWindow(int center, List<PdfPageSlot> slots) {
            renderPage(center, slots.get(center));
            for (int offset = 1; offset <= RENDER_RADIUS && !closed.get(); offset++) {
                if (requestedCenter.get() != center) {
                    return;
                }
                int before = center - offset;
                if (before >= 0) {
                    renderPage(before, slots.get(before));
                }
                if (requestedCenter.get() != center) {
                    return;
                }
                int after = center + offset;
                if (after < slots.size()) {
                    renderPage(after, slots.get(after));
                }
            }
        }

        private boolean isRenderedWindow(int center, List<PdfPageSlot> slots) {
            int from = Math.max(0, center - RENDER_RADIUS);
            int to = Math.min(slots.size() - 1, center + RENDER_RADIUS);
            for (int i = from; i <= to; i++) {
                if (!slots.get(i).rendered && !slots.get(i).failed) {
                    return false;
                }
            }
            return true;
        }

        private void renderPage(int index, PdfPageSlot slot) {
            if (slot.rendered || slot.rendering || closed.get()) {
                return;
            }
            Image cached;
            synchronized (cache) {
                cached = cache.get(index);
            }
            if (cached != null) {
                slot.rendered = true;
                Platform.runLater(() -> {
                    if (!closed.get()) {
                        slot.setImage(cached);
                    }
                });
                return;
            }
            slot.rendering = true;
            try {
                if (closed.get()) {
                    return;
                }
                BufferedImage page = renderer.renderImageWithDPI(index, PDF_RENDER_DPI, ImageType.RGB);
                Image image = toFxImage(page);
                synchronized (cache) {
                    cache.put(index, image);
                }
                slot.rendered = true;
                Platform.runLater(() -> {
                    if (!closed.get()) {
                        slot.setImage(image);
                    }
                });
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Failed to render PDF page " + (index + 1), ex);
                slot.failed = true;
                Platform.runLater(slot::showError);
            } finally {
                slot.rendering = false;
            }
        }

        void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                document.close();
            } catch (IOException ex) {
                logger.log(Level.FINE, "Failed to close PDF document", ex);
            }
        }
    }

    private static final class PdfPageSlot extends StackPane {
        private final double aspectRatio;
        private final ImageView imageView = new ImageView();
        private boolean rendered;
        private boolean rendering;
        private boolean failed;

        PdfPageSlot(double aspectRatio) {
            this.aspectRatio = aspectRatio;
            getStyleClass().add("viewer-pdf-placeholder");
            setPadding(new Insets(0));
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            imageView.getStyleClass().add("viewer-pdf-page");
            Label loading = new Label("…");
            loading.getStyleClass().add("viewer-info");
            getChildren().add(loading);
        }

        void setFitWidth(double width) {
            double safeWidth = Math.max(120, width);
            imageView.setFitWidth(safeWidth);
            setPrefSize(safeWidth, Math.max(160, safeWidth * aspectRatio));
            setMinSize(safeWidth, Math.max(160, safeWidth * aspectRatio));
        }

        void setImage(Image image) {
            rendered = true;
            imageView.setImage(image);
            getChildren().setAll(imageView);
        }

        void showError() {
            Label error = new Label("PDF page error");
            error.getStyleClass().add("viewer-info");
            getChildren().setAll(error);
        }
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
