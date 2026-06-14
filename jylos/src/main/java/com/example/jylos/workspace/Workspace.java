package com.example.jylos.workspace;

import java.util.ArrayList;
import java.util.List;

/**
 * A saved working context: the open note tabs, the active note and a little layout
 * state, stored under a user-given name. Restoring a workspace reopens those notes and
 * reapplies the layout — handy for switching between writing, research, programming or
 * personal contexts.
 *
 * <p>Persisted in Jylos' local app data (not inside notes) as a single line via
 * {@link #serialize()} / {@link #parse(String)}, using control-character separators so
 * note ids/paths and names round-trip without a JSON dependency. A line that doesn't
 * parse is treated as corrupt and skipped by the repository.</p>
 *
 * @param id            stable identifier
 * @param name          user-visible name (unique per store, case-insensitive)
 * @param createdAt     ISO-8601 creation timestamp
 * @param updatedAt     ISO-8601 last-update timestamp
 * @param openNoteIds   ids/paths of the notes open in tabs, in display order
 * @param activeNoteId  id of the active note (may be null)
 * @param viewMode      editor view mode name ({@code EDITOR_ONLY}/{@code SPLIT}/{@code PREVIEW_ONLY}), or ""
 * @param sidebarVisible whether the sidebar was visible
 * @param focusMode     whether focus mode was active
 * @param splitMain     main split-pane divider position (sidebar | content)
 * @param splitContent  content split-pane divider position (notes list | editor)
 * @param storageMode   storage mode when saved ({@code filesystem}/{@code sqlite}); used to warn on mismatch
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.1.0
 */
public record Workspace(
        String id,
        String name,
        String createdAt,
        String updatedAt,
        List<String> openNoteIds,
        String activeNoteId,
        String viewMode,
        boolean sidebarVisible,
        boolean focusMode,
        double splitMain,
        double splitContent,
        String storageMode) {

    /** Field separator (ASCII Unit Separator) — never appears in ids, paths or names. */
    private static final char FS = '\u001F';
    /** List-item separator (ASCII Record Separator) for {@link #openNoteIds}. */
    private static final char US = '\u001E';
    private static final int FIELD_COUNT = 12;

    public Workspace {
        openNoteIds = openNoteIds != null ? List.copyOf(openNoteIds) : List.of();
    }

    /** Serializes this workspace to a single line (no trailing newline). */
    public String serialize() {
        return String.join(String.valueOf(FS),
                nz(id),
                nz(name),
                nz(createdAt),
                nz(updatedAt),
                nz(activeNoteId),
                nz(viewMode),
                Boolean.toString(sidebarVisible),
                Boolean.toString(focusMode),
                Double.toString(splitMain),
                Double.toString(splitContent),
                nz(storageMode),
                String.join(String.valueOf(US), openNoteIds));
    }

    /**
     * Parses a line produced by {@link #serialize()}.
     *
     * @return the workspace, or {@code null} when the line is blank or malformed
     */
    public static Workspace parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] f = line.split(String.valueOf(FS), -1);
        if (f.length != FIELD_COUNT) {
            return null;
        }
        List<String> openIds = new ArrayList<>();
        if (!f[11].isEmpty()) {
            for (String id : f[11].split(String.valueOf(US), -1)) {
                if (!id.isEmpty()) {
                    openIds.add(id);
                }
            }
        }
        return new Workspace(
                f[0], f[1], f[2], f[3],
                openIds,
                emptyToNull(f[4]),
                f[5],
                Boolean.parseBoolean(f[6]),
                Boolean.parseBoolean(f[7]),
                parseDouble(f[8]),
                parseDouble(f[9]),
                f[10]);
    }

    /** A copy with refreshed state fields and {@code updatedAt}, keeping id/name/createdAt. */
    public Workspace withState(List<String> openNoteIds, String activeNoteId, String viewMode,
            boolean sidebarVisible, boolean focusMode, double splitMain, double splitContent,
            String storageMode, String updatedAt) {
        return new Workspace(id, name, createdAt, updatedAt, openNoteIds, activeNoteId, viewMode,
                sidebarVisible, focusMode, splitMain, splitContent, storageMode);
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
