package com.example.jylos.data.dao.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The cache file: an entry is valid only for its file's modified time and size, and the file is disposable. */
class NoteMetadataCacheFileTest {

    private static final NoteMetadataCache.Metadata META =
            NoteMetadataCache.Metadata.of("---\nup: \"[[A]]\"\n---\nTexto #t [[B]] ![[c.png]]\n");

    @Test
    void anEntryIsWhatWasReadAndOnlyForTheSameModifiedTimeAndSize(@TempDir Path dir) {
        NoteMetadataCache cache = NoteMetadataCache.load(dir.resolve("m.json"));
        cache.put("a.md", 10L, 20L, META);

        assertEquals(Optional.of(META), cache.get("a.md", 10L, 20L));
        assertEquals(List.of("A", "B"), META.links());
        assertEquals(List.of("c.png"), META.fileLinks());
        assertEquals(List.of("t"), META.inlineTags());
        assertEquals("up: \"[[A]]\"\n", META.yaml());
        assertEquals(Optional.empty(), cache.get("a.md", 11L, 20L));
        assertEquals(Optional.empty(), cache.get("a.md", 10L, 21L));
        assertEquals(Optional.empty(), cache.get("b.md", 10L, 20L));
    }

    @Test
    void savedEntriesSurviveAReloadAndRetainOnlyDropsTheRest(@TempDir Path dir) {
        Path file = dir.resolve("sub/m.json");
        NoteMetadataCache cache = NoteMetadataCache.load(file);
        cache.put("a.md", 1L, 2L, META);
        cache.put("b.md", 3L, 4L, META);
        cache.retainOnly(Set.of("a.md"));
        assertTrue(cache.save());
        assertFalse(cache.save(), "nothing changed since");

        NoteMetadataCache reloaded = NoteMetadataCache.load(file);
        assertEquals(Optional.of(META), reloaded.get("a.md", 1L, 2L));
        assertEquals(Optional.empty(), reloaded.get("b.md", 3L, 4L));
    }

    @Test
    void aMissingCorruptOrOlderFileIsAnEmptyCache(@TempDir Path dir) throws Exception {
        assertEquals(Optional.empty(), NoteMetadataCache.load(dir.resolve("nada.json")).get("a.md", 1L, 2L));
        Path broken = dir.resolve("roto.json");
        Files.writeString(broken, "{ no json", StandardCharsets.UTF_8);
        NoteMetadataCache cache = NoteMetadataCache.load(broken);
        assertEquals(Optional.empty(), cache.get("a.md", 1L, 2L));
        cache.put("a.md", 1L, 2L, META);
        assertTrue(cache.save());
        assertEquals(Optional.of(META), NoteMetadataCache.load(broken).get("a.md", 1L, 2L), "written again");

        Path older = dir.resolve("vieja.json");
        Files.writeString(older, "{\"a.md\":{\"modified\":1,\"size\":2,\"links\":[\"X\"],\"format\":3}}",
                StandardCharsets.UTF_8);
        assertEquals(Optional.empty(), NoteMetadataCache.load(older).get("a.md", 1L, 2L));
    }

    @Test
    void aDerivedValueLivesAsLongAsItsEntry(@TempDir Path dir) {
        Path file = dir.resolve("m.json");
        NoteMetadataCache cache = NoteMetadataCache.load(file);
        assertFalse(cache.putDerived("a.md", 1L, 2L, "k@1", "v"), "no entry to keep it with");
        cache.put("a.md", 1L, 2L, META);

        assertTrue(cache.putDerived("a.md", 1L, 2L, "k@1", "v"));
        assertFalse(cache.putDerived("a.md", 9L, 2L, "k@1", "otro"), "another version of the file");
        cache.save();

        NoteMetadataCache reloaded = NoteMetadataCache.load(file);
        assertEquals(Optional.of("v"), reloaded.derived("a.md", 1L, 2L, "k@1"));
        assertEquals(Optional.empty(), reloaded.derived("a.md", 1L, 2L, "k@2"));
        assertEquals(Optional.empty(), reloaded.derived("a.md", 1L, 3L, "k@1"));
        reloaded.put("a.md", 1L, 2L, META);
        assertEquals(Optional.empty(), reloaded.derived("a.md", 1L, 2L, "k@1"), "read again: worked out again");
    }

    @Test
    void anInMemoryCacheWorksButSavesNothing() {
        NoteMetadataCache cache = NoteMetadataCache.inMemory();
        cache.put("a.md", 1L, 2L, META);
        assertEquals(Optional.of(META), cache.get("a.md", 1L, 2L));
        assertFalse(cache.save());
    }
}
