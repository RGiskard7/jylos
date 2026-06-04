package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.dao.sqlite.FolderDAOSQLite;
import com.example.jylos.data.models.Folder;

class FolderDAOSQLiteContractTest {

    private Connection connection;
    private FolderDAOSQLite folderDAO;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:folderdb;DB_CLOSE_DELAY=-1");
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS folders ("
                    + "folder_id TEXT PRIMARY KEY, "
                    + "parent_id TEXT, "
                    + "title TEXT NOT NULL, "
                    + "created_date TEXT NOT NULL, "
                    + "modified_date TEXT DEFAULT NULL, "
                    + "is_deleted INTEGER NOT NULL DEFAULT 0, "
                    + "deleted_date TEXT DEFAULT NULL)");
        }
        folderDAO = new FolderDAOSQLite(connection);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP TABLE folders");
            }
            connection.close();
        }
    }

    @Test
    void createFolderPersistsParentIdWhenParentIsProvided() throws Exception {
        Folder parent = new Folder("Parent");
        String parentId = folderDAO.createFolder(parent);
        assertNotNull(parentId);

        Folder child = new Folder("Child");
        child.setParent(parent);
        String childId = folderDAO.createFolder(child);
        assertNotNull(childId);

        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT parent_id FROM folders WHERE folder_id = '" + childId + "'")) {
            rs.next();
            assertEquals(parentId, rs.getString("parent_id"));
        }
    }
}
