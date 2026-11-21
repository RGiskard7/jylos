package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.workspace.TabHistory;
import com.example.jylos.workspace.WorkspaceHistoryRepository;

/**
 * Which tabs are pinned travels with each tab's back and forward, in the file that already
 * keeps them: a file of an older version (no mark) reads as no tab pinned, and an older
 * version reading a file of this one still gets each tab's back and forward right.
 */
class PinnedTabsPersistenceTest {

    // Separators of the history file (see WorkspaceHistoryRepository).
    private static final String FIELD = "\u001F";
    private static final String TAB = "\u001D";
    private static final String SIDE = "\u001C";
    private static final String ENTRY = "\u001E";

    /** As it is today: a file written by 2.6.0 is read as it was written. */
    @Test
    void aFileOfTheCurrentFormatIsReadAsWritten(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("workspace-history.dat");
        Files.writeString(file, "w1" + FIELD + "a.md" + ENTRY + "b.md" + SIDE + "c.md" + TAB + SIDE + "\n",
                StandardCharsets.UTF_8);

        List<TabHistory> read = new WorkspaceHistoryRepository(file).load("w1");

        assertEquals(List.of(new TabHistory(List.of("a.md", "b.md"), List.of("c.md")), TabHistory.NONE), read);
    }

    @Test
    void whichTabsArePinnedIsKeptWithTheirHistory(@TempDir Path dir) {
        Path file = dir.resolve("workspace-history.dat");
        List<TabHistory> tabs = List.of(
                new TabHistory(List.of("a.md"), List.of("b.md"), true),
                new TabHistory(List.of(), List.of(), true),
                new TabHistory(List.of("x.md"), List.of(), false));
        new WorkspaceHistoryRepository(file).save("w1", tabs);

        List<TabHistory> read = new WorkspaceHistoryRepository(file).load("w1");

        assertEquals(tabs, read);
        assertEquals(List.of(true, true, false), read.stream().map(TabHistory::pinned).toList());
    }

    /** What an older version does with a line of this one: it takes back and forward, and nothing else. */
    @Test
    void anOlderVersionStillReadsEachTabsBackAndForward(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("workspace-history.dat");
        new WorkspaceHistoryRepository(file).save("w1", List.of(
                new TabHistory(List.of("a.md"), List.of("b.md"), true),
                new TabHistory(List.of("x.md"), List.of(), false)));

        String line = Files.readAllLines(file, StandardCharsets.UTF_8).get(0);
        List<List<List<String>>> olderReading = new ArrayList<>();
        for (String tab : line.substring(line.indexOf(FIELD) + 1).split(TAB, -1)) {
            String[] sides = tab.split(SIDE, -1); // 2.6.0 keeps sides[0] and sides[1]
            olderReading.add(List.of(entries(sides[0]), sides.length > 1 ? entries(sides[1]) : List.of()));
        }

        assertEquals(List.of(List.of(List.of("a.md"), List.of("b.md")), List.of(List.of("x.md"), List.of())),
                olderReading);
        assertTrue(line.split(TAB, -1)[1].split(SIDE, -1).length >= 2);
    }

    private static List<String> entries(String text) {
        List<String> entries = new ArrayList<>();
        for (String entry : text.split(ENTRY, -1)) {
            if (!entry.isEmpty()) {
                entries.add(entry);
            }
        }
        return entries;
    }
}
