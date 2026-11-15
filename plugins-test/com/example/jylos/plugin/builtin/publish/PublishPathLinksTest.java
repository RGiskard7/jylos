package com.example.jylos.plugin.builtin.publish;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;

/**
 * A link in a published page goes to the note its path names, as in the app: with two notes
 * called "Reunión" in different folders, {@code [[Reunión]]} goes to the one beside the note
 * and {@code [[Personal/Reunión]]} to that one. Run by scripts/test-plugins.sh.
 */
public final class PublishPathLinksTest {

    private static int failed;

    private static void check(String name, boolean condition, String detail) {
        System.out.println((condition ? "  PASS  " : "  FAIL  ") + name + (condition ? "" : "  -> " + detail));
        if (!condition) {
            failed++;
        }
    }

    public static void main(String[] args) throws Exception {
        Path vault = Files.createTempDirectory("jylos-publish-paths");
        write(vault, "Proyectos/Reunión.md", "Acta de proyectos\n");
        write(vault, "Personal/Reunión.md", "Cosas personales\n");
        write(vault, "Proyectos/Acta.md", "Ver [[Reunión]] y [[Personal/Reunión]].\n");
        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        FolderDAOFileSystem folderDao = new FolderDAOFileSystem(vault.toString());
        NoteService notes = new NoteService(noteDao, folderDao);
        VaultExporter exporter = new VaultExporter(notes, new FolderService(folderDao, noteDao),
                new TagService(new TagDAOFileSystem(noteDao), noteDao));

        Path out = Files.createTempDirectory("jylos-publish-paths-out");
        exporter.export(out, (done, total) -> { });

        Path acta = Files.walk(out).filter(p -> p.getFileName().toString().equals("Acta.html")).findFirst().orElseThrow();
        String html = Files.readString(acta, StandardCharsets.UTF_8);
        check("[[Reunión]] goes to the Reunión beside the note", html.contains("href=\"Reuni"), html);
        check("[[Personal/Reunión]] goes to Personal's", html.contains("href=\"../Personal/Reuni"), html);
        check("neither is a dead link", !html.contains("href=\"#\""), html);

        System.out.println("\n" + (failed == 0 ? "all passed" : failed + " failed"));
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void write(Path vault, String path, String content) throws Exception {
        Path file = vault.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
