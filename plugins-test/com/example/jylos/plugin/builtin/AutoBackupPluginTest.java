package com.example.jylos.plugin.builtin;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.FrontmatterHandler;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.sqlite.FolderDAOSQLite;
import com.example.jylos.data.dao.sqlite.NoteDAOSQLite;
import com.example.jylos.data.database.SQLiteDB;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.service.NoteService;

/**
 * A backup holds every note whole, as it is stored: in a folder vault, each file byte for
 * byte at its path (properties, long text, private notes still encrypted, attachments,
 * canvases) plus the vault's {@code .jylos} folder, without which private notes could not be
 * decrypted after a restore; in SQLite, each note's whole text with its properties, private
 * notes left to the database copy. Run by scripts/test-plugins.sh.
 */
public final class AutoBackupPluginTest {

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
        System.out.println("\n-- folder vault backup --");
        aFolderVaultIsBackedUpFileByFile();
        aSingleNoteOfAFolderVaultIsItsFile();
        System.out.println("\n-- SQLite backup --");
        sqliteNotesAreBackedUpWholeWithTheirProperties();
        System.out.println("\npassed: " + passed + "   failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void aFolderVaultIsBackedUpFileByFile() throws Exception {
        Path root = Files.createTempDirectory("backup-vault-");
        Path vault = Files.createDirectories(root.resolve("vault"));
        String longText = "---\ntags:\n  - libro\naliases:\n  - \"Otra forma\"\nrating: 4\n---\n"
                + "Principio. " + "palabra ".repeat(1500) + "\nFinal [[Destino]]\n";
        write(vault, "Larga.md", longText);
        write(vault, "Proyectos/Idea.md", "La idea de proyectos.\n");
        write(vault, "Lecturas/Idea.md", "La idea de lecturas.\n");
        write(vault, "Privada.md", "---\nprivate: true\n---\nJENC1:QUJDREVGR0hJSktMTU5PUA==\n");
        write(vault, "Lienzo.canvas", "{\"nodes\":[],\"edges\":[]}");
        Files.createDirectories(vault.resolve("img"));
        Files.write(vault.resolve("img/foto.png"), new byte[] { (byte) 0x89, 'P', 'N', 'G', 0, 1, 2 });
        write(vault, ".jylos/encryption.json", "{\"salt\":\"s\",\"verifier\":\"v\"}");
        write(vault, ".git/config", "[core]\n");

        EventBus.getInstance().clear();
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString());
        NoteService notes = new NoteService(dao, new FolderDAOFileSystem(vault.toString()));
        Path target = root.resolve("backup");
        AutoBackupPlugin.NoteBackup.Result result = AutoBackupPlugin.NoteBackup.backupAll(notes, target);

        Map<String, byte[]> original = files(vault);
        original.remove(".git/config");
        Map<String, byte[]> copied = files(target);
        check("every note, attachment and canvas is in the backup, at its path, and nothing else",
                original.keySet().equals(copied.keySet()), "vault " + original.keySet() + " backup " + copied.keySet());
        boolean identical = true;
        for (String path : original.keySet()) {
            identical &= copied.containsKey(path) && Arrays.equals(original.get(path), copied.get(path));
        }
        check("each file is copied byte for byte (whole text, properties, private note encrypted)", identical,
                new String(copied.getOrDefault("Larga.md", new byte[0]), StandardCharsets.UTF_8).length()
                        + " chars of Larga.md, " + longText.length() + " expected");
        check("the counts are what was written", result.written() == 6 && result.failed() == 0
                && result.skippedPrivate() == 0, result.toString());
        check("the copies keep their modified time",
                Files.getLastModifiedTime(vault.resolve("Larga.md")).equals(Files.getLastModifiedTime(target.resolve("Larga.md"))),
                Files.getLastModifiedTime(target.resolve("Larga.md")).toString());
    }

    private static void aSingleNoteOfAFolderVaultIsItsFile() throws Exception {
        Path root = Files.createTempDirectory("backup-note-");
        Path vault = Files.createDirectories(root.resolve("vault"));
        String text = "---\nestado: leyendo\n---\n" + "texto ".repeat(500) + "\nfin\n";
        write(vault, "Nota.md", text);
        EventBus.getInstance().clear();
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        Note listed = notes.getAllNotes().get(0);
        Path out = root.resolve("exportada.md");

        boolean written = AutoBackupPlugin.NoteBackup.exportNote(notes, listed, out);

        check("a single note is exported as its file", written
                && text.equals(Files.readString(out, StandardCharsets.UTF_8)),
                written ? Files.readString(out, StandardCharsets.UTF_8).length() + " chars" : "not written");
    }

    private static void sqliteNotesAreBackedUpWholeWithTheirProperties() throws Exception {
        Path root = Files.createTempDirectory("backup-sqlite-");
        resetDatabase();
        SQLiteDB.configure(root.resolve("db.sqlite").toString());
        SQLiteDB.getInstance().initDatabase();
        Connection connection = SQLiteDB.getInstance().openConnection();
        try {
            NoteService notes = new NoteService(new NoteDAOSQLite(connection), new FolderDAOSQLite(connection));
            Note larga = new Note("Larga", "Principio. " + "palabra ".repeat(1500) + "\nFinal\n");
            // SQLite keeps no free properties: its own columns are what a note has.
            larga.setStatus("leyendo");
            larga.setAuthor("Ana");
            notes.createNote(larga);
            notes.createNote(new Note("Idea", "Primera idea"));
            notes.createNote(new Note("Idea", "Segunda idea"));
            Note secreta = new Note("Secreta", "SECRETO");
            secreta.setPrivate(true);
            notes.createNote(secreta);
            Path target = root.resolve("backup");

            AutoBackupPlugin.NoteBackup.Result result = AutoBackupPlugin.NoteBackup.backupAll(notes, target);

            Map<String, byte[]> copied = files(target);
            check("each note once, same titles told apart, private notes left to the database copy",
                    copied.keySet().equals(java.util.Set.of("Larga.md", "Idea.md", "Idea (2).md")), copied.keySet().toString());
            Note full = notes.getAllNotes().stream().filter(n -> "Larga".equals(n.getTitle()))
                    .map(n -> notes.getNoteById(n.getId()).orElseThrow()).findFirst().orElseThrow();
            String saved = new String(copied.getOrDefault("Larga.md", new byte[0]), StandardCharsets.UTF_8);
            check("a note is written whole with its properties, as the vault writes it",
                    saved.equals(FrontmatterHandler.generate(full)) && saved.contains("status: leyendo")
                            && saved.contains("author: Ana")
                            && saved.contains("Final"), saved.length() + " chars; same as generate: "
                            + saved.equals(FrontmatterHandler.generate(full)) + "; head: "
                            + saved.substring(0, Math.min(120, saved.length())).replace("\n", "\\n"));
            check("both notes titled Idea are there", copied.values().stream()
                    .map(b -> new String(b, StandardCharsets.UTF_8)).filter(s -> s.contains("idea")).count() == 2, "");
            check("no private text in the backup", copied.values().stream()
                    .noneMatch(b -> new String(b, StandardCharsets.UTF_8).contains("SECRETO")), "");
            check("the counts are what was written", result.written() == 3 && result.failed() == 0
                    && result.skippedPrivate() == 1, result.toString());
        } finally {
            SQLiteDB.getInstance().closeConnection(connection);
            resetDatabase();
        }
    }

    private static void write(Path vault, String path, String text) throws Exception {
        Path file = vault.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    /** Every file under {@code dir} by its path relative to it, with '/'. */
    private static Map<String, byte[]> files(Path dir) throws Exception {
        Map<String, byte[]> files = new TreeMap<>();
        if (!Files.isDirectory(dir)) {
            return files;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                files.put(dir.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
            }
        }
        return files;
    }

    private static void resetDatabase() throws Exception {
        Field instance = SQLiteDB.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
    }
}
