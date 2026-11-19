package com.example.jylos.service.templates;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.jylos.util.MomentFormat;

/**
 * Writes a template as Obsidian's Templates writes it: first each variable,
 * {@code {{title}}} and the others, in any case; then {@code {{date}}} and {@code {{time}}}, in
 * the vault's formats, or {@code {{date:FORMAT}}} and {@code {{time:FORMAT}}} in their own
 * (moment's symbols, {@link MomentFormat}), all at the same instant. What it does not know is
 * left as it is. A value is read as JavaScript reads the text it puts in, as Obsidian does:
 * {@code $$} is one dollar, {@code $&} the text it replaces.
 *
 * <p>Jylos also writes {@code {{datetime}}} ({@link #variables}): the date and time,
 * {@code YYYY-MM-DDTHH:mm:ss}.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class TemplateEngine {

    /** The format of Jylos' {@code {{datetime}}}. */
    public static final String DATETIME_FORMAT = "YYYY-MM-DDTHH:mm:ss";

    private static final Pattern DATE_OR_TIME = Pattern.compile("\\{\\{(date|time)(?::(.*?))?\\}\\}",
            Pattern.CASE_INSENSITIVE);

    private TemplateEngine() {
    }

    /** The variables of a note called {@code title}: {@code {{title}}} and Jylos' {@code {{datetime}}}. */
    public static Map<String, String> variables(String title, ZonedDateTime now) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("title", title != null ? title : "");
        variables.put("datetime", MomentFormat.format(now, DATETIME_FORMAT, Locale.ROOT));
        return variables;
    }

    /**
     * {@code text} with its variables, dates and times written.
     *
     * @param variables  each {@code {{key}}} and what it writes
     * @param dateFormat what {@code {{date}}} writes
     * @param timeFormat what {@code {{time}}} writes
     * @param now        the instant of every date and time
     * @param language   the language of the names of months and days
     */
    public static String render(String text, Map<String, String> variables, String dateFormat, String timeFormat,
            ZonedDateTime now, Locale language) {
        if (text == null) {
            return "";
        }
        String out = text;
        for (Map.Entry<String, String> variable : variables.entrySet()) {
            Pattern key = Pattern.compile("\\{\\{" + Pattern.quote(variable.getKey()) + "\\}\\}",
                    Pattern.CASE_INSENSITIVE);
            out = replaceAll(out, key, variable.getValue() != null ? variable.getValue() : "");
        }
        Matcher m = DATE_OR_TIME.matcher(out);
        StringBuilder written = new StringBuilder();
        while (m.find()) {
            String format = m.group(2);
            if (format == null || format.isEmpty()) {
                format = "date".equalsIgnoreCase(m.group(1)) ? dateFormat : timeFormat;
            }
            m.appendReplacement(written, Matcher.quoteReplacement(MomentFormat.format(now, format, language)));
        }
        m.appendTail(written);
        return written.toString();
    }

    /** JavaScript's {@code text.replace(/key/gi, value)}: {@code $$}, {@code $&}, {@code $`} and {@code $'} read as it reads them. */
    private static String replaceAll(String text, Pattern key, String value) {
        Matcher m = key.matcher(text);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(text, last, m.start());
            String before = text.substring(0, m.start());
            String after = text.substring(m.end());
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                char next = i + 1 < value.length() ? value.charAt(i + 1) : 0;
                if (c == '$' && (next == '$' || next == '&' || next == '`' || next == '\'')) {
                    out.append(next == '$' ? "$" : next == '&' ? m.group() : next == '`' ? before : after);
                    i++;
                } else {
                    out.append(c);
                }
            }
            last = m.end();
        }
        out.append(text.substring(last));
        return out.toString();
    }
}
