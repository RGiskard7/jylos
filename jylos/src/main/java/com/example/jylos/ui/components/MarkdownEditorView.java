package com.example.jylos.ui.components;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.util.MarkdownPreview;
import com.example.jylos.util.WikiLinkResolver;
import com.google.gson.Gson;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

/**
 * JavaFX host for the offline CodeMirror 6 Markdown editor.
 *
 * <p>The component is the only Java-to-JavaScript boundary for text editing. It
 * owns the embedded {@link WebView}, keeps a Java-side text snapshot, and exposes
 * typed operations used by the editor controller. Domain state and persistence
 * remain outside this class.</p>
 *
 * @author Edu Diaz (RGiskard7)
 * @since 2.5.0
 */
public final class MarkdownEditorView extends StackPane {

    private static final Logger logger = LoggerConfig.getLogger(MarkdownEditorView.class);
    private static final Gson GSON = new Gson();
    private static final String HTML_RESOURCE = "/com/example/jylos/ui/editor/editor.html";
    private static final String BUNDLE_RESOURCE = "/com/example/jylos/ui/editor/editor.bundle.js";
    private static final String BUNDLE_PLACEHOLDER = "/*__JYLOS_EDITOR_BUNDLE__*/";

    private final WebView webView = new WebView();
    private final WebEngine engine = webView.getEngine();
    private final EditorBridge bridge = new EditorBridge();
    private final Queue<Runnable> pendingActions = new ArrayDeque<>();
    private final ContextMenu editorContextMenu = new ContextMenu();
    private final MenuItem undoMenuItem = new MenuItem();
    private final MenuItem redoMenuItem = new MenuItem();
    private final MenuItem cutMenuItem = new MenuItem();
    private final MenuItem copyMenuItem = new MenuItem();
    private final MenuItem pasteMenuItem = new MenuItem();
    private final MenuItem selectAllMenuItem = new MenuItem();

    private Consumer<String> textChangeListener = text -> {
    };
    private Consumer<String> wikiLinkHandler = title -> {
    };
    private Consumer<String> externalLinkHandler = url -> {
    };
    private Function<String, String> imageSourceResolver = source -> "";
    private UnaryOperator<String> insertionTransformer = UnaryOperator.identity();
    private String text = "";
    private boolean ready;
    private boolean darkTheme;
    private String accent = "";
    private double fontSize = 14;
    private boolean livePreviewEnabled = true;
    private boolean readableLineLength = false;
    private boolean editable;
    /** Plugin-rendered fenced blocks for the current document; see {@link #setBlockRenders}. */
    private Map<String, String> blockRenders = Map.of();
    private Map<String, String> labels = Map.of(
            "undo", "Undo",
            "redo", "Redo",
            "cut", "Cut",
            "copy", "Copy",
            "paste", "Paste",
            "selectAll", "Select All");

    public MarkdownEditorView() {
        getStyleClass().add("markdown-editor-view");
        webView.getStyleClass().add("markdown-editor-webview");
        webView.setContextMenuEnabled(false);
        webView.setMinSize(0, 0);
        webView.setPrefSize(0, 0);
        getChildren().add(webView);
        installDesktopEditingIntegration();

        engine.setJavaScriptEnabled(true);
        engine.setOnError(event -> logger.warning("CodeMirror WebView error: " + event.getMessage()));
        engine.setOnAlert(event -> logger.warning("CodeMirror alert: " + event.getData()));
        engine.getLoadWorker().exceptionProperty().addListener((obs, oldError, error) -> {
            if (error != null) {
                logger.log(Level.SEVERE, "Could not load CodeMirror editor", error);
            }
        });
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                initializeBridge();
            }
        });
        engine.loadContent(loadEditorHtml(), "text/html");
    }

    /** Sets the listener invoked after user or command-driven document changes. */
    public void setTextChangeListener(Consumer<String> listener) {
        textChangeListener = listener != null ? listener : text -> {
        };
    }

    /** Sets the plugin-backed transformation used by semantic editor insertions. */
    public void setInsertionTransformer(UnaryOperator<String> transformer) {
        insertionTransformer = transformer != null ? transformer : UnaryOperator.identity();
    }

    /** Sets the handlers used by links rendered inside editable Live Preview. */
    public void setLinkHandlers(Consumer<String> wikiLinks, Consumer<String> externalLinks) {
        wikiLinkHandler = wikiLinks != null ? wikiLinks : title -> {
        };
        externalLinkHandler = externalLinks != null ? externalLinks : url -> {
        };
    }

    /** Sets the resolver that converts relative Markdown image paths to WebView-safe URIs. */
    public void setImageSourceResolver(Function<String, String> resolver) {
        imageSourceResolver = resolver != null ? resolver : source -> "";
    }

    /** Replaces the document and starts a fresh undo history. */
    public void setText(String value) {
        text = value != null ? value : "";
        if (ready) {
            // setDocument() builds a fresh CodeMirror state, which discards the block
            // renders held in it. They belong to the previous document anyway; the owning
            // controller pushes the new note's renders right after loading it.
            blockRenders = Map.of();
            execute("window.JylosEditor.setDocument(" + GSON.toJson(text) + ")");
        }
    }

    /**
     * Supplies the HTML the Live Preview shows in place of plugin-claimed fenced blocks,
     * keyed as {@code language + "\n" + trimmed body}.
     *
     * <p>Pushed from the owning controller rather than fetched by the editor: JavaScript
     * in a {@code WebView} runs on the JavaFX Application Thread, so letting the editor
     * call back into Java while building decorations would run plugin code — and its I/O —
     * on the UI thread during scrolling.</p>
     *
     * @param renders block key to HTML; an empty map clears any previous results
     */
    public void setBlockRenders(Map<String, String> renders) {
        blockRenders = renders != null ? Map.copyOf(renders) : Map.of();
        whenReady(() -> execute("window.JylosEditor.setBlockRenders(" + GSON.toJson(blockRenders) + ")"));
    }

    /** Returns the latest editor snapshot without crossing the JavaScript bridge. */
    public String getText() {
        return text;
    }

    public void clear() {
        setText("");
    }

    public String getSelectedText() {
        return stringResult("window.JylosEditor.getSelectedText()", "");
    }

    public int getSelectionStart() {
        return intResult("window.JylosEditor.getSelectionFrom()", 0);
    }

    public int getSelectionEnd() {
        return intResult("window.JylosEditor.getSelectionTo()", getSelectionStart());
    }

    public int getCaretPosition() {
        return intResult("window.JylosEditor.getCaretPosition()", 0);
    }

    public void replaceSelection(String value) {
        replaceSelection(value, -1);
    }

    public void replaceSelection(String value, int cursorOffset) {
        String insertion = value != null ? value : "";
        whenReady(() -> execute("window.JylosEditor.replaceSelection("
                + GSON.toJson(insertion) + "," + cursorOffset + ")"));
    }

    public void replaceRange(int from, int to, String value, int caretPosition) {
        String insertion = value != null ? value : "";
        whenReady(() -> execute("window.JylosEditor.replaceRange("
                + from + "," + to + "," + GSON.toJson(insertion) + "," + caretPosition + ")"));
    }

    /** Applies a whole-document transformation as one undoable edit. */
    public void replaceDocument(String value) {
        String replacement = value != null ? value : "";
        whenReady(() -> execute("window.JylosEditor.replaceDocument(" + GSON.toJson(replacement) + ")"));
    }

    public boolean undo() {
        return editable && booleanResult("window.JylosEditor.undo()", false);
    }

    public boolean redo() {
        return editable && booleanResult("window.JylosEditor.redo()", false);
    }

    public void cut() {
        if (!editable) return;
        whenReady(() -> execute("window.JylosEditor.cut()"));
    }

    public void copy() {
        whenReady(() -> execute("window.JylosEditor.copy()"));
    }

    public void paste() {
        if (!editable) return;
        whenReady(() -> execute("window.JylosEditor.paste()"));
    }

    public void selectAll() {
        whenReady(() -> execute("window.JylosEditor.selectAll()"));
    }

    public void openSearch() {
        whenReady(() -> execute("window.JylosEditor.openSearch()"));
    }

    public void openReplace() {
        whenReady(() -> execute("window.JylosEditor.openReplace()"));
    }

    public void setAutocompleteTitles(List<String> titles) {
        List<String> snapshot = titles != null ? List.copyOf(titles) : List.of();
        whenReady(() -> execute("window.JylosEditor.setAutocompleteTitles(" + GSON.toJson(snapshot) + ")"));
    }

    public void setEditorLabels(Map<String, String> values) {
        labels = values != null ? Map.copyOf(values) : Map.of();
        updateContextMenuLabels();
        whenReady(() -> execute("window.JylosEditor.setLabels(" + GSON.toJson(labels) + ")"));
    }

    public void setEditorTheme(boolean dark, String accentColor) {
        darkTheme = dark;
        accent = accentColor != null ? accentColor : "";
        whenReady(() -> execute("window.JylosEditor.setTheme("
                + GSON.toJson(Map.of("dark", darkTheme, "accent", accent)) + ")"));
    }

    public void setEditorFontSize(double size) {
        fontSize = Math.max(10, Math.min(36, size));
        whenReady(() -> execute("window.JylosEditor.setFontSize(" + fontSize + ")"));
    }

    /** Places the caret at the given character offset and scrolls it into view. */
    public void scrollToPosition(int offset) {
        int safeOffset = Math.max(0, offset);
        whenReady(() -> execute("window.JylosEditor.scrollToOffset(" + safeOffset + ")"));
    }

    /** Enables or disables source-backed Live Preview without replacing the document. */
    public void setLivePreviewEnabled(boolean enabled) {
        livePreviewEnabled = enabled;
        whenReady(() -> execute("window.JylosEditor.setLivePreviewEnabled(" + enabled + ")"));
    }

    /** Returns the selected Markdown presentation mode. */
    public boolean isLivePreviewEnabled() {
        return livePreviewEnabled;
    }

    /** Obsidian-style centered content column: caps editor width, margins fill the rest. */
    public void setReadableLineLength(boolean enabled) {
        readableLineLength = enabled;
        whenReady(() -> execute("window.JylosEditor.setReadableLineLength(" + enabled + ")"));
    }

    /** Sets whether CodeMirror accepts document-changing commands and input. */
    public void setEditable(boolean value) {
        editable = value;
        whenReady(() -> execute("window.JylosEditor.setEditable(" + value + ")"));
    }

    /** Returns the editability selected by the owning editor controller. */
    public boolean isEditable() {
        return editable;
    }

    public void resetUndoHistory() {
        setText(text);
    }

    @Override
    public void requestFocus() {
        super.requestFocus();
        whenReady(() -> execute("window.JylosEditor.focus()"));
    }

    public WebView getWebView() {
        return webView;
    }

    private void initializeBridge() {
        try {
            JSObject window = (JSObject) engine.executeScript("window");
            window.setMember("javaEditor", bridge);
            Map<String, Object> config = Map.of(
                    "dark", darkTheme,
                    "accent", accent,
                    "fontSize", fontSize,
                    "labels", labels,
                    "livePreview", livePreviewEnabled,
                    "readableLineLength", readableLineLength,
                    "editable", editable);
            execute("window.JylosEditor.initialize(" + GSON.toJson(config) + ")");
            ready = true;
            execute("window.JylosEditor.setDocument(" + GSON.toJson(text) + ")");
            while (!pendingActions.isEmpty()) {
                pendingActions.remove().run();
            }
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not initialize CodeMirror bridge", e);
        }
    }

    /**
     * Keeps desktop text conventions outside WebKit: JavaFX owns the native menu
     * and platform shortcuts, while CodeMirror remains the sole document editor.
     */
    private void installDesktopEditingIntegration() {
        undoMenuItem.setOnAction(event -> undo());
        redoMenuItem.setOnAction(event -> redo());
        cutMenuItem.setOnAction(event -> cut());
        copyMenuItem.setOnAction(event -> copy());
        pasteMenuItem.setOnAction(event -> paste());
        selectAllMenuItem.setOnAction(event -> selectAll());
        editorContextMenu.getItems().setAll(
                undoMenuItem,
                redoMenuItem,
                new SeparatorMenuItem(),
                cutMenuItem,
                copyMenuItem,
                pasteMenuItem,
                new SeparatorMenuItem(),
                selectAllMenuItem);
        editorContextMenu.setOnShowing(event -> refreshContextMenuState());
        updateContextMenuLabels();

        webView.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            if (!ready) {
                return;
            }
            refreshContextMenuState();
            editorContextMenu.show(webView, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        webView.addEventFilter(KeyEvent.KEY_PRESSED, this::handleDesktopShortcut);
    }

    private void handleDesktopShortcut(KeyEvent event) {
        // JavaFX-side focus is authoritative here: CodeMirror's own internal
        // `document.activeElement` state (queried via the JS bridge) can lag a
        // tick behind after switching notes/panels, silently dropping the
        // shortcut since JavaFX WebView has no native clipboard paste to fall
        // back on for a contentEditable element.
        if (!ready || !event.isShortcutDown() || event.isAltDown() || !webView.isFocused()) {
            return;
        }

        KeyCode code = event.getCode();
        if (code == KeyCode.C) {
            copy();
        } else if (code == KeyCode.A) {
            selectAll();
        } else if (!editable) {
            return;
        } else if (code == KeyCode.X) {
            cut();
        } else if (code == KeyCode.V) {
            paste();
        } else if (code == KeyCode.Z) {
            if (event.isShiftDown()) {
                redo();
            } else {
                undo();
            }
        } else if (code == KeyCode.Y) {
            redo();
        } else {
            return;
        }
        event.consume();
    }

    private void refreshContextMenuState() {
        boolean hasSelection = booleanResult("window.JylosEditor.hasSelection()", false);
        undoMenuItem.setDisable(!editable || !booleanResult("window.JylosEditor.canUndo()", false));
        redoMenuItem.setDisable(!editable || !booleanResult("window.JylosEditor.canRedo()", false));
        cutMenuItem.setDisable(!editable || !hasSelection);
        copyMenuItem.setDisable(!hasSelection);
        pasteMenuItem.setDisable(!editable || !Clipboard.getSystemClipboard().hasString());
        selectAllMenuItem.setDisable(text.isEmpty());
    }

    private void updateContextMenuLabels() {
        undoMenuItem.setText(label("undo", "Undo"));
        redoMenuItem.setText(label("redo", "Redo"));
        cutMenuItem.setText(label("cut", "Cut"));
        copyMenuItem.setText(label("copy", "Copy"));
        pasteMenuItem.setText(label("paste", "Paste"));
        selectAllMenuItem.setText(label("selectAll", "Select All"));
    }

    private String label(String key, String fallback) {
        String value = labels.get(key);
        return value != null && !value.isBlank() ? value : fallback;
    }

    private String loadEditorHtml() {
        String html = readResource(HTML_RESOURCE);
        String bundle = readResource(BUNDLE_RESOURCE);
        if (!html.contains(BUNDLE_PLACEHOLDER)) {
            throw new IllegalStateException("CodeMirror HTML template is missing its bundle placeholder");
        }
        return html.replace(BUNDLE_PLACEHOLDER, bundle);
    }

    private String readResource(String resource) {
        try (InputStream stream = MarkdownEditorView.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing editor resource: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read editor resource: " + resource, e);
        }
    }

    void whenReady(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (ready) {
            action.run();
        } else {
            pendingActions.add(action);
        }
    }

    private Object execute(String script) {
        try {
            return engine.executeScript(script);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "CodeMirror command failed", e);
            return null;
        }
    }

    private String stringResult(String script, String fallback) {
        if (!ready) {
            return fallback;
        }
        Object value = execute(script);
        return value instanceof String string ? string : fallback;
    }

    private int intResult(String script, int fallback) {
        if (!ready) {
            return fallback;
        }
        Object value = execute(script);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private boolean booleanResult(String script, boolean fallback) {
        if (!ready) {
            return fallback;
        }
        Object value = execute(script);
        return value instanceof Boolean result ? result : fallback;
    }

    private String transformInsertion(String value) {
        String input = value != null ? value : "";
        String transformed = insertionTransformer.apply(input);
        return transformed != null ? transformed : input;
    }

    /** Java object exposed only to the locally bundled editor page. */
    public final class EditorBridge {
        public void onDocumentChanged(String content) {
            text = content != null ? content : "";
            textChangeListener.accept(text);
        }

        public String transformInsertion(String content) {
            return MarkdownEditorView.this.transformInsertion(content);
        }

        public void copyToClipboard(String content) {
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(content != null ? content : "");
            Clipboard.getSystemClipboard().setContent(clipboardContent);
        }

        public String readClipboard() {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            return clipboard.hasString() ? clipboard.getString() : "";
        }

        public void openNote(String title) {
            String resolvedTitle = WikiLinkResolver.internalNoteTarget(title);
            Platform.runLater(() -> wikiLinkHandler.accept(resolvedTitle != null
                    ? resolvedTitle
                    : title != null ? title : ""));
        }

        public void openExternal(String url) {
            Platform.runLater(() -> externalLinkHandler.accept(url != null ? url : ""));
        }

        public void openMarkdownLink(String target) {
            String title = WikiLinkResolver.internalNoteTarget(target);
            if (title != null) {
                Platform.runLater(() -> wikiLinkHandler.accept(title));
            } else {
                Platform.runLater(() -> externalLinkHandler.accept(target != null ? target : ""));
            }
        }

        public String resolveImageSource(String source) {
            String resolved = imageSourceResolver.apply(source != null ? source : "");
            return resolved != null ? resolved : "";
        }

        /**
         * Rasterises an emoji run for the editor's emoji decoration, reusing the same
         * cache and bundled font as the Preview (see {@link MarkdownPreview}) — the
         * WebView's own font rendering cannot show colour emoji.
         */
        public String rasterizeEmoji(String run) {
            String dataUri = MarkdownPreview.rasterizeEmojiRun(run, darkTheme);
            return dataUri != null ? dataUri : "";
        }

        public void onEditorError(String message) {
            Platform.runLater(() -> logger.severe("CodeMirror error: " + message));
        }
    }
}
