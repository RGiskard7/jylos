package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.plugin.EditorHook;
import com.example.jylos.plugin.EditorHooks;

/**
 * The editor-hook dispatcher must chain hooks in registration order, treat a null
 * return as "keep the previous value", survive throwing hooks, and remove a plugin's
 * hooks wholesale on unregister.
 */
class EditorHooksTest {

    @Test
    void chainsBeforeSaveInRegistrationOrder() {
        EditorHooks hooks = new EditorHooks();
        hooks.registerEditorHook("p1", new EditorHook() {
            @Override public String onBeforeSave(Note note, String content) {
                return content + "-a";
            }
        });
        hooks.registerEditorHook("p1", new EditorHook() {
            @Override public String onBeforeSave(Note note, String content) {
                return content + "-b";
            }
        });
        assertEquals("x-a-b", hooks.applyBeforeSave(new Note("t", "x"), "x"));
    }

    @Test
    void nullReturnKeepsPreviousValueAndThrowingHookIsSkipped() {
        EditorHooks hooks = new EditorHooks();
        hooks.registerEditorHook("p1", new EditorHook() {
            @Override public String onBeforeTextInsert(Note note, String text) {
                return null; // keep
            }
        });
        hooks.registerEditorHook("p2", new EditorHook() {
            @Override public String onBeforeTextInsert(Note note, String text) {
                throw new IllegalStateException("boom"); // must not break the chain
            }
        });
        hooks.registerEditorHook("p3", new EditorHook() {
            @Override public String onBeforeTextInsert(Note note, String text) {
                return text.toUpperCase(java.util.Locale.ROOT);
            }
        });
        assertEquals("HOLA", hooks.applyBeforeTextInsert(null, "hola"));
    }

    @Test
    void unregisterRemovesAllHooksOfAPlugin() {
        EditorHooks hooks = new EditorHooks();
        AtomicInteger afterSaves = new AtomicInteger();
        hooks.registerEditorHook("p1", new EditorHook() {
            @Override public void onAfterSave(Note note, String content) {
                afterSaves.incrementAndGet();
            }
        });
        hooks.fireAfterSave(new Note("t", "c"), "c");
        assertEquals(1, afterSaves.get());

        hooks.unregisterEditorHooks("p1");
        assertTrue(hooks.isEmpty());
        hooks.fireAfterSave(new Note("t", "c"), "c");
        assertEquals(1, afterSaves.get(), "no hooks should run after unregister");
    }
}
