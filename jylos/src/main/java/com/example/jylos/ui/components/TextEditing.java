package com.example.jylos.ui.components;

/**
 * The text of an editor and its selection, and replacing the selection: what inserting a
 * template or a date needs of the editor, apart from how it draws the text.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public interface TextEditing {

    /** The whole text. */
    String getText();

    /** Where the selection starts (the cursor, when nothing is selected). */
    int getSelectionStart();

    /** Where the selection ends. */
    int getSelectionEnd();

    /**
     * Puts {@code value} in place of the selection, with the cursor {@code cursorOffset}
     * characters into it, or after it when negative.
     */
    void replaceSelection(String value, int cursorOffset);
}
