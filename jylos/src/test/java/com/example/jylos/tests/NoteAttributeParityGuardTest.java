package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Notes have no shared attribute-mapping layer between the SQLite and filesystem
 * backends: {@code SQLiteDB} defines a boolean/derived field as a SQL column,
 * {@code FrontmatterHandler} independently defines it as a YAML frontmatter key.
 * A field added to only one side silently drifts in behavior between storage
 * backends. This guard test fails, naming the missing side, if either the SQLite
 * schema or the filesystem frontmatter mapping is missing an attribute the other
 * one has — and separately keeps {@code scripts/schema.txt} honest against the
 * schema SQLiteDB.java actually creates.
 */
class NoteAttributeParityGuardTest {

    private static final Path SQLITE_DB = Path.of("src/main/java/com/example/jylos/data/database/SQLiteDB.java");
    private static final Path FRONTMATTER_HANDLER =
            Path.of("src/main/java/com/example/jylos/data/dao/filesystem/FrontmatterHandler.java");
    private static final Path SCHEMA_TXT = Path.of("..", "scripts", "schema.txt");

    /** SQL column name (SQLiteDB) → frontmatter key (FrontmatterHandler) for the same note attribute. */
    private static final Map<String, String> ATTRIBUTE_MAPPING = Map.of(
            "is_favorite", "favorite",
            "is_pinned", "pinned",
            "is_deleted", "deleted",
            "deleted_date", "deleted_date",
            "status", "status",
            "is_private", "private");

    @Test
    void everyNoteAttributeHasBothASqlColumnAndAFrontmatterKey() throws IOException {
        String sqliteSource = Files.readString(SQLITE_DB, StandardCharsets.UTF_8);
        String frontmatterSource = Files.readString(FRONTMATTER_HANDLER, StandardCharsets.UTF_8);

        ATTRIBUTE_MAPPING.forEach((sqlColumn, frontmatterKey) -> {
            assertTrue(sqliteSource.contains(sqlColumn + " "),
                    "SQLiteDB.java is missing the '" + sqlColumn + "' column for the note attribute "
                            + "mapped to frontmatter key '" + frontmatterKey + "' — a field must exist on "
                            + "both storage backends or they will silently drift in behavior.");
            assertTrue(frontmatterSource.contains("\"" + frontmatterKey + "\""),
                    "FrontmatterHandler.java is missing the '" + frontmatterKey + "' key for the note "
                            + "attribute backed by SQL column '" + sqlColumn + "' — a field must exist on "
                            + "both storage backends or they will silently drift in behavior.");
        });
    }

    @Test
    void schemaDotTxtListsEveryNoteAttributeColumnSqliteDbActuallyCreates() throws IOException {
        String schemaTxt = Files.readString(SCHEMA_TXT, StandardCharsets.UTF_8);

        ATTRIBUTE_MAPPING.keySet().forEach(sqlColumn ->
                assertTrue(schemaTxt.contains(sqlColumn),
                        "scripts/schema.txt is missing the '" + sqlColumn + "' column that SQLiteDB.java "
                                + "actually creates on the notes table — regenerate schema.txt from the real "
                                + "CREATE TABLE statements so restoring from it produces a usable database."));
    }
}
