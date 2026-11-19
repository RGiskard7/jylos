package com.example.jylos.util;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes a date with the symbols of moment.js, as Obsidian and its plugins do
 * ({@code YYYY-MM-DD}, {@code dddd}, {@code Do MMMM}, {@code [Semana] W}…), and as they do it:
 * the same names of months and days, the same weeks, the same long formats ({@code LL}), the
 * same escapes ({@code [texto]}, {@code \Y}) and the same quirks, down to an unmatched
 * {@code [} being written as is and a line break in the format being left out.
 *
 * <p>Months, days, ordinals and long formats come in the languages of the interface (English
 * and Spanish), with moment's own data for them; any other language is written in English, as
 * moment does with a language it has no data for. Eras are always moment's English ones.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class MomentFormat {

    /** moment's {@code format()} with no format. */
    public static final String DEFAULT_FORMAT = "YYYY-MM-DDTHH:mm:ssZ";

    private static final Pattern TOKENS = Pattern.compile("(\\[[^\\[]*\\])|(\\\\)?([Hh]mm(ss)?|Mo|MM?M?M?|Do|DDDo"
            + "|DD?D?D?|ddd?d?|do?|w[o|w]?|W[o|W]?|Qo?|N{1,5}|YYYYYY|YYYYY|YYYY|YY|y{2,4}|yo?|gg(ggg?)?|GG(GGG?)?"
            + "|e|E|a|A|hh?|HH?|kk?|mm?|ss?|S{1,9}|x|X|zz?|ZZ?|.)");
    private static final Pattern LONG_TOKENS = Pattern.compile("(\\[[^\\[]*\\])|(\\\\)?(LTS|LT|LL?L?L?|l{1,4})");
    private static final Pattern ESCAPED = Pattern.compile("\\[[\\s\\S]");

    private MomentFormat() {
    }

    /** {@code when} written with the moment format {@code format}, in {@code language}. */
    public static String format(ZonedDateTime when, String format, Locale language) {
        Names names = Names.of(language);
        String expanded = expandLongFormats(format == null || format.isEmpty() ? DEFAULT_FORMAT : format, names);
        StringBuilder out = new StringBuilder();
        Matcher m = TOKENS.matcher(expanded);
        while (m.find()) {
            String token = m.group();
            String value = token(token, when, expanded, names);
            if (value != null) {
                out.append(value);
            } else if (ESCAPED.matcher(token).lookingAt()) {
                out.append(token.replaceAll("^\\[|\\]$", ""));
            } else {
                out.append(token.replace("\\", ""));
            }
        }
        return out.toString();
    }

    private static String expandLongFormats(String format, Names names) {
        String expanded = format;
        for (int i = 5; i >= 0 && LONG_TOKENS.matcher(expanded).find(); i--) {
            Matcher m = LONG_TOKENS.matcher(expanded);
            StringBuilder next = new StringBuilder();
            while (m.find()) {
                String replacement = names.longFormat(m.group());
                m.appendReplacement(next, Matcher.quoteReplacement(replacement != null ? replacement : m.group()));
            }
            m.appendTail(next);
            expanded = next.toString();
        }
        return expanded;
    }

    /** The text of one token, or {@code null} when it is not one and is written as is. */
    private static String token(String token, ZonedDateTime when, String format, Names names) {
        int month = when.getMonthValue();
        int day = when.getDayOfMonth();
        int weekday = when.getDayOfWeek().getValue() % 7; // Sunday 0, as moment's day()
        int hour = when.getHour();
        int minute = when.getMinute();
        int second = when.getSecond();
        int millis = when.get(ChronoField.MILLI_OF_SECOND);
        int year = when.getYear();
        return switch (token) {
            case "M" -> String.valueOf(month);
            case "MM" -> pad(month, 2, false);
            case "Mo" -> names.ordinal(month);
            case "MMM" -> names.monthShort(month - 1, format);
            case "MMMM" -> names.months.get(month - 1);
            case "Q" -> String.valueOf((month + 2) / 3);
            case "Qo" -> names.ordinal((month + 2) / 3);
            case "D" -> String.valueOf(day);
            case "DD" -> pad(day, 2, false);
            case "Do" -> names.ordinal(day);
            case "DDD" -> String.valueOf(when.getDayOfYear());
            case "DDDD" -> pad(when.getDayOfYear(), 3, false);
            case "DDDo" -> names.ordinal(when.getDayOfYear());
            case "d" -> String.valueOf(weekday);
            case "do" -> names.ordinal(weekday);
            case "dd" -> names.weekdaysMin.get(weekday);
            case "ddd" -> names.weekdaysShort.get(weekday);
            case "dddd" -> names.weekdays.get(weekday);
            case "e" -> String.valueOf((weekday + 7 - names.dow) % 7);
            case "E" -> String.valueOf(weekday == 0 ? 7 : weekday);
            case "w" -> String.valueOf(week(when, names.dow, names.doy)[0]);
            case "ww" -> pad(week(when, names.dow, names.doy)[0], 2, false);
            case "wo" -> names.ordinal(week(when, names.dow, names.doy)[0]);
            case "W" -> String.valueOf(week(when, 1, 4)[0]);
            case "WW" -> pad(week(when, 1, 4)[0], 2, false);
            case "Wo" -> names.ordinal(week(when, 1, 4)[0]);
            case "gg" -> pad(week(when, names.dow, names.doy)[1] % 100, 2, false);
            case "gggg" -> pad(week(when, names.dow, names.doy)[1], 4, false);
            case "ggggg" -> pad(week(when, names.dow, names.doy)[1], 5, false);
            case "GG" -> pad(week(when, 1, 4)[1] % 100, 2, false);
            case "GGGG" -> pad(week(when, 1, 4)[1], 4, false);
            case "GGGGG" -> pad(week(when, 1, 4)[1], 5, false);
            case "Y" -> year <= 9999 ? pad(year, 4, false) : "+" + year;
            case "YY" -> pad(year % 100, 2, false);
            case "YYYY" -> pad(year, 4, false);
            case "YYYYY" -> pad(year, 5, false);
            case "YYYYYY" -> pad(year, 6, true);
            case "N", "NN", "NNN", "NNNNN" -> year > 0 ? "AD" : "BC";
            case "NNNN" -> year > 0 ? "Anno Domini" : "Before Christ";
            case "y" -> String.valueOf(eraYear(year));
            case "yy" -> pad(eraYear(year), 2, false);
            case "yyy" -> pad(eraYear(year), 3, false);
            case "yyyy" -> pad(eraYear(year), 4, false);
            case "yo" -> names.ordinal(eraYear(year));
            case "a" -> hour > 11 ? "pm" : "am";
            case "A" -> hour > 11 ? "PM" : "AM";
            case "H" -> String.valueOf(hour);
            case "HH" -> pad(hour, 2, false);
            case "h" -> String.valueOf(hour12(hour));
            case "hh" -> pad(hour12(hour), 2, false);
            case "k" -> String.valueOf(hour == 0 ? 24 : hour);
            case "kk" -> pad(hour == 0 ? 24 : hour, 2, false);
            case "hmm" -> hour12(hour) + pad(minute, 2, false);
            case "hmmss" -> hour12(hour) + pad(minute, 2, false) + pad(second, 2, false);
            case "Hmm" -> hour + pad(minute, 2, false);
            case "Hmmss" -> hour + pad(minute, 2, false) + pad(second, 2, false);
            case "m" -> String.valueOf(minute);
            case "mm" -> pad(minute, 2, false);
            case "s" -> String.valueOf(second);
            case "ss" -> pad(second, 2, false);
            case "z", "zz" -> "";
            case "Z" -> offset(when, ":");
            case "ZZ" -> offset(when, "");
            case "X" -> String.valueOf(Math.floorDiv(when.toInstant().toEpochMilli(), 1000));
            case "x" -> String.valueOf(when.toInstant().toEpochMilli());
            default -> token.startsWith("S") && token.chars().allMatch(c -> c == 'S') ? fraction(millis, token.length())
                    : null;
        };
    }

    /** The first {@code digits} digits of the second's fraction, as moment's {@code S…S}. */
    private static String fraction(int millis, int digits) {
        if (digits <= 3) {
            return pad(millis / (int) Math.pow(10, 3 - digits), digits, false);
        }
        return pad(millis, 3, false) + "0".repeat(digits - 3);
    }

    private static int hour12(int hour) {
        return hour % 12 == 0 ? 12 : hour % 12;
    }

    private static int eraYear(int year) {
        return year > 0 ? year : 1 - year;
    }

    private static String offset(ZonedDateTime when, String separator) {
        int minutes = when.getOffset().getTotalSeconds() / 60;
        String sign = minutes < 0 ? "-" : "+";
        int abs = Math.abs(minutes);
        return sign + pad(abs / 60, 2, false) + separator + pad(abs % 60, 2, false);
    }

    /** moment's zero fill: at least {@code width} digits, a minus sign, and a plus when asked. */
    private static String pad(long value, int width, boolean plus) {
        String digits = String.valueOf(Math.abs(value));
        String sign = value >= 0 ? (plus ? "+" : "") : "-";
        return sign + "0".repeat(Math.max(0, width - digits.length())) + digits;
    }

    /**
     * {@code {week, weekYear}} of {@code when} for weeks starting on {@code dow} (0 Sunday) whose
     * first week holds 1 January's day {@code doy}, as moment's {@code weekOfYear}: ISO weeks are
     * {@code dow 1, doy 4}.
     */
    private static int[] week(ZonedDateTime when, int dow, int doy) {
        int year = when.getYear();
        int offset = firstWeekOffset(year, dow, doy);
        int week = Math.floorDiv(when.getDayOfYear() - offset - 1, 7) + 1;
        if (week < 1) {
            return new int[] {week + weeksInYear(year - 1, dow, doy), year - 1};
        }
        if (week > weeksInYear(year, dow, doy)) {
            return new int[] {week - weeksInYear(year, dow, doy), year + 1};
        }
        return new int[] {week, year};
    }

    private static int firstWeekOffset(int year, int dow, int doy) {
        int fwd = 7 + dow - doy;
        int fwdWeekday = LocalDate.of(year, 1, fwd).getDayOfWeek().getValue() % 7;
        int fwdlw = (7 + fwdWeekday - dow) % 7;
        return -fwdlw + fwd - 1;
    }

    private static int weeksInYear(int year, int dow, int doy) {
        int days = LocalDate.of(year, 1, 1).lengthOfYear();
        return (days - firstWeekOffset(year, dow, doy) + firstWeekOffset(year + 1, dow, doy)) / 7;
    }

    /** The day a week starts on in {@code language}, as moment counts it: 0 Sunday, 1 Monday. */
    public static int firstDayOfWeek(Locale language) {
        return Names.of(language).dow;
    }

    private static final Pattern ISO = Pattern.compile(
            "(\\d{4})-(\\d\\d)-(\\d\\d)(?:[T ](\\d\\d):(\\d\\d)(?::(\\d\\d)(?:[.,](\\d+))?)?)?(Z|[+-]\\d\\d:?\\d\\d)?");

    /**
     * {@code text} read as moment reads it ({@code moment(text, format)}, not strict): each symbol
     * of {@code format} takes the first text that matches it in what is left; what is missing is
     * taken from {@code now} for the parts before the first one given, and is the first day or
     * zero after it; a date out of range (30 February) is no date. With no format, ISO 8601.
     *
     * @return the date, in {@code now}'s zone, or empty when it is not one
     */
    public static Optional<ZonedDateTime> parse(String text, String format, Locale language, ZonedDateTime now) {
        if (text == null) {
            return Optional.empty();
        }
        Names names = Names.of(language);
        Integer[] parts = new Integer[7]; // year, month (0-11), day, hour, minute, second, millisecond
        Boolean pm = null;
        if (format == null || format.isEmpty()) {
            Matcher iso = ISO.matcher(text.strip());
            if (!iso.matches()) {
                return Optional.empty();
            }
            for (int i = 0; i < 6; i++) {
                String part = iso.group(i + 1);
                parts[i] = part == null ? null : Integer.parseInt(part) - (i == 1 ? 1 : 0);
            }
            if (iso.group(7) != null) {
                parts[6] = Integer.parseInt((iso.group(7) + "00").substring(0, 3));
            }
        } else {
            String rest = text;
            boolean any = false;
            Matcher tokens = TOKENS.matcher(expandLongFormats(format, names));
            while (tokens.find()) {
                String token = tokens.group();
                Pattern pattern = parsePattern(token, names);
                Matcher found = pattern.matcher(rest);
                if (!found.find()) {
                    continue;
                }
                String value = found.group();
                rest = rest.substring(found.end());
                Integer[] before = parts.clone();
                Boolean meridiem = readToken(token, value, names, parts);
                if (meridiem != null) {
                    pm = meridiem;
                }
                any |= meridiem != null || !Arrays.equals(before, parts);
            }
            if (!any) {
                return Optional.empty();
            }
        }
        if (pm != null && parts[3] != null) {
            if (pm && parts[3] < 12) {
                parts[3] += 12;
            } else if (!pm && parts[3] == 12) {
                parts[3] = 0;
            }
        }
        int[] current = {now.getYear(), now.getMonthValue() - 1, now.getDayOfMonth()};
        int i = 0;
        for (; i < 3 && parts[i] == null; i++) {
            parts[i] = current[i];
        }
        for (; i < 7; i++) {
            if (parts[i] == null) {
                parts[i] = i == 2 ? 1 : 0;
            }
        }
        int year = parts[0];
        int month = parts[1];
        if (month < 0 || month > 11) {
            return Optional.empty();
        }
        int length = LocalDate.of(year, month + 1, 1).lengthOfMonth();
        boolean midnight = parts[3] == 24 && parts[4] == 0 && parts[5] == 0 && parts[6] == 0;
        if (parts[2] < 1 || parts[2] > length || parts[3] < 0 || (parts[3] > 23 && !midnight) || parts[4] < 0
                || parts[4] > 59 || parts[5] < 0 || parts[5] > 59 || parts[6] < 0 || parts[6] > 999) {
            return Optional.empty();
        }
        ZonedDateTime date = LocalDate.of(year, month + 1, parts[2]).atStartOfDay(now.getZone())
                .plusHours(parts[3]).plusMinutes(parts[4]).plusSeconds(parts[5]).plusNanos(parts[6] * 1_000_000L);
        return Optional.of(date);
    }

    /** What moment's lenient parser looks for for {@code token}. */
    private static Pattern parsePattern(String token, Names names) {
        String regex = switch (token) {
            case "YYYY", "YYYYY", "YYYYYY", "gggg", "GGGG" -> "[+-]?\\d{1,6}";
            case "Y" -> "[+-]?\\d+";
            case "YY", "M", "MM", "D", "DD", "H", "HH", "h", "hh", "k", "kk", "m", "mm", "s", "ss", "w", "ww", "W",
                    "WW", "d", "e", "E", "Q" -> "\\d\\d?";
            case "DDD", "DDDD" -> "\\d{1,3}";
            case "Do" -> "\\d{1,2}(?:º|th|st|nd|rd)?";
            case "MMM", "MMMM" -> names(names.months, names.monthsShort, names.monthsShortWithoutDot);
            case "dd", "ddd", "dddd" -> names(names.weekdays, names.weekdaysShort, names.weekdaysMin);
            case "a", "A" -> "[AaPp]\\.?[Mm]?\\.?";
            case "X" -> "[+-]?\\d+(?:\\.\\d{1,3})?";
            case "x" -> "[+-]?\\d+";
            case "Z", "ZZ" -> "Z|[+-]\\d\\d:?\\d\\d";
            default -> token.startsWith("S") && token.chars().allMatch(c -> c == 'S') ? "\\d+"
                    : Pattern.quote(ESCAPED.matcher(token).lookingAt() ? token.replaceAll("^\\[|\\]$", "")
                            : token.replace("\\", ""));
        };
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /** Any of {@code lists}' names, the longest first. */
    @SafeVarargs
    private static String names(List<String>... lists) {
        List<String> all = new ArrayList<>();
        for (List<String> list : lists) {
            if (list != null) {
                list.forEach(name -> all.add(Pattern.quote(name)));
            }
        }
        all.sort((a, b) -> b.length() - a.length());
        return String.join("|", all);
    }

    /** Puts what {@code value} says for {@code token} in {@code parts}; for a meridiem, whether it is pm. */
    private static Boolean readToken(String token, String value, Names names, Integer[] parts) {
        switch (token) {
            case "YYYY", "YYYYY", "YYYYYY", "Y" -> parts[0] = Integer.parseInt(value.replace("+", ""));
            case "YY" -> {
                int two = Integer.parseInt(value);
                parts[0] = two + (two > 68 ? 1900 : 2000);
            }
            case "M", "MM" -> parts[1] = Integer.parseInt(value) - 1;
            case "MMM", "MMMM" -> parts[1] = monthOf(value, names);
            case "D", "DD", "Do" -> parts[2] = Integer.parseInt(value.replaceAll("\\D", ""));
            case "H", "HH", "h", "hh", "k", "kk" -> parts[3] = Integer.parseInt(value);
            case "m", "mm" -> parts[4] = Integer.parseInt(value);
            case "s", "ss" -> parts[5] = Integer.parseInt(value);
            case "a", "A" -> {
                return Character.toLowerCase(value.charAt(0)) == 'p';
            }
            default -> {
                if (token.startsWith("S") && token.chars().allMatch(c -> c == 'S')) {
                    parts[6] = Integer.parseInt((value + "00").substring(0, 3));
                }
            }
        }
        return null;
    }

    private static int monthOf(String value, Names names) {
        String name = value.toLowerCase(Locale.ROOT);
        for (int i = 0; i < 12; i++) {
            if (names.months.get(i).equalsIgnoreCase(name) || names.monthsShort.get(i).equalsIgnoreCase(name)
                    || names.monthsShortWithoutDot != null && names.monthsShortWithoutDot.get(i).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    /** moment's data for one language: names, ordinals, weeks and long formats. */
    private record Names(List<String> months, List<String> monthsShort, List<String> monthsShortWithoutDot,
            List<String> weekdays, List<String> weekdaysShort, List<String> weekdaysMin, String ordinalSuffix,
            int dow, int doy, String lt, String lts, String l, String ll, String lll, String llll) {

        static final Names ENGLISH = new Names(
                split("January_February_March_April_May_June_July_August_September_October_November_December"),
                split("Jan_Feb_Mar_Apr_May_Jun_Jul_Aug_Sep_Oct_Nov_Dec"), null,
                split("Sunday_Monday_Tuesday_Wednesday_Thursday_Friday_Saturday"),
                split("Sun_Mon_Tue_Wed_Thu_Fri_Sat"), split("Su_Mo_Tu_We_Th_Fr_Sa"), null, 0, 6,
                "h:mm A", "h:mm:ss A", "MM/DD/YYYY", "MMMM D, YYYY", "MMMM D, YYYY h:mm A",
                "dddd, MMMM D, YYYY h:mm A");

        static final Names SPANISH = new Names(
                split("enero_febrero_marzo_abril_mayo_junio_julio_agosto_septiembre_octubre_noviembre_diciembre"),
                split("ene._feb._mar._abr._may._jun._jul._ago._sep._oct._nov._dic."),
                split("ene_feb_mar_abr_may_jun_jul_ago_sep_oct_nov_dic"),
                split("domingo_lunes_martes_miércoles_jueves_viernes_sábado"),
                split("dom._lun._mar._mié._jue._vie._sáb."), split("do_lu_ma_mi_ju_vi_sá"), "º", 1, 4,
                "H:mm", "H:mm:ss", "DD/MM/YYYY", "D [de] MMMM [de] YYYY", "D [de] MMMM [de] YYYY H:mm",
                "dddd, D [de] MMMM [de] YYYY H:mm");

        static Names of(Locale language) {
            return language != null && "es".equals(language.getLanguage()) ? SPANISH : ENGLISH;
        }

        private static List<String> split(String names) {
            return List.of(names.split("_"));
        }

        /** {@code MMM}: Spanish leaves out the dot between dashes ({@code 30-dic-2024}), as moment. */
        String monthShort(int index, String format) {
            if (monthsShortWithoutDot != null && format.contains("-MMM-")) {
                return monthsShortWithoutDot.get(index);
            }
            return monthsShort.get(index);
        }

        String ordinal(int number) {
            if (ordinalSuffix != null) {
                return number + ordinalSuffix;
            }
            int last = number % 10;
            String suffix = (number % 100) / 10 == 1 ? "th"
                    : last == 1 ? "st" : last == 2 ? "nd" : last == 3 ? "rd" : "th";
            return number + suffix;
        }

        /** A long format ({@code LL}…) in moment's words, or {@code null} for any other text. */
        String longFormat(String key) {
            return switch (key) {
                case "LT" -> lt;
                case "LTS" -> lts;
                case "L" -> l;
                case "LL" -> ll;
                case "LLL" -> lll;
                case "LLLL" -> llll;
                case "l", "ll", "lll", "llll" -> shortened(longFormat(key.toUpperCase(Locale.ROOT)));
                default -> null;
            };
        }

        /** moment's lowercase long formats: its {@code MMMM}, {@code MM}, {@code DD} and {@code dddd} one letter shorter. */
        private static String shortened(String upper) {
            StringBuilder out = new StringBuilder();
            Matcher m = TOKENS.matcher(upper);
            while (m.find()) {
                String token = m.group();
                out.append("MMMM".equals(token) || "MM".equals(token) || "DD".equals(token) || "dddd".equals(token)
                        ? token.substring(1) : token);
            }
            return out.toString();
        }
    }
}
