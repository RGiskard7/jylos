package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class FileSystemDaoLocaleNeutralGuardTest {

    private static final Path FOLDER_DAO = Path
            .of("src/main/java/com/example/jylos/data/dao/filesystem/FolderDAOFileSystem.java");
    private static final Path NOTE_DAO = Path
            .of("src/main/java/com/example/jylos/data/dao/filesystem/NoteDAOFileSystem.java");

    @Test
    void fileSystemDaosShouldNotContainHardcodedAllNotesLabel() throws IOException {
        String folderDaoSource = Files.readString(FOLDER_DAO, StandardCharsets.UTF_8);
        String noteDaoSource = Files.readString(NOTE_DAO, StandardCharsets.UTF_8);

        assertFalse(folderDaoSource.contains("\"All Notes\""),
                "FolderDAOFileSystem should not hardcode UI locale labels.");
        assertFalse(noteDaoSource.contains("\"All Notes\""),
                "NoteDAOFileSystem should not hardcode UI locale labels.");
    }

    @Test
    void folderDaoCachePruningShouldUseOsNeutralPathNormalization() throws IOException {
        String folderDaoSource = Files.readString(FOLDER_DAO, StandardCharsets.UTF_8);
        assertTrue(folderDaoSource.contains("String normalizedId = id.replace(\"\\\\\", \"/\")"),
                "FolderDAOFileSystem cache pruning should normalize IDs in an OS-neutral way.");
        assertTrue(folderDaoSource.contains("String normalizedKey = k.replace(\"\\\\\", \"/\")"),
                "FolderDAOFileSystem cache pruning should normalize cache keys in an OS-neutral way.");
    }
}
