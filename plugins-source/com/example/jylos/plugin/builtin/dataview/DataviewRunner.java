package com.example.jylos.plugin.builtin.dataview;

import com.example.jylos.data.models.Note;

/**
 * Parses, executes and renders one query, shared by both surfaces the plugin renders on:
 * the reading-mode preview ({@link DataviewEnhancer}) and the editor's Live Preview
 * ({@link DataviewBlockRenderer}).
 *
 * <p>Keeping it in one place is what makes the two surfaces agree — a query must not
 * produce a different table depending on whether the note is being read or edited.</p>
 */
final class DataviewRunner {

    private final PageSource index;
    private final QueryEngine engine;

    DataviewRunner(PageSource index) {
        this.index = index;
        this.engine = new QueryEngine(index);
    }

    /**
     * Runs a query and returns its HTML, or a rendered error box when it cannot run.
     *
     * @param query       the query source, already unescaped
     * @param currentPage the page holding the query, resolving {@code this}
     */
    String render(String query, Page currentPage) {
        if (query == null || query.isBlank()) {
            return DataviewRenderer.renderError("Empty query.", query == null ? "" : query);
        }
        try {
            Ast.Query parsed = DqlParser.parse(query);
            return DataviewRenderer.render(engine.run(parsed, currentPage));
        } catch (DqlException e) {
            return DataviewRenderer.renderError(e.getMessage(), query);
        } catch (RuntimeException e) {
            // A malformed vault (unparseable frontmatter, a field of an unexpected shape)
            // must degrade to a visible message, never take the surrounding note down.
            return DataviewRenderer.renderError(
                    "Query failed: " + (e.getMessage() == null ? e.toString() : e.getMessage()), query);
        }
    }

    /**
     * Resolves {@code this} for a note. Falls back to parsing the note directly when it is
     * not in the index — a brand-new note the index has not seen, or one excluded from
     * indexing — so {@code this.file.name} still resolves.
     */
    Page resolvePage(Note note) {
        if (note == null) {
            return null;
        }
        Page indexed = index.pageByTitle(note.getTitle());
        if (indexed != null) {
            return indexed;
        }
        try {
            return PageParser.parse(note, "", note.getId());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
