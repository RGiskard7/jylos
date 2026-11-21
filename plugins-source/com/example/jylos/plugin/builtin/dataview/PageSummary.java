package com.example.jylos.plugin.builtin.dataview;

import java.util.regex.Pattern;

import com.example.jylos.data.models.Note;

/**
 * What {@link PageParser} needs of a note's text, small enough to be kept with the vault's
 * cache of each note so a query does not read every note again: the length of the text and
 * its lines that can hold a task, a field, a tag or a link, each on its own line number (the
 * others left empty), plus the code fences that hide them. Parsing the summary gives the same
 * page as parsing the text.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.1
 */
final class PageSummary {

    /** Names the summary and its rules in the vault's cache: bump it when they change. */
    static final String KEY = "dataview@1";

    private static final Pattern FRONTMATTER = Pattern.compile("^---\\r?\\n(.*?)\\r?\\n---\\s*?(\\r?\\n|$)",
            Pattern.DOTALL);
    private static final Pattern TASK_LINE = Pattern.compile("^(\\s*)[-*+]\\s+\\[(.)\\]\\s*(.*)$");

    private PageSummary() {
    }

    /** The summary of a note's text (without its properties). */
    static String of(String text) {
        String body = text != null ? text : "";
        StringBuilder summary = new StringBuilder().append(body.length()).append('\n');
        if (FRONTMATTER.matcher(body).find()) {
            // A properties block in the text itself is read by PageParser: kept whole.
            return summary.append(body).toString();
        }
        String[] lines = body.split("\r?\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                summary.append('\n');
            }
            if (matters(lines[i])) {
                summary.append(lines[i]);
            }
        }
        return summary.toString();
    }

    /** Whether PageParser can find anything on {@code line}, or it opens or closes a code block. */
    private static boolean matters(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("```") || trimmed.startsWith("~~~")
                || line.contains("::") || line.indexOf('#') >= 0 || line.contains("[[")
                || TASK_LINE.matcher(line).matches();
    }

    /**
     * The note PageParser reads for {@code listed}, a note the vault listed with all its
     * properties, tags and links: its text is the summary's, and its size the text's.
     */
    static Note note(Note listed, String summary) {
        int newline = summary.indexOf('\n');
        Note note = new Note(listed.getId(), listed.getTitle(), summary.substring(newline + 1),
                listed.getCreatedDate(), listed.getModifiedDate());
        note.setFavorite(listed.isFavorite());
        note.setPinned(listed.isPinned());
        note.setTags(listed.getTags());
        note.setStructuredFrontmatterProperties(listed.getStructuredFrontmatterProperties());
        note.setLinkTargets(listed.getLinkTargets());
        return note;
    }

    /** The length of the text the summary was made from. */
    static long textLength(String summary) {
        return Long.parseLong(summary.substring(0, summary.indexOf('\n')));
    }
}
