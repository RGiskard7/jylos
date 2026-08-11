package com.example.jylos.ui.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Note;
import com.example.jylos.plugin.EditorBlockRenderer;

/**
 * Produces the rendered HTML that the editor's Live Preview shows in place of fenced
 * blocks claimed by a plugin ({@link EditorBlockRenderer}).
 *
 * <h2>Why results are pushed, not pulled</h2>
 * <p>JavaScript inside a JavaFX {@code WebView} runs on the JavaFX Application Thread, so
 * a bridge call made from the editor's decoration loop would run the renderer — including
 * any note reading it does — on the UI thread, on every scroll tick that brings a block
 * into view. Instead the renderer runs here, on a background thread, and the finished
 * markup is handed to the editor as a plain lookup table; the editor never calls back.</p>
 *
 * <h2>Scheduling</h2>
 * <p>Renders are coalesced: typing schedules one after a short pause instead of one per
 * keystroke, while switching notes renders immediately so a block does not sit visibly as
 * source. Only the newest request survives — an older pending one is cancelled, and a
 * completed one is dropped if a newer request started while it was running.</p>
 */
final class EditorBlockRenderSupport {

    private static final Logger logger = LoggerConfig.getLogger(EditorBlockRenderSupport.class);

    /** Pause after the last keystroke before re-rendering blocks. */
    private static final long TYPING_DEBOUNCE_MS = 350;

    /**
     * A fenced block with an info string. The closing fence must repeat the opening one
     * exactly; a block whose fences do not pair off simply keeps showing its source,
     * which is the safe outcome for a half-typed block.
     */
    private static final Pattern FENCED_BLOCK = Pattern.compile(
            "^[ \\t]{0,3}(`{3,}|~{3,})[ \\t]*([A-Za-z0-9_+-]+)[ \\t]*$(.*?)^[ \\t]{0,3}\\1[ \\t]*$",
            Pattern.MULTILINE | Pattern.DOTALL);

    private final Map<String, Registration> renderersByLanguage = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "editor-block-render");
        thread.setDaemon(true);
        return thread;
    });

    private ScheduledFuture<?> pending;

    /** Guards against a slow render overwriting the result of a newer one. */
    private volatile long latestRequest;

    private record Registration(String pluginId, EditorBlockRenderer renderer) {
    }

    void registerRenderer(String pluginId, String language, EditorBlockRenderer renderer) {
        if (pluginId == null || language == null || language.isBlank() || renderer == null) {
            return;
        }
        renderersByLanguage.put(normalizeLanguage(language), new Registration(pluginId, renderer));
    }

    void unregisterRenderers(String pluginId) {
        if (pluginId != null) {
            renderersByLanguage.values().removeIf(registration -> pluginId.equals(registration.pluginId()));
        }
    }

    /** True when no plugin claims any language, letting callers skip the work entirely. */
    boolean isEmpty() {
        return renderersByLanguage.isEmpty();
    }

    /**
     * Schedules a render of every claimed block in {@code markdown}.
     *
     * @param note      the note being edited, passed through to the renderer
     * @param markdown  the current editor content
     * @param immediate {@code true} to render without the typing pause (note switches)
     * @param onRendered receives the block key to HTML table, on the scheduler thread
     */
    void requestRender(Note note, String markdown, boolean immediate, Consumer<Map<String, String>> onRendered) {
        if (onRendered == null) {
            return;
        }
        cancelPending();
        if (renderersByLanguage.isEmpty() || markdown == null || markdown.isEmpty()) {
            // Still deliver, so the editor drops results left over from the previous note.
            onRendered.accept(Map.of());
            return;
        }

        long request = ++latestRequest;
        pending = scheduler.schedule(() -> {
            Map<String, String> rendered = render(note, markdown);
            if (request == latestRequest) {
                onRendered.accept(rendered);
            }
        }, immediate ? 0 : TYPING_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    private void cancelPending() {
        ScheduledFuture<?> current = pending;
        if (current != null) {
            current.cancel(false);
            pending = null;
        }
    }

    void shutdown() {
        cancelPending();
        renderersByLanguage.clear();
        scheduler.shutdownNow();
    }

    private Map<String, String> render(Note note, String markdown) {
        Map<String, String> rendered = new LinkedHashMap<>();
        for (Block block : extractBlocks(markdown)) {
            Registration registration = renderersByLanguage.get(block.language());
            if (registration == null || rendered.containsKey(block.key())) {
                continue;
            }
            try {
                String html = registration.renderer().render(note, block.body());
                if (html != null && !html.isBlank()) {
                    rendered.put(block.key(), html);
                }
            } catch (RuntimeException e) {
                // A failing renderer leaves its block showing source; the note stays editable.
                logger.log(Level.WARNING, "Editor block renderer '" + registration.pluginId()
                        + "' failed for language '" + block.language() + "'", e);
            }
        }
        return rendered;
    }

    /** A claimed fenced block: its language, trimmed body, and the key the editor looks up by. */
    record Block(String language, String body) {

        String key() {
            return blockKey(language, body);
        }
    }

    /**
     * Key shared with the editor's Live Preview, which derives it from the syntax tree.
     * Both sides must agree exactly: lower-cased language, a newline, then the trimmed
     * body. The newline is unambiguous because an info string cannot contain one.
     */
    static String blockKey(String language, String body) {
        return normalizeLanguage(language) + "\n" + (body == null ? "" : body.trim());
    }

    static String normalizeLanguage(String language) {
        return language == null ? "" : language.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** Finds every fenced block carrying an info string, in document order. */
    static List<Block> extractBlocks(String markdown) {
        List<Block> blocks = new java.util.ArrayList<>();
        if (markdown == null || markdown.isEmpty()) {
            return blocks;
        }
        Matcher matcher = FENCED_BLOCK.matcher(markdown);
        while (matcher.find()) {
            blocks.add(new Block(normalizeLanguage(matcher.group(2)), matcher.group(3).trim()));
        }
        return blocks;
    }

    /** Languages currently claimed by a plugin, for diagnostics and tests. */
    List<String> languages() {
        return List.copyOf(renderersByLanguage.keySet());
    }

    @Override
    public String toString() {
        return "EditorBlockRenderSupport" + Objects.toString(renderersByLanguage.keySet());
    }
}
