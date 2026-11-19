package com.example.jylos.data.dao.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The links of long notes, kept between sessions and valid only while the file is unchanged. */
class LinkCacheTest {

    @Test
    void anEntryIsOnlyValidForTheSameModifiedTimeAndSize(@TempDir Path dir) {
        LinkCache cache = LinkCache.load(dir.resolve("links.json"));
        cache.put("A/Largo.md", 1000L, 20_000L, List.of("Uno", "Carpeta/Dos"));

        assertEquals(Optional.of(List.of("Uno", "Carpeta/Dos")), cache.get("A/Largo.md", 1000L, 20_000L));
        assertEquals(Optional.empty(), cache.get("A/Largo.md", 1001L, 20_000L), "modified time changed");
        assertEquals(Optional.empty(), cache.get("A/Largo.md", 1000L, 20_001L), "size changed");
        assertEquals(Optional.empty(), cache.get("Otro.md", 1000L, 20_000L), "never indexed");
    }

    @Test
    void savedEntriesSurviveAReloadAndRetainOnlyDropsTheRest(@TempDir Path dir) {
        Path file = dir.resolve("sub/links.json");
        LinkCache cache = LinkCache.load(file);
        cache.put("a.md", 1L, 2L, List.of("X"));
        cache.put("b.md", 3L, 4L, List.of());
        cache.retainOnly(Set.of("a.md"));
        assertTrue(cache.save());

        LinkCache reloaded = LinkCache.load(file);
        assertEquals(Optional.of(List.of("X")), reloaded.get("a.md", 1L, 2L));
        assertEquals(Optional.empty(), reloaded.get("b.md", 3L, 4L));
    }

    @Test
    void aMissingOrCorruptFileIsAnEmptyCache(@TempDir Path dir) throws Exception {
        assertEquals(Optional.empty(), LinkCache.load(dir.resolve("nada.json")).get("a.md", 1L, 2L));
        Path broken = dir.resolve("roto.json");
        Files.writeString(broken, "{ esto no es json");
        LinkCache cache = LinkCache.load(broken);
        assertEquals(Optional.empty(), cache.get("a.md", 1L, 2L));
        cache.put("a.md", 1L, 2L, List.of("X"));
        assertTrue(cache.save(), "a corrupt file is overwritten");
        assertEquals(Optional.of(List.of("X")), LinkCache.load(broken).get("a.md", 1L, 2L));
    }

    @Test
    void anInMemoryCacheWorksButSavesNothing() {
        LinkCache cache = LinkCache.inMemory();
        cache.put("a.md", 1L, 2L, List.of("X"));
        assertEquals(Optional.of(List.of("X")), cache.get("a.md", 1L, 2L));
        assertFalse(cache.save());
    }
}
