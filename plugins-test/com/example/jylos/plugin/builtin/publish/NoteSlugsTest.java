package com.example.jylos.plugin.builtin.publish;

import java.util.List;

/**
 * Behavioural checks for {@link NoteSlugs}, run by scripts/test-plugins.sh.
 *
 * <p>Lives outside the Maven module because plugin sources are compiled against the app
 * as an external JAR, not as part of it — the same way a third-party plugin would be. A
 * plain main() keeps the plugin build free of a test-framework dependency.</p>
 */
public final class NoteSlugsTest {

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
        System.out.println("\n-- root-level note --");
        NoteSlugs slugs = new NoteSlugs();
        slugs.register("id-1", List.of(), "Hello World");
        check("root note gets notes/<slug>.html", "notes/Hello-World.html".equals(slugs.pathFor("id-1")),
                String.valueOf(slugs.pathFor("id-1")));

        System.out.println("\n-- nested folder mirrors path --");
        NoteSlugs nested = new NoteSlugs();
        nested.register("id-2", List.of("Books", "Sci-Fi"), "Dune");
        check("nested note mirrors folder path",
                "notes/Books/Sci-Fi/Dune.html".equals(nested.pathFor("id-2")),
                String.valueOf(nested.pathFor("id-2")));

        System.out.println("\n-- unsafe characters sanitized --");
        NoteSlugs unsafe = new NoteSlugs();
        unsafe.register("id-3", List.of(), "What is \"real\"? A/B test <notes>");
        String path = unsafe.pathFor("id-3");
        check("no slash/quote/question-mark/angle-bracket survives",
                path != null && !path.substring("notes/".length(), path.length() - ".html".length())
                        .matches(".*[\\\\/:*?\"<>|].*"),
                String.valueOf(path));
        check("still ends in .html", path != null && path.endsWith(".html"), String.valueOf(path));

        System.out.println("\n-- blank title falls back, never collapses the path --");
        NoteSlugs blank = new NoteSlugs();
        blank.register("id-4", List.of(), "   ");
        check("blank title becomes 'untitled', not an empty segment",
                "notes/untitled.html".equals(blank.pathFor("id-4")),
                String.valueOf(blank.pathFor("id-4")));

        System.out.println("\n-- collisions get a numeric suffix, never silently overwrite --");
        NoteSlugs collide = new NoteSlugs();
        collide.register("id-a", List.of(), "Duplicate");
        collide.register("id-b", List.of(), "Duplicate");
        collide.register("id-c", List.of(), "Duplicate");
        String a = collide.pathFor("id-a");
        String b = collide.pathFor("id-b");
        String c = collide.pathFor("id-c");
        check("first note keeps the plain slug", "notes/Duplicate.html".equals(a), String.valueOf(a));
        check("second colliding note gets -2", "notes/Duplicate-2.html".equals(b), String.valueOf(b));
        check("third colliding note gets -3", "notes/Duplicate-3.html".equals(c), String.valueOf(c));
        check("all three paths are distinct", a != null && !a.equals(b) && !b.equals(c) && !a.equals(c),
                a + " / " + b + " / " + c);

        System.out.println("\n-- unregistered id --");
        check("pathFor returns null for an id never registered",
                new NoteSlugs().pathFor("nope") == null, "expected null");

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
