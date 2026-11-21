package com.example.jylos.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.event.EventBus;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;

/**
 * The graph of a loaded vault comes from what the vault listed of each note (its links, file
 * links and tags, from the whole file or its cache), not from reading every note again: with
 * iCloud, that read downloads the whole vault.
 */
class GraphFromMetadataCacheTest {

    private static final String FILLER = "palabra ".repeat(4000); // ~32 KB

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void theGraphOfAnUnchangedVaultReadsNoNote(@TempDir Path root) throws Exception {
        Path vault = root.resolve("vault");
        Map<String, String> files = new LinkedHashMap<>();
        files.put("A.md", "---\nup: \"[[B]]\"\n---\nVer [[C]] y ![[foto.png]] #tema\n");
        files.put("B.md", "Sin etiquetas, enlaza a [[A]]\n");
        files.put("C.md", "Inicio\n" + FILLER + "\n[[Lejos]] ![[doc.pdf]] #alfinal\n");
        files.put("Lejos.md", "");
        for (Map.Entry<String, String> file : files.entrySet()) {
            Files.createDirectories(vault);
            Files.writeString(vault.resolve(file.getKey()), file.getValue(), StandardCharsets.UTF_8);
        }
        Files.write(vault.resolve("foto.png"), new byte[] { 1, 2, 3 });
        Files.write(vault.resolve("doc.pdf"), new byte[] { 4, 5, 6 });
        Path cache = root.resolve("cache/vault.json");

        Set<String> cold = edges(vault, cache);
        for (String id : files.keySet()) {
            sameSizeOtherBytes(vault.resolve(id));
        }
        Set<String> warm = edges(vault, cache);

        assertTrue(cold.containsAll(Set.of("A.md>B.md", "A.md>C.md", "A.md>foto.png", "B.md>A.md",
                "C.md>Lejos.md", "C.md>doc.pdf", "A.md>#tema", "C.md>#alfinal")), cold.toString());
        assertEquals(cold, warm);
    }

    /** Insights' untagged notes and search's tag operators ask for a note's tags: an untagged one is not read. */
    @Test
    void anUntaggedNoteHasNoTagsWithoutReadingIt(@TempDir Path root) throws Exception {
        Path vault = root.resolve("vault");
        Files.createDirectories(vault);
        Files.writeString(vault.resolve("Suelta.md"), "Una nota sin etiquetas.\n", StandardCharsets.UTF_8);
        Path cache = root.resolve("cache/vault.json");
        new NoteDAOFileSystem(vault.toString(), true, cache).awaitContentLoaded(20_000);
        EventBus.getInstance().clear();
        sameSizeOtherBytes(vault.resolve("Suelta.md"));

        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString(), true, cache);
        assertTrue(dao.awaitContentLoaded(20_000));
        TagService tags = new TagService(new TagDAOFileSystem(dao), dao);
        Note listed = dao.fetchAllNotes().get(0);

        assertEquals(List.of(), tags.getTagsForNote(listed).stream().map(Tag::getTitle).toList());
    }

    private static Set<String> edges(Path vault, Path cache) throws Exception {
        EventBus.getInstance().clear();
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString(), true, cache);
        assertTrue(dao.awaitContentLoaded(20_000));
        GraphBuilder graph = new GraphBuilder(new NoteService(dao, new FolderDAOFileSystem(vault.toString())),
                new TagService(new TagDAOFileSystem(dao), dao));
        GraphData data = graph.buildGlobalGraph(new GraphBuilder.Options(true, true, true, true));
        Set<String> edges = new TreeSet<>();
        for (GraphEdge edge : data.edges()) {
            edges.add(edge.source() + ">" + edge.target().replaceFirst("^tag:", "#"));
        }
        return edges;
    }

    /** Same size and modified time, other bytes: a read of the file would find the tag {@code #z} in them. */
    private static void sameSizeOtherBytes(Path file) throws Exception {
        FileTime modified = Files.getLastModifiedTime(file);
        byte[] bytes = new byte[(int) Files.size(file)];
        byte[] pattern = "#z ".getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = pattern[i % pattern.length];
        }
        Files.write(file, bytes);
        Files.setLastModifiedTime(file, modified);
    }
}
