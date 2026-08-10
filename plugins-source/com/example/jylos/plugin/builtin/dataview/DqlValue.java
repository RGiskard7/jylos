package com.example.jylos.plugin.builtin.dataview;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Value semantics for the query language: coercion, truthiness, ordering and display.
 *
 * <p>Values are plain Java objects rather than a wrapper hierarchy, because they come
 * straight out of YAML frontmatter (which already yields {@code String}/{@code Number}/
 * {@code Boolean}/{@code List}/{@code Map}) and go straight into HTML. The supported set
 * is {@code null}, {@link Boolean}, {@link Double}, {@link String}, {@link LocalDate},
 * {@link LocalDateTime}, {@link Link}, {@link Task}, {@link List} and {@link Map}.</p>
 *
 * <p>Every operation is total: comparing a string to a date, or adding a number to
 * {@code null}, yields a defined result instead of throwing. A query over a real vault
 * inevitably meets pages where a field is missing or holds an unexpected type, and one
 * such page must not blank out the whole table.</p>
 */
final class DqlValue {

    private static final DateTimeFormatter DATE_DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private DqlValue() {
    }

    // ── Type inspection ──────────────────────────────────────────────────────

    static String typeName(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof LocalDate || value instanceof LocalDateTime) {
            return "date";
        }
        if (value instanceof Link) {
            return "link";
        }
        if (value instanceof Task) {
            return "task";
        }
        if (value instanceof List) {
            return "array";
        }
        if (value instanceof Map) {
            return "object";
        }
        return "string";
    }

    /**
     * Ordering rank used when two values of different types are compared, so a mixed
     * column still sorts deterministically instead of depending on row order.
     */
    private static int typeRank(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Boolean) {
            return 1;
        }
        if (value instanceof Number) {
            return 2;
        }
        if (value instanceof LocalDate || value instanceof LocalDateTime) {
            return 3;
        }
        if (value instanceof List) {
            return 5;
        }
        if (value instanceof Map) {
            return 6;
        }
        return 4;
    }

    // ── Coercion ─────────────────────────────────────────────────────────────

    /** Truthiness: {@code null}, {@code false}, 0, "" and empty collections are false. */
    static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0;
        }
        if (value instanceof CharSequence text) {
            return !text.isEmpty();
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    /** Returns the value as a number, or {@code null} when it has no numeric meaning. */
    static Double asNumber(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof Boolean bool) {
            return bool ? 1.0 : 0.0;
        }
        if (value instanceof CharSequence text) {
            try {
                return Double.parseDouble(text.toString().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    static String asString(Object value) {
        return toDisplayString(value);
    }

    /** Wraps a scalar into a single-element list; {@code null} becomes an empty list. */
    static List<Object> asList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        List<Object> single = new ArrayList<>(1);
        single.add(value);
        return single;
    }

    /** Parses the date formats that appear in frontmatter and inline fields. */
    static Object asDate(Object value) {
        if (value instanceof LocalDate || value instanceof LocalDateTime) {
            return value;
        }
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        // ISO instants (the format Jylos stores note timestamps in) carry a zone that
        // LocalDateTime cannot parse directly; the local part is what queries compare.
        try {
            return LocalDateTime.parse(text.replace("Z", "").replace(" ", "T"));
        } catch (RuntimeException ignored) {
            // fall through to date-only parsing
        }
        try {
            return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static LocalDateTime toComparableDate(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof LocalDate date) {
            return date.atStartOfDay();
        }
        return null;
    }

    // ── Comparison ───────────────────────────────────────────────────────────

    /**
     * Total ordering over values. Strings compare case-insensitively (so sorting a title
     * column does not put every lowercase entry after every uppercase one) and lists
     * compare element-wise.
     */
    static int compare(Object left, Object right) {
        if (left == null && right == null) {
            return 0;
        }
        int leftRank = typeRank(left);
        int rightRank = typeRank(right);

        LocalDateTime leftDate = toComparableDate(left);
        LocalDateTime rightDate = toComparableDate(right);
        if (leftDate != null && rightDate != null) {
            return leftDate.compareTo(rightDate);
        }

        if (left instanceof Number || right instanceof Number) {
            Double leftNumber = asNumber(left);
            Double rightNumber = asNumber(right);
            if (leftNumber != null && rightNumber != null) {
                return Double.compare(leftNumber, rightNumber);
            }
        }

        if (leftRank != rightRank) {
            return Integer.compare(leftRank, rightRank);
        }

        if (left instanceof Boolean leftBool && right instanceof Boolean rightBool) {
            return Boolean.compare(leftBool, rightBool);
        }

        if (left instanceof List<?> leftList && right instanceof List<?> rightList) {
            int shared = Math.min(leftList.size(), rightList.size());
            for (int i = 0; i < shared; i++) {
                int element = compare(leftList.get(i), rightList.get(i));
                if (element != 0) {
                    return element;
                }
            }
            return Integer.compare(leftList.size(), rightList.size());
        }

        return toDisplayString(left).compareToIgnoreCase(toDisplayString(right));
    }

    /** Equality follows {@link #compare}, so {@code 1 = "1"} and {@code "A" = "a"} hold. */
    static boolean equal(Object left, Object right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return compare(left, right) == 0;
    }

    /**
     * {@code contains(haystack, needle)}: substring for text, membership for lists,
     * key lookup for objects.
     */
    static boolean contains(Object haystack, Object needle) {
        if (haystack == null) {
            return false;
        }
        if (haystack instanceof List<?> list) {
            for (Object element : list) {
                if (equal(element, needle)) {
                    return true;
                }
            }
            return false;
        }
        if (haystack instanceof Map<?, ?> map) {
            for (Object key : map.keySet()) {
                if (equal(key, needle)) {
                    return true;
                }
            }
            return false;
        }
        return toDisplayString(haystack).toLowerCase()
                .contains(toDisplayString(needle).toLowerCase());
    }

    // ── Display ──────────────────────────────────────────────────────────────

    /** Plain-text rendering, used for grouping keys, sorting and non-HTML contexts. */
    static String toDisplayString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Double number) {
            return formatNumber(number);
        }
        if (value instanceof Float number) {
            return formatNumber(number.doubleValue());
        }
        if (value instanceof LocalDate date) {
            return date.format(DATE_DISPLAY);
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(dateTime.getHour() == 0 && dateTime.getMinute() == 0
                    ? DATE_DISPLAY : DATETIME_DISPLAY);
        }
        if (value instanceof List<?> list) {
            StringBuilder out = new StringBuilder();
            for (Object element : list) {
                if (out.length() > 0) {
                    out.append(", ");
                }
                out.append(toDisplayString(element));
            }
            return out.toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (out.length() > 1) {
                    out.append(", ");
                }
                out.append(entry.getKey()).append(": ").append(toDisplayString(entry.getValue()));
            }
            return out.append("}").toString();
        }
        return value.toString();
    }

    /** Drops the trailing {@code .0} so whole numbers read as {@code 3}, not {@code 3.0}. */
    private static String formatNumber(double number) {
        if (Double.isNaN(number) || Double.isInfinite(number)) {
            return String.valueOf(number);
        }
        if (number == Math.rint(number) && Math.abs(number) < 1e15) {
            return String.valueOf((long) number);
        }
        return String.valueOf(number);
    }

    /** HTML rendering: links become anchors, lists become comma-separated anchors. */
    static String toHtml(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof RawHtml raw) {
            return raw.html();
        }
        if (value instanceof Link link) {
            return link.toHtml();
        }
        if (value instanceof Task task) {
            return task.toHtmlText();
        }
        if (value instanceof Boolean bool) {
            return bool ? "✔" : "✘";
        }
        if (value instanceof List<?> list) {
            StringBuilder out = new StringBuilder();
            for (Object element : list) {
                if (out.length() > 0) {
                    out.append(", ");
                }
                out.append(toHtml(element));
            }
            return out.toString();
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> ordered = new LinkedHashMap<>(map);
            StringBuilder out = new StringBuilder();
            for (Map.Entry<Object, Object> entry : ordered.entrySet()) {
                if (out.length() > 0) {
                    out.append(", ");
                }
                out.append(Html.escape(String.valueOf(entry.getKey())))
                        .append(": ").append(toHtml(entry.getValue()));
            }
            return out.toString();
        }
        return Html.escape(toDisplayString(value));
    }
}
