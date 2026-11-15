package com.example.jylos.ui.web;

import javafx.scene.Node;

/**
 * A page of HTML shown in the window, and the way Java talks to it: load the page, run or
 * evaluate script in it, expose a Java object to its script, and act once it has loaded.
 *
 * <p>Every part of the app that shows a web page (the editor, the reading view, the release
 * notes) goes through this interface, so the engine behind it is in one place
 * ({@link JavaFxHtmlView} today; see {@code docs/WEBVIEW.md}).</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public interface HtmlView {

    /** The node to put in the scene. */
    Node node();

    /** Replaces the page with {@code html}. */
    void loadHtml(String html);

    /** Runs {@code script} in the page; a failure is logged, not thrown. */
    void run(String script);

    /** Evaluates {@code expression} in the page: its value, or {@code null} if it failed. */
    Object evaluate(String expression);

    /**
     * Makes {@code bridge} reachable from the page's script as {@code window.name}, on the page
     * shown now (if loaded) and on every page loaded later. The object's class must be public
     * all the way out for the page to reach its methods.
     */
    void exposeBridge(String name, Object bridge);

    /** Runs {@code action} each time a page finishes loading (after bridges are exposed). */
    void onLoaded(Runnable action);

    /**
     * Runs {@code action} once the page being loaded has loaded; right away if it already has.
     * Dropped if the load fails or is cancelled.
     */
    void whenLoaded(Runnable action);

    /** Whether the last page asked for has finished loading. */
    boolean isLoaded();

    /** Gives the page the keyboard focus. */
    void focus();

    /** Whether the page has the keyboard focus. */
    boolean hasFocus();
}
