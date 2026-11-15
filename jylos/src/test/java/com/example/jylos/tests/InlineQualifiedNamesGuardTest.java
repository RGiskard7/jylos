package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Code names a class through an import, not by its fully qualified name written inline
 * ({@code com.example.jylos.ui.UiDialogs.show(...)}, {@code java.util.List}): the inline
 * form is noise in the code. The only exceptions are names that clash with another class
 * the same file uses, listed below. A guard on the source text: it watches a convention,
 * it is not coverage of behaviour.
 */
class InlineQualifiedNamesGuardTest {

    private static final Path MAIN = Path.of("src/main/java/com/example/jylos");

    private static final Pattern QUALIFIED = Pattern.compile(
            "(?<![\\w.\"])((?:com\\.example\\.jylos|javax|javafx|java|org)\\.(?:[a-z_]\\w*\\.)+[A-Z]\\w*)");
    private static final Pattern STRING = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");

    /** File and name, for names that clash with another class used in the same file. */
    private static final Set<String> CLASHES = Set.of(
            "MarkdownMini.java javafx.scene.Node", // commonmark's Node is imported
            "MarkdownMini.java javafx.scene.text.Text", // commonmark's Text is imported
            "NoteExporter.java org.w3c.dom.Document", // jsoup's Document is imported
            "FrontmatterHandler.java org.yaml.snakeyaml.nodes.Tag"); // Jylos's Tag is imported

    @Test
    void classesAreImportedNotNamedInline() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                boolean inComment = false;
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).strip();
                    if (inComment) {
                        inComment = !line.contains("*/");
                        continue;
                    }
                    if (line.startsWith("/*")) {
                        inComment = !line.contains("*/");
                        continue;
                    }
                    if (line.startsWith("import ") || line.startsWith("package ") || line.startsWith("//")
                            || line.startsWith("*")) {
                        continue;
                    }
                    String code = STRING.matcher(line).replaceAll("\"\"");
                    int comment = code.indexOf("//");
                    if (comment >= 0) {
                        code = code.substring(0, comment);
                    }
                    Matcher name = QUALIFIED.matcher(code);
                    while (name.find()) {
                        if (!CLASHES.contains(file.getFileName() + " " + name.group(1))) {
                            offenders.add(MAIN.relativize(file) + ":" + (i + 1) + " " + name.group(1));
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), offenders, "import these classes instead of naming them inline");
    }
}
