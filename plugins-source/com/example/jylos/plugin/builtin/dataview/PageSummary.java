package com.example.jylos.plugin.builtin.dataview;

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

    private PageSummary() {
    }

    /** The summary of a note's text (without its properties). */
    static String of(String text) {
        String body = text != null ? text : "";
        StringBuilder summary = new StringBuilder().append(body.length()).append('\n');
        if (PageParser.FRONTMATTER.matcher(body).find()) {
            // A properties block in the text itself is read by PageParser: kept whole.
            return summary.append(body).toString();
        }
        String[] lines = body.split("\r?\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                summary.append('\n');
            }
            if (PageParser.mayMatter(lines[i])) {
                summary.append(lines[i]);
            }
        }
        return summary.toString();
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
