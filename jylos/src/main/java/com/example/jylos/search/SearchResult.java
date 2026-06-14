package com.example.jylos.search;

import java.util.List;

import com.example.jylos.data.models.Note;

/**
 * A single advanced-search hit: the matched {@link Note} plus the presentation extras
 * the results UI shows (snippet, folder, tags). Title and modified date are read
 * straight from the note.
 *
 * @param note    the matched note
 * @param snippet a short, single-line excerpt of the note body
 * @param folder  the containing folder name (empty when at the root)
 * @param tags    the note's tag titles (may be empty)
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.1.0
 */
public record SearchResult(Note note, String snippet, String folder, List<String> tags) {

    public String title() {
        String t = note != null ? note.getTitle() : null;
        return (t != null && !t.isBlank()) ? t : "(untitled)";
    }

    public String modifiedDate() {
        return note != null ? note.getModifiedDate() : null;
    }
}
