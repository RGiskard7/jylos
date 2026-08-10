package com.example.jylos.plugin.builtin.dataview;

/**
 * Markup produced by the plugin itself that must reach the preview unescaped.
 *
 * <p>Only ever constructed from values the plugin built and already escaped (such as
 * {@code elink()} anchors). Note content is never wrapped in it, so this cannot become a
 * path for a note to inject arbitrary HTML into the preview.</p>
 *
 * @param html safe, fully-escaped markup
 */
record RawHtml(String html) {

    @Override
    public String toString() {
        return html.replaceAll("<[^>]*>", "");
    }
}
