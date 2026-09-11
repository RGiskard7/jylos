package com.example.jylos.plugin.builtin.publish;

/**
 * Behavioural checks for {@link PublishTemplates}, run by scripts/test-plugins.sh.
 *
 * <p>Guards against the exact class of bug the CSS/JS text blocks are prone to:
 * {@code page()} runs its template through {@code String.formatted()} (so a
 * literal {@code %} inside it must be escaped {@code %%}), but {@code css()} and
 * {@code graphJs()} return their text block as-is with no {@code .formatted()}
 * call at all — a {@code %%} accidentally left in either of those would leak into
 * the published site as a literal, broken {@code 100%%} instead of {@code 100%}.</p>
 */
public final class PublishTemplatesTest {

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
        System.out.println("\n-- page() substitutes every placeholder --");
        String html = PublishTemplates.page("My <Title>", "../../", "<p>body</p>", "", "notes/a/b.html", "a",
                "Jylos Vault");
        check("title is HTML-escaped in <title>", html.contains("<title>My &lt;Title&gt;</title>"), html);
        check("depth prefix applied to stylesheet href", html.contains("href=\"../../assets/style.css\""), html);
        check("depth prefix applied to nav links", html.contains("href=\"../../index.html\"")
                && html.contains("href=\"../../graph.html\""), html);
        check("depth prefix applied to nav-data.js and nav.js", html.contains("src=\"../../assets/nav-data.js\"")
                && html.contains("src=\"../../assets/nav.js\""), html);
        check("body html is embedded verbatim", html.contains("<p>body</p>"), html);
        check("pageHref is written onto <body> for nav.js to read",
                html.contains("data-page-href=\"notes/a/b.html\""), html);
        check("pageFolderPath is written onto <body> for nav.js to read",
                html.contains("data-page-folder=\"a\""), html);
        check("sidebar nav placeholder is present for nav.js to render into",
                html.contains("id=\"jylos-sidebar\""), html);
        check("site title is used as the header home-link label",
                html.contains("<a class=\"site-title\" href=\"../../index.html\">Jylos Vault</a>"), html);
        check("a blank site title falls back to 'Jylos Vault'",
                PublishTemplates.page("Root", "", "x", "", "index.html", "", "  ")
                        .contains(">Jylos Vault</a>"), html);
        check("root page (empty depth prefix) still resolves to a plain relative link",
                PublishTemplates.page("Root", "", "x", "", "index.html", "", "Jylos Vault")
                        .contains("href=\"index.html\""), html);

        System.out.println("\n-- page()'s optional TOC pane --");
        String withoutToc = PublishTemplates.page("T", "", "body", "", "index.html", "", "Jylos Vault");
        check("no aside element when tocHtml is empty", !withoutToc.contains("toc-pane"), withoutToc);
        String withToc = PublishTemplates.page("T", "", "body", "<li>heading</li>", "index.html", "", "Jylos Vault");
        check("aside element present and carries the toc html verbatim when non-empty",
                withToc.contains("<aside class=\"toc-pane\"><li>heading</li></aside>"), withToc);

        System.out.println("\n-- css() is not run through .formatted(), so it must contain no stray %% --");
        String css = PublishTemplates.css();
        check("no leftover %% escape sequence in the emitted CSS", !css.contains("%%"), "found '%%' in css()");
        check("percentage widths are real single '%' values", css.contains("100%;"), css);

        System.out.println("\n-- graphJs() is likewise raw, no stray %% --");
        String js = PublishTemplates.graphJs();
        check("no leftover %% escape sequence in the emitted JS", !js.contains("%%"), "found '%%' in graphJs()");
        check("falls back to fetching graph.json when no inline data is embedded",
                js.contains("fetch('graph.json')"), js);
        check("prefers the inline window.__JYLOS_GRAPH__ over fetch when present",
                js.contains("window.__JYLOS_GRAPH__"), js);

        System.out.println("\n-- navJs() is likewise raw, no stray %% --");
        String navJs = PublishTemplates.navJs();
        check("no leftover %% escape sequence in the emitted JS", !navJs.contains("%%"), "found '%%' in navJs()");
        check("reads window.__JYLOS_NAV__ (written once by NavTree, loaded via <script src>, not fetched)",
                navJs.contains("window.__JYLOS_NAV__"), navJs);
        check("renders into the #jylos-sidebar placeholder page() emits",
                navJs.contains("getElementById('jylos-sidebar')"), navJs);

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
