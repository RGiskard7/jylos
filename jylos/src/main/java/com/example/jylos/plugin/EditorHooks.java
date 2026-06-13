package com.example.jylos.plugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Note;

/**
 * Concrete {@link EditorHookRegistry} and dispatcher for {@link EditorHook}s.
 *
 * <p>The shell owns one instance: {@code PluginManager} hands it to plugins as the
 * registry, and {@code EditorController} calls the {@code apply*}/{@code fire*}
 * methods at the editor's hook points. Hooks are chained in registration order; a
 * hook that throws is logged and skipped, so a faulty plugin can never break
 * editing or saving.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
public final class EditorHooks implements EditorHookRegistry {

    private static final Logger logger = LoggerConfig.getLogger(EditorHooks.class);

    /** pluginId → hooks, in registration order. Concurrent for safe un/registration. */
    private final Map<String, List<EditorHook>> hooksByPlugin = new ConcurrentHashMap<>();

    @Override
    public void registerEditorHook(String pluginId, EditorHook hook) {
        if (pluginId == null || hook == null) {
            return;
        }
        hooksByPlugin.computeIfAbsent(pluginId, k -> new CopyOnWriteArrayList<>()).add(hook);
        logger.fine("Editor hook registered by plugin: " + pluginId);
    }

    @Override
    public void unregisterEditorHooks(String pluginId) {
        if (pluginId != null && hooksByPlugin.remove(pluginId) != null) {
            logger.fine("Editor hooks removed for plugin: " + pluginId);
        }
    }

    /** Chains {@link EditorHook#onBeforeTextInsert} over all hooks. Never returns null. */
    public String applyBeforeTextInsert(Note note, String text) {
        String current = text;
        for (List<EditorHook> hooks : hooksByPlugin.values()) {
            for (EditorHook hook : hooks) {
                try {
                    String out = hook.onBeforeTextInsert(note, current);
                    if (out != null) {
                        current = out;
                    }
                } catch (Exception e) {
                    logger.warning("EditorHook onBeforeTextInsert failed: " + e.getMessage());
                }
            }
        }
        return current != null ? current : text;
    }

    /** Chains {@link EditorHook#onBeforeSave} over all hooks. Never returns null. */
    public String applyBeforeSave(Note note, String content) {
        String current = content;
        for (List<EditorHook> hooks : hooksByPlugin.values()) {
            for (EditorHook hook : hooks) {
                try {
                    String out = hook.onBeforeSave(note, current);
                    if (out != null) {
                        current = out;
                    }
                } catch (Exception e) {
                    logger.warning("EditorHook onBeforeSave failed: " + e.getMessage());
                }
            }
        }
        return current != null ? current : content;
    }

    /** Fires {@link EditorHook#onAfterSave} on all hooks (observation only). */
    public void fireAfterSave(Note note, String content) {
        for (List<EditorHook> hooks : hooksByPlugin.values()) {
            for (EditorHook hook : hooks) {
                try {
                    hook.onAfterSave(note, content);
                } catch (Exception e) {
                    logger.warning("EditorHook onAfterSave failed: " + e.getMessage());
                }
            }
        }
    }

    /** True when no plugin has hooks registered (lets callers skip work). */
    public boolean isEmpty() {
        return hooksByPlugin.isEmpty();
    }
}
