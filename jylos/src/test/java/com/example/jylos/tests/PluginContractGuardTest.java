package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.example.jylos.plugin.PluginApiSurface;

class PluginContractGuardTest {

    private static final Path NOTE_EVENTS = Path.of("src/main/java/com/example/jylos/event/events/NoteEvents.java");
    private static final Path MAIN_CONTROLLER = Path.of("src/main/java/com/example/jylos/ui/controller/MainController.java");
    private static final Path PLUGINS_SOURCE_DIR = Path.of("..", "plugins-source");

    /** A plugin's OWN classes live under this prefix — referencing a sibling class in your
     *  own plugin (or another built-in plugin's package) is not reaching into the app at
     *  all, so it is not what this guard is checking and must not be flagged. */
    private static final String PLUGINS_OWN_PACKAGE_PREFIX = "com.example.jylos.plugin.builtin.";

    /** Matches both a plain {@code import com.example.jylos.foo.Bar;} and a fully-qualified
     *  inline reference like {@code com.example.jylos.ui.UiDialogs.apply(...)} — the coupling
     *  this guard exists to catch showed up as both in this codebase's own history (the
     *  latter specifically to avoid an import line while still reaching into the app). */
    private static final Pattern JYLOS_REFERENCE =
            Pattern.compile("com\\.example\\.jylos(?:\\.[A-Za-z_][A-Za-z0-9_]*)+");

    @Test
    void noteSelectionEventShouldRemainAvailableAsPluginExtensibilityContract() throws IOException {
        String noteEvents = Files.readString(NOTE_EVENTS, StandardCharsets.UTF_8);
        String mainController = Files.readString(MAIN_CONTROLLER, StandardCharsets.UTF_8);

        assertTrue(noteEvents.contains("class NoteSelectedEvent"),
                "NoteSelectedEvent should remain available for plugin fan-out/extensibility.");
        assertTrue(mainController.contains("publishNoteSelected(activeNote);"),
                "MainController should publish note selection fan-out for plugins after opening a note.");
    }

    /**
     * Every {@code com.example.jylos.*} class a built-in plugin references (by import or by
     * fully-qualified name) must fall inside {@link PluginApiSurface} — the same list
     * {@link com.example.jylos.plugin.PluginClassLoader} enforces at real class-load time.
     *
     * <p>A text scan like this one proves only what a plugin's <em>source</em> reaches for,
     * not that loading it actually fails when it does not — that behavioural proof is
     * {@code PluginClassLoaderBoundaryTest}, which loads a real compiled JAR through the
     * real {@link com.example.jylos.plugin.PluginLoader}. What this test buys instead is
     * speed and precision: it runs as a plain {@code mvn test} (a plugin need not even
     * compile for this to catch it), and points straight at the offending file and class
     * name instead of an exception thrown three layers down from a running plugin.</p>
     */
    @Test
    void builtinPluginSourcesShouldOnlyReferenceTheDocumentedPluginApiSurface() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(PLUGINS_SOURCE_DIR)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = JYLOS_REFERENCE.matcher(source);
                while (matcher.find()) {
                    String reference = matcher.group();
                    if (reference.startsWith(PLUGINS_OWN_PACKAGE_PREFIX)) {
                        continue; // a plugin referencing its own (or a sibling plugin's) class
                    }
                    if (!isWithinDocumentedSurface(reference)) {
                        violations.add(reference + "  <-  " + file);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "Built-in plugin source referenced a com.example.jylos class outside PluginApiSurface — "
                        + "either the reference is a mistake, or PluginApiSurface needs updating (in one place, "
                        + "for both this check and the real PluginClassLoader):\n"
                        + String.join("\n", violations));
    }

    /**
     * {@link PluginApiSurface#isAllowed} expects one exact class name; a source scan finds
     * an arbitrarily long dotted reference ({@code com.example.jylos.ui.UiDialogs.apply}
     * for a static call, {@code com.example.jylos.graph.GraphBuilder.build} likewise) that
     * may have trailing member/method segments past the actual class name. Checking every
     * non-empty prefix of the match — shortest first — against {@code isAllowed} finds the
     * class name regardless of how many segments follow it, without needing this test to
     * know Java capitalization conventions or maintain its own copy of the real class list.
     */
    private static boolean isWithinDocumentedSurface(String reference) {
        String[] segments = reference.split("\\.");
        StringBuilder candidate = new StringBuilder(segments[0]);
        for (int i = 1; i < segments.length; i++) {
            candidate.append('.').append(segments[i]);
            if (PluginApiSurface.isAllowed(candidate.toString())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void pluginSourcesShouldNotDependOnRemovedInternalUiEvents() throws IOException {
        try (Stream<Path> files = Files.walk(PLUGINS_SOURCE_DIR)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                assertFalse(source.contains("NoteOpenRequestEvent"),
                        "Plugin sources should not depend on removed internal note-open request events: " + file);
                assertFalse(source.contains("NoteModifiedEvent"),
                        "Plugin sources should not depend on removed internal note-modified events: " + file);
                assertFalse(source.contains("UIEvents"),
                        "Plugin sources should not depend on removed UIEvents wrappers: " + file);
            }
        }
    }
}
