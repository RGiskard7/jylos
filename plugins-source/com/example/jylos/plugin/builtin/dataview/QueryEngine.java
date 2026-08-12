package com.example.jylos.plugin.builtin.dataview;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.example.jylos.plugin.builtin.dataview.DqlEvaluator.Row;

/**
 * Runs a parsed query against the index and produces render-ready results.
 *
 * <p>Clause order follows Dataview: {@code FROM} narrows the page set, {@code FLATTEN}
 * expands rows, {@code WHERE} filters, {@code SORT} orders, {@code GROUP BY} buckets and
 * {@code LIMIT} caps. Filtering after flattening is what makes
 * {@code FLATTEN authors AS author WHERE author = "X"} behave as written.</p>
 */
final class QueryEngine {

    /** One output row: the evaluated projection plus the page it came from. */
    record ResultRow(Page page, List<Object> values, Task task) {
    }

    /** A bucket of rows; {@code key} is {@code null} for ungrouped results. */
    record ResultGroup(Object key, List<ResultRow> rows) {
    }

    record QueryResult(Ast.Kind kind, List<String> headers, List<ResultGroup> groups,
            boolean grouped, int total) {
    }

    private final PageSource index;

    QueryEngine(PageSource index) {
        this.index = index;
    }

    QueryResult run(Ast.Query query, Page currentPage) {
        DqlEvaluator evaluator = new DqlEvaluator(index, currentPage);

        List<Page> pages = selectPages(query.source(), currentPage);
        List<Row> rows = new ArrayList<>();
        for (Page page : pages) {
            rows.add(Row.of(page));
        }

        if (query.kind() == Ast.Kind.TASK) {
            rows = expandTasks(rows);
        }

        for (Ast.Column flatten : query.flattens()) {
            rows = flattenRows(rows, flatten, evaluator);
        }

        List<Row> filtered = new ArrayList<>();
        for (Row row : rows) {
            if (matchesFilters(query, row, evaluator)) {
                filtered.add(row);
            }
        }

        sortRows(filtered, query.sorts(), evaluator);

        List<String> headers = new ArrayList<>();
        if (query.kind() == Ast.Kind.TABLE && !query.withoutId()) {
            headers.add("File");
        }
        for (Ast.Column column : query.columns()) {
            headers.add(column.header());
        }

        List<ResultRow> projected = new ArrayList<>(filtered.size());
        for (Row row : filtered) {
            projected.add(project(query, row, evaluator));
        }

        List<ResultGroup> groups = group(query, filtered, projected, evaluator);
        int total = projected.size();

        if (query.limit() != null) {
            if (query.groupBy() != null) {
                groups = groups.subList(0, Math.min(groups.size(), query.limit()));
            } else if (!groups.isEmpty()) {
                List<ResultRow> capped = groups.get(0).rows();
                groups = List.of(new ResultGroup(null,
                        capped.subList(0, Math.min(capped.size(), query.limit()))));
            }
        }

        return new QueryResult(query.kind(), headers, groups, query.groupBy() != null, total);
    }

    private boolean matchesFilters(Ast.Query query, Row row, DqlEvaluator evaluator) {
        for (Ast.Expr filter : query.filters()) {
            if (!DqlValue.truthy(evaluator.evaluate(filter, row))) {
                return false;
            }
        }
        return true;
    }

    private ResultRow project(Ast.Query query, Row row, DqlEvaluator evaluator) {
        List<Object> values = new ArrayList<>();
        if (query.kind() == Ast.Kind.TABLE && !query.withoutId()) {
            values.add(row.page().link());
        }
        for (Ast.Column column : query.columns()) {
            values.add(evaluator.evaluate(column.expression(), row));
        }
        if (query.kind() == Ast.Kind.LIST && query.columns().isEmpty()) {
            values.add(row.page().link());
        }
        Object task = row.bindings().get("task");
        return new ResultRow(row.page(), values, task instanceof Task typed ? typed : null);
    }

    // ── Grouping ─────────────────────────────────────────────────────────────

    private List<ResultGroup> group(Ast.Query query, List<Row> rows, List<ResultRow> projected,
            DqlEvaluator evaluator) {
        if (query.groupBy() == null) {
            // TASK results read best grouped by their source note, which is also what
            // Dataview does by default when no explicit GROUP BY is given.
            if (query.kind() == Ast.Kind.TASK) {
                return groupByPage(projected);
            }
            return List.of(new ResultGroup(null, projected));
        }

        Map<String, ResultGroup> buckets = new LinkedHashMap<>();
        Map<String, List<ResultRow>> members = new LinkedHashMap<>();
        Map<String, Object> keys = new LinkedHashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            Object key = evaluator.evaluate(query.groupBy(), rows.get(i));
            String bucketKey = DqlValue.toDisplayString(key);
            keys.putIfAbsent(bucketKey, key);
            members.computeIfAbsent(bucketKey, unused -> new ArrayList<>()).add(projected.get(i));
        }
        for (Map.Entry<String, List<ResultRow>> entry : members.entrySet()) {
            buckets.put(entry.getKey(), new ResultGroup(keys.get(entry.getKey()), entry.getValue()));
        }
        return new ArrayList<>(buckets.values());
    }

    private static List<ResultGroup> groupByPage(List<ResultRow> rows) {
        Map<String, List<ResultRow>> byPage = new LinkedHashMap<>();
        for (ResultRow row : rows) {
            byPage.computeIfAbsent(row.page().title(), unused -> new ArrayList<>()).add(row);
        }
        List<ResultGroup> groups = new ArrayList<>(byPage.size());
        for (Map.Entry<String, List<ResultRow>> entry : byPage.entrySet()) {
            groups.add(new ResultGroup(Link.to(entry.getKey()), entry.getValue()));
        }
        return groups;
    }

    // ── Row expansion ────────────────────────────────────────────────────────

    /** Turns each page row into one row per checklist item, binding the task's fields. */
    private static List<Row> expandTasks(List<Row> rows) {
        List<Row> expanded = new ArrayList<>();
        for (Row row : rows) {
            for (Task task : row.page().tasks()) {
                Map<String, Object> bindings = new LinkedHashMap<>(row.bindings());
                bindings.put("task", task);
                bindings.put("text", task.text());
                bindings.put("completed", task.completed());
                bindings.put("checked", task.completed());
                bindings.put("status", task.status());
                bindings.put("line", (double) task.line());
                bindings.putAll(task.fields());
                expanded.add(new Row(row.page(), bindings));
            }
        }
        return expanded;
    }

    private static List<Row> flattenRows(List<Row> rows, Ast.Column flatten, DqlEvaluator evaluator) {
        List<Row> expanded = new ArrayList<>();
        for (Row row : rows) {
            Object value = evaluator.evaluate(flatten.expression(), row);
            List<Object> elements = DqlValue.asList(value);
            if (elements.isEmpty()) {
                // Keep the row with a null binding rather than dropping the page: a
                // FLATTEN over a missing field should not silently delete results.
                expanded.add(row.bind(flatten.header(), null));
                continue;
            }
            for (Object element : elements) {
                expanded.add(row.bind(flatten.header(), element));
            }
        }
        return expanded;
    }

    private static void sortRows(List<Row> rows, List<Ast.SortBy> sorts, DqlEvaluator evaluator) {
        if (sorts.isEmpty()) {
            return;
        }
        rows.sort((left, right) -> {
            for (Ast.SortBy sort : sorts) {
                Object a = evaluator.evaluate(sort.expression(), left);
                Object b = evaluator.evaluate(sort.expression(), right);
                int comparison = DqlValue.compare(a, b);
                if (comparison != 0) {
                    return sort.ascending() ? comparison : -comparison;
                }
            }
            return 0;
        });
    }

    // ── FROM ─────────────────────────────────────────────────────────────────

    private List<Page> selectPages(Ast.Source source, Page currentPage) {
        List<Page> selected = new ArrayList<>();
        for (Page page : index.pages()) {
            if (matchesSource(source, page, currentPage)) {
                selected.add(page);
            }
        }
        return selected;
    }

    private boolean matchesSource(Ast.Source source, Page page, Page currentPage) {
        if (source instanceof Ast.AllPages) {
            return true;
        }
        if (source instanceof Ast.TagSource tag) {
            return hasTag(page, tag.tag());
        }
        if (source instanceof Ast.FolderSource folder) {
            return inFolder(page, folder.folder());
        }
        if (source instanceof Ast.IncomingLinks incoming) {
            String target = resolveSelfReference(incoming.target(), currentPage);
            for (Link link : page.outlinks()) {
                if (link.target().equalsIgnoreCase(target)) {
                    return true;
                }
            }
            return false;
        }
        if (source instanceof Ast.OutgoingLinks outgoing) {
            String origin = resolveSelfReference(outgoing.target(), currentPage);
            Page originPage = index.pageByTitle(origin);
            if (originPage == null) {
                return false;
            }
            for (Link link : originPage.outlinks()) {
                if (link.target().equalsIgnoreCase(page.title())) {
                    return true;
                }
            }
            return false;
        }
        if (source instanceof Ast.NotSource not) {
            return !matchesSource(not.inner(), page, currentPage);
        }
        if (source instanceof Ast.BinarySource binary) {
            boolean left = matchesSource(binary.left(), page, currentPage);
            if ("or".equals(binary.operator())) {
                return left || matchesSource(binary.right(), page, currentPage);
            }
            return left && matchesSource(binary.right(), page, currentPage);
        }
        return true;
    }

    /** Lets {@code FROM [[]]} mean "this note", as Obsidian's own link syntax does. */
    private static String resolveSelfReference(String target, Page currentPage) {
        if ((target == null || target.isEmpty()) && currentPage != null) {
            return currentPage.title();
        }
        return target == null ? "" : target;
    }

    /** Tag match is hierarchical: {@code #project} also selects {@code #project/active}. */
    private static boolean hasTag(Page page, String tag) {
        String wanted = tag.startsWith("#") ? tag : "#" + tag;
        String wantedLower = wanted.toLowerCase(Locale.ROOT);
        for (String candidate : page.tags()) {
            String candidateLower = candidate.toLowerCase(Locale.ROOT);
            if (candidateLower.equals(wantedLower) || candidateLower.startsWith(wantedLower + "/")) {
                return true;
            }
        }
        return false;
    }

    private static boolean inFolder(Page page, String folder) {
        String wanted = folder.replace('\\', '/').trim();
        while (wanted.endsWith("/")) {
            wanted = wanted.substring(0, wanted.length() - 1);
        }
        if (wanted.isEmpty() || "/".equals(wanted)) {
            return true;
        }
        String pageFolder = page.folder() == null ? "" : page.folder().replace('\\', '/');
        if (pageFolder.equalsIgnoreCase(wanted)) {
            return true;
        }
        // Also match on the note's path so nested folders work in vault storage, where
        // the note id is its relative path.
        String path = page.id() == null ? "" : page.id().replace('\\', '/');
        String prefix = wanted.toLowerCase(Locale.ROOT) + "/";
        return path.toLowerCase(Locale.ROOT).startsWith(prefix)
                || pageFolder.toLowerCase(Locale.ROOT).startsWith(prefix);
    }
}
