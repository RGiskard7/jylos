package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The web engine (JavaFX's WebView, and its JavaScript bridge) is used only in {@code ui/web},
 * behind {@code HtmlView}: everything else shows pages through that interface, so a change of
 * engine (see {@code docs/WEBVIEW.md}) touches one package. A guard on the source text: it
 * watches the boundary, it is not coverage of behaviour.
 */
class WebEngineConfinementGuardTest {

    private static final Path MAIN = Path.of("src/main/java/com/example/jylos");
    private static final Path WEB_PACKAGE = MAIN.resolve("ui/web");
    private static final Path VIEWS = Path.of("src/main/resources/com/example/jylos/ui/view");

    @Test
    void onlyTheWebPackageUsesTheEngine() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (file.startsWith(WEB_PACKAGE)) {
                    continue;
                }
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).strip();
                    if (line.startsWith("*") || line.startsWith("/*") || line.startsWith("//")) {
                        continue; // documentation may name the engine
                    }
                    if (line.contains("javafx.scene.web") || line.contains("netscape.javascript")) {
                        offenders.add(MAIN.relativize(file) + ":" + (i + 1));
                    }
                }
            }
        }
        try (Stream<Path> files = Files.list(VIEWS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".fxml")).toList()) {
                if (Files.readString(file, StandardCharsets.UTF_8).contains("javafx.scene.web")) {
                    offenders.add(file.getFileName().toString());
                }
            }
        }
        assertEquals(List.of(), offenders, "use HtmlView (ui/web) instead of the engine");
    }
}
