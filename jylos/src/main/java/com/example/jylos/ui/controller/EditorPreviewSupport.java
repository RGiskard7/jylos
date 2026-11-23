package com.example.jylos.ui.controller;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Note;
import com.example.jylos.plugin.PreviewContext;
import com.example.jylos.plugin.PreviewEnhancer;
import com.example.jylos.service.LinkIndex;
import com.example.jylos.service.NoteService;
import com.example.jylos.ui.components.WebViewImages;
import com.example.jylos.ui.theme.ActiveThemePalette;
import com.example.jylos.ui.web.HtmlView;
import com.example.jylos.util.MarkdownPreview;
import com.example.jylos.util.MarkdownProcessor;
import com.example.jylos.util.SystemBrowser;
import com.example.jylos.util.ThemePalette;
import com.example.jylos.util.Transclusion;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.util.Duration;

/**
 * The editor's reading view: renders the open note off the FX thread into the preview
 * {@link HtmlView}, keeps the reader's place when the same note is drawn again, jumps to a
 * heading once its page has loaded, and answers the page's clicks and failed images through
 * its Java bridge. Plugin preview enhancers are registered here.
 *
 * <p>Public, as is {@link PreviewJavaBridge}: JavaScript in a WebView only reaches methods of
 * a class that is public all the way out.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class EditorPreviewSupport {

    private static final Logger logger = LoggerConfig.getLogger(EditorPreviewSupport.class);

    /** How long a newly opened note's render may take before the previous note's preview is cleared. */
    private static final Duration STALE_PREVIEW_GRACE = Duration.millis(200);

    /**
     * Ids assigned by MarkdownProcessor's heading AttributeProvider: lowercase Unicode
     * letters/digits (accented letters included) and hyphens.
     */
    private static final Pattern VALID_HEADING_SLUG = Pattern.compile("[\\p{L}\\p{N}-]+");

    /** What the reading view reads from the editor that owns it. */
    interface Host {
        HtmlView previewWebView();

        /** Whether the reading view is on screen (shown, and not replaced by an attachment). */
        boolean previewShown();

        Note currentNote();

        /** The text being edited, which may be newer than the note's saved content. */
        String liveContent();

        NoteService noteService();

        boolean readableLineLength();

        double contentFontSize();

        /** Follows a wiki-link clicked in the page; in a new tab with Cmd/Ctrl or the middle button. */
        void openLink(String link, boolean newTab);

        /** Opens the notes with a tag clicked in the page (without {@code #}). */
        void openTag(String tag);
    }

    private final Host host;
    private final Map<String, PreviewEnhancer> enhancers = new HashMap<>();
    private final PreviewJavaBridge bridge = new PreviewJavaBridge();

    /**
     * Runs preview renders off the FX thread, on reused threads (no thread creation on
     * every pause in typing). Not a single thread: a render can block for a long time on
     * file I/O — an image or embedded note in a cloud folder (iCloud, OneDrive) that is
     * not downloaded yet — and cancelling it does not interrupt that read. Queued behind
     * it, the next note's render would wait too, leaving the previous note on screen.
     * Only the newest render is ever shown (see the {@code currentTask} checks).
     */
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "preview-render");
        thread.setDaemon(true);
        return thread;
    });

    /** Cancels any in-flight render so the FX thread is never blocked by Markdown parsing. */
    private volatile Task<String> currentTask;
    /**
     * Theme to draw in: the one last rendered or applied, so a render that has no caller
     * (a preference toggle, a plugin, a view-mode change) uses the theme on screen.
     */
    private boolean lastDarkTheme = false;
    private boolean bridgeInstalled;
    private Double pendingScrollY;
    private String pendingNoteId;
    /** Where the next page of {@link #requestedNoteId} has to show (see {@link #showAt}). */
    private Double requestedScrollY;
    private String requestedNoteId;
    private String renderedNoteId;
    /** A heading or block to scroll to once the page of {@link #pendingAnchorNoteId} loads. */
    private String pendingAnchor;
    private String pendingAnchorNoteId;

    EditorPreviewSupport(Host host) {
        this.host = host;
    }

    boolean lastDarkTheme() {
        return lastDarkTheme;
    }

    /** Remembers the theme to draw in without drawing; the next render uses it. */
    void setDarkTheme(boolean darkTheme) {
        lastDarkTheme = darkTheme;
    }

    void registerEnhancer(String pluginId, PreviewEnhancer enhancer) {
        if (pluginId != null && enhancer != null) {
            enhancers.put(pluginId, enhancer);
            // Drawn again in the theme it is shown in.
            Platform.runLater(() -> refresh(lastDarkTheme));
        }
    }

    void unregisterEnhancer(String pluginId) {
        if (pluginId != null) {
            enhancers.remove(pluginId);
            Platform.runLater(() -> refresh(lastDarkTheme));
        }
    }

    void refresh(boolean darkTheme) {
        HtmlView webView = host.previewWebView();
        Note note = host.currentNote();
        // A plugin's own shutdown() can call unregisterEnhancer(), which queues this via
        // Platform.runLater — that queued call always lands on a *later* tick of the event
        // loop, after shutdown() has already stopped the executor during app shutdown, no
        // matter what order those two run in beforehand. Without this guard, execute() below
        // throws RejectedExecutionException for every plugin that has a preview enhancer.
        if (webView == null || note == null || !host.previewShown() || executor.isShutdown()) {
            return;
        }
        lastDarkTheme = darkTheme;

        Task<String> prev = currentTask;
        if (prev != null) {
            prev.cancel();
        }

        String content = host.liveContent();
        String noteId = note.getId();
        boolean preserveScroll = noteId != null && noteId.equals(renderedNoteId);
        double scrollY;
        if (noteId != null && noteId.equals(requestedNoteId)) {
            scrollY = requestedScrollY;
            requestedNoteId = null;
            requestedScrollY = null;
        } else {
            scrollY = preserveScroll ? currentScrollY() : 0;
        }
        // Captured on the FX thread so an enhancer post-processing this render always sees
        // the note this HTML came from, even if the user switches note mid-render.
        Collection<PreviewEnhancer> renderEnhancers = new ArrayList<>(enhancers.values());
        Path baseDir = baseDir(note);
        NoteService notes = host.noteService();
        Path containmentRoot = imageContainmentRoot();
        PreviewContext context = new PreviewContext(note, darkTheme);
        boolean readable = host.readableLineLength();
        int fontSize = (int) Math.round(host.contentFontSize());
        ThemePalette palette = ActiveThemePalette.get();

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                if (content != null && !content.trim().isEmpty()) {
                    // The vault's warm link index (NoteTitleIndex), read off the FX thread.
                    LinkIndex links = notes != null ? notes.linkIndex() : null;
                    return MarkdownPreview.buildPreviewHtml(content, darkTheme, renderEnhancers,
                            new MarkdownPreview.PreviewOptions(baseDir, link -> embedContent(link, note),
                                    context, readable, fontSize, containmentRoot, links, palette)
                                    .withImageLocator(imageLocator(notes, noteId)));
                }
                return MarkdownPreview.buildEmptyHtml(darkTheme, palette);
            }
        };
        task.setOnSucceeded(e -> {
            if (task != currentTask || task.isCancelled() || !host.previewShown()) {
                return;
            }
            pendingScrollY = scrollY;
            pendingNoteId = noteId;
            webView.loadHtml(task.getValue());
            installBridge(webView);
        });
        task.setOnCancelled(e -> {
            if (currentTask == task) {
                currentTask = null;
            }
        });
        task.setOnFailed(e -> {
            if (currentTask == task) {
                currentTask = null;
            }
            logger.warning("Preview render failed: " + task.getException());
        });
        currentTask = task;
        executor.execute(task);
        if (noteId == null || !noteId.equals(renderedNoteId)) {
            blankIfStillRendering(task, darkTheme);
        }
    }

    /**
     * On switching notes the WebView still holds the previous note's page until the new
     * render lands. Renders are normally a few milliseconds, so that is invisible; when
     * one is slow (files still downloading from a cloud folder), show an empty page rather
     * than the previous note's content under the new note's title.
     */
    private void blankIfStillRendering(Task<String> task, boolean darkTheme) {
        PauseTransition grace = new PauseTransition(STALE_PREVIEW_GRACE);
        grace.setOnFinished(e -> {
            if (task == currentTask && !task.isDone() && host.previewShown()) {
                renderedNoteId = null;
                host.previewWebView().loadHtml(MarkdownPreview.buildEmptyHtml(darkTheme, ActiveThemePalette.get()));
            }
        });
        grace.play();
    }

    /**
     * The next page drawn for {@code noteId} shows at {@code scrollY} (where it was left, or 0:
     * its top) instead of where the page on screen is. A jump to a heading still wins.
     */
    void showAt(String noteId, double scrollY) {
        requestedNoteId = noteId;
        requestedScrollY = Math.max(0, scrollY);
    }

    /** Where the reading view is scrolled while it shows {@code noteId}'s page; 0 otherwise. */
    double scrollY(String noteId) {
        HtmlView webView = host.previewWebView();
        if (webView == null || noteId == null || !noteId.equals(renderedNoteId) || !host.previewShown()) {
            return 0;
        }
        return currentScrollY();
    }

    private double currentScrollY() {
        Object value = host.previewWebView().evaluate("window.scrollY || 0");
        return value instanceof Number number ? Math.max(0, number.doubleValue()) : 0;
    }

    /** The HTML of {@code ![[link]]} for Live Preview: the same embed the reading view draws. */
    String renderEditorEmbed(String link) {
        Note source = host.currentNote();
        String sourceId = source != null ? source.getId() : null;
        NoteService notes = host.noteService();
        LinkIndex links = notes != null ? notes.linkIndex() : LinkIndex.EMPTY;
        Transclusion.Result embed = Transclusion.protect("![[" + link + "]]", l -> embedContent(l, source),
                candidate -> links.exists(candidate, sourceId));
        return Transclusion.restore(MarkdownProcessor.markdownToHtml(embed.markdown()), embed.embeds()).strip();
    }

    /** The Markdown of an embedded note: its link as written, resolved by path from {@code source}. */
    private String embedContent(String link, Note source) {
        NoteService notes = host.noteService();
        if (notes == null || link == null || link.isBlank()) {
            return null;
        }
        return notes.resolveLink(link, source != null ? source.getId() : null)
                .flatMap(note -> notes.getNoteById(note.getId()))
                .map(Note::getContent)
                .orElse(null);
    }

    /**
     * Finds an image the open note names that is not where its path says, as Obsidian does:
     * by its name, in the note's folder first, else the shortest path in the vault.
     */
    Function<String, Optional<Path>> imageLocator() {
        Note note = host.currentNote();
        return imageLocator(host.noteService(), note != null ? note.getId() : null);
    }

    private static Function<String, Optional<Path>> imageLocator(NoteService notes, String noteId) {
        return name -> notes != null ? notes.resolveAttachmentPath(name, noteId) : Optional.empty();
    }

    /** Folder of the open note: where its relative image paths start (editor and reading view). */
    Path imageBaseDir() {
        return baseDir(host.currentNote());
    }

    /**
     * Bound of the local images a note may inline: the vault root; {@code null} (SQLite)
     * contains the lookup to the note's own folder.
     */
    Path imageContainmentRoot() {
        NoteService notes = host.noteService();
        return notes != null ? notes.getStorageRoot().orElse(null) : null;
    }

    /** Folder of the note, used to resolve relative image paths in the preview. */
    private Path baseDir(Note note) {
        NoteService notes = host.noteService();
        if (notes == null || note == null || note.getId() == null) {
            return null;
        }
        return notes.getNoteFilePath(note.getId()).map(Path::getParent).orElse(null);
    }

    void clear() {
        HtmlView webView = host.previewWebView();
        if (webView != null) {
            webView.loadHtml("");
        }
    }

    void ensureThemeClass() {
        HtmlView webView = host.previewWebView();
        if (webView != null && !webView.node().getStyleClass().contains("webview-theme")) {
            webView.node().getStyleClass().add("webview-theme");
        }
    }

    void scrollToHeading(String headingSlug) {
        HtmlView webView = host.previewWebView();
        if (webView == null || headingSlug == null || !VALID_HEADING_SLUG.matcher(headingSlug).matches()) {
            return;
        }
        // The page of this note is not shown yet (it was just opened and is still being
        // rendered): jump once that page loads, not on the previous note's page.
        Note note = host.currentNote();
        String noteId = note != null ? note.getId() : null;
        Task<String> rendering = currentTask;
        if (noteId != null && (!noteId.equals(renderedNoteId) || (rendering != null && !rendering.isDone()))) {
            pendingAnchor = headingSlug;
            pendingAnchorNoteId = noteId;
            return;
        }
        // A render triggered moments ago (e.g. just switched to the reading view, or a
        // background refresh from a recent edit) may not have landed yet — jumping now would run
        // the script against the page that's about to be replaced: wait for it to load, once.
        webView.whenLoaded(() -> executeHeadingScroll(headingSlug));
    }

    private void executeHeadingScroll(String headingSlug) {
        host.previewWebView().run(
                "document.getElementById('" + headingSlug + "')?.scrollIntoView({block: 'start'})");
    }

    /** Sets the page's bridge on every load, then restores the scroll and pending jumps. */
    private void installBridge(HtmlView webView) {
        if (bridgeInstalled) {
            return;
        }
        bridgeInstalled = true;
        webView.exposeBridge("javaApp", bridge);
        webView.onLoaded(() -> {
            WebViewImages.bridgeReady(webView);
            installAcceleratedScroll(webView);
            restorePendingScroll(webView);
        });
    }

    private static void installAcceleratedScroll(HtmlView webView) {
        webView.run("""
                if (!window.__jylosWheelSpeedInstalled) {
                  window.__jylosWheelSpeedInstalled = true;
                  document.addEventListener('wheel', function(e) {
                    if (e.ctrlKey || e.metaKey) return;
                    window.scrollBy(0, e.deltaY * 1.8);
                    e.preventDefault();
                  }, { passive: false });
                }
                """);
    }

    private void restorePendingScroll(HtmlView webView) {
        if (pendingScrollY != null) {
            double scrollY = pendingScrollY;
            String noteId = pendingNoteId;
            pendingScrollY = null;
            pendingNoteId = null;
            webView.run("window.scrollTo(0, " + scrollY + ");");
            renderedNoteId = noteId;
        }
        // A link to a heading or block of this note was followed before its page loaded.
        if (pendingAnchor != null && pendingAnchorNoteId != null && pendingAnchorNoteId.equals(renderedNoteId)) {
            String anchor = pendingAnchor;
            pendingAnchor = null;
            pendingAnchorNoteId = null;
            executeHeadingScroll(anchor);
        }
    }

    void shutdown() {
        executor.shutdownNow();
    }

    /** Bridge exposed to the preview page as {@code window.javaApp}; public, see the class note. */
    public final class PreviewJavaBridge {
        public void openNote(String title) {
            Platform.runLater(() -> host.openLink(title, false));
        }

        /** A link clicked with Cmd/Ctrl or the middle button: its note opens in a new tab. */
        public void openNoteInNewTab(String title) {
            Platform.runLater(() -> host.openLink(title, true));
        }

        /** Opens the notes with a tag (a {@code #tag} clicked in the reading view). */
        public void openTag(String tag) {
            Platform.runLater(() -> {
                if (tag != null && !tag.isBlank()) {
                    host.openTag(tag);
                }
            });
        }

        /** Opens an external {@code http(s)} link (e.g. a rich-link card) in the system browser. */
        public void openExternal(String url) {
            Platform.runLater(() -> SystemBrowser.open(url));
        }

        /** An image the reading view could not draw (WebP): answered later with a PNG copy, or "". */
        public void convertImage(String id, String source) {
            WebViewImages.convert(host.previewWebView(), WebViewImages.READING_VIEW, id, source);
        }
    }
}
