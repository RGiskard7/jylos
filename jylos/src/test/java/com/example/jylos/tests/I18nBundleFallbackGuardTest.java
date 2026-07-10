package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

class I18nBundleFallbackGuardTest {

    private static final String[] BUNDLES = {
        "/com/example/jylos/i18n/messages.properties",
        "/com/example/jylos/i18n/messages_en.properties",
        "/com/example/jylos/i18n/messages_es.properties"
    };

    private static final String[] PALETTE_COMMAND_IDS = {
        "cmd.new_note",
        "cmd.new_canvas",
        "cmd.new_folder",
        "cmd.save",
        "cmd.save_all",
        "cmd.import",
        "cmd.export",
        "cmd.delete_note",
        "cmd.undo",
        "cmd.redo",
        "cmd.find",
        "cmd.replace",
        "cmd.cut",
        "cmd.copy",
        "cmd.paste",
        "cmd.bold",
        "cmd.italic",
        "cmd.underline",
        "cmd.insert_link",
        "cmd.insert_rich_link",
        "cmd.insert_image",
        "cmd.insert_todo",
        "cmd.insert_list",
        "cmd.toggle_sidebar",
        "cmd.graph_view",
        "cmd.knowledge_insights",
        "cmd.workspace_save",
        "cmd.workspace_save_as",
        "cmd.workspace_open",
        "cmd.workspace_manage",
        "cmd.git_panel",
        "cmd.git_sync",
        "cmd.git_commit_push",
        "cmd.git_pull",
        "cmd.git_init",
        "cmd.git_add_remote",
        "cmd.toggle_info_panel",
        "cmd.editor_mode",
        "cmd.preview_mode",
        "cmd.split_mode",
        "cmd.zoom_in",
        "cmd.zoom_out",
        "cmd.reset_zoom",
        "cmd.theme_light",
        "cmd.theme_dark",
        "cmd.theme_system",
        "cmd.quick_switcher",
        "cmd.global_search",
        "cmd.goto_all_notes",
        "cmd.goto_favorites",
        "cmd.goto_recent",
        "cmd.tag_manager",
        "cmd.preferences",
        "cmd.toggle_favorite",
        "cmd.refresh",
        "cmd.plugins.manage",
        "cmd.keyboard_shortcuts",
        "cmd.documentation",
        "cmd.about",
        "plugin.word-count.word-count-current-note",
        "plugin.word-count.word-count-all-notes",
        "plugin.reading-time.reading-time-current-note",
        "plugin.reading-time.reading-time-all-notes",
        "plugin.reading-time.reading-time-quick-estimate",
        "plugin.table-of-contents.toc-generate-table-of-contents",
        "plugin.table-of-contents.toc-preview-table-of-contents",
        "plugin.table-of-contents.toc-generate-numbered-toc",
        "plugin.templates.templates-new-from-template",
        "plugin.templates.templates-meeting-notes",
        "plugin.templates.templates-project-plan",
        "plugin.templates.templates-weekly-review",
        "plugin.templates.templates-checklist",
        "plugin.auto-backup.backup-export-all-notes",
        "plugin.auto-backup.backup-database-backup",
        "plugin.auto-backup.backup-full-backup",
        "plugin.auto-backup.backup-export-current-note",
        "plugin.calendar.calendar-show-hide-panel",
        "plugin.calendar.calendar-go-to-today",
        "plugin.calendar.calendar-refresh",
        "plugin.daily-notes.daily-notes-open-today",
        "plugin.daily-notes.daily-notes-open-yesterday",
        "plugin.daily-notes.daily-notes-open-tomorrow",
        "plugin.daily-notes.daily-notes-this-week",
        "plugin.outline.outline-refresh",
        "plugin.ai-assistant.ai-summarize-note",
        "plugin.ai-assistant.ai-translate-note",
        "plugin.ai-assistant.ai-improve-writing",
        "plugin.ai-assistant.ai-generate-content",
        "plugin.ai-assistant.ai-configure-api"
    };

    @Test
    void baseBundleShouldResolveCriticalKeysForUnsupportedLocale() {
        ResourceBundle bundle = ResourceBundle.getBundle(
                "com.example.jylos.i18n.messages",
                Locale.forLanguageTag("fr-FR"));

        assertNotNull(bundle);
        assertTrue(bundle.containsKey("app.all_notes"));
        assertTrue(bundle.containsKey("app.my_notes"));
        assertNotEquals("app.all_notes", bundle.getString("app.all_notes"));
    }

    /** Every language bundle must define exactly the same set of keys (ready for translation). */
    @Test
    void allBundlesHaveIdenticalKeySets() throws Exception {
        Set<String> reference = keysOf(BUNDLES[0]);
        for (String path : BUNDLES) {
            Set<String> keys = keysOf(path);
            Set<String> missing = new TreeSet<>(reference);
            missing.removeAll(keys);
            Set<String> extra = new TreeSet<>(keys);
            extra.removeAll(reference);
            assertTrue(missing.isEmpty() && extra.isEmpty(),
                    path + " key set diverges. Missing: " + missing + " Extra: " + extra);
        }
    }

    /** No duplicate keys in any bundle (a Properties load would silently drop them). */
    @Test
    void bundlesHaveNoDuplicateKeys() throws Exception {
        for (String path : BUNDLES) {
            int unique;
            try (InputStream in = getClass().getResourceAsStream(path)) {
                assertNotNull(in, "missing bundle: " + path);
                Properties props = new Properties();
                props.load(in);
                unique = props.size();
            }
            long rawKeyLines;
            try (InputStream in = getClass().getResourceAsStream(path)) {
                rawKeyLines = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                        .lines()
                        .filter(l -> l.matches("^[A-Za-z][A-Za-z0-9._-]*=.*"))
                        .count();
            }
            assertTrue(rawKeyLines == unique,
                    path + " has duplicate keys (" + rawKeyLines + " key lines vs " + unique + " unique).");
        }
    }

    @Test
    void commandPaletteCommandsHaveLocalizedNameAndDescription() throws Exception {
        for (String path : BUNDLES) {
            Set<String> keys = keysOf(path);
            for (String commandId : PALETTE_COMMAND_IDS) {
                assertTrue(keys.contains("palette.command." + commandId + ".name"),
                        path + " missing command name for " + commandId);
                assertTrue(keys.contains("palette.command." + commandId + ".desc"),
                        path + " missing command description for " + commandId);
            }
        }
    }

    private Set<String> keysOf(String resourcePath) throws Exception {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            assertNotNull(in, "missing bundle: " + resourcePath);
            props.load(in);
        }
        return new TreeSet<>(props.stringPropertyNames());
    }
}
