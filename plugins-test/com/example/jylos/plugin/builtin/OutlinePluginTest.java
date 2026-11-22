package com.example.jylos.plugin.builtin;

import java.util.List;
import java.util.stream.Collectors;

import com.example.jylos.plugin.builtin.OutlinePlugin.Entry;
import com.example.jylos.util.InlineMarkdown;
import com.example.jylos.util.MarkdownPreview;

/**
 * The outline lists a note's headings as the reading view shows them: without their marks,
 * nested under the heading above, a {@code #} line inside code left out, and each with the id
 * the reading view gives that heading (so clicking the second of two equal headings goes to
 * the second). Run by scripts/test-plugins.sh.
 */
public final class OutlinePluginTest {

    private static int passed;
    private static int failed;

    private static void check(String name, Object expected, Object actual) {
        if (expected.equals(actual)) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name + "  -> expected " + expected + " but was " + actual);
        }
    }

    public static void main(String[] args) {
        System.out.println("\n-- outline entries --");
        theHeadingsShowWithoutTheirMarksAndNested();
        aHashLineInsideCodeIsNotAHeading();
        eachHeadingHasTheIdTheReadingViewGivesIt();
        System.out.println("\npassed: " + passed + "   failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static String text(Entry entry) {
        return entry.runs().stream().map(InlineMarkdown.Run::text).collect(Collectors.joining());
    }

    /** Each entry as "depth:text". */
    private static List<String> shown(String content) {
        return OutlinePlugin.entries(content).stream().map(e -> e.depth() + ":" + text(e)).toList();
    }

    private static void theHeadingsShowWithoutTheirMarksAndNested() {
        check("marks gone, nested by the headings above",
                List.of("0:Plan con negrita", "1:Fase x", "0:Otra", "1:Final"),
                shown("## Plan con **negrita**\n### Fase `x` ###\n# Otra\n### Final\n"));
        Entry bold = OutlinePlugin.entries("# Uno **dos**").get(0);
        check("the bold piece is bold, marked here with *", List.of("Uno ", "dos*"), bold.runs().stream()
                .map(run -> run.text() + (run.styles().contains(InlineMarkdown.Style.BOLD) ? "*" : "")).toList());
        check("where its line starts", List.of(0, 6), OutlinePlugin.entries("# Uno\n# Dos").stream()
                .map(Entry::offset).toList());
        check("no headings, none listed", List.of(), shown("Texto\n#etiqueta\n"));
    }

    private static void aHashLineInsideCodeIsNotAHeading() {
        check("a # comment in a code block is left out", List.of("0:Instalar"),
                shown("# Instalar\n```bash\n# comentario\nnpm i\n```\n"));
    }

    private static void eachHeadingHasTheIdTheReadingViewGivesIt() {
        String note = "# Resumen\n## Ver [[Idea|la idea]] y **más**\n# Resumen\n";
        List<String> anchors = OutlinePlugin.entries(note).stream().map(Entry::anchor).toList();
        check("ids, the repeated one numbered", List.of("resumen", "ver-la-idea-y-más", "resumen-1"), anchors);
        String html = MarkdownPreview.renderBody(note, false, true, MarkdownPreview.PreviewOptions.none());
        check("the reading view has those ids", List.of(true, true, true),
                anchors.stream().map(id -> html.contains("id=\"" + id + "\"")).toList());
    }
}
