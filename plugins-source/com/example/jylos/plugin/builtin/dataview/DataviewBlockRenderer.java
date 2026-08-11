package com.example.jylos.plugin.builtin.dataview;

import com.example.jylos.data.models.Note;
import com.example.jylos.plugin.EditorBlockRenderer;

/**
 * Renders a <code>```dataview</code> block inline in the editor's Live Preview.
 *
 * <p>The block's source arrives as plain text here — unlike the preview surface, nothing
 * has HTML-escaped it — so it goes straight to the shared runner.</p>
 */
final class DataviewBlockRenderer implements EditorBlockRenderer {

    private final DataviewRunner runner;

    DataviewBlockRenderer(DataviewRunner runner) {
        this.runner = runner;
    }

    @Override
    public String render(Note note, String source) {
        // Styles travel with the markup: the editor styles the extension point generically
        // but knows nothing about Dataview's own classes, and must not be taught them.
        return DataviewRenderer.editorStyles() + runner.render(source, runner.resolvePage(note));
    }
}
