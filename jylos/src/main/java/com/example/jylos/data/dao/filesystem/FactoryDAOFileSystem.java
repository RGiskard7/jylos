package com.example.jylos.data.dao.filesystem;

import com.example.jylos.data.dao.interfaces.FactoryDAO;
import com.example.jylos.data.dao.interfaces.FolderDAO;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.dao.interfaces.TagDAO;

public class FactoryDAOFileSystem extends FactoryDAO {

    private NoteDAOFileSystem noteDAO;
    private FolderDAOFileSystem folderDAO;
    private TagDAOFileSystem tagDAO;

    public FactoryDAOFileSystem(String rootDirectory) {
        // Ideally we should cache instances to share caches.
        // deferContentLoad=true: startup builds a fast metadata-only cache and loads
        // note contents in the background, so opening a large/iCloud vault is instant.
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
