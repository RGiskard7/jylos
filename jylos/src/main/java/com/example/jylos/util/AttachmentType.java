package com.example.jylos.util;

import java.util.Locale;
import java.util.Set;

/**
 * Classifies a vault file by its extension so the UI can decide whether to open
 * it in the Markdown editor or in a native viewer (image / PDF).
 *
 * <p>Notes are identified by a relative path id that includes the extension
 * (e.g. {@code "Folder/Note.md"}, {@code "Assets/diagram.png"}), so the same
 * helper works on ids, filenames and paths.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.6.0
 */
public enum AttachmentType {

    /** Editable Markdown/plain-text note (the default). */
    MARKDOWN,
    /** A PDF document, shown in the paginated PDF viewer. */
    PDF,
    /** A raster image, shown in the image viewer. */
    IMAGE;

    /** Image extensions JavaFX can decode natively. */
    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "gif", "bmp");

    /** Markdown / text extensions handled by the editor. */
    private static final Set<String> TEXT_EXTENSIONS =
            Set.of("md", "markdown", "txt");

    /** All non-text extensions the vault should surface alongside notes. */
    public static final Set<String> ATTACHMENT_EXTENSIONS = buildAttachmentExtensions();

    private static Set<String> buildAttachmentExtensions() {
        var all = new java.util.HashSet<>(IMAGE_EXTENSIONS);
        all.add("pdf");
        return Set.copyOf(all);
    }

    /** Returns the lowercase extension (without dot) of a filename/path/id, or "". */
    public static String extensionOf(String name) {
        if (name == null) {
            return "";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        int slash = Math.max(lower.lastIndexOf('/'), lower.lastIndexOf('\\'));
        int dot = lower.lastIndexOf('.');
        return dot > slash && dot >= 0 ? lower.substring(dot + 1) : "";
    }

    /** Classifies a filename/path/id. Unknown or text extensions map to {@link #MARKDOWN}. */
    public static AttachmentType fromName(String name) {
        String ext = extensionOf(name);
        if (ext.equals("pdf")) {
            return PDF;
        }
        if (IMAGE_EXTENSIONS.contains(ext)) {
            return IMAGE;
        }
        return MARKDOWN;
    }

    /** True if {@code name} is a viewable attachment (image or PDF), not a text note. */
    public static boolean isAttachment(String name) {
        return fromName(name) != MARKDOWN;
    }

    /** True for files the vault should list (Markdown/text notes or known attachments). */
    public static boolean isSupportedVaultFile(String name) {
        String ext = extensionOf(name);
        return TEXT_EXTENSIONS.contains(ext) || ATTACHMENT_EXTENSIONS.contains(ext);
    }

    public boolean isAttachment() {
        return this != MARKDOWN;
    }
}
