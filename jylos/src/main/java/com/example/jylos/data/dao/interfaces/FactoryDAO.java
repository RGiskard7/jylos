package com.example.jylos.data.dao.interfaces;

import java.sql.Connection;

import com.example.jylos.data.dao.sqlite.FactoryDAOSQLite;
import com.example.jylos.data.dao.filesystem.FactoryDAOFileSystem;

/**
 * Abstract Factory class for creating DAO instances.
 * This class defines the contract for DAO factory implementations.
 */
public abstract class FactoryDAO {

    /**
     * Constant representing the SQLite factory type.
     */
    public static final int SQLITE_FACTORY = 1;

    /**
     * Retrieves an instance of NoteDAO.
     *
     * @return A NoteDAO instance.
     */
    public abstract NoteDAO getNoteDAO();

    /**
     * Retrieves an instance of FolderDAO.
     *
     * @return A FolderDAO instance.
     */
    public abstract FolderDAO getFolderDAO();

    /**
     * Retrieves an instance of TagDAO.
     *
     * @return A TagDAO instance.
     */
    public abstract TagDAO getLabelDAO();

    /**
     * Factory method to obtain a concrete implementation of FactoryDAO based on the
     * given key.
     *
     * @param keyFactory The factory type identifier.
     * @param connection The database connection to be used.
     * @return A specific implementation of FactoryDAO.
     * @throws IllegalArgumentException if an unsupported factory type is provided.
     */
    /**
     * Constant representing the File System factory type.
     */
    public static final int FILE_SYSTEM_FACTORY = 2;

    /**
     * Factory method to obtain a concrete implementation of FactoryDAO based on the
     * given key.
     *
     * @param keyFactory The factory type identifier.
     * @param connection The database connection to be used.
     * @return A specific implementation of FactoryDAO.
     * @throws IllegalArgumentException if an unsupported factory type is provided.
     */
    public static FactoryDAO getFactory(int keyFactory, Connection connection) {
        switch (keyFactory) {
            case SQLITE_FACTORY:
                return new FactoryDAOSQLite(connection);
            default:
                throw new IllegalArgumentException("Unsupported factory type");
        }
    }

    /**
     * Factory method to obtain a File System factory.
     * 
     * @param keyFactory The factory type (must be FILE_SYSTEM_FACTORY).
     * @param rootPath   The root directory path.
     * @return A FactoryDAOFileSystem instance.
     */
    public static FactoryDAO getFactory(int keyFactory, String rootPath) {
        switch (keyFactory) {
            case FILE_SYSTEM_FACTORY:
                return new FactoryDAOFileSystem(rootPath);
            default:
                throw new IllegalArgumentException("Unsupported factory type for path argument");
        }
    }
}
