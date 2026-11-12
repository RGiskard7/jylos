package com.example.jylos.util;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where the heading or block named by a link's subpath ({@code Heading}, {@code A#B},
 * {@code ^block-id}) is in a note: the character offset, for the editor, and the element id
 * the reading view gives it ({@link MarkdownProcessor#slugifyHeading} with the same
 * de-duplication, or {@link ReadingSyntax#BLOCK_ID_PREFIX} plus the id).
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
        BitSet code = WikiLinkResolver.codeRanges(content);
        return target.startsWith("^") ? block(content, target.substring(1), code) : heading(content, target, code);
    }

    private static Optional<Anchor> heading(String content, String text, BitSet code) {
        Map<String, Integer> seen = new HashMap<>();
        int offset = 0;
        for (String line : content.split("\n", -1)) {
            Matcher m = HEADING.matcher(line);
            if (!code.get(offset) && m.matches()) {
                String title = m.group(2).strip();
                String base = MarkdownProcessor.slugifyHeading(title);
                if (!base.isEmpty()) {
                    int occurrence = seen.merge(base, 1, Integer::sum) - 1;
                    if (title.equalsIgnoreCase(text)) {
                        return Optional.of(new Anchor(offset, occurrence == 0 ? base : base + "-" + occurrence));
                    }
                }
            }
            offset += line.length() + 1;
        }
        return Optional.empty();
    }

    private static Optional<Anchor> block(String content, String id, BitSet code) {
        if (id.isEmpty()) {
            return Optional.empty();
        }
        Pattern marker = Pattern.compile("(?:^|[ \\t])\\^" + Pattern.quote(id) + "[ \\t]*$");
        int offset = 0;
        for (String line : content.split("\n", -1)) {
            Matcher m = marker.matcher(line);
            if (m.find() && !code.get(offset + line.lastIndexOf('^'))) {
                return Optional.of(new Anchor(offset, ReadingSyntax.BLOCK_ID_PREFIX + id));
            }
            offset += line.length() + 1;
        }
        return Optional.empty();
    }
}
