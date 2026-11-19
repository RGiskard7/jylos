package com.example.jylos.ui.controller;

import java.util.function.Supplier;

import com.example.jylos.data.dao.filesystem.FrontmatterHandler;
import com.example.jylos.data.models.Note;
import com.example.jylos.ui.components.TextEditing;

/**
 * Inserting in the open note what a template or a command writes, as Obsidian's Templates
 * does: a template's text goes in place of the selection, and its properties are added to the
 * note's ({@link FrontmatterHandler#mergeProperties}); the current date or time goes where the
 * cursor is, with the cursor after it.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
final class EditorTemplateSupport {

    /** What came of inserting a template. */
    enum Outcome {
        /** It is in the note. */
        INSERTED,
        /** No note is open for editing. */
        NO_NOTE,
        /** Its properties are not YAML: nothing was inserted. */
        INVALID_PROPERTIES
    }

    private final Supplier<TextEditing> editor;
    private final Supplier<Note> note;
    private final Runnable changed;

    /**
     * @param editor  the editor of the open note
     * @param note    the note open for editing, with what its properties panel shows; {@code null} when none
     * @param changed told once the note has changed
     */
    EditorTemplateSupport(Supplier<TextEditing> editor, Supplier<Note> note, Runnable changed) {
        this.editor = editor;
        this.note = note;
        this.changed = changed;
    }

    /** Inserts the written template {@code text}: its text at the cursor, its properties in the note's. */
    Outcome insertTemplate(String text) {
        TextEditing area = editor.get();
        Note open = note.get();
        if (area == null || open == null) {
            return Outcome.NO_NOTE;
        }
        FrontmatterHandler.TemplateText template;
        try {
            template = FrontmatterHandler.splitTemplate(text);
        } catch (IllegalArgumentException e) {
            return Outcome.INVALID_PROPERTIES;
        }
        area.replaceSelection(template.content(), -1);
        FrontmatterHandler.mergeProperties(open, template.properties());
        changed.run();
        return Outcome.INSERTED;
    }

    /** Writes {@code text} at the cursor, in place of the selection, with the cursor after it. */
    boolean insertText(String text) {
        TextEditing area = editor.get();
        if (area == null || note.get() == null) {
            return false;
        }
        area.replaceSelection(text, -1);
        changed.run();
        return true;
    }
}
