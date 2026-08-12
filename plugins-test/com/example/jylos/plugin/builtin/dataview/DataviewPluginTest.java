package com.example.jylos.plugin.builtin.dataview;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.plugin.PreviewContext;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;

/**
 * Behavioural checks for the Dataview plugin, run by scripts/test-plugins.sh.
 *
 * <p>Lives outside the Maven module because plugin sources are compiled against the app
 * as an external JAR, not as part of it — the same way a third-party plugin would be. A
 * plain main() keeps the plugin build free of a test-framework dependency.</p>
 */
public final class DataviewPluginTest {

    private static int passed;
    private static int failed;

    static final class FakeSource implements PageSource {
        private final List<Page> pages = new ArrayList<>();

        void add(String id, String title, String folder, String content) {
            Note note = new Note(id, title, content);
            note.setCreatedDate("2026-01-05T10:00:00Z");
            note.setModifiedDate("2026-02-10T12:30:00Z");
            pages.add(PageParser.parse(note, folder, id));
        }

        void linkUp() {
            for (Page source : pages) {
                for (Link out : source.outlinks()) {
                    Page target = pageByTitle(out.target());
                    if (target != null && target != source) {
                        target.addInlink(source.title());
                    }
                }
            }
        }

        @Override public List<Page> pages() { return pages; }

        @Override public Page pageByTitle(String title) {
            for (Page page : pages) {
                if (page.title().equalsIgnoreCase(title)) return page;
            }
            return null;
        }
    }

    private static FakeSource vault() {
        FakeSource source = new FakeSource();
        source.add("books/dune.md", "Dune", "books", """
                ---
                tags: [book, scifi]
                rating: 5
                author: Frank Herbert
                published: 1965-08-01
                genres: [scifi, classic]
                ---
                A desert planet. Links to [[Foundation]].

                status:: read
                - [ ] Re-read part two [due:: 2026-03-01]
                - [x] Write review
                """);
        source.add("books/foundation.md", "Foundation", "books", """
                ---
                tags:
                  - book
                  - scifi
                rating: 4
                author: Isaac Asimov
                ---
                Psychohistory. #classic

                - [ ] Finish trilogy
                """);
        source.add("notes/meeting.md", "Meeting", "notes", """
                ---
                tags: [work]
                rating: 2
                ---
                Discussed [[Dune]].
                priority:: high
                - [ ] Send summary
                """);
        source.add("archive/old.md", "Old Note", "archive", """
                ---
                tags: [book]
                rating: 1
                ---
                Archived.
                """);
        source.linkUp();
        return source;
    }

    private static String run(FakeSource source, String query, Page current) {
        return DataviewRenderer.render(new QueryEngine(source).run(DqlParser.parse(query), current));
    }

    private static void check(String name, boolean condition, String detail) {
        if (condition) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name + "  -> " + detail);
        }
    }

    public static void main(String[] args) {
        FakeSource source = vault();
        Page dune = source.pageByTitle("Dune");

        System.out.println("\n-- metadata extraction --");
        check("frontmatter scalar", "Frank Herbert".equals(dune.resolve("author")),
                String.valueOf(dune.resolve("author")));
        check("frontmatter number is numeric", DqlValue.asNumber(dune.resolve("rating")) == 5.0,
                String.valueOf(dune.resolve("rating")));
        check("frontmatter inline list", DqlValue.asList(dune.resolve("genres")).size() == 2,
                String.valueOf(dune.resolve("genres")));
        check("frontmatter block list (Foundation tags)",
                source.pageByTitle("Foundation").tags().contains("#book"),
                String.valueOf(source.pageByTitle("Foundation").tags()));
        check("frontmatter date typed", dune.resolve("published") instanceof java.time.LocalDate,
                String.valueOf(dune.resolve("published")));
        check("inline field key:: value", "read".equals(dune.resolve("status")),
                String.valueOf(dune.resolve("status")));
        check("body #tag collected", source.pageByTitle("Foundation").tags().contains("#classic"),
                String.valueOf(source.pageByTitle("Foundation").tags()));
        check("tasks parsed", dune.tasks().size() == 2, String.valueOf(dune.tasks().size()));
        check("task completion", dune.tasks().get(1).completed(), "second task should be done");
        check("task inline field", dune.tasks().get(0).fields().containsKey("due"),
                String.valueOf(dune.tasks().get(0).fields()));
        check("task text strips bracket field",
                !dune.tasks().get(0).text().contains("due::"), dune.tasks().get(0).text());
        check("outlinks", dune.outlinks().size() == 1 && dune.outlinks().get(0).target().equals("Foundation"),
                String.valueOf(dune.outlinks()));
        check("inlinks resolved", DqlValue.toDisplayString(
                        ((java.util.Map<?, ?>) dune.fileObject()).get("inlinks")).contains("Meeting"),
                String.valueOf(dune.fileObject().get("inlinks")));

        System.out.println("\n-- TABLE --");
        String table = run(source, "TABLE rating AS \"Score\" FROM #book SORT rating DESC", dune);
        check("table renders rows", table.contains("<table") && table.contains("Score"), table);
        check("table ordered desc", table.indexOf("Dune") < table.indexOf("Foundation"), table);
        check("table has file column", table.contains(">File<"), table);

        String withoutId = run(source, "TABLE WITHOUT ID rating FROM #book", dune);
        check("WITHOUT ID drops file column", !withoutId.contains(">File<"), withoutId);

        System.out.println("\n-- WHERE / operators --");
        String where = run(source, "TABLE rating FROM #book WHERE rating >= 4", dune);
        check("where numeric filter", where.contains("Dune") && where.contains("Foundation")
                && !where.contains("Old Note"), where);
        String andOr = run(source, "LIST WHERE rating > 3 AND contains(author, \"Herbert\")", dune);
        check("where AND + contains()", andOr.contains("Dune") && !andOr.contains("Foundation"), andOr);
        String negated = run(source, "LIST FROM #book AND -\"archive\"", dune);
        check("FROM negation excludes folder", !negated.contains("Old Note"), negated);

        System.out.println("\n-- FROM sources --");
        check("FROM folder", run(source, "LIST FROM \"books\"", dune).contains("Dune"), "");
        String incoming = run(source, "LIST FROM [[Dune]]", dune);
        check("FROM [[link]] = pages linking to it",
                incoming.contains("Meeting") && !incoming.contains("Foundation"), incoming);
        String outgoing = run(source, "LIST FROM outgoing([[Dune]])", dune);
        check("FROM outgoing()", outgoing.contains("Foundation") && !outgoing.contains("Meeting"), outgoing);

        System.out.println("\n-- LIMIT / GROUP BY / FLATTEN --");
        String limited = run(source, "LIST FROM #book LIMIT 1", dune);
        check("LIMIT caps rows", limited.split("<li>").length - 1 == 1, limited);
        String grouped = run(source, "TABLE rating FROM #book GROUP BY author", dune);
        check("GROUP BY buckets", grouped.contains("dataview-group-header")
                && grouped.contains("Frank Herbert"), grouped);
        String flattened = run(source, "LIST genre FROM #book FLATTEN genres AS genre WHERE genre = \"classic\"", dune);
        check("FLATTEN + filter on binding",
                flattened.contains("classic") && !flattened.contains("scifi"), flattened);

        System.out.println("\n-- TASK --");
        String tasks = run(source, "TASK WHERE !completed", dune);
        check("task query filters incomplete", tasks.contains("Re-read part two")
                && !tasks.contains("Write review"), tasks);
        check("task renders checkbox", tasks.contains("type=\"checkbox\""), tasks);
        check("tasks grouped by note", tasks.contains("dataview-group-header"), tasks);
        String taskField = run(source, "TASK WHERE due", dune);
        check("task inline field usable in WHERE", taskField.contains("Re-read"), taskField);

        System.out.println("\n-- functions & expressions --");
        check("length()", eval(source, dune, "length(genres)").equals(2.0), "");
        check("upper()", "DUNE".equals(eval(source, dune, "upper(file.name)")), "");
        check("default()", "n/a".equals(eval(source, dune, "default(missing, \"n/a\")")), "");
        check("choice()", "yes".equals(eval(source, dune, "choice(rating > 4, \"yes\", \"no\")")), "");
        check("round()", eval(source, dune, "round(7/3, 2)").equals(2.33),
                String.valueOf(eval(source, dune, "round(7/3, 2)")));
        check("join()", "scifi|classic".equals(eval(source, dune, "join(genres, \"|\")")), "");
        check("dateformat()", "1965".equals(eval(source, dune, "dateformat(published, \"yyyy\")")), "");
        check("date arithmetic (days between)",
                eval(source, dune, "date(\"2026-01-10\") - date(\"2026-01-01\")").equals(9.0),
                String.valueOf(eval(source, dune, "date(\"2026-01-10\") - date(\"2026-01-01\")")));
        check("dur() shifts a date",
                "2026-01-01".equals(DqlValue.toDisplayString(
                        eval(source, dune, "date(\"2026-01-08\") - dur(\"1 week\")"))),
                String.valueOf(eval(source, dune, "date(\"2026-01-08\") - dur(\"1 week\")")));
        check("this.file.name", "Dune".equals(eval(source, dune, "this.file.name")), "");
        check("sum over list", eval(source, dune, "sum([1,2,3])").equals(6.0), "");
        check("nested field access", "Isaac Asimov".equals(
                DqlValue.toDisplayString(eval(source, dune, "link(\"Foundation\").author"))),
                String.valueOf(eval(source, dune, "link(\"Foundation\").author")));
        check("short-circuit guard on missing field",
                Boolean.FALSE.equals(eval(source, dune, "missing and length(missing) > 0")), "");

        System.out.println("\n-- rendering safety --");
        FakeSource evil = new FakeSource();
        evil.add("x.md", "Evil", "", "---\nnote: <img src=x onerror=alert(1)>\n---\nbody\n");
        String escaped = run(evil, "TABLE note", null);
        check("field values are HTML-escaped", !escaped.contains("<img") && escaped.contains("&lt;img"),
                escaped);

        System.out.println("\n-- enhancer end-to-end --");
        DataviewRunner runner = new DataviewRunner(source);
        DataviewEnhancer enhancer = new DataviewEnhancer(source, runner);
        Note current = new Note("books/dune.md", "Dune", "");
        PreviewContext context = new PreviewContext(current, false);

        String html = "<p>Before</p><pre><code class=\"language-dataview\">TABLE rating\nFROM #book\n"
                + "WHERE rating &gt;= 4</code></pre><p>After</p>";
        String out = enhancer.transformHtml(context, html);
        check("block replaced by table", out.contains("<table") && !out.contains("language-dataview"), out);
        check("escaped operator survives", out.contains("Dune") && !out.contains("Old Note"), out);
        check("surrounding content kept", out.contains("Before") && out.contains("After"), out);
        check("styles injected once", out.indexOf("dataview-group-header") >= 0
                || out.contains("<style>"), out);

        String inline = enhancer.transformHtml(context, "<p>Note <code>= this.file.name</code></p>");
        check("inline query evaluated", inline.contains("Dune"), inline);
        String untouched = enhancer.transformHtml(context, "<p>code <code>x = 1</code></p>");
        check("normal code span untouched", untouched.contains("<code>x = 1</code>"), untouched);

        String bad = enhancer.transformHtml(context,
                "<pre><code class=\"language-dataview\">TABEL nope</code></pre>");
        check("bad query shows error box", bad.contains("dataview-error"), bad);
        String js = enhancer.transformHtml(context,
                "<pre><code class=\"language-dataviewjs\">dv.pages()</code></pre>");
        check("dataviewjs reports unsupported", js.contains("not supported"), js);

        String noQuery = "<p>plain</p>";
        check("note without queries unchanged",
                noQuery.equals(enhancer.transformHtml(context, noQuery)), "");

        System.out.println("\n-- editor block renderer (Live Preview) --");
        DataviewBlockRenderer blockRenderer = new DataviewBlockRenderer(runner);
        // The editor hands over the raw block body: nothing has HTML-escaped it here.
        String editorHtml = blockRenderer.render(current, "TABLE rating\nFROM #book\nWHERE rating >= 4");
        check("renders a table for the editor", editorHtml.contains("<table") && editorHtml.contains("Dune"),
                editorHtml);
        check("editor styles are theme-agnostic (CSS vars, no baked colours)",
                editorHtml.contains("var(--jylos-") && !editorHtml.contains("#f5f6f8"), editorHtml);
        check("raw operators need no unescaping in the editor",
                !stripStyles(editorHtml).contains("dataview-error"), editorHtml);
        check("both surfaces agree on the same query",
                stripStyles(editorHtml).equals(stripStyles(
                        enhancer.transformHtml(context,
                                "<pre><code class=\"language-dataview\">TABLE rating\nFROM #book\n"
                                        + "WHERE rating &gt;= 4</code></pre>"))),
                "preview and editor output diverged");
        check("this resolves to the edited note",
                blockRenderer.render(current, "LIST WHERE file.name = this.file.name").contains("Dune"), "");
        String editorError = blockRenderer.render(current, "TABEL nope");
        check("bad query renders an error box, not an exception",
                stripStyles(editorError).contains("dataview-error"), editorError);

        System.out.println("\n-- typographic (smart) quotes --");
        // macOS "Smart Quotes" substitutes straight quotes broadly in text input,
        // including a query typed straight into the editor — a curly-quote string is a
        // routine input, not a crafted edge case.
        check("curly double quotes work as string literals",
                eval(source, dune, "upper(“hello”)").equals("HELLO"), "");
        check("curly single quotes work as string literals",
                eval(source, dune, "upper(‘hello’)").equals("HELLO"), "");
        String curlyQuery = run(source, "TABLE rating AS “Score” FROM #book WHERE rating >= 4", dune);
        check("a full query with curly quotes renders normally, not an error",
                !stripStyles(curlyQuery).contains("dataview-error") && curlyQuery.contains("Score"), curlyQuery);

        System.out.println("\n-- insert-template menu item --");
        // Mirrors MermaidPlugin's "Insert Mermaid Template": the menu shows this string
        // verbatim in an info dialog for the user to copy. It must actually be a valid,
        // runnable query, not just plausible-looking text.
        String template = "TABLE rating AS \"Score\", file.mtime AS \"Updated\"\n"
                + "FROM #tag\n"
                + "WHERE rating >= 4\n"
                + "SORT rating DESC\n"
                + "LIMIT 10";
        String templateResult = run(source, template, dune);
        check("the insert-template query is valid and runs without an error box",
                !stripStyles(templateResult).contains("dataview-error"), templateResult);

        System.out.println("\n-- DataviewIndex resilience to unparseable notes --");
        checkIndexSkipsUnparseableNotesWithoutFailingTheWholeVault();

        System.out.println("\n========================================");
        System.out.println("passed: " + passed + "   failed: " + failed);
        System.out.println("========================================");
        if (failed > 0) {
            System.exit(1);
        }
    }

    /** Drops style blocks so the two surfaces can be compared on their markup alone. */
    private static String stripStyles(String html) {
        return html.replaceAll("(?s)<style>.*?</style>", "").trim();
    }

    /**
     * Regression check for a real crash: {@code DataviewIndex.build()} used to let one
     * note's unparseable content (bad frontmatter YAML) throw out of the whole indexing
     * loop, blanking every query in the vault instead of excluding just that note. Uses
     * the real filesystem DAO — not {@link FakeSource} — because the bug lived in the
     * {@code getNoteById} call {@link DataviewIndex} makes, which {@code FakeSource}
     * bypasses entirely.
     */
    private static void checkIndexSkipsUnparseableNotesWithoutFailingTheWholeVault() {
        try {
            Path vault = Files.createTempDirectory("dataview-index-resilience-");
            Files.writeString(vault.resolve("Good.md"), """
                    ---
                    rating: 5
                    ---
                    A perfectly normal note.
                    """, StandardCharsets.UTF_8);
            // Mirrors the real failure: a YAML folded scalar ('>') followed by a line that
            // is not valid folded-scalar continuation syntax. Written directly to disk,
            // bypassing the app's own writer, exactly like a hand-edited or externally
            // synced vault file would arrive.
            Files.writeString(vault.resolve("Broken.md"), """
                    ---
                    Body: > this
                      is not: valid, folded > scalar syntax
                    ---
                    Content that Jylos itself would never have written this way.
                    """, StandardCharsets.UTF_8);

            NoteDAOFileSystem noteDAO = new NoteDAOFileSystem(vault.toString());
            FolderDAOFileSystem folderDAO = new FolderDAOFileSystem(vault.toString());
            NoteService noteService = new NoteService(noteDAO, folderDAO);
            FolderService folderService = new FolderService(folderDAO, noteDAO);

            DataviewIndex index = new DataviewIndex(noteService, folderService);
            List<Page> pages = index.pages();

            check("build() does not throw when a note has unparseable frontmatter", true, "");
            check("the well-formed note is still indexed",
                    pages.stream().anyMatch(page -> "Good".equals(page.title())),
                    pages.stream().map(Page::title).toList().toString());
            check("the unparseable note is excluded, not silently given empty metadata",
                    pages.stream().noneMatch(page -> "Broken".equals(page.title())),
                    pages.stream().map(Page::title).toList().toString());
        } catch (IOException e) {
            check("DataviewIndex resilience test setup", false, e.toString());
        } catch (RuntimeException e) {
            check("build() does not throw when a note has unparseable frontmatter", false, e.toString());
        }
    }

    private static Object eval(FakeSource source, Page page, String expression) {
        return new DqlEvaluator(source, page)
                .evaluate(DqlParser.parseExpression(expression), DqlEvaluator.Row.of(page));
    }
}
