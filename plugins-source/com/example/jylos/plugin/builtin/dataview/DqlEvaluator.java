package com.example.jylos.plugin.builtin.dataview;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Evaluates parsed expressions against a result row.
 *
 * <h2>Dates and durations</h2>
 * <p>Durations are plain day counts rather than a dedicated type: {@code date(today) - 7}
 * and {@code date(today) - dur("1 week")} both mean "seven days ago", and subtracting two
 * dates yields the number of days between them. A full duration type would buy little
 * here — every practical vault query works in days — while adding a second numeric
 * hierarchy to every arithmetic and comparison path.</p>
 */
final class DqlEvaluator {

    /** A page under evaluation, plus any names bound by FLATTEN or GROUP BY. */
    record Row(Page page, Map<String, Object> bindings) {

        static Row of(Page page) {
            return new Row(page, Map.of());
        }

        Row bind(String name, Object value) {
            Map<String, Object> merged = new LinkedHashMap<>(bindings);
            merged.put(Page.normalizeKey(name), value);
            return new Row(page, merged);
        }
    }

    private final PageSource index;
    private final Page currentPage;

    DqlEvaluator(PageSource index, Page currentPage) {
        this.index = index;
        this.currentPage = currentPage;
    }

    Object evaluate(Ast.Expr expression, Row row) {
        if (expression instanceof Ast.Literal literal) {
            return literal.value();
        }
        if (expression instanceof Ast.Variable variable) {
            return resolveVariable(variable.name(), row);
        }
        if (expression instanceof Ast.FieldAccess field) {
            return accessField(evaluate(field.target(), row), field.name());
        }
        if (expression instanceof Ast.IndexAccess indexAccess) {
            return accessIndex(evaluate(indexAccess.target(), row), evaluate(indexAccess.index(), row));
        }
        if (expression instanceof Ast.ListLiteral list) {
            List<Object> values = new ArrayList<>(list.elements().size());
            for (Ast.Expr element : list.elements()) {
                values.add(evaluate(element, row));
            }
            return values;
        }
        if (expression instanceof Ast.ObjectLiteral object) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<String, Ast.Expr> entry : object.entries().entrySet()) {
                values.put(entry.getKey(), evaluate(entry.getValue(), row));
            }
            return values;
        }
        if (expression instanceof Ast.Unary unary) {
            return applyUnary(unary.operator(), evaluate(unary.operand(), row));
        }
        if (expression instanceof Ast.Binary binary) {
            return applyBinary(binary, row);
        }
        if (expression instanceof Ast.Call call) {
            List<Object> arguments = new ArrayList<>(call.arguments().size());
            for (Ast.Expr argument : call.arguments()) {
                arguments.add(evaluate(argument, row));
            }
            return callFunction(call.name(), arguments);
        }
        throw new DqlException("Cannot evaluate expression");
    }

    // ── Names ────────────────────────────────────────────────────────────────

    private Object resolveVariable(String name, Row row) {
        String key = Page.normalizeKey(name);
        if (row.bindings().containsKey(key)) {
            return row.bindings().get(key);
        }
        if ("this".equals(key)) {
            return currentPage == null ? null : pageObject(currentPage);
        }
        switch (key) {
            case "today" -> {
                return LocalDate.now();
            }
            case "now" -> {
                return LocalDateTime.now();
            }
            case "tomorrow" -> {
                return LocalDate.now().plusDays(1);
            }
            case "yesterday" -> {
                return LocalDate.now().minusDays(1);
            }
            default -> {
                // fall through to page lookup
            }
        }
        if (row.page() != null) {
            return row.page().resolve(name);
        }
        return null;
    }

    /** Exposes a page as a plain map so {@code this.file.name} and {@code rows.x} work. */
    static Map<String, Object> pageObject(Page page) {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("file", page.fileObject());
        object.putAll(page.userFields());
        return object;
    }

    private Object accessField(Object target, String name) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            String key = Page.normalizeKey(name);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (Page.normalizeKey(String.valueOf(entry.getKey())).equals(key)) {
                    return entry.getValue();
                }
            }
            return null;
        }
        // Field access distributes over lists, which is what makes GROUP BY usable:
        // `rows.file.link` must yield one link per grouped page, not null.
        if (target instanceof List<?> list) {
            List<Object> mapped = new ArrayList<>(list.size());
            for (Object element : list) {
                mapped.add(accessField(element, name));
            }
            return mapped;
        }
        if (target instanceof Page page) {
            return accessField(pageObject(page), name);
        }
        if (target instanceof Task task) {
            return accessTaskField(task, Page.normalizeKey(name));
        }
        if (target instanceof Link link) {
            Page linked = index == null ? null : index.pageByTitle(link.target());
            return linked == null ? null : accessField(pageObject(linked), name);
        }
        return null;
    }

    private static Object accessTaskField(Task task, String key) {
        return switch (key) {
            case "text" -> task.text();
            case "completed", "checked" -> task.completed();
            case "status" -> task.status();
            case "line" -> (double) task.line();
            case "page", "path" -> task.pageTitle();
            case "link" -> task.pageLink();
            default -> task.fields().get(key);
        };
    }

    private static Object accessIndex(Object target, Object index) {
        if (target == null) {
            return null;
        }
        if (target instanceof List<?> list) {
            Double position = DqlValue.asNumber(index);
            if (position == null) {
                return null;
            }
            int at = position.intValue();
            if (at < 0) {
                at += list.size();
            }
            return at >= 0 && at < list.size() ? list.get(at) : null;
        }
        if (target instanceof Map<?, ?> map) {
            String key = Page.normalizeKey(DqlValue.toDisplayString(index));
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (Page.normalizeKey(String.valueOf(entry.getKey())).equals(key)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    // ── Operators ────────────────────────────────────────────────────────────

    private Object applyUnary(String operator, Object value) {
        if ("!".equals(operator)) {
            return !DqlValue.truthy(value);
        }
        Double number = DqlValue.asNumber(value);
        return number == null ? null : -number;
    }

    private Object applyBinary(Ast.Binary binary, Row row) {
        String operator = binary.operator();

        // Short-circuit: `x and y` must not evaluate y when x is already false, so a
        // guard like `field and field.length > 0` stays safe on pages without the field.
        if ("and".equals(operator)) {
            Object left = evaluate(binary.left(), row);
            return DqlValue.truthy(left) && DqlValue.truthy(evaluate(binary.right(), row));
        }
        if ("or".equals(operator)) {
            Object left = evaluate(binary.left(), row);
            return DqlValue.truthy(left) || DqlValue.truthy(evaluate(binary.right(), row));
        }

        Object left = evaluate(binary.left(), row);
        Object right = evaluate(binary.right(), row);

        return switch (operator) {
            case "=" -> DqlValue.equal(left, right);
            case "!=" -> !DqlValue.equal(left, right);
            case ">" -> DqlValue.compare(left, right) > 0;
            case ">=" -> DqlValue.compare(left, right) >= 0;
            case "<" -> DqlValue.compare(left, right) < 0;
            case "<=" -> DqlValue.compare(left, right) <= 0;
            case "+" -> add(left, right);
            case "-" -> subtract(left, right);
            case "*" -> arithmetic(left, right, (a, b) -> a * b);
            case "/" -> divide(left, right);
            case "%" -> arithmetic(left, right, (a, b) -> b == 0 ? Double.NaN : a % b);
            default -> throw new DqlException("Unknown operator '" + operator + "'");
        };
    }

    private interface Arithmetic {
        double apply(double left, double right);
    }

    private static Object arithmetic(Object left, Object right, Arithmetic operation) {
        Double a = DqlValue.asNumber(left);
        Double b = DqlValue.asNumber(right);
        if (a == null || b == null) {
            return null;
        }
        return operation.apply(a, b);
    }

    private static Object divide(Object left, Object right) {
        Double b = DqlValue.asNumber(right);
        if (b != null && b == 0) {
            return null;
        }
        return arithmetic(left, right, (x, y) -> x / y);
    }

    private static Object add(Object left, Object right) {
        if (left instanceof List<?> || right instanceof List<?>) {
            List<Object> combined = new ArrayList<>(DqlValue.asList(left));
            combined.addAll(DqlValue.asList(right));
            return combined;
        }
        Object shifted = shiftDate(left, right, 1);
        if (shifted != null) {
            return shifted;
        }
        Double a = DqlValue.asNumber(left);
        Double b = DqlValue.asNumber(right);
        if (a != null && b != null && !(left instanceof CharSequence) && !(right instanceof CharSequence)) {
            return a + b;
        }
        return DqlValue.toDisplayString(left) + DqlValue.toDisplayString(right);
    }

    private static Object subtract(Object left, Object right) {
        // Two dates yield the gap between them, in days.
        if (isDate(left) && isDate(right)) {
            LocalDateTime a = toDateTime(left);
            LocalDateTime b = toDateTime(right);
            return (double) ChronoUnit.DAYS.between(b, a);
        }
        Object shifted = shiftDate(left, right, -1);
        if (shifted != null) {
            return shifted;
        }
        return arithmetic(left, right, (a, b) -> a - b);
    }

    /** Adds or subtracts a day count from a date, preserving date vs date-time type. */
    private static Object shiftDate(Object left, Object right, int sign) {
        if (!isDate(left) || isDate(right)) {
            return null;
        }
        Double days = DqlValue.asNumber(right);
        if (days == null) {
            return null;
        }
        long amount = sign * days.longValue();
        if (left instanceof LocalDate date) {
            return date.plusDays(amount);
        }
        return ((LocalDateTime) left).plusDays(amount);
    }

    private static boolean isDate(Object value) {
        return value instanceof LocalDate || value instanceof LocalDateTime;
    }

    private static LocalDateTime toDateTime(Object value) {
        return value instanceof LocalDate date ? date.atStartOfDay() : (LocalDateTime) value;
    }

    // ── Functions ────────────────────────────────────────────────────────────

    private Object callFunction(String name, List<Object> arguments) {
        Object first = arguments.isEmpty() ? null : arguments.get(0);
        Object second = arguments.size() > 1 ? arguments.get(1) : null;
        Object third = arguments.size() > 2 ? arguments.get(2) : null;

        return switch (name) {
            case "length" -> lengthOf(first);
            case "contains" -> DqlValue.contains(first, second);
            case "icontains" -> DqlValue.toDisplayString(first).toLowerCase(Locale.ROOT)
                    .contains(DqlValue.toDisplayString(second).toLowerCase(Locale.ROOT));
            case "econtains" -> exactContains(first, second);
            case "typeof" -> DqlValue.typeName(first);

            case "lower" -> DqlValue.toDisplayString(first).toLowerCase(Locale.ROOT);
            case "upper" -> DqlValue.toDisplayString(first).toUpperCase(Locale.ROOT);
            case "replace" -> DqlValue.toDisplayString(first)
                    .replace(DqlValue.toDisplayString(second), DqlValue.toDisplayString(third));
            case "split" -> splitText(first, second);
            case "join" -> joinList(first, second);
            case "truncate" -> truncate(first, second, third);
            case "startswith" -> DqlValue.toDisplayString(first)
                    .regionMatches(true, 0, DqlValue.toDisplayString(second), 0,
                            DqlValue.toDisplayString(second).length());
            case "endswith" -> DqlValue.toDisplayString(first).toLowerCase(Locale.ROOT)
                    .endsWith(DqlValue.toDisplayString(second).toLowerCase(Locale.ROOT));
            case "regexmatch", "regextest" -> regexTest(first, second, "regexmatch".equals(name));
            case "regexreplace" -> regexReplace(first, second, third);

            case "number" -> DqlValue.asNumber(first);
            case "string" -> DqlValue.toDisplayString(first);
            case "round" -> round(first, second);
            case "floor" -> applyMath(first, Math::floor);
            case "ceil" -> applyMath(first, Math::ceil);
            case "abs" -> applyMath(first, Math::abs);
            case "min" -> extremum(arguments, true);
            case "max" -> extremum(arguments, false);
            case "sum" -> sum(arguments);
            case "average" -> average(arguments);

            case "date" -> DqlValue.asDate(first);
            case "dateformat" -> formatDate(first, second);
            case "striptime" -> stripTime(first);
            case "dur" -> durationInDays(first);

            case "default", "ifnull" -> first == null ? second : first;
            case "nonnull" -> nonNull(first);
            case "choice" -> DqlValue.truthy(first) ? second : third;

            case "link" -> new Link(DqlValue.toDisplayString(first),
                    second == null ? null : DqlValue.toDisplayString(second));
            case "elink" -> externalLink(first, second);

            case "sort" -> sortList(first);
            case "reverse" -> reverseList(first);
            case "unique" -> uniqueList(first);
            case "flat" -> flatten(first);
            case "first" -> firstOf(first);
            case "last" -> lastOf(first);
            case "any" -> anyTruthy(first);
            case "all" -> allTruthy(first);
            case "none" -> !anyTruthy(first);

            default -> throw new DqlException("Unknown function '" + name + "()'");
        };
    }

    private static Object lengthOf(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof List<?> list) {
            return (double) list.size();
        }
        if (value instanceof Map<?, ?> map) {
            return (double) map.size();
        }
        return (double) DqlValue.toDisplayString(value).length();
    }

    private static boolean exactContains(Object haystack, Object needle) {
        for (Object element : DqlValue.asList(haystack)) {
            if (DqlValue.toDisplayString(element).equals(DqlValue.toDisplayString(needle))) {
                return true;
            }
        }
        return false;
    }

    private static Object splitText(Object value, Object separator) {
        String text = DqlValue.toDisplayString(value);
        String delimiter = separator == null ? "\\s+" : Pattern.quote(DqlValue.toDisplayString(separator));
        List<Object> parts = new ArrayList<>();
        for (String part : text.split(delimiter)) {
            parts.add(part);
        }
        return parts;
    }

    private static Object joinList(Object value, Object separator) {
        String delimiter = separator == null ? ", " : DqlValue.toDisplayString(separator);
        StringBuilder out = new StringBuilder();
        for (Object element : DqlValue.asList(value)) {
            if (out.length() > 0) {
                out.append(delimiter);
            }
            out.append(DqlValue.toDisplayString(element));
        }
        return out.toString();
    }

    private static Object truncate(Object value, Object length, Object suffix) {
        String text = DqlValue.toDisplayString(value);
        Double limit = DqlValue.asNumber(length);
        if (limit == null || text.length() <= limit) {
            return text;
        }
        String ellipsis = suffix == null ? "…" : DqlValue.toDisplayString(suffix);
        int cut = Math.max(0, limit.intValue() - ellipsis.length());
        return text.substring(0, Math.min(cut, text.length())) + ellipsis;
    }

    private static Object regexTest(Object value, Object pattern, boolean fullMatch) {
        try {
            Pattern compiled = Pattern.compile(DqlValue.toDisplayString(pattern));
            String text = DqlValue.toDisplayString(value);
            return fullMatch ? compiled.matcher(text).matches() : compiled.matcher(text).find();
        } catch (PatternSyntaxException e) {
            throw new DqlException("Invalid regular expression: " + e.getDescription());
        }
    }

    private static Object regexReplace(Object value, Object pattern, Object replacement) {
        try {
            return Pattern.compile(DqlValue.toDisplayString(pattern))
                    .matcher(DqlValue.toDisplayString(value))
                    .replaceAll(DqlValue.toDisplayString(replacement));
        } catch (PatternSyntaxException e) {
            throw new DqlException("Invalid regular expression: " + e.getDescription());
        }
    }

    private interface MathOperation {
        double apply(double value);
    }

    private static Object applyMath(Object value, MathOperation operation) {
        Double number = DqlValue.asNumber(value);
        return number == null ? null : operation.apply(number);
    }

    private static Object round(Object value, Object digits) {
        Double number = DqlValue.asNumber(value);
        if (number == null) {
            return null;
        }
        int places = digits == null ? 0 : (int) Math.max(0, DqlValue.asNumber(digits) == null
                ? 0 : DqlValue.asNumber(digits));
        double factor = Math.pow(10, places);
        return Math.round(number * factor) / factor;
    }

    /** Accepts either a single list argument or several scalar arguments. */
    private static List<Object> spread(List<Object> arguments) {
        if (arguments.size() == 1) {
            return DqlValue.asList(arguments.get(0));
        }
        return arguments;
    }

    private static Object extremum(List<Object> arguments, boolean smallest) {
        Object best = null;
        for (Object candidate : spread(arguments)) {
            if (candidate == null) {
                continue;
            }
            if (best == null || (smallest
                    ? DqlValue.compare(candidate, best) < 0
                    : DqlValue.compare(candidate, best) > 0)) {
                best = candidate;
            }
        }
        return best;
    }

    private static Object sum(List<Object> arguments) {
        double total = 0;
        boolean any = false;
        for (Object element : spread(arguments)) {
            Double number = DqlValue.asNumber(element);
            if (number != null) {
                total += number;
                any = true;
            }
        }
        return any ? total : 0.0;
    }

    private static Object average(List<Object> arguments) {
        double total = 0;
        int count = 0;
        for (Object element : spread(arguments)) {
            Double number = DqlValue.asNumber(element);
            if (number != null) {
                total += number;
                count++;
            }
        }
        return count == 0 ? null : total / count;
    }

    private static Object formatDate(Object value, Object pattern) {
        Object date = DqlValue.asDate(value);
        if (date == null) {
            return "";
        }
        String format = pattern == null ? "yyyy-MM-dd" : DqlValue.toDisplayString(pattern);
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            return date instanceof LocalDate localDate
                    ? localDate.format(formatter)
                    : ((LocalDateTime) date).format(formatter);
        } catch (RuntimeException e) {
            throw new DqlException("Invalid date format '" + format + "'");
        }
    }

    private static Object stripTime(Object value) {
        Object date = DqlValue.asDate(value);
        if (date instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        return date;
    }

    /** Parses "3 days", "2 weeks", "1 month" and bare numbers into a day count. */
    private static Object durationInDays(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = DqlValue.toDisplayString(value).trim().toLowerCase(Locale.ROOT);
        java.util.regex.Matcher matcher =
                Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([a-z]*)").matcher(text);
        double days = 0;
        boolean matched = false;
        while (matcher.find()) {
            matched = true;
            double amount = Double.parseDouble(matcher.group(1));
            String unit = matcher.group(2);
            days += amount * switch (unit) {
                case "week", "weeks", "w" -> 7;
                case "month", "months", "mo" -> 30;
                case "year", "years", "y" -> 365;
                case "hour", "hours", "h" -> 1.0 / 24;
                case "minute", "minutes", "m" -> 1.0 / 1440;
                default -> 1;
            };
        }
        return matched ? days : null;
    }

    private static Object nonNull(Object value) {
        List<Object> result = new ArrayList<>();
        for (Object element : DqlValue.asList(value)) {
            if (element != null) {
                result.add(element);
            }
        }
        return result;
    }

    private static Object externalLink(Object url, Object display) {
        String href = DqlValue.toDisplayString(url);
        String label = display == null ? href : DqlValue.toDisplayString(display);
        return new RawHtml("<a href=\"" + Html.escape(href) + "\">" + Html.escape(label) + "</a>");
    }

    private static Object sortList(Object value) {
        List<Object> sorted = new ArrayList<>(DqlValue.asList(value));
        sorted.sort(DqlValue::compare);
        return sorted;
    }

    private static Object reverseList(Object value) {
        List<Object> reversed = new ArrayList<>(DqlValue.asList(value));
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    private static Object uniqueList(Object value) {
        return new ArrayList<Object>(new LinkedHashSet<>(DqlValue.asList(value)));
    }

    private static Object flatten(Object value) {
        List<Object> flat = new ArrayList<>();
        for (Object element : DqlValue.asList(value)) {
            flat.addAll(DqlValue.asList(element));
        }
        return flat;
    }

    private static Object firstOf(Object value) {
        List<Object> list = DqlValue.asList(value);
        return list.isEmpty() ? null : list.get(0);
    }

    private static Object lastOf(Object value) {
        List<Object> list = DqlValue.asList(value);
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    private static boolean anyTruthy(Object value) {
        for (Object element : DqlValue.asList(value)) {
            if (DqlValue.truthy(element)) {
                return true;
            }
        }
        return false;
    }

    private static boolean allTruthy(Object value) {
        for (Object element : DqlValue.asList(value)) {
            if (!DqlValue.truthy(element)) {
                return false;
            }
        }
        return true;
    }
}
