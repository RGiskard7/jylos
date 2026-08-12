package com.example.jylos.plugin.builtin.dataview;

import java.util.Map;

/**
 * A single checklist item found in a note, as produced by {@code TASK} queries.
 *
 * @param text      the task text with its checkbox marker and inline fields stripped
 * @param completed whether the checkbox is ticked
 * @param status    the raw character inside the brackets ({@code " "}, {@code "x"}, …)
 * @param line      zero-based line number within the note
 * @param indent    leading whitespace width, used to keep subtasks visually nested
 * @param pageTitle title of the note the task belongs to
 * @param fields    inline fields declared on the task line ({@code [due:: 2026-01-01]})
 */
record Task(String text, boolean completed, String status, int line, int indent,
        String pageTitle, Map<String, Object> fields) {

    /** True for any non-blank marker, matching Obsidian's "custom status" convention. */
    boolean hasCustomStatus() {
        return !status.isBlank() && !"x".equalsIgnoreCase(status);
    }

    String toHtmlText() {
        return Html.inline(text);
    }

    Link pageLink() {
        return Link.to(pageTitle);
    }

    @Override
    public String toString() {
        return text;
    }
}
