package com.example.jylos.service.templates;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.jylos.util.MomentFormat;

/**
 * Templater's syntax, written as Templater writes it, without running code: each
 * {@code <% expression %>} is replaced by what it writes, and {@code <%-}/{@code -%>} take one
 * line break away before or after it, {@code <%_}/{@code _%>} all the whitespace.
 *
 * <p>An expression is Templater's functions with values: {@code tp.date} ({@code now},
 * {@code tomorrow}, {@code yesterday}, {@code weekday}), {@code tp.file} ({@code title},
 * {@code tags}, {@code content}, {@code folder}, {@code path}, {@code creation_date},
 * {@code last_modified_date}, {@code cursor}, {@code selection}, {@code exists},
 * {@code include}), {@code tp.frontmatter} and {@code tp.system} ({@code clipboard},
 * {@code prompt}, {@code suggester}); their arguments are texts, numbers, lists, {@code true},
 * {@code false}, {@code null} or other such expressions, and {@code await} is allowed before one.
 * A value is written as JavaScript writes it: {@code null}, {@code undefined}, a list joined
 * by commas. What runs code ({@code <%* %>}, {@code tp.user}, {@code tp.web}, any other
 * JavaScript) is left as it is and named in {@link Written#unsupported}. What Templater stops a
 * template for (a reference date not in its format, an include more than ten deep) stops it
 * here too ({@link Failure}).</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class TemplaterDialect {

    /** The note a template is written for. */
    public interface Target {
        /** Its name, without {@code .md}. */
        String title();

        /** Its tags, each with its {@code #}. */
        List<String> tags();

        /** Its text. */
        String content();

        /** The name of its folder ({@code ""} at the root). */
        String folderName();

        /** The path of its folder in the vault ({@code "/"} at the root). */
        String folderPath();

        /** Its path in the vault. */
        String path();

        /** Its path on disk. */
        String absolutePath();

        ZonedDateTime created();

        ZonedDateTime modified();

        /** What is selected in its editor ({@code ""} for nothing). */
        String selection();

        /** Its properties. */
        Map<String, Object> frontmatter();
    }

    /** What a template asks of the app. */
    public interface Host {
        /** The clipboard's text. */
        Optional<String> clipboard();

        /** What the user answers to {@code question}; empty when they cancel. */
        Optional<String> prompt(String question, String defaultValue, boolean multiline);

        /** The item the user picks by its text; empty when they cancel. */
        Optional<Object> suggest(List<String> texts, List<Object> items, String placeholder);

        /** Whether a note or folder is at {@code path}. */
        boolean exists(String path);

        /** The text of the note {@code link} names ({@code [[link]]}), as it is in its file. */
        Optional<String> include(String link);
    }

    /**
     * @param text        the template written
     * @param unsupported what was left as it is because it runs code, in order
     */
    public record Written(String text, List<String> unsupported) {
    }

    /** What Templater stops a template for: nothing of it is written. */
    public static final class Failure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /** Why. */
        public enum Reason {
            /** A reference date does not read in its format. */
            REFERENCE_DATE,
            /** {@code tp.date.weekday} with no weekday. */
            WEEKDAY,
            /** {@code tp.file.include} of something that is not a {@code [[link]]}. */
            INCLUDE_LINK,
            /** {@code tp.file.include} of a note that is not there. */
            INCLUDE_MISSING,
            /** Notes included more than ten deep. */
            INCLUDE_DEPTH,
            /** A question that must be answered was cancelled. */
            CANCELLED
        }

        private final Reason reason;

        Failure(Reason reason) {
            super(reason.name());
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }
    }

    /** JavaScript's {@code undefined}. */
    private static final Object UNDEFINED = new Object() {
        @Override
        public String toString() {
            return "undefined";
        }
    };
    private static final int MAX_INCLUDE_DEPTH = 10;
    private static final Pattern LINK = Pattern.compile("^\\[\\[(.*)\\]\\]$");
    private static final Pattern ISO_DURATION = Pattern.compile("^(-|\\+)?P(?:([-+]?[0-9,.]*)Y)?(?:([-+]?[0-9,.]*)M)?"
            + "(?:([-+]?[0-9,.]*)W)?(?:([-+]?[0-9,.]*)D)?(?:T(?:([-+]?[0-9,.]*)H)?(?:([-+]?[0-9,.]*)M)?"
            + "(?:([-+]?[0-9,.]*)S)?)?$");
    private static final Pattern CLOCK_DURATION = Pattern.compile("^(-|\\+)?(?:(\\d*)[. ])?(\\d+):(\\d+)(?::(\\d+)(\\.\\d*)?)?$");

    private TemplaterDialect() {
    }

    /** {@code text} with its Templater tags written for {@code target}, at {@code now}, in {@code language}. */
    public static Written render(String text, Target target, Host host, ZonedDateTime now, Locale language) {
        Run run = new Run(target, host, now, language);
        String written = run.render(text != null ? text : "", 0);
        return new Written(written, List.copyOf(run.unsupported));
    }

    /** An expression that runs code: its tag is left as it is. */
    private static final class Unsupported extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Unsupported(String what) {
            super(what, null, false, false);
        }
    }

    /** A {@code tp.…} name not yet a value: a module or a function. */
    private record Ref(String path) {
        Ref member(String name) {
            return new Ref(path + "." + name);
        }
    }

    private static final class Run {
        private final Target target;
        private final Host host;
        private final ZonedDateTime now;
        private final Locale language;
        private final List<String> unsupported = new ArrayList<>();

        Run(Target target, Host host, ZonedDateTime now, Locale language) {
            this.target = target;
            this.host = host;
            this.now = now;
            this.language = language;
        }

        String render(String text, int depth) {
            StringBuilder out = new StringBuilder();
            int at = 0;
            char trimNext = 0;
            while (true) {
                int open = text.indexOf("<%", at);
                int close = open < 0 ? -1 : text.indexOf("%>", open + 2);
                String literal = text.substring(at, close < 0 ? text.length() : open);
                out.append(trimStart(literal, trimNext));
                trimNext = 0;
                if (close < 0) {
                    return out.toString();
                }
                String tag = text.substring(open, close + 2);
                String inner = text.substring(open + 2, close);
                char left = 0;
                char right = 0;
                if (!inner.isEmpty() && (inner.charAt(0) == '-' || inner.charAt(0) == '_')) {
                    left = inner.charAt(0);
                    inner = inner.substring(1);
                }
                if (!inner.isEmpty() && (inner.charAt(inner.length() - 1) == '-' || inner.charAt(inner.length() - 1) == '_')) {
                    right = inner.charAt(inner.length() - 1);
                    inner = inner.substring(0, inner.length() - 1);
                }
                String value;
                if (inner.startsWith("*") || inner.startsWith("+")) {
                    unsupported.add("<%" + inner.charAt(0));
                    value = null;
                } else {
                    try {
                        value = jsString(evaluate(inner, depth));
                    } catch (Unsupported e) {
                        unsupported.add(e.getMessage());
                        value = null;
                    }
                }
                if (value == null) {
                    out.append(tag); // left as it is, with the whitespace around it
                } else {
                    trimEnd(out, left);
                    out.append(value);
                    trimNext = right;
                }
                at = close + 2;
            }
        }

        private static String trimStart(String text, char mode) {
            if (mode == '-') {
                return text.startsWith("\r\n") ? text.substring(2) : text.startsWith("\n") ? text.substring(1) : text;
            }
            if (mode == '_') {
                int i = 0;
                while (i < text.length() && isBlank(text.charAt(i))) {
                    i++;
                }
                return text.substring(i);
            }
            return text;
        }

        private static void trimEnd(StringBuilder out, char mode) {
            int length = out.length();
            if (mode == '-') {
                if (length >= 2 && out.charAt(length - 2) == '\r' && out.charAt(length - 1) == '\n') {
                    out.setLength(length - 2);
                } else if (length >= 1 && out.charAt(length - 1) == '\n') {
                    out.setLength(length - 1);
                }
            } else if (mode == '_') {
                while (length > 0 && isBlank(out.charAt(length - 1))) {
                    length--;
                }
                out.setLength(length);
            }
        }

        private static boolean isBlank(char c) {
            return c == ' ' || c == '\t' || c == '\n' || c == '\r';
        }

        private Object evaluate(String expression, int depth) {
            Parser parser = new Parser(expression.strip(), this, depth);
            Object value = parser.expression();
            if (!parser.atEnd()) {
                throw new Unsupported(expression.strip());
            }
            return resolve(value, expression.strip());
        }

        /** A name that is a value ({@code tp.file.title}) as that value. */
        private Object resolve(Object value, String expression) {
            if (!(value instanceof Ref ref)) {
                return value;
            }
            return switch (ref.path()) {
                case "tp.file.title" -> target.title();
                case "tp.file.tags" -> target.tags();
                case "tp.file.content" -> target.content();
                case "tp.date", "tp.file", "tp.frontmatter", "tp.system" -> Map.of(); // "[object Object]"
                default -> throw new Unsupported(ref.path());
            };
        }

        /** {@code value.name}. */
        Object member(Object value, String name, String expression) {
            if (value instanceof Ref ref) {
                if ("tp.frontmatter".equals(ref.path())) {
                    Map<String, Object> properties = target.frontmatter();
                    return properties.containsKey(name) ? properties.get(name) : UNDEFINED;
                }
                Ref next = ref.member(name);
                return switch (next.path()) {
                    case "tp.file.title", "tp.file.tags", "tp.file.content" -> resolve(next, expression);
                    default -> next;
                };
            }
            if (value instanceof Map<?, ?> map) {
                return map.containsKey(name) ? map.get(name) : UNDEFINED;
            }
            if ("length".equals(name) && value instanceof List<?> list) {
                return list.size();
            }
            if ("length".equals(name) && value instanceof String text) {
                return text.length();
            }
            throw new Unsupported(expression);
        }

        /** {@code value(arguments)}. */
        Object call(Object value, List<Object> args, int depth, String expression) {
            if (!(value instanceof Ref ref)) {
                throw new Unsupported(expression);
            }
            return switch (ref.path()) {
                case "tp.date.now" -> dateNow(args);
                case "tp.date.tomorrow" -> format(now.plusDays(1), format(arg(args, 0), "YYYY-MM-DD"));
                case "tp.date.yesterday" -> format(now.minusDays(1), format(arg(args, 0), "YYYY-MM-DD"));
                case "tp.date.weekday" -> weekday(args);
                case "tp.file.creation_date" -> format(target.created(), format(arg(args, 0), "YYYY-MM-DD HH:mm"));
                case "tp.file.last_modified_date" -> format(target.modified(), format(arg(args, 0), "YYYY-MM-DD HH:mm"));
                case "tp.file.cursor" -> {
                    Object order = arg(args, 0);
                    yield "<% tp.file.cursor(" + (order == null || order == UNDEFINED ? "" : jsString(order)) + ") %>";
                }
                case "tp.file.folder" -> truthy(arg(args, 0)) ? target.folderPath() : target.folderName();
                case "tp.file.path" -> truthy(arg(args, 0)) ? target.path() : target.absolutePath();
                case "tp.file.selection" -> target.selection();
                case "tp.file.exists" -> host.exists(normalize(jsString(arg(args, 0))));
                case "tp.file.include" -> include(arg(args, 0), depth);
                case "tp.system.clipboard" -> host.clipboard().orElse("");
                case "tp.system.prompt" -> {
                    Object answer = host.prompt(jsString(arg(args, 0)), arg(args, 1) == UNDEFINED || arg(args, 1) == null
                            ? null : jsString(arg(args, 1)), truthy(arg(args, 3))).orElse(null);
                    if (answer == null && truthy(arg(args, 2))) {
                        throw new Failure(Failure.Reason.CANCELLED);
                    }
                    yield answer;
                }
                case "tp.system.suggester" -> {
                    if (!(arg(args, 0) instanceof List<?> texts) || !(arg(args, 1) instanceof List<?> items)) {
                        throw new Unsupported(ref.path());
                    }
                    List<String> labels = texts.stream().map(TemplaterDialect::jsString).toList();
                    Object placeholder = arg(args, 3);
                    Object picked = host.suggest(labels, new ArrayList<>(items),
                            placeholder == UNDEFINED || placeholder == null ? "" : jsString(placeholder)).orElse(null);
                    if (picked == null && truthy(arg(args, 2))) {
                        throw new Failure(Failure.Reason.CANCELLED);
                    }
                    yield picked;
                }
                default -> throw new Unsupported(ref.path());
            };
        }

        private String include(Object argument, int depth) {
            Matcher link = LINK.matcher(argument instanceof String text ? text : "");
            if (!link.matches()) {
                throw new Failure(Failure.Reason.INCLUDE_LINK);
            }
            if (link.group(1).contains("#")) {
                throw new Unsupported("tp.file.include");
            }
            if (depth + 1 > MAX_INCLUDE_DEPTH) {
                throw new Failure(Failure.Reason.INCLUDE_DEPTH);
            }
            String text = host.include(link.group(1)).orElseThrow(() -> new Failure(Failure.Reason.INCLUDE_MISSING));
            return render(text, depth + 1);
        }

        private String dateNow(List<Object> args) {
            ZonedDateTime base = reference(arg(args, 2), arg(args, 3));
            Object offset = arg(args, 1);
            if (offset instanceof Number days) {
                base = add(base, 0, days.doubleValue(), 0);
            } else if (offset instanceof String duration) {
                base = addDuration(base, duration);
            }
            return format(base, format(arg(args, 0), "YYYY-MM-DD"));
        }

        private String weekday(List<Object> args) {
            ZonedDateTime base = reference(arg(args, 2), arg(args, 3));
            if (!(arg(args, 1) instanceof Number day)) {
                throw new Failure(Failure.Reason.WEEKDAY);
            }
            int dow = MomentFormat.firstDayOfWeek(language);
            int current = (base.getDayOfWeek().getValue() % 7 + 7 - dow) % 7;
            return format(base.plusDays(Math.round(day.doubleValue()) - current), format(arg(args, 0), "YYYY-MM-DD"));
        }

        /** Templater's {@code moment(reference, format)}: now with no reference. */
        private ZonedDateTime reference(Object reference, Object format) {
            if (!truthy(reference)) {
                return now;
            }
            String pattern = format == UNDEFINED || format == null ? null : jsString(format);
            return MomentFormat.parse(jsString(reference), pattern, language, now)
                    .orElseThrow(() -> new Failure(Failure.Reason.REFERENCE_DATE));
        }

        private String format(ZonedDateTime when, String format) {
            return MomentFormat.format(when, format, language);
        }

        /** The format argument: its default when not given, moment's own when {@code null}. */
        private static String format(Object argument, String fallback) {
            if (argument == UNDEFINED) {
                return fallback;
            }
            return argument == null ? null : jsString(argument);
        }

        /** moment's {@code add(duration)}: the time first, then the days, then the months, these rounded. */
        private static ZonedDateTime add(ZonedDateTime base, double months, double days, double millis) {
            return base.plusNanos(Math.round(millis * 1_000_000)).plusDays(absRound(days)).plusMonths(absRound(months));
        }

        /** {@code moment.duration(text)} added to {@code base}: ISO 8601 ({@code P1D}) or {@code d.HH:mm:ss}; else nothing. */
        private static ZonedDateTime addDuration(ZonedDateTime base, String text) {
            Matcher iso = ISO_DURATION.matcher(text);
            if (iso.matches()) {
                double sign = "-".equals(iso.group(1)) ? -1 : 1;
                double months = sign * (iso(iso.group(2)) * 12 + iso(iso.group(3)));
                double days = sign * (iso(iso.group(4)) * 7 + iso(iso.group(5)));
                double millis = sign * (iso(iso.group(6)) * 3_600_000 + iso(iso.group(7)) * 60_000 + iso(iso.group(8)) * 1000);
                return add(base, months, days, millis);
            }
            Matcher clock = CLOCK_DURATION.matcher(text);
            if (clock.matches()) {
                double sign = "-".equals(clock.group(1)) ? -1 : 1;
                double days = clock.group(2) == null || clock.group(2).isEmpty() ? 0 : Double.parseDouble(clock.group(2));
                double millis = Double.parseDouble(clock.group(3)) * 3_600_000 + Double.parseDouble(clock.group(4)) * 60_000
                        + (clock.group(5) == null ? 0 : Double.parseDouble(clock.group(5)) * 1000)
                        + (clock.group(6) == null ? 0 : Math.round(Double.parseDouble("0" + clock.group(6)) * 1000));
                return add(base, 0, sign * days, sign * millis);
            }
            return base;
        }

        private static double iso(String part) {
            if (part == null || part.isEmpty()) {
                return 0;
            }
            try {
                return Double.parseDouble(part.replace(',', '.'));
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        private static long absRound(double value) {
            return value < 0 ? -Math.round(-value) : Math.round(value);
        }

        private static Object arg(List<Object> args, int index) {
            return index < args.size() ? args.get(index) : UNDEFINED;
        }
    }

    /** A JavaScript expression of values and {@code tp} names, evaluated as it is read. */
    private static final class Parser {
        private final String text;
        private final Run run;
        private final int depth;
        private int at;

        Parser(String text, Run run, int depth) {
            this.text = text;
            this.run = run;
            this.depth = depth;
        }

        boolean atEnd() {
            skipBlank();
            return at >= text.length();
        }

        Object expression() {
            skipBlank();
            if (text.startsWith("await", at) && (at + 5 >= text.length() || !isNamePart(text.charAt(at + 5)))) {
                at += 5;
            }
            Object value = primary();
            while (true) {
                skipBlank();
                if (at >= text.length()) {
                    return value;
                }
                char c = text.charAt(at);
                if (c == '.') {
                    at++;
                    skipBlank();
                    value = run.member(value, name(), text);
                } else if (c == '[') {
                    at++;
                    Object key = run.resolve(expression(), text);
                    expect(']');
                    value = key instanceof Number index && value instanceof List<?> list
                            ? element(list, index.intValue()) : run.member(value, jsString(key), text);
                } else if (c == '(') {
                    at++;
                    value = run.call(value, arguments(), depth, text);
                } else {
                    return value;
                }
            }
        }

        private static Object element(List<?> list, int index) {
            return index >= 0 && index < list.size() ? list.get(index) : UNDEFINED;
        }

        private List<Object> arguments() {
            List<Object> args = new ArrayList<>();
            skipBlank();
            if (peek() == ')') {
                at++;
                return args;
            }
            while (true) {
                args.add(run.resolve(expression(), text));
                skipBlank();
                char c = peek();
                at++;
                if (c == ')') {
                    return args;
                }
                if (c != ',') {
                    throw new Unsupported(text);
                }
            }
        }

        private Object primary() {
            skipBlank();
            char c = peek();
            if (c == '\'' || c == '"' || c == '`') {
                return string(c);
            }
            if (Character.isDigit(c) || c == '-' && at + 1 < text.length() && Character.isDigit(text.charAt(at + 1))) {
                return number();
            }
            if (c == '[') {
                at++;
                List<Object> items = new ArrayList<>();
                skipBlank();
                if (peek() == ']') {
                    at++;
                    return items;
                }
                while (true) {
                    items.add(run.resolve(expression(), text));
                    skipBlank();
                    char next = peek();
                    at++;
                    if (next == ']') {
                        return items;
                    }
                    if (next != ',') {
                        throw new Unsupported(text);
                    }
                }
            }
            if (isNameStart(c)) {
                String name = name();
                return switch (name) {
                    case "true" -> Boolean.TRUE;
                    case "false" -> Boolean.FALSE;
                    case "null" -> null;
                    case "undefined" -> UNDEFINED;
                    case "tp" -> new Ref("tp");
                    default -> throw new Unsupported(text);
                };
            }
            throw new Unsupported(text);
        }

        private String string(char quote) {
            StringBuilder out = new StringBuilder();
            at++;
            while (at < text.length()) {
                char c = text.charAt(at++);
                if (c == quote) {
                    return out.toString();
                }
                if (quote == '`' && c == '$' && peek() == '{') {
                    throw new Unsupported(text);
                }
                if (c == '\\' && at < text.length()) {
                    char e = text.charAt(at++);
                    out.append(switch (e) {
                        case 'n' -> '\n';
                        case 't' -> '\t';
                        case 'r' -> '\r';
                        default -> e;
                    });
                } else {
                    out.append(c);
                }
            }
            throw new Unsupported(text);
        }

        private Double number() {
            int start = at;
            if (peek() == '-') {
                at++;
            }
            while (at < text.length() && (Character.isDigit(text.charAt(at)) || text.charAt(at) == '.')) {
                at++;
            }
            return Double.parseDouble(text.substring(start, at));
        }

        private String name() {
            int start = at;
            if (at >= text.length() || !isNameStart(text.charAt(at))) {
                throw new Unsupported(text);
            }
            while (at < text.length() && isNamePart(text.charAt(at))) {
                at++;
            }
            return text.substring(start, at);
        }

        private void expect(char c) {
            skipBlank();
            if (peek() != c) {
                throw new Unsupported(text);
            }
            at++;
        }

        private char peek() {
            return at < text.length() ? text.charAt(at) : 0;
        }

        private void skipBlank() {
            while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
                at++;
            }
        }

        private static boolean isNameStart(char c) {
            return Character.isLetter(c) || c == '_' || c == '$';
        }

        private static boolean isNamePart(char c) {
            return Character.isLetterOrDigit(c) || c == '_' || c == '$';
        }
    }

    /** {@code value} as JavaScript writes it in a text. */
    static String jsString(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Double number) {
            double d = number;
            if (Double.isFinite(d) && d == Math.rint(d) && Math.abs(d) < 1e21) {
                return Long.toString((long) d);
            }
            return Double.toString(d);
        }
        if (value instanceof List<?> list) {
            List<String> items = new ArrayList<>();
            for (Object item : list) {
                items.add(item == null || item == UNDEFINED ? "" : jsString(item));
            }
            return String.join(",", items);
        }
        if (value instanceof Map<?, ?>) {
            return "[object Object]";
        }
        return String.valueOf(value);
    }

    private static boolean truthy(Object value) {
        if (value == null || value == UNDEFINED) {
            return false;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Number number) {
            double d = number.doubleValue();
            return d != 0 && !Double.isNaN(d);
        }
        if (value instanceof String text) {
            return !text.isEmpty();
        }
        return true;
    }

    private static String normalize(String path) {
        String clean = path.strip().replace('\\', '/').replaceAll("/{2,}", "/");
        while (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        return clean;
    }
}
