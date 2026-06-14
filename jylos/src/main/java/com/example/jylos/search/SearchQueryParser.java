package com.example.jylos.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.example.jylos.search.SearchFilter.Field;

/**
 * Parses a raw search string into a {@link SearchQuery}: free-text words, quoted
 * phrases and {@code operator:value} clauses, each optionally negated with a leading
 * {@code -}.
 *
 * <p>The grammar is intentionally small and forgiving — it never throws. Unknown
 * operators fall back to free text (with a warning), and invalid operator values
 * (bad date, non-boolean, unknown {@code has:}/{@code is:} target) are dropped with a
 * warning so the rest of the query still runs.</p>
 *
 * <h3>Examples</h3>
 * <pre>
 *   java tag:spring                  → TEXT[java] AND TAG[spring]
 *   "design patterns" -tag:archive   → TEXT[design patterns] AND NOT TAG[archive]
 *   body:"java virtual machine"      → BODY[java virtual machine]
 *   modified:last-week is:orphan     → MODIFIED[last-week] AND IS[orphan]
 * </pre>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.1.0
 */
public final class SearchQueryParser {

    /** Recognised {@code key:} operators → their {@link Field}. */
    private static final Map<String, Field> KEYS = Map.ofEntries(
            Map.entry("tag", Field.TAG),
            Map.entry("folder", Field.FOLDER),
            Map.entry("title", Field.TITLE),
            Map.entry("body", Field.BODY),
            Map.entry("created", Field.CREATED),
            Map.entry("modified", Field.MODIFIED),
            Map.entry("favorite", Field.FAVORITE),
            Map.entry("private", Field.PRIVATE),
            Map.entry("encrypted", Field.PRIVATE),
            Map.entry("has", Field.HAS),
            Map.entry("is", Field.IS));

    private SearchQueryParser() {
    }

    /** Parses {@code raw} into a {@link SearchQuery} (never null, never throws). */
    public static SearchQuery parse(String raw) {
        List<SearchFilter> filters = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return new SearchQuery(raw == null ? "" : raw, List.of(), List.of());
        }
        for (String token : tokenize(raw)) {
            classify(token, filters, warnings);
        }
        return new SearchQuery(raw, List.copyOf(filters), List.copyOf(warnings));
    }

    // ── Tokenizing ────────────────────────────────────────────────────────────

    /** Splits on whitespace while keeping {@code "quoted phrases"} (and their quotes) intact. */
    static List<String> tokenize(String raw) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    // ── Classification ──────────────────────────────────────────────────────────

    private static void classify(String token, List<SearchFilter> filters, List<String> warnings) {
        if (token.equals("-") || token.equals("\"") || token.equals("\"\"")) {
            return; // stray dash / empty quotes
        }
        boolean negated = false;
        String tok = token;
        if (tok.length() > 1 && tok.charAt(0) == '-') {
            negated = true;
            tok = tok.substring(1);
        }
        if (tok.isEmpty()) {
            return;
        }
        // Quoted phrase → free text.
        if (tok.charAt(0) == '"') {
            String phrase = stripQuotes(tok);
            if (phrase.isBlank()) {
                warnings.add("Empty quoted phrase ignored");
            } else {
                filters.add(new SearchFilter(Field.TEXT, phrase, negated));
            }
            return;
        }
        int colon = tok.indexOf(':');
        if (colon <= 0) {
            filters.add(new SearchFilter(Field.TEXT, tok, negated));
            return;
        }
        String key = tok.substring(0, colon).toLowerCase(Locale.ROOT);
        String value = stripQuotes(tok.substring(colon + 1)).trim();
        Field field = KEYS.get(key);
        if (field == null) {
            // Unknown operator → keep it as literal free text, but tell the user.
            filters.add(new SearchFilter(Field.TEXT, tok, negated));
            warnings.add("Unknown operator '" + key + ":' — searched as text");
            return;
        }
        addOperator(field, key, value, negated, filters, warnings);
    }

    private static void addOperator(Field field, String key, String value, boolean negated,
            List<SearchFilter> filters, List<String> warnings) {
        if (value.isEmpty()) {
            warnings.add("Empty value for '" + key + ":' ignored");
            return;
        }
        switch (field) {
            case FAVORITE, PRIVATE -> {
                String bool = parseBool(value);
                if (bool == null) {
                    warnings.add("'" + key + ":' expects true/false (got '" + value + "')");
                    return;
                }
                filters.add(new SearchFilter(field, bool, negated));
            }
            case HAS -> {
                String norm = normalizeHas(value);
                if (norm == null) {
                    warnings.add("'has:' expects tag, links or backlinks (got '" + value + "')");
                    return;
                }
                filters.add(new SearchFilter(field, norm, negated));
            }
            case IS -> {
                if (!value.equalsIgnoreCase("orphan")) {
                    warnings.add("'is:' expects orphan (got '" + value + "')");
                    return;
                }
                filters.add(new SearchFilter(field, "orphan", negated));
            }
            case CREATED, MODIFIED -> {
                if (!SearchDates.isValidToken(value)) {
                    warnings.add("Invalid date '" + value + "' for '" + key + ":' ignored");
                    return;
                }
                filters.add(new SearchFilter(field, value.toLowerCase(Locale.ROOT), negated));
            }
            default -> filters.add(new SearchFilter(field, value, negated)); // TITLE, BODY, TAG, FOLDER
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /** Removes surrounding (or a single leading, when unterminated) double quotes. */
    static String stripQuotes(String s) {
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            return s.substring(1, s.length() - 1);
        }
        if (s.startsWith("\"")) {
            return s.substring(1);
        }
        return s;
    }

    /** Lenient boolean: returns "true"/"false", or null when unrecognised. */
    private static String parseBool(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1" -> "true";
            case "false", "no", "0" -> "false";
            default -> null;
        };
    }

    private static String normalizeHas(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "tag", "tags" -> "tag";
            case "link", "links" -> "links";
            case "backlink", "backlinks" -> "backlinks";
            default -> null;
        };
    }
}
