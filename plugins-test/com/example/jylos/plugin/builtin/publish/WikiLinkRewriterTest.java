package com.example.jylos.plugin.builtin.publish;

/**
 * Behavioural checks for {@link WikiLinkRewriter}, run by scripts/test-plugins.sh.
 */
public final class WikiLinkRewriterTest {

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

    public static void main(String[] args) {
        System.out.println("\n-- resolved link rewritten to the given relative href --");
        WikiLinkRewriter resolved = new WikiLinkRewriter(title -> "Foundation".equals(title) ? "../Foundation.html" : null);
        String html1 = resolved.rewrite("<a class=\"wikilink\" href=\"jylos://open-note/Foundation\">Foundation</a>");
        check("href points at the resolved relative path", html1.contains("href=\"../Foundation.html\""), html1);
        check("original wikilink class is preserved", html1.contains("class=\"wikilink\""), html1);

        System.out.println("\n-- unresolved link becomes a dead '#' href --");
        WikiLinkRewriter unresolved = new WikiLinkRewriter(title -> null);
        String html2 = unresolved.rewrite("<a class=\"wikilink wikilink-new\" href=\"jylos://open-note/Nope\">Nope</a>");
        check("href becomes '#'", html2.contains("href=\"#\""), html2);
        check("broken-link styling class is preserved", html2.contains("wikilink-new"), html2);

        System.out.println("\n-- heading anchor is appended and slugified --");
        WikiLinkRewriter withHeading = new WikiLinkRewriter(title -> "Foundation".equals(title) ? "Foundation.html" : null);
        String html3 = withHeading.rewrite("<a href=\"jylos://open-note/Foundation%7CThe%20Mule\">link</a>"
                .replace("%7C", "|"));
        check("heading gets appended as a slug anchor", html3.contains("href=\"Foundation.html#the-mule\""), html3);

        System.out.println("\n-- percent-encoded title is decoded before resolving --");
        boolean[] sawDecodedTitle = {false};
        WikiLinkRewriter decoding = new WikiLinkRewriter(title -> {
            if ("Two Words".equals(title)) {
                sawDecodedTitle[0] = true;
            }
            return "Two-Words.html";
        });
        decoding.rewrite("<a href=\"jylos://open-note/Two%20Words\">x</a>");
        check("resolver receives the decoded title, not the raw percent-encoding", sawDecodedTitle[0],
                "resolver was never called with the decoded title");

        System.out.println("\n-- multiple links in the same document are each rewritten independently --");
        WikiLinkRewriter multi = new WikiLinkRewriter(title -> "A".equals(title) ? "A.html" : ("B".equals(title) ? "B.html" : null));
        String html5 = multi.rewrite(
                "<p><a href=\"jylos://open-note/A\">A</a> and <a href=\"jylos://open-note/B\">B</a> and "
                        + "<a href=\"jylos://open-note/C\">C</a></p>");
        check("first link resolved", html5.contains("href=\"A.html\""), html5);
        check("second link resolved", html5.contains("href=\"B.html\""), html5);
        check("third (unresolved) link becomes dead", html5.contains("href=\"#\""), html5);

        System.out.println("\n-- HTML with no jylos:// links passes through unchanged --");
        WikiLinkRewriter noOp = new WikiLinkRewriter(title -> "should never be called");
        String plain = "<p>Just a normal paragraph, <a href=\"https://example.com\">external link</a>.</p>";
        check("plain HTML/external links are untouched", plain.equals(noOp.rewrite(plain)), noOp.rewrite(plain));

        System.out.println("\n-- null/empty input --");
        WikiLinkRewriter any = new WikiLinkRewriter(title -> null);
        check("null input returns empty string, not NPE", "".equals(any.rewrite(null)), "expected empty string");
        check("empty input returns empty string", "".equals(any.rewrite("")), "expected empty string");

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
