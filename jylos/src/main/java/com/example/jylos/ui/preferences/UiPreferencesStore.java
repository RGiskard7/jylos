package com.example.jylos.ui.preferences;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.prefs.Preferences;

import com.example.jylos.ui.theme.CssSnippetCatalog;

/**
 * Centralizes UI preference keys, defaults and persistence.
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

    public record UiPreferencesData(
            boolean autosaveEnabled,
            int autosaveIdleMs,
            String themeSource,
            String externalThemeId,
            int notesPreviewLines,
            int uiFontSize,
            String accentColor) {
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
                accent);
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

    public static String sanitizeAccent(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        return v.matches("#[0-9a-fA-F]{6}") ? v.toLowerCase(java.util.Locale.ROOT) : "";
    }

    public static int clampPreviewLines(int lines) {
        return Math.max(MIN_NOTES_PREVIEW_LINES, Math.min(MAX_NOTES_PREVIEW_LINES, lines));
    }

    public static int clampFontSize(int size) {
        return Math.max(MIN_UI_FONT_SIZE, Math.min(MAX_UI_FONT_SIZE, size));
    }
}
