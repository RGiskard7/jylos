package com.example.jylos.ui;

import com.example.jylos.data.models.Note;
import com.example.jylos.util.AttachmentType;
import com.example.jylos.util.KanbanModel;

/**
 * The icon of each kind of file wherever notes are listed (the Explorer tree, the notes list,
 * the tabs): an image, a PDF, a canvas, a Kanban board or a note.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class NoteIcons {

    private NoteIcons() {
    }

    /** The Feather icon literal for {@code note}; a note's when {@code null}. */
    public static String literalFor(Note note) {
        if (note == null) {
            return "fth-file-text";
        }
        return switch (AttachmentType.fromName(note.getId())) {
            case IMAGE -> "fth-image";
            case PDF -> "fth-file";
            case CANVAS -> "fth-grid";
            default -> KanbanModel.isBoard(note.getContent()) ? "fth-columns" : "fth-file-text";
        };
    }

    /** The icon of {@code note}'s tab: none for a plain note, as in Obsidian; its kind's otherwise. */
    public static String tabIconFor(Note note) {
        String icon = literalFor(note);
        return "fth-file-text".equals(icon) ? null : icon;
    }
}
