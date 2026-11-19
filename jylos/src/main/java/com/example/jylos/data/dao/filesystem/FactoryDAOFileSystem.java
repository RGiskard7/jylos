package com.example.jylos.data.dao.filesystem;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.example.jylos.data.dao.FolderColorStore;
import com.example.jylos.data.dao.VaultSettingsStore;
import com.example.jylos.data.dao.interfaces.EncryptionKeyDAO;
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
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.0.0
 */
public class FactoryDAOFileSystem extends FactoryDAO {

    private NoteDAOFileSystem noteDAO;
    private FolderDAOFileSystem folderDAO;
    private TagDAOFileSystem tagDAO;
    private EncryptionKeyDAOFileSystem encryptionKeyDAO;
    private final FolderColorStore folderColors;
    private final VaultSettingsStore vaultSettings;

    public FactoryDAOFileSystem(String rootDirectory) {
        // Deferred loading keeps large or cloud-backed vaults responsive at startup.
        this.noteDAO = new NoteDAOFileSystem(rootDirectory, true, linkCacheFile(rootDirectory));
        this.folderDAO = new FolderDAOFileSystem(rootDirectory);
        // A folder renamed or moved changes the paths of the notes inside it: both DAOs follow it.
        FileSystemPathRedirects redirects = new FileSystemPathRedirects();
        this.folderDAO.usePathRedirects(redirects);
        this.noteDAO.usePathRedirects(redirects);
        this.tagDAO = new TagDAOFileSystem(this.noteDAO);
        this.encryptionKeyDAO = new EncryptionKeyDAOFileSystem(Path.of(rootDirectory));
        // In the vault's own hidden folder, beside the encryption key: the colours travel with the notes.
        this.folderColors = new FolderColorStore(Path.of(rootDirectory, ".jylos", "folder-colors.json"));
        this.vaultSettings = new VaultSettingsStore(Path.of(rootDirectory, ".jylos", "settings.json"));
    }

    /**
     * Where the links of this vault's long notes are cached: one file per vault in the app's
     * cache folder, named after a hash of the vault path (as Obsidian keeps its index outside
     * the vault, so nothing is added to the user's notes folder).
     */
    static Path linkCacheFile(String rootDirectory) {
        try {
            String key = Path.of(rootDirectory).toAbsolutePath().normalize().toString();
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
            String name = HexFormat.of().formatHex(hash, 0, 8) + ".json";
            return Path.of(com.example.jylos.AppDataDirectory.layout().cacheDir().toString(),
                    "links", name);
        } catch (NoSuchAlgorithmException | RuntimeException e) {
            return null;
        }
    }

    @Override
    public FolderColorStore getFolderColorStore() {
        return folderColors;
    }

    /** {@code .jylos/settings.json} in the vault. */
    @Override
    public VaultSettingsStore getVaultSettingsStore() {
        return vaultSettings;
    }

    @Override
    public EncryptionKeyDAO getEncryptionKeyDAO() {
        return encryptionKeyDAO;
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
