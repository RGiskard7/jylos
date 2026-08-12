package com.example.jylos.plugin;

/**
 * Interface that plugins can implement to enhance the note preview.
 * This can be used to inject CSS, JavaScript, or other HTML content into the
 * note preview.
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.5.0
 */
public interface PreviewEnhancer {
    /**
     * Hook to inject content into the {@code <head>} section of the preview HTML.
     * Useful for injecting CSS files or meta tags.
     * 
     * @return HTML string to inject into {@code <head>}, or empty string if none
     */
    default String getHeadInjections() {
        return "";
    }

    /**
     * Hook to inject content into the end of the {@code <body>} section of the preview
     * HTML.
     * Useful for injecting JavaScript files or initialization scripts.
     *
     * @return HTML string to inject into {@code <body>}, or empty string if none
     */
    default String getBodyInjections() {
        return "";
    }

    /**
     * Post-processes the rendered note HTML before it is assembled into the final
     * preview document.
     *
     * <p>Unlike the injection hooks above, this one knows <em>which</em> note is being
     * rendered ({@link PreviewContext#note()}), so a plugin can replace or augment
     * content per note — for example turning a fenced <code>```dataview</code> block
     * into a generated table. The returned string replaces the note's rendered body.</p>
     *
     * <p>Runs on a background render thread, not the JavaFX Application Thread, and is
     * called once per preview render. Keep it deterministic: the same note and HTML
     * should produce the same output, since the preview re-renders on every edit. An
     * implementation that throws is logged and skipped, leaving the HTML untouched.</p>
     *
     * @param context the note and theme this render belongs to
     * @param html    the note's rendered HTML body
     * @return the transformed HTML; returning {@code null} keeps {@code html} unchanged
     */
    default String transformHtml(PreviewContext context, String html) {
        return html;
    }
}
