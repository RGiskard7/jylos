package com.example.jylos.ui.components;

/**
 * Where a note was left in its tab, so that going back, going forward or coming back to the
 * tab shows it there again: the editor's first visible line ({@code editorTop}, a document
 * offset) and how far it was scrolled past that line's top ({@code editorOffset}, in px), the
 * editor's selection, and the reading view's scroll ({@code readingY}, in px). A note just
 * opened has none and starts at the top.
 *
 * @param noteId the note it belongs to
 * @param editorTop document offset of the start of the editor's first visible line
 * @param editorOffset how many px of that line were above the editor's top edge; negative
 *                     while the space above the first line was in sight
 * @param selectionAnchor the editor selection's anchor
 * @param selectionHead the editor selection's head (the caret)
 * @param readingY the reading view's vertical scroll, in px
 * @since 2.6.0
 * @author Edu Díaz (RGiskard7)
 */
public record NotePosition(String noteId, int editorTop, double editorOffset, int selectionAnchor,
        int selectionHead, double readingY) {
}
