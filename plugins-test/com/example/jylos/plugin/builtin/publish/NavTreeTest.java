package com.example.jylos.plugin.builtin.publish;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.jylos.data.models.Note;

/**
 * Behavioural checks for {@link NavTree}, run by scripts/test-plugins.sh.
 */
public final class NavTreeTest {

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
        Note root = note("root", "Root Note");
        Note dune = note("dune", "Dune");
        Note foundation = note("foundation", "Foundation");
        Note sibling = note("sibling", "Sibling In Same Folder");
        Note quotes = note("quotes", "A \"Quoted\" \\Title\\ With\nNewline");
        Note scriptBreaker = note("scriptbreaker", "Weird </script> Title");

        Map<String, List<String>> folderPathByNoteId = new LinkedHashMap<>();
        folderPathByNoteId.put(dune.getId(), List.of("books"));
        folderPathByNoteId.put(foundation.getId(), List.of("books", "scifi"));
        folderPathByNoteId.put(sibling.getId(), List.of("books"));
        // root, quotes, scriptBreaker: no entry -> root-level notes

        List<Note> exportable = List.of(root, dune, sibling, foundation, quotes, scriptBreaker);

        NoteSlugs slugs = new NoteSlugs();
        for (Note n : exportable) {
            slugs.register(n.getId(), folderPathByNoteId.getOrDefault(n.getId(), List.of()), n.getTitle());
        }

        String script = NavTree.buildScript(exportable, folderPathByNoteId, slugs);

        System.out.println("\n-- overall shape --");
        check("starts with the window assignment", script.startsWith("window.__JYLOS_NAV__ = {"), script);
        check("ends with a closing semicolon", script.trim().endsWith("};"), script);

        System.out.println("\n-- root-level notes --");
        check("a note with no folder path appears in the top-level notes array",
                script.contains("\"title\":\"Root Note\",\"href\":\"" + slugs.pathFor("root") + "\""), script);

        System.out.println("\n-- nested folders --");
        check("a top-level folder entry named 'books' exists",
                script.contains("\"name\":\"books\",\"path\":\"books\""), script);
        check("the nested folder's path is the joined 'books/scifi', not just 'scifi'",
                script.contains("\"name\":\"scifi\",\"path\":\"books/scifi\""), script);
        check("Foundation (in books/scifi) is nested under the scifi folder, not the top level",
                script.contains("\"path\":\"books/scifi\",\"notes\":[{\"title\":\"Foundation\"")
                        || script.contains("\"path\":\"books/scifi\",\"notes\":[{\"title\":\"Foundation\","),
                script);

        System.out.println("\n-- two notes sharing a folder merge into ONE folder node, not two --");
        int booksFolderOccurrences = countOccurrences(script, "\"path\":\"books\"");
        check("the 'books' folder appears exactly once even though two notes live in it",
                booksFolderOccurrences == 1, "occurrences=" + booksFolderOccurrences);
        check("both notes sharing 'books' end up in the same folder's notes list",
                script.contains("Dune") && script.contains("Sibling In Same Folder")
                        && script.indexOf("\"path\":\"books\"") < script.indexOf("Dune")
                        && script.indexOf("\"path\":\"books\"") < script.indexOf("Sibling In Same Folder"),
                script);

        System.out.println("\n-- JSON string escaping (this is a plain JS assignment, not parsed as JSON --");
        check("embedded double quotes are escaped", script.contains("\\\"Quoted\\\""), script);
        check("embedded backslashes are escaped", script.contains("\\\\Title\\\\"), script);
        check("embedded newline is escaped, not a raw line break inside the string",
                script.contains("With\\nNewline"), script);
        check("a title containing '</script>' cannot break out of the embedding <script> tag",
                !script.contains("</script> Title") && script.contains("\\u003c/script> Title"),
                script);

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static Note note(String id, String title) {
        Note n = new Note(id, "");
        n.setId(id);
        n.setTitle(title);
        return n;
    }
}
