package com.example.jylos.plugin;

import com.example.jylos.data.models.Note;

/**
 * Context handed to {@link PreviewEnhancer#transformHtml(PreviewContext, String)}.
 *
 * <p>Identifies <em>which</em> note produced the HTML being post-processed. Without it a
 * preview enhancer can only inject static assets, since {@code getHeadInjections()} and
 * {@code getBodyInjections()} are called with no notion of the active document — a
 * plugin that renders per-note content (queries, computed tables, per-note dashboards)
 * would otherwise have to guess the active note by tracking selection events, which
 * races with fast note switching.</p>
 *
 * <p>Instances are created per preview render and are never mutated. The {@code note}
 * may be {@code null} when the preview is rendered without an owning note.</p>
 *
 * @param note      the note whose content was rendered, or {@code null} when unknown
 * @param darkTheme whether the preview is being rendered with the dark palette
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.5.0
 */
public record PreviewContext(Note note, boolean darkTheme) {
}
