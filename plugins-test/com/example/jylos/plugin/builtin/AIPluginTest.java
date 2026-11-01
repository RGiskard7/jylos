package com.example.jylos.plugin.builtin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.NoteService;

/**
 * Behavioural checks for what {@link AIPlugin} is willing to send to a remote AI provider,
 * run by scripts/test-plugins.sh.
 *
 * <p>Two rules: a private note never leaves the machine, and a normal note is sent in
 * full — not the truncated head the notes list keeps for a filesystem vault.</p>
 */
public final class AIPluginTest {

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
        Path vault = Files.createTempDirectory("ai-plugin-test-vault-");
        String longBody = "word ".repeat(1_000) + "\nTAILMARKER at the very end\n";
        Files.writeString(vault.resolve("Long.md"), longBody, StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("Secret.md"),
                "---\nprivate: true\n---\nsecret body that must stay local\n", StandardCharsets.UTF_8);

        NoteDAOFileSystem noteDAO = new NoteDAOFileSystem(vault.toString());
        NoteService noteService = new NoteService(noteDAO, new FolderDAOFileSystem(vault.toString()));

        Note listedLong = find(noteService, "Long");
        Note listedSecret = find(noteService, "Secret");

        System.out.println("\n-- private notes stay local --");
        Optional<String> secret = AIPlugin.contentToSend(listedSecret, noteService);
        check("a private note is never sent", secret.isEmpty(), String.valueOf(secret));

        System.out.println("\n-- normal notes are sent in full --");
        check("the notes list only holds the head of a long vault note (why the full read matters)",
                !listedLong.getContent().contains("TAILMARKER"), "list content length " + listedLong.getContent().length());
        Optional<String> sent = AIPlugin.contentToSend(listedLong, noteService);
        check("a normal note is sent", sent.isPresent(), String.valueOf(sent));
        check("the full body is sent, including text past the list preview",
                sent.orElse("").contains("TAILMARKER"), "sent length " + sent.orElse("").length());

        System.out.println("\n========================================");
        System.out.println("passed: " + passed + "   failed: " + failed);
        System.out.println("========================================");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static Note find(NoteService noteService, String title) {
        return noteService.getAllNotes().stream()
                .filter(n -> title.equals(n.getTitle()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("test note not found: " + title));
    }
}
