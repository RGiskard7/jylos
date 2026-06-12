package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.service.NoteHistoryService;
import com.example.jylos.util.LineDiff;

/**
 * Local note history must record changed content, skip unchanged content, prune to
 * the configured cap, and (paired here) the line diff must classify changes.
 */
class NoteHistoryServiceTest {

    @Test
    void snapshotsChangedContentAndSkipsUnchanged(@TempDir Path dir) throws Exception {
        NoteHistoryService history = new NoteHistoryService(dir, 50, 0);
        history.snapshot("note-1", "v1");
        Thread.sleep(5); // distinct millisecond timestamps for stable ordering
        history.snapshot("note-1", "v1"); // unchanged → skipped
        Thread.sleep(5);
        history.snapshot("note-1", "v2");

        List<NoteHistoryService.Snapshot> snaps = history.list("note-1");
        assertEquals(2, snaps.size(), "identical consecutive content must not duplicate");
        assertEquals("v2", history.read(snaps.get(0)), "newest first");
        assertEquals("v1", history.read(snaps.get(1)));
    }

    @Test
    void prunesToTheConfiguredMaximum(@TempDir Path dir) throws Exception {
        NoteHistoryService history = new NoteHistoryService(dir, 3, 0);
        for (int i = 1; i <= 6; i++) {
            history.snapshot("note-1", "v" + i);
            Thread.sleep(5);
        }
        List<NoteHistoryService.Snapshot> snaps = history.list("note-1");
        assertEquals(3, snaps.size(), "history capped at 3 snapshots");
        assertEquals("v6", history.read(snaps.get(0)));
        assertEquals("v4", history.read(snaps.get(2)), "oldest snapshots were pruned");
    }

    @Test
    void coalescingWindowSwallowsRapidSaves(@TempDir Path dir) {
        NoteHistoryService history = new NoteHistoryService(dir, 50, 60_000);
        history.snapshot("note-1", "v1");
        history.snapshot("note-1", "v2"); // within the window → coalesced
        assertEquals(1, history.list("note-1").size());
    }

    @Test
    void notesWithPathIdsGetIsolatedHistories(@TempDir Path dir) throws Exception {
        NoteHistoryService history = new NoteHistoryService(dir, 50, 0);
        history.snapshot("a/b.md", "one");
        history.snapshot("a_b.md", "two"); // sanitises to the same base name — must not collide
        assertEquals(1, history.list("a/b.md").size());
        assertEquals(1, history.list("a_b.md").size());
        assertEquals("one", history.read(history.list("a/b.md").get(0)));
        assertEquals("two", history.read(history.list("a_b.md").get(0)));
    }

    @Test
    void lineDiffClassifiesChanges() {
        List<LineDiff.Line> diff = LineDiff.diff("a\nb\nc", "a\nX\nc");
        assertEquals(LineDiff.Type.SAME, diff.get(0).type());
        assertTrue(diff.stream().anyMatch(l -> l.type() == LineDiff.Type.REMOVED && l.text().equals("b")));
        assertTrue(diff.stream().anyMatch(l -> l.type() == LineDiff.Type.ADDED && l.text().equals("X")));
        assertEquals(LineDiff.Type.SAME, diff.get(diff.size() - 1).type());
    }
}
