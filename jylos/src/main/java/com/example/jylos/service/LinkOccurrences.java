package com.example.jylos.service;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.jylos.util.WikiLinkResolver;

/**
 * Where the links and the unlinked mentions of a note are: the line they are on, to show as
 * context in the backlinks panel, and their place in it, to highlight them and to turn a
 * mention into a link. Pure text work, no storage.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class LinkOccurrences {

    /** Terms shorter than this are not searched as mentions: they would match everywhere. */
    private static final int MIN_TERM_LENGTH = 2;

    /** Links of any kind and bare URLs: text inside them is already part of a link. */
    private static final Pattern LINK_LIKE = Pattern.compile(
            "!?\\[\\[[^\\]\\n]*\\]\\]|!?\\[[^\\[\\]]*\\]\\([^)\\n]*\\)|(?i:https?://|www\\.)\\S+");

    private LinkOccurrences() {
    }

    /**
     * One place in a note: a link, or a mention of a note's title or alias.
     *
     * @param line     0-based line in the note
     * @param start    start in {@code lineText}
     * @param end      end (exclusive) in {@code lineText}
     * @param lineText the whole line, without its line break
     * @param target   for a link, what it points to as written; for a mention, the term found
     */
    public record Occurrence(int line, int start, int end, String lineText, String target) {
        /** The text that matched: the link as written, or the mention. */
        public String matched() {
            return lineText.substring(start, end);
        }
    }

    /** Every internal link in {@code markdown}, outside code, in order. */
    public static List<Occurrence> links(String markdown) {
        List<Occurrence> out = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) {
            return out;
        }
        LineIndex lines = new LineIndex(markdown);
        for (WikiLinkResolver.LinkMatch match : WikiLinkResolver.findLinks(markdown)) {
            out.add(lines.occurrence(match.start(), match.end(), match.path()));
        }
        return out;
    }

    /**
     * The places where one of {@code terms} appears as plain text: whole words, any case,
     * outside links, code, URLs and the leading properties block. A term inside a longer term
     * ("Target" in "Alias Target") is a mention of its own.
     */
    public static List<Occurrence> mentions(String markdown, Collection<String> terms) {
        List<Occurrence> out = new ArrayList<>();
        if (markdown == null || markdown.isEmpty() || terms == null) {
            return out;
        }
        BitSet excluded = WikiLinkResolver.codeRanges(markdown);
        int frontmatterEnd = frontmatterEnd(markdown);
        if (frontmatterEnd > 0) {
            excluded.set(0, frontmatterEnd);
        }
        Matcher link = LINK_LIKE.matcher(markdown);
        while (link.find()) {
            excluded.set(link.start(), link.end());
        }

        LineIndex lines = new LineIndex(markdown);
        Set<String> seen = new HashSet<>();
        List<Occurrence> found = new ArrayList<>();
        for (String term : terms) {
            if (term == null || term.strip().length() < MIN_TERM_LENGTH) {
                continue;
            }
            String clean = term.strip();
            Matcher m = Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(clean) + "(?![\\p{L}\\p{N}_])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(markdown);
            while (m.find()) {
                int nextExcluded = excluded.nextSetBit(m.start());
                if (nextExcluded >= 0 && nextExcluded < m.end()) {
                    continue;
                }
                if (m.group().indexOf('\n') >= 0 || !seen.add(m.start() + ":" + m.end())) {
                    continue;
                }
                found.add(lines.occurrence(m.start(), m.end(), clean));
            }
        }
        found.sort(Comparator.comparingInt(Occurrence::line).thenComparingInt(Occurrence::start)
                .thenComparingInt(o -> -(o.end() - o.start())));
        out.addAll(found);
        return out;
    }

    /**
     * {@code markdown} with {@code occurrence} replaced by {@code replacement}, or {@code null}
     * when the note no longer has that text in that place (it was edited since the occurrence
     * was found), so nothing is rewritten by mistake.
     */
    public static String replace(String markdown, Occurrence occurrence, String replacement) {
        if (markdown == null || occurrence == null || replacement == null) {
            return null;
        }
        LineIndex lines = new LineIndex(markdown);
        if (occurrence.line() < 0 || occurrence.line() >= lines.count()) {
            return null;
        }
        int lineStart = lines.lineStart(occurrence.line());
        String lineText = lines.lineText(occurrence.line());
        if (!lineText.equals(occurrence.lineText())) {
            return null;
        }
        int from = lineStart + occurrence.start();
        int to = lineStart + occurrence.end();
        return markdown.substring(0, from) + replacement + markdown.substring(to);
    }

    /** End offset of a leading {@code ---} properties block, or 0 when the note has none. */
    private static int frontmatterEnd(String markdown) {
        if (!markdown.startsWith("---")) {
            return 0;
        }
        int firstLineEnd = markdown.indexOf('\n');
        if (firstLineEnd < 0 || !markdown.substring(0, firstLineEnd).strip().equals("---")) {
            return 0;
        }
        int pos = firstLineEnd + 1;
        while (pos < markdown.length()) {
            int next = markdown.indexOf('\n', pos);
            int end = next < 0 ? markdown.length() : next;
            if (markdown.substring(pos, end).strip().equals("---")) {
                return next < 0 ? markdown.length() : next + 1;
            }
            if (next < 0) {
                break;
            }
            pos = next + 1;
        }
        return 0;
    }

    /** Line starts of a text, to place offsets on lines. */
    private static final class LineIndex {
        private final String text;
        private final List<Integer> starts = new ArrayList<>();

        LineIndex(String text) {
            this.text = text;
            starts.add(0);
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    starts.add(i + 1);
                }
            }
        }

        int count() {
            return starts.size();
        }

        int lineStart(int line) {
            return starts.get(line);
        }

        String lineText(int line) {
            int start = starts.get(line);
            int end = line + 1 < starts.size() ? starts.get(line + 1) - 1 : text.length();
            if (end > start && text.charAt(end - 1) == '\r') {
                end--;
            }
            return text.substring(start, end);
        }

        Occurrence occurrence(int from, int to, String target) {
            int line = java.util.Collections.binarySearch(starts, from);
            if (line < 0) {
                line = -line - 2;
            }
            String lineText = lineText(line);
            int start = from - starts.get(line);
            int end = Math.min(to - starts.get(line), lineText.length());
            return new Occurrence(line, start, Math.max(start, end), lineText, target);
        }
    }
}
