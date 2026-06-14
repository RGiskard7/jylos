package com.example.jylos.search;

import java.util.List;

/**
 * A parsed search query: the original raw string, the list of valid
 * {@link SearchFilter}s, and any non-fatal {@code warnings} (e.g. an unknown operator
 * that was treated as free text, or an invalid date that was dropped).
 *
 * <p>Parsing never throws: a malformed query simply yields fewer filters and one or
 * more warnings, so the search box stays usable.</p>
 *
 * @param raw      the original query text
 * @param filters  the clauses to apply (ANDed together)
 * @param warnings human-readable, non-intrusive notices about the query
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.1.0
 */
public record SearchQuery(String raw, List<SearchFilter> filters, List<String> warnings) {

    /** True when there is nothing to filter by (blank query → "all notes"). */
    public boolean isEmpty() {
        return filters.isEmpty();
    }

    /** True when the query uses at least one advanced operator (not just free text). */
    public boolean hasOperators() {
        return filters.stream().anyMatch(f -> f.field() != SearchFilter.Field.TEXT);
    }
}
