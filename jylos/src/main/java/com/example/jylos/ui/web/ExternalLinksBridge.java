package com.example.jylos.ui.web;

import java.util.function.Consumer;

import javafx.application.Platform;

/**
 * The {@code window.javaApp} of a page that only shows text, such as the release notes: its
 * {@code http(s)} links open in the browser (the page's own script calls
 * {@code javaApp.openExternal}). Public: a page only reaches methods of public classes.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class ExternalLinksBridge {

    private final Consumer<String> openInBrowser;

    public ExternalLinksBridge(Consumer<String> openInBrowser) {
        this.openInBrowser = openInBrowser;
    }

    public void openExternal(String url) {
        if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
            Platform.runLater(() -> openInBrowser.accept(url));
        }
    }
}
