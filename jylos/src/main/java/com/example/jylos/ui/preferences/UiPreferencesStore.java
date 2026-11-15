package com.example.jylos.ui.preferences;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.example.jylos.ui.theme.CssSnippetCatalog;

/**
 * Centralizes UI preference keys, defaults and persistence.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.0.0
 */
public final class UiPreferencesStore {

    public static final String AUTOSAVE_ENABLED_KEY = "ui.autosave.enabled";
    public static final String AUTOSAVE_IDLE_MS_KEY = "ui.autosave.idle_ms";
    public static final String THEME_SOURCE_KEY = "ui.theme.source";
    public static final String THEME_EXTERNAL_ID_KEY = "ui.theme.external.id";
    public static final String NOTES_PREVIEW_LINES_KEY = "ui.notes.preview.lines";
    public static final String UI_FONT_SIZE_KEY = "ui.font.size";
    public static final String SNIPPETS_ENABLED_KEY = "ui.snippets.enabled";
    public static final String UI_ACCENT_KEY = "ui.accent.color";
    public static final String MARKDOWN_LIVE_PREVIEW_KEY = "ui.editor.live_preview";
    public static final String READABLE_LINE_LENGTH_KEY = "ui.editor.readable_line_length";
    public static final String CONTENT_FONT_SIZE_KEY = "ui.content.font_size";
    public static final String SHOW_FOLDER_NOTE_COUNTS_KEY = "ui.sidebar.show_folder_note_counts";
    public static final String ALWAYS_OPEN_IN_NEW_TAB_KEY = "ui.tabs.always_new_tab";
    public static final String LAYOUT_MODE_KEY = "ui.layout.mode";
    public static final String SHOW_FORMAT_TOOLBAR_KEY = "ui.editor.format_toolbar";
    public static final String SPLIT_MAIN_KEY = "ui.split.main";
    public static final String SPLIT_CONTENT_KEY = "ui.split.content";
    public static final double DEFAULT_SPLIT_MAIN = 0.22;
    public static final double DEFAULT_SPLIT_CONTENT = 0.25;
    public static final String THEME_SOURCE_BUILTIN = "builtin";
    public static final String THEME_SOURCE_EXTERNAL = "external";
    public static final int DEFAULT_AUTOSAVE_IDLE_MS = 2000;
    public static final int DEFAULT_NOTES_PREVIEW_LINES = 2;
    public static final int MIN_NOTES_PREVIEW_LINES = 0;
    public static final int MAX_NOTES_PREVIEW_LINES = 5;
    public static final int DEFAULT_UI_FONT_SIZE = 13;
    public static final int MIN_UI_FONT_SIZE = 10;
    public static final int MAX_UI_FONT_SIZE = 22;
    /** Default matches both {@code MarkdownEditorView}'s own hardcoded CodeMirror default
     * and {@code MainController.editorFontSize}'s field default, kept in sync deliberately. */
    public static final int DEFAULT_CONTENT_FONT_SIZE = 14;
    public static final int MIN_CONTENT_FONT_SIZE = 10;
    public static final int MAX_CONTENT_FONT_SIZE = 24;

    /**
     * Snapshot of persisted UI preferences used to populate the Preferences dialog.
     *
     * @param autosaveEnabled whether autosave is enabled
     * @param autosaveIdleMs debounce delay before autosave runs
     * @param themeSource built-in or external theme source
     * @param externalThemeId selected external theme id, if any
     * @param notesPreviewLines number of preview lines shown in the notes list
     * @param uiFontSize base UI font size
     * @param accentColor optional custom accent color
     * @param livePreviewEnabled whether editing uses Live Preview instead of source mode
     * @param readableLineLength whether editor/preview content is capped to a centered, readable-width column
     * @param contentFontSize font size applied to the note editor (CodeMirror) and preview body text —
     *                        independent from {@code uiFontSize}, which only affects the native JavaFX chrome
     * @param showFolderNoteCounts whether the sidebar folder tree shows each folder's note count
     * @param alwaysOpenInNewTab whether every opened note gets its own tab instead of replacing the
     *                           note in the current tab (off by default, like VS Code)
     */
    public record UiPreferencesData(
            boolean autosaveEnabled,
            int autosaveIdleMs,
            String themeSource,
            String externalThemeId,
            int notesPreviewLines,
            int uiFontSize,
            String accentColor,
            boolean livePreviewEnabled,
            boolean readableLineLength,
            int contentFontSize,
            boolean showFolderNoteCounts,
            boolean alwaysOpenInNewTab) {
    }

    public UiPreferencesData load(Preferences prefs) {
        boolean autosaveEnabled = prefs == null || prefs.getBoolean(AUTOSAVE_ENABLED_KEY, true);
        int autosaveIdleMs = DEFAULT_AUTOSAVE_IDLE_MS;
        if (prefs != null) {
            int saved = prefs.getInt(AUTOSAVE_IDLE_MS_KEY, DEFAULT_AUTOSAVE_IDLE_MS);
            autosaveIdleMs = Math.max(500, Math.min(10000, saved));
        }
        String source = prefs != null ? prefs.get(THEME_SOURCE_KEY, THEME_SOURCE_BUILTIN) : THEME_SOURCE_BUILTIN;
        if (!THEME_SOURCE_EXTERNAL.equals(source)) {
            source = THEME_SOURCE_BUILTIN;
        }
        String externalId = prefs != null ? prefs.get(THEME_EXTERNAL_ID_KEY, "") : "";
        int previewLines = clampPreviewLines(
                prefs != null ? prefs.getInt(NOTES_PREVIEW_LINES_KEY, DEFAULT_NOTES_PREVIEW_LINES)
                        : DEFAULT_NOTES_PREVIEW_LINES);
        int fontSize = clampFontSize(
                prefs != null ? prefs.getInt(UI_FONT_SIZE_KEY, DEFAULT_UI_FONT_SIZE) : DEFAULT_UI_FONT_SIZE);
        String accent = sanitizeAccent(prefs != null ? prefs.get(UI_ACCENT_KEY, "") : "");
        return new UiPreferencesData(autosaveEnabled, autosaveIdleMs, source, externalId, previewLines, fontSize,
                accent, loadLivePreviewEnabled(prefs), loadReadableLineLength(prefs), loadContentFontSize(prefs),
                loadShowFolderNoteCounts(prefs),
                prefs != null && prefs.getBoolean(ALWAYS_OPEN_IN_NEW_TAB_KEY, false));
    }

    public void save(Preferences prefs, UiPreferencesData value) {
        if (prefs == null || value == null) {
            return;
        }
        prefs.putBoolean(AUTOSAVE_ENABLED_KEY, value.autosaveEnabled());
        prefs.putInt(AUTOSAVE_IDLE_MS_KEY, Math.max(500, Math.min(10000, value.autosaveIdleMs())));
        prefs.putInt(NOTES_PREVIEW_LINES_KEY, clampPreviewLines(value.notesPreviewLines()));
        prefs.putInt(UI_FONT_SIZE_KEY, clampFontSize(value.uiFontSize()));
        prefs.put(UI_ACCENT_KEY, sanitizeAccent(value.accentColor()));
        prefs.putBoolean(MARKDOWN_LIVE_PREVIEW_KEY, value.livePreviewEnabled());
        prefs.putBoolean(READABLE_LINE_LENGTH_KEY, value.readableLineLength());
        prefs.putInt(CONTENT_FONT_SIZE_KEY, clampContentFontSize(value.contentFontSize()));
        prefs.putBoolean(SHOW_FOLDER_NOTE_COUNTS_KEY, value.showFolderNoteCounts());
        prefs.putBoolean(ALWAYS_OPEN_IN_NEW_TAB_KEY, value.alwaysOpenInNewTab());

        String source = THEME_SOURCE_EXTERNAL.equals(value.themeSource()) ? THEME_SOURCE_EXTERNAL : THEME_SOURCE_BUILTIN;
        prefs.put(THEME_SOURCE_KEY, source);
        prefs.put(THEME_EXTERNAL_ID_KEY, value.externalThemeId() != null ? value.externalThemeId().trim() : "");
    }

    public Set<String> loadEnabledSnippets(Preferences prefs) {
        Set<String> enabled = new LinkedHashSet<>();
        if (prefs == null) {
            return enabled;
        }
        String raw = prefs.get(SNIPPETS_ENABLED_KEY, "");
        for (String name : raw.split("\n")) {
            String trimmed = name.trim();
            if (CssSnippetCatalog.isValidSnippetName(trimmed)) {
                enabled.add(trimmed);
            }
        }
        return enabled;
    }

    public void saveEnabledSnippets(Preferences prefs, Set<String> enabled) {
        if (prefs == null) {
            return;
        }
        if (enabled == null || enabled.isEmpty()) {
            prefs.put(SNIPPETS_ENABLED_KEY, "");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String name : enabled) {
            if (CssSnippetCatalog.isValidSnippetName(name)) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(name.trim());
            }
        }
        prefs.put(SNIPPETS_ENABLED_KEY, sb.toString());
    }

    /** Returns whether Markdown editing should use source-backed Live Preview. */
    public boolean loadLivePreviewEnabled(Preferences prefs) {
        return prefs == null || prefs.getBoolean(MARKDOWN_LIVE_PREVIEW_KEY, true);
    }

    /** Persists the Markdown presentation independently from editing/reading layout. */
    public void saveLivePreviewEnabled(Preferences prefs, boolean enabled) {
        if (prefs != null) {
            prefs.putBoolean(MARKDOWN_LIVE_PREVIEW_KEY, enabled);
        }
    }

    /** Whether editor/preview content is capped to a centered, readable-width column (Obsidian-style). */
    public boolean loadReadableLineLength(Preferences prefs) {
        return prefs != null && prefs.getBoolean(READABLE_LINE_LENGTH_KEY, false);
    }

    /** Font size for the note editor and preview body text, independent from {@code uiFontSize}. */
    public int loadContentFontSize(Preferences prefs) {
        return clampContentFontSize(
                prefs != null ? prefs.getInt(CONTENT_FONT_SIZE_KEY, DEFAULT_CONTENT_FONT_SIZE)
                        : DEFAULT_CONTENT_FONT_SIZE);
    }

    /** Persists editor/preview font size independently, so Ctrl+/- zoom on the editor survives a restart. */
    public void saveContentFontSize(Preferences prefs, int fontSize) {
        if (prefs != null) {
            prefs.putInt(CONTENT_FONT_SIZE_KEY, clampContentFontSize(fontSize));
        }
    }

    /** Whether the sidebar folder tree shows each folder's note count badge. */
    public boolean loadShowFolderNoteCounts(Preferences prefs) {
        return prefs == null || prefs.getBoolean(SHOW_FOLDER_NOTE_COUNTS_KEY, true);
    }

    public static String sanitizeAccent(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        return v.matches("#[0-9a-fA-F]{6}") ? v.toLowerCase(Locale.ROOT) : "";
    }

    public static int clampPreviewLines(int lines) {
        return Math.max(MIN_NOTES_PREVIEW_LINES, Math.min(MAX_NOTES_PREVIEW_LINES, lines));
    }

    public static int clampFontSize(int size) {
        return Math.max(MIN_UI_FONT_SIZE, Math.min(MAX_UI_FONT_SIZE, size));
    }

    public static int clampContentFontSize(int size) {
        return Math.max(MIN_CONTENT_FONT_SIZE, Math.min(MAX_CONTENT_FONT_SIZE, size));
    }

    /**
     * Decides the layout on the first start of a version that has the setting, and stores
     * it, so the guess happens once. Call it before anything else writes preferences: a node
     * that already holds keys means Jylos ran before (an upgrade), an empty one a new install.
     */
    public LayoutMode initLayoutMode(Preferences prefs) {
        String stored = prefs.get(LAYOUT_MODE_KEY, null);
        boolean existingInstall;
        try {
            existingInstall = prefs.keys().length > 0;
        } catch (BackingStoreException e) {
            existingInstall = true;
        }
        LayoutMode mode = LayoutMode.resolveDefault(stored, existingInstall);
        if (!mode.key().equals(stored)) {
            prefs.put(LAYOUT_MODE_KEY, mode.key());
        }
        return mode;
    }

    /** The stored layout; classic columns if none was stored (see {@link #initLayoutMode}). */
    public LayoutMode loadLayoutMode(Preferences prefs) {
        return LayoutMode.resolveDefault(prefs.get(LAYOUT_MODE_KEY, null), true);
    }

    public void saveLayoutMode(Preferences prefs, LayoutMode mode) {
        prefs.put(LAYOUT_MODE_KEY, (mode != null ? mode : LayoutMode.CLASSIC_COLUMNS).key());
    }

    /** Whether the format bar under the editor is shown; on unless the user turned it off. */
    public boolean loadShowFormatToolbar(Preferences prefs) {
        return prefs.getBoolean(SHOW_FORMAT_TOOLBAR_KEY, true);
    }

    public void saveShowFormatToolbar(Preferences prefs, boolean show) {
        prefs.putBoolean(SHOW_FORMAT_TOOLBAR_KEY, show);
    }
}
