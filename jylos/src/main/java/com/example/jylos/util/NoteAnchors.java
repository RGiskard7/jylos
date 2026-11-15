package com.example.jylos.util;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where the heading or block named by a link's subpath ({@code Heading}, {@code A#B},
 * {@code ^block-id}) is in a note: the character offset, for the editor, and the element id
 * the reading view gives it ({@link MarkdownProcessor#slugifyHeading} with the same
 * de-duplication, or {@link ReadingSyntax#BLOCK_ID_PREFIX} plus the id).
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class NoteAnchors {

    private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,6})[ \\t]+(.*?)[ \\t#]*$");

    private NoteAnchors() {
    }

    /**
     * @param offset    character offset of the line in the note
     * @param elementId id of the rendered heading or block anchor
     */
    public record Anchor(int offset, String elementId) {
    }

    /**
     * A heading of a note, outside code.
     *
     * @param level  1 to 6
     * @param title  its text, without the {@code #}s
     * @param offset character offset of its line
     */
    public record Heading(int level, String title, int offset) {
    }

    /**
     * The headings of a note, in order: lines of up to three spaces, one to six {@code #}s
     * and the title, which may close with {@code #}s. A {@code #} line inside code is not one.
     */
    public static List<Heading> headings(String content) {
        List<Heading> headings = new ArrayList<>();
        if (content == null) {
            return headings;
        }
        BitSet code = WikiLinkResolver.codeRanges(content);
        int offset = 0;
        for (String line : content.split("\n", -1)) {
            Matcher m = HEADING.matcher(line);
            if (!code.get(offset) && m.matches()) {
                headings.add(new Heading(m.group(1).length(), m.group(2).strip(), offset));
            }
            offset += line.length() + 1;
        }
        return headings;
    }

    /**
     * Where the block marked {@code ^id} ends its line: the offset of that line, or -1. A marker
     * inside code marks nothing.
     */
    static int blockLine(String content, String id) {
        if (content == null || id == null || id.isEmpty()) {
            return -1;
        }
        BitSet code = WikiLinkResolver.codeRanges(content);
        Pattern marker = Pattern.compile("(?:^|[ \\t])\\^" + Pattern.quote(id) + "[ \\t]*$");
        int offset = 0;
        for (String line : content.split("\n", -1)) {
            if (marker.matcher(line).find() && !code.get(offset + line.lastIndexOf('^'))) {
                return offset;
            }
            offset += line.length() + 1;
        }
        return -1;
    }

    public static Optional<Anchor> locate(String content, String subpath) {
        if (content == null || subpath == null || subpath.isBlank()) {
            return Optional.empty();
        }
        String target = subpath.strip();
        int lastHash = target.lastIndexOf('#');
        if (lastHash >= 0) {
            target = target.substring(lastHash + 1).strip();
        }
        if (target.isEmpty()) {
            return Optional.empty();
        }
        if (target.startsWith("^")) {
            String id = target.substring(1);
            int line = blockLine(content, id);
            return line < 0 ? Optional.empty() : Optional.of(new Anchor(line, ReadingSyntax.BLOCK_ID_PREFIX + id));
        }
        Map<String, Integer> seen = new HashMap<>();
        for (Heading heading : headings(content)) {
            String base = MarkdownProcessor.slugifyHeading(heading.title());
            if (!base.isEmpty()) {
                int occurrence = seen.merge(base, 1, Integer::sum) - 1;
                if (heading.title().equalsIgnoreCase(target)) {
                    return Optional.of(new Anchor(heading.offset(), occurrence == 0 ? base : base + "-" + occurrence));
                }
            }
        }
        return Optional.empty();
    }
}
