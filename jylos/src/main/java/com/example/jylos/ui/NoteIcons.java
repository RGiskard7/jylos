package com.example.jylos.ui;

import org.kordamp.ikonli.javafx.FontIcon;

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

    /** Style class of a folder's icon, coloured like the files' when coloured icons are on. */
    public static final String FOLDER_CLASS = "icon-type-folder";
    private static final String TYPE_CLASS_PREFIX = "icon-type-";

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

    /**
     * The style class of {@code note}'s kind (icon-type-note, -board, -canvas, -image or -pdf),
     * which the themes colour when coloured icons are on.
     */
    public static String typeClassFor(Note note) {
        return typeClassForLiteral(literalFor(note));
    }

    /** The kind's style class for an icon shown by its literal; {@code null} for an icon that is not a file's. */
    public static String typeClassForLiteral(String literal) {
        if (literal == null) {
            return null;
        }
        return switch (literal) {
            case "fth-file-text" -> TYPE_CLASS_PREFIX + "note";
            case "fth-columns" -> TYPE_CLASS_PREFIX + "board";
            case "fth-grid" -> TYPE_CLASS_PREFIX + "canvas";
            case "fth-image" -> TYPE_CLASS_PREFIX + "image";
            case "fth-file" -> TYPE_CLASS_PREFIX + "pdf";
            default -> null;
        };
    }

    /** Shows {@code note}'s kind on {@code icon}, which may have shown another note before: its literal and class. */
    public static void apply(FontIcon icon, Note note) {
        icon.setIconLiteral(literalFor(note));
        icon.getStyleClass().removeIf(styleClass -> styleClass.startsWith(TYPE_CLASS_PREFIX));
        icon.getStyleClass().add(typeClassFor(note));
    }

    /** A new icon showing {@code note}'s kind. */
    public static FontIcon iconFor(Note note) {
        FontIcon icon = new FontIcon();
        apply(icon, note);
        return icon;
    }

    /** A new folder icon. */
    public static FontIcon folderIcon() {
        FontIcon icon = new FontIcon("fth-folder");
        icon.getStyleClass().add(FOLDER_CLASS);
        return icon;
    }

    /** The icon of {@code note}'s tab: none for a plain note, as in Obsidian; its kind's otherwise. */
    public static String tabIconFor(Note note) {
        String icon = literalFor(note);
        return "fth-file-text".equals(icon) ? null : icon;
    }
}
