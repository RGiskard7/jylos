package com.example.jylos.data.dao.filesystem;

import com.example.jylos.data.dao.interfaces.FactoryDAO;
import com.example.jylos.data.dao.interfaces.FolderDAO;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.dao.interfaces.TagDAO;

/**
 * DAO factory for filesystem-backed vaults.
 *
 * <p>The factory creates one shared note DAO instance and wires folder/tag DAOs
 * against it so path caches, metadata sidecars and tag extraction stay consistent
 * within the active vault session.</p>
 */
public class FactoryDAOFileSystem extends FactoryDAO {

    private NoteDAOFileSystem noteDAO;
    private FolderDAOFileSystem folderDAO;
    private TagDAOFileSystem tagDAO;

    public FactoryDAOFileSystem(String rootDirectory) {
        // Deferred loading keeps large or cloud-backed vaults responsive at startup.
        this.noteDAO = new NoteDAOFileSystem(rootDirectory, true);
        this.folderDAO = new FolderDAOFileSystem(rootDirectory);
        this.tagDAO = new TagDAOFileSystem(this.noteDAO);
    }

    @Override
    public NoteDAO getNoteDAO() {
        return noteDAO;
    }

    @Override
    public FolderDAO getFolderDAO() {
        return folderDAO;
    }

    @Override
    public TagDAO getLabelDAO() {
        return tagDAO;
    }
}
