package com.example.jylos.plugin.builtin.dataview;

import java.util.Objects;

/**
 * A link value: a reference to another note, optionally with a display alias.
 *
 * <p>Links are first-class values in DQL — they can be compared, sorted, stored in
 * frontmatter lists and rendered as clickable anchors. {@code target} is the bare note
 * title (no path, no {@code .md}, no heading), matching how Jylos resolves wiki-links
 * everywhere else; {@code display} is what the reader sees.</p>
 *
 * @param target  bare note title this link points at
 * @param display text shown to the reader (falls back to {@code target})
 */
record Link(String target, String display) {

    Link {
        target = target == null ? "" : target.trim();
        display = display == null || display.isBlank() ? target : display.trim();
    }

    static Link to(String target) {
        return new Link(target, null);
    }

    /** Renders the anchor format the Jylos preview already intercepts for note opening. */
    String toHtml() {
        return "<a class=\"wikilink\" href=\"jylos://open-note/" + encode(target)
                + "\" data-target=\"" + Html.escape(target)
                + "\">" + Html.escape(display) + "</a>";
    }

    /**
     * Mirrors {@code WikiLinkResolver.encodeTitle}: the preview's click handler reads the
     * note title from {@code data-target}, but the href must still be a well-formed URL
     * so the WebView does not mangle it before the handler runs.
     */
    private static String encode(String title) {
        return title.replace(" ", "%20")
                .replace("#", "%23")
                .replace("&", "%26")
                .replace("\"", "%22")
                .replace("'", "%27");
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Link link && link.target.equalsIgnoreCase(target);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(target.toLowerCase());
    }

    @Override
    public String toString() {
        return display;
    }
}
