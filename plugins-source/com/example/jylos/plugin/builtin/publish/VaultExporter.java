package com.example.jylos.plugin.builtin.publish;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.graph.GraphBuilder;
import com.example.jylos.graph.GraphData;
import com.example.jylos.graph.GraphEdge;
import com.example.jylos.graph.GraphNode;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;
import com.example.jylos.util.MarkdownProcessor;
import com.example.jylos.util.WikiLinkResolver;

/**
 * Exports the whole vault to a self-contained static HTML/CSS/JS site: one page
 * per note (folder structure mirrored under {@code notes/}), a {@code graph.json}
 * + a small vanilla-JS graph viewer, and an {@code index.html} listing every
 * exported note grouped by folder.
 *
 * <h2>What gets exported, and what does not</h2>
 * <ul>
 *   <li><b>Private notes</b> ({@link Note#isPrivate()}) are never written, never
 *       listed, and never linkable — a wiki-link pointing at one becomes a dead
 *       {@code href="#"} on the exported site, the same as a link to a note that
 *       does not exist at all. Their title is deliberately not leaked into a real
 *       href either way.</li>
 *   <li><b>Attachments</b> (a binary file the filesystem backend represents as a
 *       Note, e.g. an image or PDF — detected by extension via
 *       {@link com.example.jylos.util.AttachmentType#isAttachment(String)} on
 *       the note's title, which for an attachment always keeps its extension)
 *       are skipped, not rendered as an HTML page. Note: {@link
 *       Note#isContentComplete()} is <i>not</i> an attachment marker — it also
 *       means "lightweight stub, full content not read from disk yet", the
 *       lazy-load path {@code getAllNotes()} takes on a large vault; every
 *       exportable note's content is force-loaded via {@link
 *       NoteService#getNoteById} before rendering regardless of that flag.
 *       <b>Known v1
 *       limitation, stated plainly rather than half-implemented:</b> images
 *       referenced from a note's Markdown are not copied into the exported site
 *       and their {@code <img>} tags are not rewritten — an embedded image will
 *       show broken on the published site. Copying attachment files needs the
 *       vault's real root directory on disk, which is only meaningful for a
 *       filesystem-backed vault and not exposed through a stable service API
 *       today; rather than bolt on a fragile, backend-specific path lookup, this
 *       is left for a follow-up.</li>
 * </ul>
 *
 * <h2>Wiki-link resolution</h2>
 * <p>Reuses {@link WikiLinkResolver#resolve} — the exact same code path the live
 * preview uses — to turn {@code [[wiki]]} / {@code [label](note)} syntax into
 * {@code jylos://open-note/...} anchors first, then {@link WikiLinkRewriter}
 * rewrites those into real relative hrefs afterward. This guarantees the
 * exported site resolves links exactly the way the app's own preview does,
 * instead of a second, potentially divergent, link-syntax implementation.</p>
 */
final class VaultExporter {

    private static final Logger logger = Logger.getLogger(VaultExporter.class.getName());

    /** Outcome of one export run. */
    record Result(int exportedNotes, int skippedPrivate, int skippedAttachments, int skippedErrors,
            int skippedByFilter, Path outputDir) {
    }

    /**
     * What the publish configuration dialog collects before a full {@link
     * #export} — deliberately NOT accepted by {@link #exportSingleNote}, which
     * instead reads back whatever {@link #export} last wrote via {@link
     * #writeManifest}/{@link #readManifest}, so a single-note republish always
     * matches the site it is updating instead of asking the user to redo the
     * same choices (or worse, silently drifting from them).
     *
     * @param siteTitle       header home-link label; {@code null}/blank means "Jylos Vault"
     * @param generateGraph   whether to build graph.json/graph.html and every note's mini-graph widget
     * @param includedNoteIds {@code null} means "every exportable note" (the common case); otherwise
     *                        only these ids are exported — everything else is skipped, counted, and
     *                        reported back as {@link Result#skippedByFilter}
     */
    record PublishOptions(String siteTitle, boolean generateGraph, Set<String> includedNoteIds) {
        static PublishOptions defaults() {
            return new PublishOptions("Jylos Vault", true, null);
        }
    }

    /**
     * One note's rendered content, held in memory between the two export passes:
     * pass one resolves every note's Markdown and (as a side effect) discovers
     * every wiki-link that successfully resolves to another exported note —
     * which is exactly the "Linked mentions" data a note further down the list
     * needs, so it cannot be written to disk until every note has been through
     * pass one first.
     */
    private record RenderedNote(Note note, String relativePath, String depthPrefix, String pageFolderPath,
            String bodyHtml, String tocHtml) {
    }

    /** Matches a heading commonmark already gave a stable id to (see MarkdownProcessor's HeadingIdAttributeProvider). */
    private static final Pattern HEADING_PATTERN =
            Pattern.compile("<h([1-6])\\b[^>]*\\sid=\"([^\"]*)\"[^>]*>(.*?)</h\\1>", Pattern.DOTALL);

    /** The vault-wide bookkeeping {@link #export} and {@link #exportSingleNote} both start from. */
    private record ExportModel(Map<String, List<String>> folderPathByNoteId, List<Note> exportable, NoteSlugs slugs,
            Set<String> knownTitles, int skippedPrivate, int skippedAttachments, int skippedByFilter) {
    }

    /** The outcome of pass 1 (see {@link #export}'s own doc on the two passes) — every note's rendered
     *  content plus the link graph discovered along the way, still nothing written to disk. */
    private record RenderResult(List<RenderedNote> rendered, Map<String, LinkedHashSet<String>> backlinksByTargetId,
            Map<String, LinkedHashSet<String>> outgoingByNoteId, int skippedErrors) {
    }

    /** Outcome of a single-note republish — see {@link #exportSingleNote}. */
    record SingleNoteResult(int pagesWritten, boolean graphRegenerated, Path outputDir) {
    }

    private final NoteService noteService;
    private final FolderService folderService;
    private final TagService tagService;

    VaultExporter(NoteService noteService, FolderService folderService, TagService tagService) {
        this.noteService = noteService;
        this.folderService = folderService;
        this.tagService = tagService;
    }

    /** Runs a full export with the default options ({@code "Jylos Vault"}, graph on, everything included). */
    Result export(Path outputDir, BiConsumer<Integer, Integer> onProgress) throws IOException {
        return export(outputDir, PublishOptions.defaults(), onProgress);
    }

    /**
     * Runs the export. {@code onProgress} is called after each note is written,
     * with (notes written so far, total notes to write) — safe to call from any
     * thread, the caller decides how to surface it (e.g. hop to the FX thread
     * itself for a progress bar). Writes a small manifest ({@link #writeManifest})
     * recording {@code options} alongside the site, so a later {@link
     * #exportSingleNote} on this same directory picks the same settings back up
     * automatically instead of needing them re-specified.
     */
    Result export(Path outputDir, PublishOptions options, BiConsumer<Integer, Integer> onProgress) throws IOException {
        Files.createDirectories(outputDir);

        ExportModel model = computeExportModel(options.includedNoteIds());
        RenderResult renderResult = renderAll(model, onProgress);

        List<Note> exported = new ArrayList<>();
        Map<String, String> titleById = new LinkedHashMap<>();
        for (RenderedNote r : renderResult.rendered()) {
            exported.add(r.note());
            titleById.put(r.note().getId(), r.note().getTitle());
        }

        // A per-note try/catch here too: a failure at this stage (e.g. disk full) is
        // far less likely than pass 1's YAML-parsing failures, but still shouldn't be
        // able to abort every other note's write. Known, accepted edge case if it does
        // happen: that note stays listed in the index/graph/any backlinks pointing at
        // it, since those were already built from the full `exported` list above.
        for (RenderedNote r : renderResult.rendered()) {
            writeNotePage(r, model.folderPathByNoteId(), renderResult.backlinksByTargetId(),
                    renderResult.outgoingByNoteId(), titleById, model.slugs(), outputDir, options);
        }

        writeCommonScaffolding(outputDir, model.slugs(), exported, model.folderPathByNoteId(), options.siteTitle());
        if (options.generateGraph()) {
            writeGraphPages(outputDir, model.slugs(), exported, options.siteTitle());
        }
        writeManifest(outputDir, options);

        return new Result(exported.size(), model.skippedPrivate(), model.skippedAttachments(),
                renderResult.skippedErrors(), model.skippedByFilter(), outputDir);
    }

    /**
     * Republishes ONE note into an ALREADY-exported site — used by the note's own
     * right-click "Publish this note" (see {@code PublishPlugin}), which updates a
     * previously published output directory rather than re-exporting the whole
     * vault.
     *
     * <p>Still has to run the exact same pass 1 as a full {@link #export} over
     * every note (folder walk, slugging, Markdown-to-HTML, link resolution) — a
     * note's backlinks are inherently a vault-wide relationship, so there is no
     * way to know who newly links to (or stopped linking to) this note without
     * looking at everyone else's content too. What it does NOT do is re-write
     * every one of those thousands of pages to disk: only this note's own page,
     * plus any note whose own "Linked mentions" section could have changed —
     * exactly its current neighbours (both directions). {@code nav-data.js} and
     * {@code index.html} are always refreshed too — cheap, data-only writes, not
     * thousands of HTML pages; the graph (only if the original publish had it on)
     * is refreshed the same way.</p>
     *
     * <p>Site title, graph on/off and the original folder/note selection all come
     * from {@link #readManifest} — whatever the last full {@link #export} into
     * this directory actually used — not fresh choices, so a single-note
     * republish can never drift from the site it is updating. One real
     * limitation this carries: if the original publish excluded some notes (a
     * deliberate {@code includedNoteIds} subset) and {@code noteId} here is one
     * of THOSE excluded notes, this call still republishes it anyway — this
     * method's only job is "keep the given note's page current", it does not
     * re-litigate whether that note belongs on the site at all.</p>
     *
     * @param outputDir  an existing output directory from a previous {@link #export}
     * @param noteId     the note to republish
     */
    SingleNoteResult exportSingleNote(Path outputDir, String noteId, BiConsumer<Integer, Integer> onProgress)
            throws IOException {
        Files.createDirectories(outputDir);

        PublishOptions options = readManifest(outputDir);
        ExportModel model = computeExportModel(null);
        RenderResult renderResult = renderAll(model, onProgress);

        List<Note> exported = new ArrayList<>();
        Map<String, String> titleById = new LinkedHashMap<>();
        for (RenderedNote r : renderResult.rendered()) {
            exported.add(r.note());
            titleById.put(r.note().getId(), r.note().getTitle());
        }

        Set<String> idsToWrite = new LinkedHashSet<>();
        idsToWrite.add(noteId);
        idsToWrite.addAll(renderResult.backlinksByTargetId().getOrDefault(noteId, new LinkedHashSet<>()));
        idsToWrite.addAll(renderResult.outgoingByNoteId().getOrDefault(noteId, new LinkedHashSet<>()));

        int written = 0;
        for (RenderedNote r : renderResult.rendered()) {
            if (!idsToWrite.contains(r.note().getId())) {
                continue;
            }
            writeNotePage(r, model.folderPathByNoteId(), renderResult.backlinksByTargetId(),
                    renderResult.outgoingByNoteId(), titleById, model.slugs(), outputDir, options);
            written++;
        }

        writeCommonScaffolding(outputDir, model.slugs(), exported, model.folderPathByNoteId(), options.siteTitle());
        if (options.generateGraph()) {
            writeGraphPages(outputDir, model.slugs(), exported, options.siteTitle());
        }

        return new SingleNoteResult(written, options.generateGraph(), outputDir);
    }

    /** The folder walk, private/attachment/selection filtering, deterministic sort and slug registration —
     *  identical setup whether {@link #export} is about to write every page or {@link #exportSingleNote}
     *  just one. {@code includedNoteIds} is {@code null} for "everything" (the common case, and always
     *  the case for {@link #exportSingleNote}, which needs the FULL model to compute backlinks correctly
     *  regardless of what the original publish's own selection was — see that method's own doc). */
    private ExportModel computeExportModel(Set<String> includedNoteIds) {
        // Map every note id to its folder path (root-to-leaf folder titles), by
        // walking the real folder tree — robust across both storage backends,
        // unlike trying to infer a path from a note's own id (which is only a
        // real filesystem path for the filesystem backend; opaque for SQLite).
        Map<String, List<String>> folderPathByNoteId = new LinkedHashMap<>();
        walkFolders(folderService.getRootFolders(), new ArrayList<>(), folderPathByNoteId);

        List<Note> allNotes = noteService.getAllNotes();
        List<Note> exportable = new ArrayList<>();
        int skippedPrivate = 0;
        int skippedAttachments = 0;
        int skippedByFilter = 0;
        for (Note note : allNotes) {
            if (note.isPrivate()) {
                skippedPrivate++;
                continue;
            }
            // A real binary attachment keeps its extension in the title (e.g.
            // "diagram.png"); a Markdown note's title never does. NOT the same
            // check as isContentComplete() — that flag also covers a Markdown note
            // that simply hasn't had its full content read from disk yet (see the
            // force-load below), which on a large vault is most of getAllNotes().
            if (com.example.jylos.util.AttachmentType.isAttachment(note.getTitle())) {
                skippedAttachments++;
                continue;
            }
            if (includedNoteIds != null && !includedNoteIds.contains(note.getId())) {
                skippedByFilter++;
                continue;
            }
            exportable.add(note);
        }
        // Deterministic output order: by folder path, then title — otherwise which
        // of two same-named notes "wins" a slug collision would depend on
        // getAllNotes()'s own (backend-specific, not contractually ordered) order.
        exportable.sort(Comparator
                .<Note, String>comparing(n -> String.join("/", folderPathByNoteId.getOrDefault(n.getId(), List.of())))
                .thenComparing(Note::getTitle, String.CASE_INSENSITIVE_ORDER));

        NoteSlugs slugs = new NoteSlugs();
        for (Note note : exportable) {
            slugs.register(note.getId(), folderPathByNoteId.getOrDefault(note.getId(), List.of()), note.getTitle());
        }

        // Only exportable notes count as "known" for wiki-link resolution: a link to
        // a private, attachment or filtered-out note must render as broken, not as
        // if it resolved.
        Set<String> knownTitles = new LinkedHashSet<>();
        for (Note note : exportable) {
            knownTitles.add(note.getTitle());
        }

        return new ExportModel(folderPathByNoteId, exportable, slugs, knownTitles, skippedPrivate, skippedAttachments,
                skippedByFilter);
    }

    /**
     * Pass 1: resolve every note's Markdown to HTML and discover which wiki-links
     * successfully resolve to another exported note — that is exactly the "who
     * links to me" (backlinks) data, and it cannot be known for a note until
     * every OTHER note has run through this same pass, so nothing gets written
     * to disk yet.
     */
    private RenderResult renderAll(ExportModel model, BiConsumer<Integer, Integer> onProgress) {
        List<RenderedNote> rendered = new ArrayList<>();
        Map<String, LinkedHashSet<String>> backlinksByTargetId = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> outgoingByNoteId = new LinkedHashMap<>();
        int skippedErrors = 0;
        int processed = 0;
        for (Note note : model.exportable()) {
            processed++;
            try {
                String relativePath = model.slugs().pathFor(note.getId());
                String depthPrefix = "../".repeat(depthOf(relativePath));
                String pageFolderPath =
                        String.join("/", model.folderPathByNoteId().getOrDefault(note.getId(), List.of()));

                // getAllNotes() returns lightweight stubs on a large vault (title/preview
                // only, isContentComplete()==false) — force a full read via the same path
                // the live editor uses to open a note, so the exported page gets the real
                // body instead of a truncated preview.
                Note fullNote = note;
                if (!note.isContentComplete()) {
                    fullNote = noteService.getNoteById(note.getId()).orElse(note);
                }

                String withAnchors = WikiLinkResolver.resolve(fullNote.getContent(), model.knownTitles());
                String bodyHtml = MarkdownProcessor.markdownToHtml(withAnchors);
                String sourceId = note.getId();
                WikiLinkRewriter rewriter = new WikiLinkRewriter(title -> hrefFromNoteToTitle(
                        sourceId, relativePath, title, model.slugs(), backlinksByTargetId, outgoingByNoteId));
                bodyHtml = rewriter.rewrite(bodyHtml);
                String tocHtml = buildToc(bodyHtml);

                rendered.add(new RenderedNote(note, relativePath, depthPrefix, pageFolderPath, bodyHtml, tocHtml));
            } catch (RuntimeException e) {
                // One malformed note (e.g. invalid YAML frontmatter that only the full
                // parser, not the lightweight listing read, chokes on) must not abort
                // the export of the other thousands of notes in a real vault. Skip it,
                // keep going — surfaced in the final summary, not silently dropped.
                logger.log(Level.WARNING, "Skipping note that failed to export: " + note.getTitle()
                        + " (" + note.getId() + ")", e);
                skippedErrors++;
            }
            if (onProgress != null) {
                onProgress.accept(processed, model.exportable().size());
            }
        }
        return new RenderResult(rendered, backlinksByTargetId, outgoingByNoteId, skippedErrors);
    }

    /** Assembles and writes one note's final page — breadcrumb, title, body, mini-graph, outline, backlinks. */
    private void writeNotePage(RenderedNote r, Map<String, List<String>> folderPathByNoteId,
            Map<String, LinkedHashSet<String>> backlinksByTargetId, Map<String, LinkedHashSet<String>> outgoingByNoteId,
            Map<String, String> titleById, NoteSlugs slugs, Path outputDir, PublishOptions options) {
        try {
            String breadcrumb = buildBreadcrumb(folderPathByNoteId.getOrDefault(r.note().getId(), List.of()));
            String backlinksHtml = buildBacklinksSection(r.note().getId(), r.relativePath(),
                    backlinksByTargetId, titleById, slugs);
            String finalBody = breadcrumb
                    + "<h1 class=\"note-title\">" + escapeHtml(r.note().getTitle()) + "</h1>"
                    + r.bodyHtml()
                    + backlinksHtml;
            // Mini-graph widget goes ABOVE the outline in the same aside pane, matching
            // Obsidian Publish's own layout — both share page()'s existing tocHtml slot
            // (rendered as one aside if either is non-empty) rather than a dedicated
            // parameter, since the widget already carries its own <script> tags inline.
            // Omitted entirely when the graph is off — it needs the same graph-engine.js
            // this export then never writes.
            String miniGraphHtml = options.generateGraph()
                    ? buildMiniGraphWidget(r.note().getId(), r.relativePath(), r.depthPrefix(),
                            backlinksByTargetId, outgoingByNoteId, titleById, slugs)
                    : "";
            String asideHtml = miniGraphHtml + r.tocHtml();
            String page = PublishTemplates.page(r.note().getTitle(), r.depthPrefix(), finalBody, asideHtml,
                    r.relativePath(), r.pageFolderPath(), options.siteTitle());
            Path target = outputDir.resolve(r.relativePath());
            Files.createDirectories(target.getParent());
            Files.writeString(target, page, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to write exported page for: " + r.note().getTitle()
                    + " (" + r.note().getId() + ")", e);
        }
    }

    /** index.html + the assets every page needs regardless of the graph setting — always written. */
    private void writeCommonScaffolding(Path outputDir, NoteSlugs slugs, List<Note> exported,
            Map<String, List<String>> folderPathByNoteId, String siteTitle) throws IOException {
        writeIndex(outputDir, exported, folderPathByNoteId, slugs, siteTitle);
        Path assetsDir = outputDir.resolve("assets");
        Files.createDirectories(assetsDir);
        Files.writeString(assetsDir.resolve("style.css"), PublishTemplates.css(), StandardCharsets.UTF_8);
        Files.writeString(assetsDir.resolve("nav.js"), PublishTemplates.navJs(), StandardCharsets.UTF_8);
        // One shared file every page loads via <script src> (not fetch — see NavTree's
        // own doc for why), so a vault of thousands of notes doesn't multiply this
        // tree's size by the note count the way inlining it per-page would.
        Files.writeString(assetsDir.resolve("nav-data.js"), NavTree.buildScript(exported, folderPathByNoteId, slugs),
                StandardCharsets.UTF_8);
    }

    /** graph.json + graph.html + the graph engine scripts — only written when the graph is enabled. */
    private void writeGraphPages(Path outputDir, NoteSlugs slugs, List<Note> exported, String siteTitle)
            throws IOException {
        String graphJson = writeGraph(outputDir, slugs, exported);
        // The graph JSON is embedded inline here (in addition to the standalone
        // graph.json file) so the page still renders when opened directly as a
        // file — a plain fetch('graph.json') is rejected by the browser as
        // cross-origin when there is no server, only a local file:// URL. "</" is
        // escaped so a note title containing it can't break out of the <script>
        // tag early.
        String inlineGraphData = "<script>window.__JYLOS_GRAPH__ = "
                + graphJson.replace("</", "<\\/") + ";</script>";
        Files.writeString(outputDir.resolve("graph.html"),
                PublishTemplates.page("Graph", "",
                        "<canvas id=\"graph-canvas\"></canvas>"
                                + "<p class=\"graph-hint\">Scroll to zoom, drag the background to pan, "
                                + "drag a node to rearrange it, click a node to open it.</p>"
                                + inlineGraphData
                                + "<script src=\"assets/graph-engine.js\"></script>"
                                + "<script src=\"assets/graph.js\"></script>",
                        "", "graph.html", "", siteTitle),
                StandardCharsets.UTF_8);

        Path assetsDir = outputDir.resolve("assets");
        Files.createDirectories(assetsDir);
        Files.writeString(assetsDir.resolve("graph-engine.js"), PublishTemplates.graphEngineJs(), StandardCharsets.UTF_8);
        Files.writeString(assetsDir.resolve("graph.js"), PublishTemplates.graphJs(), StandardCharsets.UTF_8);
        Files.writeString(assetsDir.resolve("mini-graph.js"), PublishTemplates.miniGraphJs(), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // Publish options manifest — see PublishOptions' own doc
    // ------------------------------------------------------------------

    private static final String MANIFEST_FILE_NAME = ".jylos-publish.manifest";

    /**
     * A deliberately tiny, hand-rolled format (not JSON — nothing else reads
     * this file, and it only ever holds what this class itself wrote) so {@link
     * #exportSingleNote} can recover the exact options a previous {@link
     * #export} used. Three header lines ({@code key=value}, split on the FIRST
     * {@code '='} only — safe even if a site title itself contains one, since a
     * JavaFX TextField can't contain a raw newline for that to collide with),
     * then (only when the export was NOT "everything") one included note id per
     * line — filesystem-backend ids are single-line file paths, never a
     * newline, so this needs no escaping scheme.
     */
    private void writeManifest(Path outputDir, PublishOptions options) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("siteTitle=").append(options.siteTitle() == null ? "" : options.siteTitle()).append('\n');
        sb.append("generateGraph=").append(options.generateGraph()).append('\n');
        sb.append("includeAll=").append(options.includedNoteIds() == null).append('\n');
        if (options.includedNoteIds() != null) {
            for (String id : options.includedNoteIds()) {
                sb.append(id).append('\n');
            }
        }
        Files.writeString(outputDir.resolve(MANIFEST_FILE_NAME), sb.toString(), StandardCharsets.UTF_8);
    }

    /** Reads back what {@link #writeManifest} wrote, or {@link PublishOptions#defaults()} if there is
     *  none (a site published before this feature existed, or the file was removed by hand). */
    static PublishOptions readManifest(Path outputDir) {
        Path manifestPath = outputDir.resolve(MANIFEST_FILE_NAME);
        if (!Files.isRegularFile(manifestPath)) {
            return PublishOptions.defaults();
        }
        try {
            List<String> lines = Files.readAllLines(manifestPath, StandardCharsets.UTF_8);
            String siteTitle = "Jylos Vault";
            boolean generateGraph = true;
            boolean includeAll = true;
            Set<String> includedIds = new LinkedHashSet<>();
            int i = 0;
            for (; i < lines.size(); i++) {
                String line = lines.get(i);
                int eq = line.indexOf('=');
                if (eq < 0) {
                    break;
                }
                String key = line.substring(0, eq);
                String value = line.substring(eq + 1);
                switch (key) {
                    case "siteTitle" -> siteTitle = value;
                    case "generateGraph" -> generateGraph = Boolean.parseBoolean(value);
                    case "includeAll" -> includeAll = Boolean.parseBoolean(value);
                    default -> { /* an unknown future key — ignore, keep reading */ }
                }
                if ("includeAll".equals(key)) {
                    i++;
                    break;
                }
            }
            if (!includeAll) {
                for (; i < lines.size(); i++) {
                    if (!lines.get(i).isEmpty()) {
                        includedIds.add(lines.get(i));
                    }
                }
            }
            return new PublishOptions(siteTitle, generateGraph, includeAll ? null : includedIds);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read publish manifest at " + manifestPath
                    + " — falling back to defaults", e);
            return PublishOptions.defaults();
        }
    }

    // ------------------------------------------------------------------
    // Folder tree walking
    // ------------------------------------------------------------------

    private void walkFolders(List<Folder> folders, List<String> pathSoFar, Map<String, List<String>> out) {
        for (Folder folder : folders) {
            // The current folder's own path, including itself — a note sitting
            // directly in this folder belongs at THIS path, not its parent's.
            List<String> ownPath = new ArrayList<>(pathSoFar);
            ownPath.add(folder.getTitle() != null ? folder.getTitle() : folder.getId());
            for (Note note : noteService.getNotesByFolder(folder)) {
                out.put(note.getId(), List.copyOf(ownPath));
            }
            walkFolders(folderService.getSubfolders(folder), ownPath, out);
        }
    }

    // ------------------------------------------------------------------
    // Wiki-link href resolution (title -> relative href from a given note)
    // ------------------------------------------------------------------

    /**
     * Resolves a wiki-link's target title to an href relative to the note
     * currently being rendered, or {@code null} if the target is not part of this
     * export. Uses {@link NoteService#findNoteByTitle}, the exact same lookup the
     * live app uses to open a wiki-link — an ambiguous title (two notes sharing
     * it) resolves to whichever note the app itself would open, not a different,
     * export-only disambiguation.
     *
     * <p>A successful resolution is also recorded into both {@code
     * backlinksByTargetId} (target note id -&gt; set of source note ids linking
     * to it, for "Linked mentions") and {@code outgoingByNoteId} (source note id
     * -&gt; set of target note ids it links to) — together these give each
     * note's full local neighborhood (its mini-graph preview needs both
     * directions, not just incoming links) without a second pass over every
     * note's content.</p>
     */
    private String hrefFromNoteToTitle(String fromNoteId, String fromRelativePath, String title, NoteSlugs slugs,
            Map<String, LinkedHashSet<String>> backlinksByTargetId,
            Map<String, LinkedHashSet<String>> outgoingByNoteId) {
        Optional<Note> target = noteService.findNoteByTitle(title);
        if (target.isEmpty()) {
            return null;
        }
        String targetId = target.get().getId();
        String targetPath = slugs.pathFor(targetId);
        if (targetPath == null) {
            // Resolves to a real note, but one this export excluded (private/attachment).
            return null;
        }
        if (!targetId.equals(fromNoteId)) {
            // A note linking to itself is not a "mention from elsewhere".
            backlinksByTargetId.computeIfAbsent(targetId, k -> new LinkedHashSet<>()).add(fromNoteId);
            outgoingByNoteId.computeIfAbsent(fromNoteId, k -> new LinkedHashSet<>()).add(targetId);
        }
        return relativize(fromRelativePath, targetPath);
    }

    // ------------------------------------------------------------------
    // Table of contents, breadcrumb, backlinks
    // ------------------------------------------------------------------

    /**
     * Builds the note's own "On this page" outline from the headings
     * {@code bodyHtml} already carries an {@code id} on (see {@code
     * MarkdownProcessor}'s {@code HeadingIdAttributeProvider} — every real
     * heading gets one), or {@code ""} when the note has no headings — {@link
     * PublishTemplates#page} omits the outline pane entirely rather than
     * rendering an empty one.
     */
    private static String buildToc(String bodyHtml) {
        Matcher matcher = HEADING_PATTERN.matcher(bodyHtml);
        StringBuilder toc = new StringBuilder();
        boolean any = false;
        while (matcher.find()) {
            int level = Integer.parseInt(matcher.group(1));
            String id = matcher.group(2);
            // The inner HTML is already-rendered, already-escaped markup (e.g. "Foo
            // &amp; Bar" for a heading that read "Foo & Bar") — stripping tags leaves
            // that escaping intact, so it must NOT be run through escapeHtml() again,
            // or "&amp;" would become "&amp;amp;".
            String label = matcher.group(3).replaceAll("<[^>]+>", "").trim();
            if (id.isEmpty() || label.isEmpty()) {
                continue;
            }
            if (!any) {
                toc.append("<p class=\"toc-title\">On this page</p><ul>");
                any = true;
            }
            toc.append("<li class=\"toc-h").append(level).append("\">")
                    .append("<a href=\"#").append(id).append("\">").append(label).append("</a></li>");
        }
        if (any) {
            toc.append("</ul>");
        }
        return toc.toString();
    }

    /** A plain-text folder trail above the note title — not links, since this export has no per-folder landing page. */
    private static String buildBreadcrumb(List<String> folderPath) {
        if (folderPath.isEmpty()) {
            return "";
        }
        return "<p class=\"breadcrumb\">" + escapeHtml(String.join(" / ", folderPath)) + "</p>";
    }

    /**
     * The "Linked mentions" section listing every other exported note whose
     * content links to this one — the reverse of the per-note wiki-link
     * resolution {@link #hrefFromNoteToTitle} performs during rendering.
     */
    private static String buildBacklinksSection(String noteId, String fromRelativePath,
            Map<String, LinkedHashSet<String>> backlinksByTargetId, Map<String, String> titleById, NoteSlugs slugs) {
        Set<String> sourceIds = backlinksByTargetId.get(noteId);
        if (sourceIds == null || sourceIds.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder("<section class=\"backlinks\"><h2>Linked mentions</h2><ul>");
        for (String sourceId : sourceIds) {
            String sourcePath = slugs.pathFor(sourceId);
            String title = titleById.get(sourceId);
            if (sourcePath == null || title == null) {
                // Defensive only: the source resolved successfully during rendering, so
                // this shouldn't happen — but a page that failed only at the pass-2 write
                // stage (see export()'s own comment on that) would leave a dangling entry.
                continue;
            }
            html.append("<li><a href=\"").append(relativize(fromRelativePath, sourcePath)).append("\">")
                    .append(escapeHtml(title)).append("</a></li>");
        }
        html.append("</ul></section>");
        return html.toString();
    }

    /** Caps how many neighbours a note's mini-graph preview shows — a small "glance" widget
     *  drawing hundreds of dots for a heavily-linked hub note would be slow and unreadable. */
    private static final int MINI_GRAPH_MAX_NEIGHBORS = 40;

    /**
     * The small "local graph" preview shown at the top of a note's outline pane
     * (matching Obsidian Publish's own per-page graph widget) — this note plus
     * its direct neighbours (both incoming and outgoing links, deduplicated),
     * rendered via the same engine {@link PublishTemplates#graphEngineJs} the
     * full graph page uses. Embeds its own tiny data + the two {@code
     * <script src>} tags it needs directly in the returned markup, since it is
     * the only page carrying this particular inline data — no shared-file
     * benefit to gain from writing it out separately the way graph-engine.js
     * and mini-graph.js (each identical on every page) are.
     */
    private static String buildMiniGraphWidget(String noteId, String fromRelativePath, String depthPrefix,
            Map<String, LinkedHashSet<String>> backlinksByTargetId, Map<String, LinkedHashSet<String>> outgoingByNoteId,
            Map<String, String> titleById, NoteSlugs slugs) {
        LinkedHashSet<String> neighborIds = new LinkedHashSet<>();
        neighborIds.addAll(backlinksByTargetId.getOrDefault(noteId, new LinkedHashSet<>()));
        neighborIds.addAll(outgoingByNoteId.getOrDefault(noteId, new LinkedHashSet<>()));

        StringBuilder nodesJson = new StringBuilder("[");
        String selfTitle = titleById.getOrDefault(noteId, "");
        nodesJson.append("{\"id\":").append(jsonString(noteId))
                .append(",\"label\":").append(jsonString(selfTitle))
                .append(",\"type\":\"note\",\"degree\":").append(neighborIds.size())
                .append(",\"href\":null}");

        StringBuilder edgesJson = new StringBuilder("[");
        int count = 0;
        for (String neighborId : neighborIds) {
            if (count >= MINI_GRAPH_MAX_NEIGHBORS) {
                break;
            }
            String neighborPath = slugs.pathFor(neighborId);
            String neighborTitle = titleById.get(neighborId);
            if (neighborPath == null || neighborTitle == null) {
                continue; // same defensive reasoning as buildBacklinksSection
            }
            // nodesJson always has the self-node already sitting at index 0 (appended
            // unconditionally above, before this loop) — so EVERY neighbour needs a
            // leading comma, not just the ones after the first. edgesJson has no such
            // head start (it starts genuinely empty), so it only needs one from the
            // second edge on. Conflating the two (as this used to) drops the comma
            // between the self-node and the first neighbour whenever a note actually
            // has any neighbours — invalid array syntax, silently breaking the whole
            // mini-graph (the assignment throws, so window.__JYLOS_MINI_GRAPH__ never
            // gets set and mini-graph.js's `if (!data) return;` draws nothing at all).
            if (count > 0) {
                edgesJson.append(',');
            }
            nodesJson.append(',');
            nodesJson.append("{\"id\":").append(jsonString(neighborId))
                    .append(",\"label\":").append(jsonString(neighborTitle))
                    .append(",\"type\":\"note\",\"degree\":1,\"href\":")
                    .append(jsonString(relativize(fromRelativePath, neighborPath))).append('}');
            edgesJson.append("{\"source\":").append(jsonString(noteId))
                    .append(",\"target\":").append(jsonString(neighborId)).append(",\"type\":\"link\"}");
            count++;
        }
        nodesJson.append(']');
        edgesJson.append(']');

        String dataJson = "{\"nodes\":" + nodesJson + ",\"edges\":" + edgesJson + "}";
        // Same "</" escape as graph.html's own inline embedding — a neighbour's title
        // containing a literal "</script>" must not be able to break out of this tag.
        String inlineData = "<script>window.__JYLOS_MINI_GRAPH__ = " + dataJson.replace("</", "<\\/") + ";</script>";

        return "<div class=\"mini-graph\">"
                + "<div class=\"mini-graph-header\"><span>Graph view</span>"
                + "<button type=\"button\" id=\"mini-graph-expand\" class=\"mini-graph-expand\" "
                + "data-href=\"" + depthPrefix + "graph.html#focus=" + urlEncode(noteId) + "\" "
                + "aria-label=\"Open full graph\">⤢</button></div>"
                + "<canvas id=\"mini-graph-canvas\"></canvas>"
                + "</div>"
                + inlineData
                + "<script src=\"" + depthPrefix + "assets/graph-engine.js\"></script>"
                + "<script src=\"" + depthPrefix + "assets/mini-graph.js\"></script>";
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * A pure, string-based relative path from one export-root-relative path to
     * another, always {@code /}-separated regardless of host OS — unlike
     * {@link java.nio.file.Path#relativize}, which on Windows would produce a
     * backslash-separated result, invalid inside an HTML {@code href}.
     */
    static String relativize(String fromPath, String toPath) {
        List<String> from = List.of(fromPath.split("/"));
        List<String> to = List.of(toPath.split("/"));
        int common = 0;
        int max = Math.min(from.size() - 1, to.size() - 1);
        while (common < max && from.get(common).equals(to.get(common))) {
            common++;
        }
        StringBuilder result = new StringBuilder();
        for (int i = common; i < from.size() - 1; i++) {
            result.append("../");
        }
        for (int i = common; i < to.size(); i++) {
            if (i > common) {
                result.append('/');
            }
            result.append(to.get(i));
        }
        return result.toString();
    }

    private static int depthOf(String relativePath) {
        int depth = 0;
        for (int i = 0; i < relativePath.length(); i++) {
            if (relativePath.charAt(i) == '/') {
                depth++;
            }
        }
        return depth;
    }

    // ------------------------------------------------------------------
    // Graph + index
    // ------------------------------------------------------------------

    private String writeGraph(Path outputDir, NoteSlugs slugs, List<Note> exportable) throws IOException {
        Set<String> exportableIds = new LinkedHashSet<>();
        for (Note note : exportable) {
            exportableIds.add(note.getId());
        }
        GraphBuilder graphBuilder = new GraphBuilder(noteService, tagService);
        GraphData data = graphBuilder.buildGlobalGraph(GraphBuilder.Options.defaults());

        List<GraphNode> nodes = new ArrayList<>();
        for (GraphNode node : data.nodes()) {
            if (node.type() == GraphNode.Type.NOTE && !exportableIds.contains(node.id())) {
                continue;
            }
            nodes.add(node);
        }
        Set<String> keptIds = new LinkedHashSet<>();
        for (GraphNode node : nodes) {
            keptIds.add(node.id());
        }
        List<GraphEdge> edges = new ArrayList<>();
        for (GraphEdge edge : data.edges()) {
            if (keptIds.contains(edge.source()) && keptIds.contains(edge.target())) {
                edges.add(edge);
            }
        }

        StringBuilder json = new StringBuilder();
        json.append("{\"nodes\":[");
        for (int i = 0; i < nodes.size(); i++) {
            GraphNode n = nodes.get(i);
            if (i > 0) {
                json.append(',');
            }
            String href = n.type() == GraphNode.Type.NOTE ? slugs.pathFor(n.id()) : null;
            json.append("{\"id\":").append(jsonString(n.id()))
                    .append(",\"label\":").append(jsonString(n.label()))
                    .append(",\"type\":").append(jsonString(n.type().name().toLowerCase()))
                    .append(",\"degree\":").append(n.degree())
                    .append(",\"href\":").append(href != null ? jsonString(href) : "null")
                    .append('}');
        }
        json.append("],\"edges\":[");
        for (int i = 0; i < edges.size(); i++) {
            GraphEdge e = edges.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"source\":").append(jsonString(e.source()))
                    .append(",\"target\":").append(jsonString(e.target()))
                    .append(",\"type\":").append(jsonString(e.type().name().toLowerCase()))
                    .append('}');
        }
        json.append("]}");

        Files.writeString(outputDir.resolve("graph.json"), json.toString(), StandardCharsets.UTF_8);
        return json.toString();
    }

    private void writeIndex(Path outputDir, List<Note> exportable, Map<String, List<String>> folderPathByNoteId,
            NoteSlugs slugs, String siteTitle) throws IOException {
        Map<String, List<Note>> byFolder = new LinkedHashMap<>();
        for (Note note : exportable) {
            String folderLabel = String.join(" / ", folderPathByNoteId.getOrDefault(note.getId(), List.of()));
            byFolder.computeIfAbsent(folderLabel, k -> new ArrayList<>()).add(note);
        }

        StringBuilder body = new StringBuilder("<h1>")
                .append(escapeHtml(siteTitle == null || siteTitle.isBlank() ? "Jylos Vault" : siteTitle))
                .append("</h1>");
        // Root-level notes (no folder) first, then folders in path order.
        List<String> orderedKeys = new ArrayList<>(byFolder.keySet());
        orderedKeys.sort(Comparator.comparing((String k) -> k.isEmpty() ? 0 : 1).thenComparing(k -> k));
        for (String folderLabel : orderedKeys) {
            if (!folderLabel.isEmpty()) {
                body.append("<span class=\"folder-label\">").append(escapeHtml(folderLabel)).append("</span>");
            }
            body.append("<ul class=\"note-list\">");
            for (Note note : byFolder.get(folderLabel)) {
                body.append("<li><a href=\"").append(slugs.pathFor(note.getId())).append("\">")
                        .append(escapeHtml(note.getTitle())).append("</a></li>");
            }
            body.append("</ul>");
        }

        Files.writeString(outputDir.resolve("index.html"),
                PublishTemplates.page(siteTitle == null || siteTitle.isBlank() ? "Jylos Vault" : siteTitle, "",
                        body.toString(), "", "index.html", "", siteTitle),
                StandardCharsets.UTF_8);
    }

    private static String jsonString(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
