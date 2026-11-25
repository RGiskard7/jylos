package com.example.jylos.data.dao.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.models.Note;

/**
 * A note read whole has the dates of its file when its properties do not give them, as
 * Obsidian (and Jylos when it lists the vault) has them; dates the user wrote in its
 * properties stay. Saving it does not write those dates into the file.
 */
class NoteFileDatesTest {

    @Test
    void aNoteReadWholeHasItsFilesDatesUnlessItsPropertiesGiveThem(@TempDir Path vault) throws Exception {
        Path plain = vault.resolve("Plain.md");
        Files.writeString(plain, "# Plain\n", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(plain, FileTime.from(Instant.parse("2026-11-20T08:00:00Z")));
        Files.writeString(vault.resolve("Dated.md"), "---\ncreated: 2020-01-02\n---\nText\n", StandardCharsets.UTF_8);

        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString());
        Note read = dao.getNoteById("Plain.md");
        Note dated = dao.getNoteById("Dated.md");

        assertEquals("2026-11-20T08:00:00Z", read.getModifiedDate());
        assertEquals(false, read.getCreatedDate() == null || read.getCreatedDate().isBlank(), "created: the file's");
        assertEquals("2020-01-02", dated.getCreatedDate(), "the date the user wrote stays");

        read.setContent("# Plain\n\nMore\n");
        dao.updateNote(read);
        assertEquals(List.of("# Plain", "", "More"), Files.readAllLines(plain, StandardCharsets.UTF_8),
                "no dates written into the file");
    }
}
