package com.example.jylos.ui.components;

import com.example.jylos.service.WebImageConverter;
import com.google.gson.Gson;

import javafx.application.Platform;
import com.example.jylos.ui.web.HtmlView;

/**
 * Shows images JavaFX's WebKit cannot decode (WebP) in the editor and the reading view.
 *
 * <p>When an image fails, the page calls its Java bridge with an id and the image's source;
 * the bridge hands them here. The image is converted to PNG off the UI thread
 * ({@link WebImageConverter}) and the answer — a PNG {@code data:} URI, or {@code ""} when it
 * cannot be read — goes back to the page through {@code receiver.imageConverted(id, png)}.</p>
 */
public final class WebViewImages {

    /** The editor page's object that takes the answers ({@code editor.js}). */
    public static final String EDITOR = "window.JylosEditor";
    /** The reading view page's object that takes the answers (see {@code MarkdownPreview}). */
    public static final String READING_VIEW = "window.__jylosImages";

    private static final Gson GSON = new Gson();

    private WebViewImages() {
    }

    /** Converts {@code source} in the background and answers {@code receiver} on the page. */
    public static void convert(HtmlView page, String receiver, String id, String source) {
        // If the page was replaced meanwhile, the receiver is gone and nothing runs.
        WebImageConverter.shared().convert(source).thenAccept(png -> Platform.runLater(() -> page.run(
                receiver + " && " + receiver + ".imageConverted(" + GSON.toJson(id) + ", " + GSON.toJson(png) + ")")));
    }

    /**
     * Tells a reading view page its bridge is in place. Images that failed while the page was
     * loading, before the bridge was set, are converted now.
     */
    public static void bridgeReady(HtmlView page) {
        page.run(READING_VIEW + " && " + READING_VIEW + ".bridgeReady()");
    }
}
