package com.example.jylos.ui.controller;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.example.jylos.data.models.Note;
import com.example.jylos.ui.components.MarkdownEditorView;
import com.example.jylos.ui.components.NotePosition;

/**
 * Where the Markdown note in the editor is, and showing it somewhere: the editor's first line
 * in sight and its selection ({@link MarkdownEditorView}), and the reading view's scroll
 * ({@link EditorPreviewSupport}). The tabs keep it for each note they leave and give it back
 * when they show that note again (see {@code TabsSupport}).
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
final class NotePositionSupport {

    private final Supplier<Note> note;
    private final BooleanSupplier ownViewer;
    private final Supplier<MarkdownEditorView> editor;
    private final EditorPreviewSupport preview;

    /**
     * @param note      the note in the editor, or {@code null}
     * @param ownViewer whether that file is shown by its own viewer (a PDF, an image, a
     *                  canvas), which keeps its own place
     * @param editor    the editor, or {@code null} before it exists
     * @param preview   the reading view
     */
    NotePositionSupport(Supplier<Note> note, BooleanSupplier ownViewer, Supplier<MarkdownEditorView> editor,
            EditorPreviewSupport preview) {
        this.note = note;
        this.ownViewer = ownViewer;
        this.editor = editor;
        this.preview = preview;
    }

    /**
     * Where the Markdown note in the editor is now; {@code null} with no note, or for a file
     * shown by its own viewer.
     */
    NotePosition where() {
        String id = markdownNoteId();
        if (id == null) {
            return null;
        }
        NotePosition inEditor = editor.get().viewPosition(id);
        double readingY = preview.scrollY(id);
        return inEditor != null
                ? new NotePosition(id, inEditor.editorTop(), inEditor.editorOffset(), inEditor.selectionAnchor(),
                        inEditor.selectionHead(), readingY)
                : new NotePosition(id, 0, 0, 0, 0, readingY);
    }

    /**
     * Shows the Markdown note in the editor where {@code position} says, in the editor and in
     * the reading view; {@code null}, or a position of another note, shows it at its top with
     * the caret at its start. A jump to a heading asked for afterwards still wins.
     */
    void showAt(NotePosition position) {
        String id = markdownNoteId();
        if (id == null) {
            return;
        }
        NotePosition at = position != null && id.equals(position.noteId()) ? position : null;
        editor.get().showViewPosition(at);
        preview.showAt(id, at != null ? at.readingY() : 0);
    }

    private String markdownNoteId() {
        Note current = note.get();
        boolean markdown = current != null && current.getId() != null && !ownViewer.getAsBoolean()
                && editor.get() != null;
        return markdown ? current.getId() : null;
    }
}
