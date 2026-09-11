package com.example.jylos.plugin.builtin.publish;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.example.jylos.data.dao.interfaces.FolderDAO;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.dao.interfaces.TagDAO;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * End-to-end behavioural checks for {@link VaultExporter}, run by
 * scripts/test-plugins.sh. Real file I/O against a temp directory — the point is
 * to verify what actually lands on disk, not that the right methods were called.
 */
public final class VaultExporterTest {

    private static int passed;
    private static int failed;

    private static void check(String name, boolean condition, String detail) {
        if (condition) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name + "  -> " + detail);
        }
    }

    public static void main(String[] args) throws Exception {
        Path outputDir = Files.createTempDirectory("jylos-publish-test");

        Folder root = folder("ROOT");
        Folder books = folder("books");
        Folder scifi = folder("scifi");

        Note dune = note("dune", "Dune",
                "A desert planet. See [[Foundation]] and [[Nonexistent]]. Also links to itself: [[Dune]].");
        Note foundation = note("foundation", "Foundation", "Links back to [[Dune]]. Also references [[Secret Diary]].");
        Note secretDiary = note("secret", "Secret Diary", "For my eyes only.");
        secretDiary.setPrivate(true);
        Note coverArt = note("cover", "cover.png", "");
        coverArt.setContentComplete(false);
        // Also doubles as the fixture for the "On this page" outline: two real
        // headings, so buildToc() has something to find, and orphan links to
        // nobody / is linked by nobody, so it's a clean case for "no backlinks".
        Note orphan = note("orphan", "Untethered Note", "# Overview\nNo links here.\n## Details\nMore text.");

        // Simulates what getAllNotes() actually returns on a large filesystem-backed
        // vault: a lightweight stub with a truncated preview and isContentComplete()
        // false — real content only comes back through getNoteById(), the same path
        // the live editor uses to open a note.
        Note lazyStub = note("lazystub", "Lazy Stub", "TRUNCATED PREVIEW ONLY");
        lazyStub.setContentComplete(false);
        Note lazyStubFull = note("lazystub", "Lazy Stub", "FULL CONTENT LOADED FROM DISK");
        lazyStubFull.setContentComplete(true);

        // A note whose full parse blows up (e.g. real-world malformed YAML
        // frontmatter that only the strict parser, not the lightweight listing
        // read, rejects) must not take the other thousands of notes in a real
        // vault down with it.
        Note corrupt = note("corrupt", "Corrupt Frontmatter", "irrelevant, never used before the throw");
        corrupt.setContentComplete(false);

        // A title containing a literal "</script>" must not be able to break out of
        // the <script> tag graph.html embeds the graph JSON in.
        Note scriptBreaker = note("scriptbreaker", "Weird </script> Title", "nothing special");

        List<Note> allNotes = List.of(dune, foundation, secretDiary, coverArt, orphan, lazyStub, corrupt,
                scriptBreaker);

        NoteService noteService = new NoteService(noOpNoteDao(), noOpFolderDao()) {
            @Override
            public List<Note> getAllNotes() {
                return allNotes;
            }

            @Override
            public List<Note> getNotesByFolder(Folder folder) {
                return switch (folder.getId()) {
                    case "books" -> List.of(dune);
                    case "scifi" -> List.of(foundation, secretDiary);
                    default -> List.of();
                };
            }

            @Override
            public Optional<Note> findNoteByTitle(String title) {
                return allNotes.stream().filter(n -> n.getTitle().equalsIgnoreCase(title)).findFirst();
            }

            @Override
            public Optional<Note> getNoteById(String id) {
                if ("corrupt".equals(id)) {
                    throw new RuntimeException("simulated YAML parse failure (invalid frontmatter)");
                }
                return "lazystub".equals(id) ? Optional.of(lazyStubFull) : Optional.empty();
            }
        };

        FolderService folderService = new FolderService(noOpFolderDao(), noOpNoteDao()) {
            @Override
            public List<Folder> getRootFolders() {
                return List.of(books);
            }

            @Override
            public List<Folder> getSubfolders(Folder parent) {
                return "books".equals(parent.getId()) ? List.of(scifi) : List.of();
            }
        };

        TagService tagService = new TagService(noOpTagDao(), noOpNoteDao());

        VaultExporter exporter = new VaultExporter(noteService, folderService, tagService);
        VaultExporter.Result result = exporter.export(outputDir, (done, total) -> { });

        System.out.println("\n-- export counts --");
        check("exports exactly the 5 real, non-private notes (dune, foundation, orphan, lazy stub, script-breaker)",
                result.exportedNotes() == 5, "exported=" + result.exportedNotes());
        check("skips exactly 1 private note", result.skippedPrivate() == 1, "skippedPrivate=" + result.skippedPrivate());
        check("skips exactly 1 attachment (contentComplete=false)",
                result.skippedAttachments() == 1, "skippedAttachments=" + result.skippedAttachments());
        check("skips exactly 1 note that failed to load (not counted as an attachment)",
                result.skippedErrors() == 1, "skippedErrors=" + result.skippedErrors());
        check("with the default options (no selection filter), nothing is skipped by filter",
                result.skippedByFilter() == 0, "skippedByFilter=" + result.skippedByFilter());

        System.out.println("\n-- folder structure is mirrored on disk --");
        Path dunePage = outputDir.resolve("notes/books/Dune.html");
        Path foundationPage = outputDir.resolve("notes/books/scifi/Foundation.html");
        Path orphanPage = outputDir.resolve("notes/Untethered-Note.html");
        check("note in a root-level folder lands under notes/<folder>/", Files.exists(dunePage), dunePage.toString());
        check("note in a nested folder lands under notes/<folder>/<subfolder>/",
                Files.exists(foundationPage), foundationPage.toString());
        check("note with no folder lands directly under notes/", Files.exists(orphanPage), orphanPage.toString());

        System.out.println("\n-- one note's load failure does not abort the whole export --");
        check("the failing note is not written to disk",
                Files.notExists(outputDir.resolve("notes/Corrupt-Frontmatter.html")), "should not exist");
        check("every other note still exported despite the sibling failure",
                Files.exists(dunePage) && Files.exists(foundationPage) && Files.exists(orphanPage)
                        && Files.exists(outputDir.resolve("notes/Lazy-Stub.html")),
                "all 4 good notes should exist regardless of the corrupt one");

        check("private note is not written to disk at all",
                Files.notExists(outputDir.resolve("notes/scifi/Secret-Diary.html"))
                        && Files.list(outputDir.resolve("notes")).noneMatch(p -> p.toString().contains("Secret")),
                "a Secret-Diary page should not exist anywhere under notes/");
        check("attachment note is not rendered as an HTML page",
                Files.notExists(outputDir.resolve("notes/cover.png.html")), "cover.png must not become a page");

        System.out.println("\n-- wiki-links rewritten to real relative hrefs --");
        String duneHtml = Files.readString(dunePage, StandardCharsets.UTF_8);
        check("Dune -> Foundation link points at the sibling subfolder page",
                duneHtml.contains("href=\"scifi/Foundation.html\""), duneHtml);
        check("link to a truly nonexistent note becomes dead ('#'), styled as broken",
                duneHtml.contains("wikilink-new") && duneHtml.contains("href=\"#\""), duneHtml);

        String foundationHtml = Files.readString(foundationPage, StandardCharsets.UTF_8);
        check("Foundation -> Dune link points back up one level",
                foundationHtml.contains("href=\"../Dune.html\""), foundationHtml);
        check("link to a real but PRIVATE note also becomes dead — private notes are not just unlisted, unreachable",
                foundationHtml.contains("href=\"#\""), foundationHtml);
        check("a link to a private note does not leak its title into a real href anywhere",
                !foundationHtml.contains("Secret-Diary.html"), foundationHtml);

        System.out.println("\n-- lazy stub gets its full content force-loaded via getNoteById --");
        Path lazyStubPage = outputDir.resolve("notes/Lazy-Stub.html");
        check("lazy stub note is exported (not mistaken for an attachment)",
                Files.exists(lazyStubPage), lazyStubPage.toString());
        String lazyStubHtml = Files.readString(lazyStubPage, StandardCharsets.UTF_8);
        check("exported page has the FULL content from getNoteById, not the truncated stub preview",
                lazyStubHtml.contains("FULL CONTENT LOADED FROM DISK"), lazyStubHtml);
        check("truncated stub preview text does not leak into the exported page",
                !lazyStubHtml.contains("TRUNCATED PREVIEW"), lazyStubHtml);

        System.out.println("\n-- graph.json excludes private/attachment notes --");
        String graphJson = Files.readString(outputDir.resolve("graph.json"), StandardCharsets.UTF_8);
        check("graph includes an exported note", graphJson.contains("\"dune\""), graphJson);
        check("graph does not include the private note's id", !graphJson.contains("\"secret\""), graphJson);
        check("graph does not include the attachment's id", !graphJson.contains("\"cover\""), graphJson);

        System.out.println("\n-- graph.html embeds the data inline (fetch() is rejected on a file:// URL) --");
        String graphHtml = Files.readString(outputDir.resolve("graph.html"), StandardCharsets.UTF_8);
        check("graph.html embeds the graph data as window.__JYLOS_GRAPH__, not just a fetch() call",
                graphHtml.contains("window.__JYLOS_GRAPH__ = {\"nodes\""), graphHtml.length() + " chars");
        check("a note title containing '</script>' cannot break out of the embedding <script> tag",
                !graphHtml.contains("Weird </script> Title") && graphHtml.contains("Weird <\\/script> Title"),
                graphHtml.length() + " chars");

        System.out.println("\n-- note title, breadcrumb and page-folder metadata --");
        check("note title rendered as a visible <h1> on its own page (not just <title>)",
                duneHtml.contains("<h1 class=\"note-title\">Dune</h1>"), duneHtml);
        check("breadcrumb shows the folder path for a nested note",
                duneHtml.contains("<p class=\"breadcrumb\">books</p>"), duneHtml);
        check("breadcrumb shows the FULL folder path for a doubly-nested note",
                foundationHtml.contains("<p class=\"breadcrumb\">books / scifi</p>"), foundationHtml);
        check("data-page-folder matches the sidebar tree's own folder.path convention (no 'notes/' prefix)",
                foundationHtml.contains("data-page-folder=\"books/scifi\""), foundationHtml);

        String orphanHtml = Files.readString(orphanPage, StandardCharsets.UTF_8);
        check("a root-level note has no breadcrumb at all", !orphanHtml.contains("breadcrumb"), orphanHtml);
        check("a root-level note's data-page-folder is empty", orphanHtml.contains("data-page-folder=\"\""), orphanHtml);

        System.out.println("\n-- 'On this page' outline (TOC), built from the note's own headings --");
        check("a note with headings gets a TOC with both of them",
                orphanHtml.contains("toc-h1") && orphanHtml.contains(">Overview<")
                        && orphanHtml.contains("toc-h2") && orphanHtml.contains(">Details<"),
                orphanHtml);
        check("a note with no headings at all (Dune) gets no TOC content "
                        + "(the aside still renders — Dune's mini-graph widget below is non-empty)",
                !duneHtml.contains("toc-title"), duneHtml);

        System.out.println("\n-- per-note mini-graph widget --");
        check("Dune's mini-graph embeds itself plus its real neighbour (Foundation)",
                duneHtml.contains("window.__JYLOS_MINI_GRAPH__")
                        && duneHtml.contains("\"id\":\"dune\"") && duneHtml.contains("\"id\":\"foundation\""),
                duneHtml);
        check("the mini-graph widget loads the shared engine and its own thin wrapper script",
                duneHtml.contains("assets/graph-engine.js") && duneHtml.contains("assets/mini-graph.js"), duneHtml);
        check("the mini-graph's expand button links to the full graph (through the right "
                        + "depth prefix, Dune being two folders deep) focused on this note",
                duneHtml.contains("data-href=\"../../graph.html#focus=dune\""), duneHtml);
        check("a note with zero neighbours (orphan) still gets a mini-graph, just showing itself alone",
                orphanHtml.contains("window.__JYLOS_MINI_GRAPH__") && orphanHtml.contains("\"id\":\"orphan\""),
                orphanHtml);
        check("orphan's mini-graph has no edges (no neighbours to link to)",
                orphanHtml.contains("\"edges\":[]"), orphanHtml);
        check("a link to a PRIVATE note does not leak it into the mini-graph either",
                !foundationHtml.substring(foundationHtml.indexOf("__JYLOS_MINI_GRAPH__"),
                        foundationHtml.indexOf(";</script>", foundationHtml.indexOf("__JYLOS_MINI_GRAPH__")))
                        .contains("Secret"),
                foundationHtml);

        // The checks above only ever did .contains(...) on the raw HTML string — that
        // proves the substrings are present, not that the embedded array is syntactically
        // valid JS/JSON. A real parse is the only thing that would have caught the bug
        // this used to have: nodesJson always starts with the self-node already appended
        // (unconditionally, before the neighbour loop), so the FIRST neighbour needs a
        // leading comma too, not just the ones after it — conflating that with edgesJson's
        // genuinely-empty-at-first comma logic dropped the comma between the self-node and
        // neighbour #1 whenever a note had any neighbours at all, e.g. `[{...}{...}]`. That
        // is invalid syntax, so the assignment throws and window.__JYLOS_MINI_GRAPH__ never
        // gets set — mini-graph.js's `if (!data) return;` then draws nothing, not even the
        // self-node, even though the exported HTML string still "contains" every substring
        // the checks above look for.
        String duneMiniGraphJson = duneHtml.substring(
                duneHtml.indexOf("__JYLOS_MINI_GRAPH__ = ") + "__JYLOS_MINI_GRAPH__ = ".length(),
                duneHtml.indexOf(";</script>", duneHtml.indexOf("__JYLOS_MINI_GRAPH__")));
        JsonObject duneMiniGraph = null;
        try {
            duneMiniGraph = JsonParser.parseString(duneMiniGraphJson).getAsJsonObject();
        } catch (JsonSyntaxException e) {
            // left null — checked below
        }
        check("Dune's mini-graph payload actually parses as valid JSON (real parse, not "
                        + "string-contains — see comment above)",
                duneMiniGraph != null, duneMiniGraphJson);
        if (duneMiniGraph != null) {
            check("...and the parsed payload has BOTH nodes (self + Foundation), not just the self-node",
                    duneMiniGraph.getAsJsonArray("nodes").size() == 2, duneMiniGraphJson);
            check("...and the one edge between them",
                    duneMiniGraph.getAsJsonArray("edges").size() == 1, duneMiniGraphJson);
        }

        System.out.println("\n-- 'Linked mentions' (backlinks) --");
        check("Foundation shows a 'Linked mentions' section listing Dune (which links to it)",
                foundationHtml.contains("Linked mentions") && foundationHtml.contains(">Dune<"), foundationHtml);
        check("Dune shows a 'Linked mentions' section listing Foundation (which links to it)",
                duneHtml.contains("Linked mentions") && duneHtml.contains(">Foundation<"), duneHtml);
        check("a note nobody links to (orphan) gets no 'Linked mentions' section at all",
                !orphanHtml.contains("Linked mentions"), orphanHtml);
        // Dune's own content links to itself ("[[Dune]]") — isolate the backlinks
        // section text before checking, since the page's own <h1>Dune</h1> title
        // also contains the substring ">Dune<" and would give a false pass/fail
        // either way if not excluded from this check.
        String duneBacklinksSection = duneHtml.substring(duneHtml.indexOf("Linked mentions"));
        check("a note does not 'backlink' itself even though it links to itself",
                !duneBacklinksSection.contains(">Dune<"), duneBacklinksSection);

        System.out.println("\n-- site scaffolding written --");
        check("index.html written", Files.exists(outputDir.resolve("index.html")), "missing index.html");
        check("graph.html written", Files.exists(outputDir.resolve("graph.html")), "missing graph.html");
        check("assets/style.css written", Files.exists(outputDir.resolve("assets/style.css")), "missing style.css");
        check("assets/graph.js written", Files.exists(outputDir.resolve("assets/graph.js")), "missing graph.js");
        check("assets/nav.js written", Files.exists(outputDir.resolve("assets/nav.js")), "missing nav.js");
        check("assets/nav-data.js written", Files.exists(outputDir.resolve("assets/nav-data.js")), "missing nav-data.js");

        String navData = Files.readString(outputDir.resolve("assets/nav-data.js"), StandardCharsets.UTF_8);
        check("nav-data.js is a plain assignment, loaded via <script src>, not JSON to fetch",
                navData.startsWith("window.__JYLOS_NAV__ = {"), navData);
        check("nav-data.js includes the 'books' folder and Dune's entry", navData.contains("\"books\"")
                && navData.contains("\"Dune\""), navData);
        check("nav-data.js does not leak the private note's title anywhere",
                !navData.contains("Secret Diary"), navData);
        check("nav-data.js does not leak the attachment's title anywhere",
                !navData.contains("cover.png"), navData);

        System.out.println("\n-- exportSingleNote: partial republish over an already-published site --");
        byte[] orphanBytesBefore = Files.readAllBytes(orphanPage);
        byte[] foundationBytesBefore = Files.readAllBytes(foundationPage);

        // Change Dune's content for real, then republish just Dune — not a full re-export.
        dune.setContent("A desert planet, now revised. See [[Foundation]] and [[Nonexistent]]."
                + " Also links to itself: [[Dune]].");
        VaultExporter.SingleNoteResult singleResult = exporter.exportSingleNote(outputDir, "dune", (done, total) -> { });

        check("exportSingleNote writes only Dune + its direct neighbour Foundation (2 pages), not the whole vault",
                singleResult.pagesWritten() == 2, "pagesWritten=" + singleResult.pagesWritten());
        check("exportSingleNote reports the graph was regenerated (graph.html already existed from the full export)",
                singleResult.graphRegenerated(), "graphRegenerated=" + singleResult.graphRegenerated());

        String duneHtmlAfterSingle = Files.readString(dunePage, StandardCharsets.UTF_8);
        check("Dune's page reflects the NEW content after the single-note republish",
                duneHtmlAfterSingle.contains("now revised"), duneHtmlAfterSingle);

        byte[] foundationBytesAfter = Files.readAllBytes(foundationPage);
        check("Foundation (Dune's neighbour) WAS rewritten too, even though its own content didn't change "
                        + "— its rendered page includes Dune's backlink entry, which is still the same text here, "
                        + "but the page is legitimately part of the 'must re-check' set",
                foundationBytesAfter.length > 0, "foundation page missing after partial republish");
        check("Foundation's own backlink to Dune still resolves correctly after the partial republish",
                new String(foundationBytesAfter, StandardCharsets.UTF_8).contains("href=\"../Dune.html\""),
                new String(foundationBytesAfter, StandardCharsets.UTF_8));

        byte[] orphanBytesAfter = Files.readAllBytes(orphanPage);
        check("a note unrelated to Dune (orphan) is byte-for-byte untouched by the single-note republish",
                java.util.Arrays.equals(orphanBytesBefore, orphanBytesAfter), "orphan.html changed unexpectedly");

        // exportSingleNote must reuse whatever a prior full export's OWN options were
        // (site title, graph on/off) via the manifest — not guess from what happens to
        // already exist in the directory.
        Path noGraphDir = Files.createTempDirectory("jylos-publish-single-nograph-test");
        exporter.export(noGraphDir, new VaultExporter.PublishOptions("Custom Title", false, null),
                (done, total) -> { });
        check("sanity: the setup export with generateGraph=false really did skip graph.html",
                Files.notExists(noGraphDir.resolve("graph.html")), "graph.html should not exist yet");

        VaultExporter.SingleNoteResult noGraphResult =
                exporter.exportSingleNote(noGraphDir, "dune", (done, total) -> { });
        check("exportSingleNote reads the manifest and keeps the graph off, matching the original publish",
                !noGraphResult.graphRegenerated(), "graphRegenerated=" + noGraphResult.graphRegenerated());
        check("...and indeed no graph.html gets written", Files.notExists(noGraphDir.resolve("graph.html")),
                "graph.html should not exist");
        check("nav-data.js is still refreshed even without a graph", Files.exists(noGraphDir.resolve("assets/nav-data.js")),
                "missing nav-data.js");
        String duneHtmlNoGraph = Files.readString(noGraphDir.resolve("notes/books/Dune.html"), StandardCharsets.UTF_8);
        check("the custom site title from the manifest carries over into the single-note republish",
                duneHtmlNoGraph.contains(">Custom Title</a>"), duneHtmlNoGraph);

        System.out.println("\n-- publish manifest (what lets exportSingleNote match a prior export) --");
        Path manifestPath = noGraphDir.resolve(".jylos-publish.manifest");
        check("the manifest file is written by export()", Files.exists(manifestPath), "missing manifest");
        String manifestText = Files.readString(manifestPath, StandardCharsets.UTF_8);
        check("manifest records the custom site title", manifestText.contains("siteTitle=Custom Title"), manifestText);
        check("manifest records generateGraph=false", manifestText.contains("generateGraph=false"), manifestText);
        VaultExporter.PublishOptions readBack = VaultExporter.readManifest(noGraphDir);
        check("readManifest() reconstructs the same options that were written",
                "Custom Title".equals(readBack.siteTitle()) && !readBack.generateGraph()
                        && readBack.includedNoteIds() == null,
                readBack.toString());
        check("readManifest() on a directory with no manifest at all falls back to defaults",
                VaultExporter.readManifest(Files.createTempDirectory("jylos-publish-no-manifest-test"))
                        .equals(VaultExporter.PublishOptions.defaults()),
                "expected defaults");

        System.out.println("\n-- PublishOptions.includedNoteIds: a real selection filter --");
        Path filteredDir = Files.createTempDirectory("jylos-publish-filtered-test");
        VaultExporter.Result filteredResult = exporter.export(filteredDir,
                new VaultExporter.PublishOptions("Filtered Site", true, Set.of("dune", "orphan")),
                (done, total) -> { });
        // Base exportable pool (private/attachment already filtered out) is dune,
        // foundation, orphan, lazyStub, corrupt, scriptBreaker = 6 — {dune, orphan}
        // included leaves 4 skipped by filter (foundation, lazyStub, corrupt,
        // scriptBreaker; "corrupt" is skipped by filter here, not skippedErrors,
        // since the selection filter runs before pass 1 ever gets to it).
        check("only the 2 explicitly included notes are exported, the rest counted as skippedByFilter",
                filteredResult.exportedNotes() == 2 && filteredResult.skippedByFilter() == 4,
                "exportedNotes=" + filteredResult.exportedNotes() + " skippedByFilter=" + filteredResult.skippedByFilter());
        check("an included note's page is actually written",
                Files.exists(filteredDir.resolve("notes/books/Dune.html")), "missing Dune.html");
        check("an excluded note's page is NOT written even though it would otherwise be perfectly exportable",
                Files.notExists(filteredDir.resolve("notes/books/scifi/Foundation.html")), "Foundation.html should not exist");
        String filteredDuneHtml = Files.readString(filteredDir.resolve("notes/books/Dune.html"), StandardCharsets.UTF_8);
        check("a link to an excluded note becomes dead ('#'), the same as a link to a private note",
                filteredDuneHtml.contains("Also links to itself") // sanity the right fixture loaded
                        && !filteredDuneHtml.contains("href=\"scifi/Foundation.html\""),
                filteredDuneHtml);
        String filteredNavData = Files.readString(filteredDir.resolve("assets/nav-data.js"), StandardCharsets.UTF_8);
        check("the excluded note's title does not leak into nav-data.js either",
                !filteredNavData.contains("Foundation"), filteredNavData);

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static Folder folder(String id) {
        Folder f = new Folder(id, null, null);
        f.setId(id);
        return f;
    }

    private static Note note(String id, String title, String content) {
        Note n = new Note(id, content);
        n.setId(id);
        n.setTitle(title);
        return n;
    }

    private static NoteDAO noOpNoteDao() {
        return (NoteDAO) Proxy.newProxyInstance(NoteDAO.class.getClassLoader(),
                new Class<?>[] { NoteDAO.class }, (proxy, method, methodArgs) -> defaultValue(method.getReturnType()));
    }

    private static FolderDAO noOpFolderDao() {
        return (FolderDAO) Proxy.newProxyInstance(FolderDAO.class.getClassLoader(),
                new Class<?>[] { FolderDAO.class }, (proxy, method, methodArgs) -> defaultValue(method.getReturnType()));
    }

    private static TagDAO noOpTagDao() {
        return (TagDAO) Proxy.newProxyInstance(TagDAO.class.getClassLoader(),
                new Class<?>[] { TagDAO.class }, (proxy, method, methodArgs) -> defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return type == List.class ? List.of() : null;
        }
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0d;
        return null;
    }
}
