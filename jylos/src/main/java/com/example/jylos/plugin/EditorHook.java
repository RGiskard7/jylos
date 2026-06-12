package com.example.jylos.plugin;

import com.example.jylos.data.models.Note;

/**
 * Editor lifecycle hook for plugins.
 *
 * <p>Hooks let a plugin transform or observe editor content at well-defined points.
 * All methods have safe defaults, so implementors override only what they need.
 * Multiple hooks are applied as a <em>chain</em> in registration order: each hook
 * receives the previous hook's output.</p>
 *
 * <h2>Scope and guarantees</h2>
 * <ul>
 *   <li>{@link #onBeforeTextInsert} fires for <b>programmatic snippet insertions</b>
 *       (link/image dialogs, wiki-link autocompletion, to-do/code-block templates) —
 *       <b>not</b> for individual keystrokes.</li>
 *   <li>{@link #onBeforeSave} fires right before a note's content is persisted; the
 *       returned string is what gets saved. Returning {@code null} keeps the content
 *       unchanged.</li>
 *   <li>{@link #onAfterSave} fires after a successful save, for observation only.</li>
 *   <li>Hooks run on the JavaFX Application Thread — keep them fast; offload heavy
 *       work to a background thread and never block.</li>
 *   <li>A hook that throws is skipped (logged) and never breaks editing or saving.</li>
 * </ul>
 *
 * <p>Register via {@link PluginContext#registerEditorHook(EditorHook)}; all hooks of a
 * plugin are removed automatically when it is disabled.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
public interface EditorHook {

    /**
     * Called before a snippet is programmatically inserted into the editor.
     *
     * @param note the note being edited (may be {@code null} if none is open)
     * @param text the snippet about to be inserted
     * @return the (possibly transformed) snippet; {@code null} keeps {@code text}
     */
    default String onBeforeTextInsert(Note note, String text) {
        return text;
    }

    /**
     * Called before the note content is persisted.
     *
     * @param note    the note being saved
     * @param content the content about to be saved
     * @return the (possibly transformed) content; {@code null} keeps {@code content}
     */
    default String onBeforeSave(Note note, String content) {
        return content;
    }

    /**
     * Called after the note has been persisted. Observation only.
     *
     * @param note    the saved note
     * @param content the content that was saved
     */
    default void onAfterSave(Note note, String content) {
        // observation hook — no-op by default
    }
}
