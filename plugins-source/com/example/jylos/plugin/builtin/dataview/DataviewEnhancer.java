package com.example.jylos.plugin.builtin.dataview;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.jylos.data.models.Note;
import com.example.jylos.plugin.PreviewContext;
import com.example.jylos.plugin.PreviewEnhancer;

/**
 * Finds queries in the rendered preview and replaces them with their results.
 *
 * <h2>Why post-processing rendered HTML</h2>
 * <p>Queries are authored as fenced <code>```dataview</code> blocks, so by the time this
 * runs CommonMark has already turned them into {@code <pre><code class="language-dataview">}
 * with their operators HTML-escaped. Working on the rendered output (rather than the raw
 * Markdown) means the query text is located exactly where the Markdown parser decided a
 * code block really was — a fence inside a blockquote or a list stays correctly scoped,
 * and a query shown as an example inside a nested fence is not executed by mistake.</p>
 */
final class DataviewEnhancer implements PreviewEnhancer {

    /** Fenced query blocks, keeping the language so unsupported dialects can be reported. */
    private static final Pattern BLOCK = Pattern.compile(
            "<pre><code class=\"language-(dataview|dataviewjs)\">(.*?)</code></pre>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    /** Inline queries: `= expression` rendered by CommonMark as an inline code span. */
    private static final Pattern INLINE = Pattern.compile(
            "<code>\\s*=\\s*([^<]+?)\\s*</code>");

    private final PageSource index;
    private final QueryEngine engine;

    DataviewEnhancer(PageSource index) {
        this.index = index;
        this.engine = new QueryEngine(index);
    }

    @Override
    public String getHeadInjections() {
        // Theme is not known here; the rendered-body transform injects the themed
        // stylesheet instead, and only when the note actually contains a query.
        return "";
    }

    @Override
    public String transformHtml(PreviewContext context, String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        boolean hasBlock = html.contains("class=\"language-dataview");
        boolean hasInline = html.contains("<code>");
        if (!hasBlock && !hasInline) {
            return html;
        }

        Page currentPage = resolveCurrentPage(context);
        String result = html;
        boolean rendered = false;

        if (hasBlock) {
            String replaced = replaceBlocks(result, currentPage);
            rendered = !replaced.equals(result);
            result = replaced;
        }
        if (hasInline) {
            String replaced = replaceInline(result, currentPage);
            rendered |= !replaced.equals(result);
            result = replaced;
        }

        return rendered ? DataviewRenderer.styles(context != null && context.darkTheme()) + result : result;
    }

    /**
     * Resolves {@code this} for the note being previewed. Falls back to parsing the note
     * directly when it is not in the index (a brand-new note the index has not seen, or
     * one excluded from indexing), so {@code this.file.name} still resolves.
     */
    private Page resolveCurrentPage(PreviewContext context) {
        Note note = context == null ? null : context.note();
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

    private String replaceBlocks(String html, Page currentPage) {
        Matcher matcher = BLOCK.matcher(html);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String language = matcher.group(1).toLowerCase();
            String query = Html.unescape(matcher.group(2)).trim();
            String replacement = "dataviewjs".equals(language)
                    ? DataviewRenderer.renderError(
                            "JavaScript queries (dataviewjs) are not supported — use a "
                                    + "dataview query block instead.", query)
                    : runQuery(query, currentPage);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String runQuery(String query, Page currentPage) {
        if (query.isEmpty()) {
            return DataviewRenderer.renderError("Empty query.", query);
        }
        try {
            Ast.Query parsed = DqlParser.parse(query);
            return DataviewRenderer.render(engine.run(parsed, currentPage));
        } catch (DqlException e) {
            return DataviewRenderer.renderError(e.getMessage(), query);
        } catch (RuntimeException e) {
            // A malformed vault (unparseable frontmatter, a field of an unexpected shape)
            // must degrade to a visible message, never take the whole preview down.
            return DataviewRenderer.renderError(
                    "Query failed: " + (e.getMessage() == null ? e.toString() : e.getMessage()), query);
        }
    }

    private String replaceInline(String html, Page currentPage) {
        Matcher matcher = INLINE.matcher(html);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String expression = Html.unescape(matcher.group(1)).trim();
            String replacement;
            try {
                Ast.Expr parsed = DqlParser.parseExpression(expression);
                Object value = new DqlEvaluator(index, currentPage)
                        .evaluate(parsed, DqlEvaluator.Row.of(currentPage));
                replacement = "<span class=\"dataview-inline\">" + DqlValue.toHtml(value) + "</span>";
            } catch (RuntimeException e) {
                // Leave anything that is not a valid expression exactly as the reader
                // wrote it: `= 1 + 1` in prose is far more likely than a broken query.
                replacement = matcher.group(0);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
