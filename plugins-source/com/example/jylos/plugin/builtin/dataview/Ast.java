package com.example.jylos.plugin.builtin.dataview;

import java.util.List;
import java.util.Map;

/** Abstract syntax tree for the query language: sources, expressions and query shape. */
final class Ast {

    private Ast() {
    }

    // ── FROM sources ─────────────────────────────────────────────────────────

    /** Selects which pages a query starts from, before {@code WHERE} narrows them. */
    sealed interface Source
            permits AllPages, TagSource, FolderSource, IncomingLinks, OutgoingLinks, BinarySource, NotSource {
    }

    /** No {@code FROM} clause: every indexed page. */
    record AllPages() implements Source {
    }

    record TagSource(String tag) implements Source {
    }

    record FolderSource(String folder) implements Source {
    }

    /** {@code FROM [[Note]]} — pages that link <em>to</em> the named note. */
    record IncomingLinks(String target) implements Source {
    }

    /** {@code FROM outgoing([[Note]])} — pages the named note links to. */
    record OutgoingLinks(String target) implements Source {
    }

    /** {@code and} / {@code or} combination of two sources. */
    record BinarySource(String operator, Source left, Source right) implements Source {
    }

    record NotSource(Source inner) implements Source {
    }

    // ── Expressions ──────────────────────────────────────────────────────────

    sealed interface Expr
            permits Literal, Variable, FieldAccess, IndexAccess, Call, Binary, Unary, ListLiteral, ObjectLiteral {
    }

    record Literal(Object value) implements Expr {
    }

    /** A bare identifier: a user field on the current page, or {@code this}/{@code file}. */
    record Variable(String name) implements Expr {
    }

    record FieldAccess(Expr target, String name) implements Expr {
    }

    record IndexAccess(Expr target, Expr index) implements Expr {
    }

    record Call(String name, List<Expr> arguments) implements Expr {
    }

    record Binary(String operator, Expr left, Expr right) implements Expr {
    }

    record Unary(String operator, Expr operand) implements Expr {
    }

    record ListLiteral(List<Expr> elements) implements Expr {
    }

    record ObjectLiteral(Map<String, Expr> entries) implements Expr {
    }

    // ── Query ────────────────────────────────────────────────────────────────

    enum Kind {
        TABLE, LIST, TASK
    }

    /** One projected column; {@code header} is the {@code AS} alias or the source text. */
    record Column(Expr expression, String header) {
    }

    record SortBy(Expr expression, boolean ascending) {
    }

    /**
     * A parsed query.
     *
     * @param kind      TABLE, LIST or TASK
     * @param withoutId whether {@code WITHOUT ID} suppressed the implicit link column
     * @param columns   projected columns (LIST uses at most one; TASK uses none)
     * @param source    the {@code FROM} clause
     * @param filters   {@code WHERE} predicates, applied in order and ANDed together
     * @param sorts     {@code SORT} keys, applied in order
     * @param groupBy   {@code GROUP BY} expression, or {@code null}
     * @param groupName alias for the group key, defaulting to {@code key}
     * @param flattens  {@code FLATTEN} expansions, applied before filtering
     * @param limit     {@code LIMIT}, or {@code null} for no cap
     */
    record Query(Kind kind, boolean withoutId, List<Column> columns, Source source,
            List<Expr> filters, List<SortBy> sorts, Expr groupBy, String groupName,
            List<Column> flattens, Integer limit) {
    }
}
