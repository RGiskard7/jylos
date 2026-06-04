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
     * Hook to inject content into the <head> section of the preview HTML.
     * Useful for injecting CSS files or meta tags.
     * 
     * @return HTML string to inject into <head>, or empty string if none
     */
    default String getHeadInjections() {
        return "";
    }

    /**
     * Hook to inject content into the end of the <body> section of the preview
     * HTML.
     * Useful for injecting JavaScript files or initialization scripts.
     * 
     * @return HTML string to inject into <body>, or empty string if none
     */
    default String getBodyInjections() {
        return "";
    }
}
