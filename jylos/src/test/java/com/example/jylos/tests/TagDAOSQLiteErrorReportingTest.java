package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.Connection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.sqlite.TagDAOSQLite;
import com.example.jylos.data.models.Tag;
import com.example.jylos.exceptions.DataAccessException;

/**
 * Like every other SQLite DAO operation, a failed tag rename or delete must be reported to
 * the caller. Swallowed, a rename that the database refused looked successful in the UI
 * until the next reload.
 */
class TagDAOSQLiteErrorReportingTest {

    @TempDir
    Path tempDir;

    private Connection connection;
    private TagDAOSQLite tags;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteTestSupport.configureFreshDatabase(tempDir.resolve("tags.db"));
        connection = SQLiteTestSupport.openConnection();
        tags = new TagDAOSQLite(connection);
    }

    @AfterEach
    void tearDown() throws Exception {
        SQLiteTestSupport.closeAndReset(connection);
    }

    @Test
    void renamingATagToATitleThatAlreadyExistsIsReportedAndChangesNothing() {
        Tag java = new Tag("java");
        java.setId(tags.createTag(java));
        Tag spring = new Tag("spring");
        spring.setId(tags.createTag(spring));

        Tag renamed = new Tag(spring.getId(), "java");
        assertThrows(DataAccessException.class, () -> tags.updateTag(renamed));

        assertEquals("spring", tags.getTagById(spring.getId()).getTitle(), "the refused rename must not apply");
        assertEquals("java", tags.getTagById(java.getId()).getTitle());
    }

    @Test
    void aValidRenameStillWorks() {
        Tag tag = new Tag("borrador");
        tag.setId(tags.createTag(tag));
        tags.updateTag(new Tag(tag.getId(), "definitivo"));
        assertEquals("definitivo", tags.getTagById(tag.getId()).getTitle());
    }

    @Test
    void deletingOnAClosedConnectionIsReported() throws Exception {
        Tag tag = new Tag("efímero");
        tag.setId(tags.createTag(tag));
        connection.close();
        assertThrows(DataAccessException.class, () -> tags.deleteTag(tag.getId()));
    }
}
