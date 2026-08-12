package com.example.jylos.plugin.builtin.dataview;

import java.util.List;

import com.example.jylos.plugin.builtin.dataview.QueryEngine.QueryResult;
import com.example.jylos.plugin.builtin.dataview.QueryEngine.ResultGroup;
import com.example.jylos.plugin.builtin.dataview.QueryEngine.ResultRow;

/** Renders query results as the HTML injected back into the note preview. */
final class DataviewRenderer {

    private DataviewRenderer() {
    }

    static String render(QueryResult result) {
        if (result.total() == 0) {
            return "<div class=\"dataview dataview-empty\">No results.</div>";
        }
        return switch (result.kind()) {
            case TABLE -> renderTable(result);
            case LIST -> renderList(result);
            case TASK -> renderTasks(result);
        };
    }

    /** An unparseable or failing query shows in place, so the note still renders. */
    static String renderError(String message, String query) {
        return "<div class=\"dataview dataview-error\">"
                + "<div class=\"dataview-error-title\">Dataview: " + Html.escape(message) + "</div>"
                + "<pre class=\"dataview-error-query\">" + Html.escape(query) + "</pre>"
                + "</div>";
    }

    // ── TABLE ────────────────────────────────────────────────────────────────

    private static String renderTable(QueryResult result) {
        StringBuilder out = new StringBuilder();
        for (ResultGroup group : result.groups()) {
            if (result.grouped()) {
                out.append(groupHeading(group, countLabel(group.rows().size())));
            }
            out.append("<table class=\"dataview dataview-table\"><thead><tr>");
            for (String header : result.headers()) {
                out.append("<th>").append(Html.escape(header)).append("</th>");
            }
            out.append("</tr></thead><tbody>");
            for (ResultRow row : group.rows()) {
                out.append("<tr>");
                for (int column = 0; column < result.headers().size(); column++) {
                    Object value = column < row.values().size() ? row.values().get(column) : null;
                    out.append("<td>").append(DqlValue.toHtml(value)).append("</td>");
                }
                out.append("</tr>");
            }
            out.append("</tbody></table>");
        }
        return out.toString();
    }

    // ── LIST ─────────────────────────────────────────────────────────────────

    private static String renderList(QueryResult result) {
        StringBuilder out = new StringBuilder();
        for (ResultGroup group : result.groups()) {
            if (result.grouped()) {
                out.append(groupHeading(group, countLabel(group.rows().size())));
            }
            out.append("<ul class=\"dataview dataview-list\">");
            for (ResultRow row : group.rows()) {
                out.append("<li>");
                List<Object> values = row.values();
                for (int i = 0; i < values.size(); i++) {
                    if (i > 0) {
                        out.append(" — ");
                    }
                    out.append(DqlValue.toHtml(values.get(i)));
                }
                out.append("</li>");
            }
            out.append("</ul>");
        }
        return out.toString();
    }

    // ── TASK ─────────────────────────────────────────────────────────────────

    private static String renderTasks(QueryResult result) {
        StringBuilder out = new StringBuilder();
        for (ResultGroup group : result.groups()) {
            out.append(groupHeading(group, countLabel(group.rows().size())));
            out.append("<ul class=\"dataview dataview-tasks\">");
            for (ResultRow row : group.rows()) {
                Task task = row.task();
                if (task == null) {
                    continue;
                }
                // Indentation is inlined rather than nested in real <ul> levels: subtask
                // depth in the source is a display detail here, and rebuilding the tree
                // would reorder tasks that SORT deliberately placed.
                String indent = task.indent() > 0
                        ? " style=\"margin-left:" + Math.min(task.indent(), 24) + "ch\""
                        : "";
                out.append("<li class=\"dataview-task")
                        .append(task.completed() ? " is-done" : "")
                        .append("\"").append(indent).append(">")
                        .append("<input type=\"checkbox\" disabled")
                        .append(task.completed() ? " checked" : "").append(">")
                        .append("<span class=\"dataview-task-text\">")
                        .append(task.toHtmlText())
                        .append("</span>");
                if (task.hasCustomStatus()) {
                    out.append("<span class=\"dataview-task-status\">")
                            .append(Html.escape(task.status())).append("</span>");
                }
                out.append("</li>");
            }
            out.append("</ul>");
        }
        return out.toString();
    }

    // ── Shared ───────────────────────────────────────────────────────────────

    private static String groupHeading(ResultGroup group, String count) {
        return "<div class=\"dataview-group-header\">"
                + DqlValue.toHtml(group.key())
                + "<span class=\"dataview-group-count\">" + count + "</span></div>";
    }

    private static String countLabel(int size) {
        return size + (size == 1 ? " result" : " results");
    }

    /**
     * Styles for the editor's Live Preview, where the theme is not known at render time.
     *
     * <p>Colours come from the CSS custom properties the editor theme already publishes,
     * so the same markup follows the light/dark switch without the renderer being told
     * which is active. Tables, lists and links are left to the editor's own baseline
     * styling for rendered blocks; only Dataview's own classes are defined here.</p>
     */
    static String editorStyles() {
        return """
                <style>
                .dataview-group-header {
                    margin: .9em 0 .3em; font-weight: 650;
                    display: flex; align-items: baseline; gap: .6em; }
                .dataview-group-header:first-child { margin-top: 0; }
                .dataview-group-count { font-size: .8em; font-weight: 400; color: var(--jylos-muted); }
                ul.dataview-tasks { list-style: none; padding-left: .2em; }
                li.dataview-task { display: flex; align-items: flex-start; gap: .5em; margin: .25em 0; }
                li.dataview-task input { margin-top: .28em; flex: none; }
                li.dataview-task.is-done .dataview-task-text { text-decoration: line-through; opacity: .65; }
                .dataview-task-status {
                    font-size: .75em; border: 1px solid var(--jylos-border); border-radius: 4px;
                    padding: 0 5px; margin-left: .35em; color: var(--jylos-muted); }
                .dataview-empty { color: var(--jylos-muted); font-style: italic; }
                .dataview-error-title { color: #d1584f; font-weight: 600; margin-bottom: 4px; }
                .dataview-error-query {
                    margin: 0; background: transparent; border: none; padding: 0;
                    font-size: .85em; color: var(--jylos-muted); white-space: pre-wrap; }
                </style>
                """;
    }

    /** Styles for every element the renderer emits, injected once per preview. */
    static String styles(boolean dark) {
        String border = dark ? "#3a3a3a" : "#e2e5ea";
        String headerBackground = dark ? "#252525" : "#f5f6f8";
        String muted = dark ? "#9ca3af" : "#667085";
        String errorBackground = dark ? "#3a2222" : "#fdf1f1";
        String errorBorder = dark ? "#7f3b3b" : "#e6b5b5";
        String errorText = dark ? "#ff8f8f" : "#b42318";

        return """
                <style>
                table.dataview-table { border-collapse: collapse; width: 100%%; margin: 1em 0; font-size: 0.95em; }
                table.dataview-table th, table.dataview-table td {
                    border: 1px solid %s; padding: 8px 10px; text-align: left; vertical-align: top; }
                table.dataview-table th { background: %s; font-weight: 600; }
                ul.dataview-list, ul.dataview-tasks { margin: 0.6em 0; padding-left: 1.4em; }
                ul.dataview-tasks { list-style: none; padding-left: 0.2em; }
                li.dataview-task { display: flex; align-items: flex-start; gap: 0.5em; margin: 0.3em 0; }
                li.dataview-task input { margin-top: 0.28em; flex: none; }
                li.dataview-task.is-done .dataview-task-text { text-decoration: line-through; opacity: 0.65; }
                .dataview-task-status {
                    font-size: 0.75em; border: 1px solid %s; border-radius: 4px;
                    padding: 0 5px; margin-left: 0.35em; color: %s; }
                .dataview-group-header {
                    margin: 1.2em 0 0.4em; font-weight: 650; font-size: 1.05em;
                    display: flex; align-items: baseline; gap: 0.6em; }
                .dataview-group-count { font-size: 0.8em; font-weight: 400; color: %s; }
                .dataview-empty { color: %s; font-style: italic; margin: 0.8em 0; }
                .dataview-error {
                    background: %s; border: 1px solid %s; border-radius: 6px;
                    padding: 10px 12px; margin: 1em 0; }
                .dataview-error-title { color: %s; font-weight: 600; margin-bottom: 6px; }
                .dataview-error-query {
                    margin: 0; background: transparent; border: none; padding: 0;
                    font-size: 0.85em; color: %s; white-space: pre-wrap; }
                .dataview-inline { border-bottom: 1px dotted %s; }
                </style>
                """.formatted(border, headerBackground, border, muted, muted, muted,
                errorBackground, errorBorder, errorText, muted, muted);
    }
}
