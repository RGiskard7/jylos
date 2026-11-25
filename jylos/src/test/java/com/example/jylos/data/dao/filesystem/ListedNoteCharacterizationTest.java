package com.example.jylos.data.dao.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.event.EventBus;

/**
 * What the vault lists for each note once it has loaded: the properties, tags, preview and
 * dates of the start of the file, as {@link FrontmatterHandler#parseLightweight} reads them,
 * and the links of the whole file, as {@link FrontmatterHandler#linksOf} finds them (a long
 * note's too). Whatever the listing reads, from the file or from a cache, it must give this.
 */
class ListedNoteCharacterizationTest {

    static final String FILLER = "palabra ".repeat(4000); // ~32 KB, past the 16 KB head

    /** One note of each kind the listing has to read right. */
    static Map<String, String> fixtures() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("Propiedades.md", "---\naliases:\n  - Props\n  - \"Otra forma\"\nup: \"[[Índice]]\"\n"
                + "rating: 4\nleido: true\nfecha: 2026-11-21\ncita: \"entre comillas\"\nstatus: leyendo\n"
                + "author:\n  - Ana\ntags:\n  - libro\n  - lectura/2026\n---\n"
                + "Texto con #enlinea y [[Destino|alias]] y ![[foto.png]] y [md](Otra.md).\n\n"
                + "```bash\n# no es etiqueta #nada [[NoEsEnlace]]\n```\n");
        files.put("Sin propiedades.md", "# Título\n\nUna #etiqueta y un [[Enlace]].\n");
        files.put("Privada.md", "---\nprivate: true\nup: \"[[Índice]]\"\n---\nJENC1:QUJDREVGR0hJSktMTU5PUA==\n");
        files.put("Largo.md", "---\ncategoria: larga\n---\nInicio [[Principio]]\n" + FILLER
                + "\n[[Tarde]] y ![[final.pdf]]\n");
        files.put("Tablero.md", "---\nkanban-plugin: board\n---\n%% jylos-kanban %%\n\n## Hacer\n\n- [ ] [[Tarea]]\n");
        files.put("Tarea.md", "---\nis_todo: true\ntodo_due: 2026-12-01\n---\nHacer algo\n");
        files.put("Windows.md", "---\r\ntitulo: crlf\r\ntags:\r\n  - win\r\n---\r\nLínea [[Uno]]\r\nOtra #dos\r\n");
        files.put("Vacía.md", "");
        files.put("Acentos.md", "ñandú áéíóú ".repeat(120) + "[[Lejos]]\n");
        files.put("Carpeta/Anidada.md", "---\ncreated: 2020-01-02T03:04:05Z\n---\nEn una carpeta [[../Arriba]]\n");
        return files;
    }

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void eachListedNoteIsWhatTheStartOfItsFileSaysWithTheLinksOfTheWholeFile(@TempDir Path root) throws Exception {
        Path vault = write(root.resolve("vault"), fixtures());

        NoteDAOFileSystem dao = loaded(vault, root.resolve("cache/vault.json"));

        for (String id : fixtures().keySet()) {
            assertEquals(expected(vault, id), shape(listed(dao, id)), id);
        }
    }

    // ── helpers shared with the cache tests ─────────────────────────────────────────

    static Path write(Path vault, Map<String, String> files) throws Exception {
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = vault.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
        }
        return vault;
    }

    /** A vault opened as the app opens it, waited for until its notes and links are in. */
    static NoteDAOFileSystem loaded(Path vault, Path cacheFile) throws Exception {
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString(), true, cacheFile);
        assertTrue(dao.awaitContentLoaded(20_000), "the vault loads");
        return dao;
    }

    static Note listed(NoteDAOFileSystem dao, String id) {
        return dao.fetchAllNotes().stream().filter(n -> id.equals(n.getId())).findFirst()
                .orElseThrow(() -> new AssertionError("not listed: " + id));
    }

    /** What a listed note must be, worked out from its whole file. */
    static Map<String, Object> expected(Path vault, String id) throws Exception {
        Path path = vault.resolve(id);
        String text = Files.readString(path, StandardCharsets.UTF_8);
        Note note = FrontmatterHandler.parseLightweight(text, 900);
        note.setId(id);
        String name = path.getFileName().toString();
        note.setTitle(name.substring(0, name.length() - 3));
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        if (note.getCreatedDate() == null || note.getCreatedDate().isBlank()) {
            note.setCreatedDate(DateTimeFormatter.ISO_INSTANT.format(attrs.creationTime().toInstant()));
        }
        if (note.getModifiedDate() == null || note.getModifiedDate().isBlank()) {
            note.setModifiedDate(DateTimeFormatter.ISO_INSTANT.format(attrs.lastModifiedTime().toInstant()));
        }
        note.setLinkTargets(FrontmatterHandler.linksOf(text));
        return shape(note);
    }

    /** Everything about a listed note that the app shows or works with. */
    static Map<String, Object> shape(Note note) {
        Map<String, Object> shape = new LinkedHashMap<>();
        shape.put("class", note.getClass().getSimpleName());
        shape.put("title", note.getTitle());
        shape.put("created", note.getCreatedDate());
        shape.put("modified", note.getModifiedDate());
        List<String> tags = new ArrayList<>();
        for (Tag tag : note.getTags()) {
            tags.add(tag.getTitle());
        }
        tags.sort(null);
        shape.put("tags", tags);
        shape.put("custom", note.getCustomProperties());
        shape.put("structured", String.valueOf(note.getStructuredFrontmatterProperties()));
        shape.put("displayable", List.copyOf(note.getDisplayableFrontmatterPropertyKeys()));
        shape.put("links", note.getLinkTargets());
        shape.put("preview", note.getContent());
        shape.put("status", note.getStatus());
        shape.put("author", note.getAuthor());
        shape.put("private", note.isPrivate());
        shape.put("favorite", note.isFavorite());
        shape.put("pinned", note.isPinned());
        return shape;
    }
}
