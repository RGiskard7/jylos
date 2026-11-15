package com.example.jylos.data.dao.filesystem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.dao.interfaces.FolderDAO;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.exceptions.DataAccessException;
import com.example.jylos.exceptions.InvalidParameterException;
import com.example.jylos.util.PathContainment;

/**
 * File System implementation of FolderDAO.
 * Maps application folders to filesystem directories.
 * Stores metadata in .folder.yaml inside each directory.
 */
public class FolderDAOFileSystem implements FolderDAO {

    private static final Logger logger = LoggerConfig.getLogger(FolderDAOFileSystem.class);
    private static final String ROOT_ID = "ROOT";
    // DAO layer must stay locale-neutral; UI is responsible for localized labels.
    private static final String ROOT_TITLE = "ROOT";
    private final Path rootPath;
    private final FileSystemDocumentMetadataStore metadataStore;

    // Cache is less critical if we rely on paths, but useful for performance
    // Map ID (Relative Path) -> Absolute Path
    private final Map<String, Path> idToPathMap = new ConcurrentHashMap<>();

    public FolderDAOFileSystem(String rootDirectory) {
        this.rootPath = Paths.get(rootDirectory);
        this.metadataStore = new FileSystemDocumentMetadataStore(this.rootPath);
        if (!Files.exists(rootPath)) {
            try {
                Files.createDirectories(rootPath);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to create root directory for folders: " + rootDirectory, e);
                throw new DataAccessException("Could not initialize file storage", e);
            }
        }
        refreshCache();
    }

    public void refreshCache() {
        FileSystemIoLock.LOCK.lock();
        try {
            metadataStore.clearCache();
            idToPathMap.clear();
            // ID "" (empty string) or "ROOT" maps to rootPath
            idToPathMap.put(ROOT_ID, rootPath);

            try (Stream<Path> walk = Files.walk(rootPath)) {
                walk.filter(Files::isDirectory)
                        .filter(p -> !p.equals(rootPath))
                        // Exclude hidden directories inside the vault (.trash, .git); a hidden
                        // directory *above* the vault (e.g. ~/.config) must not hide it all.
                        .filter(p -> !FileSystemNames.isHiddenWithinVault(rootPath, p))
                        .forEach(path -> {
                            String relativePath = FileSystemNames.normalizeId(rootPath.relativize(path).toString());
                            idToPathMap.put(relativePath, path);
                        });
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to walk directory for folder cache refresh", e);
            }
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    @Override
    public String createFolder(Folder folder) {
        FileSystemIoLock.LOCK.lock();
        try {
            if (folder == null)
                throw new InvalidParameterException("Folder cannot be null");

            // Logic: ID is path. If ID is provided, use it. If not, use Title as name in
            // root.
            String parentId = ROOT_ID;
            if (folder.getParent() != null) {
                parentId = folder.getParent().getId();
            }

            Path parentPath = resolveFolderPath(parentId);
            if (parentPath == null)
                parentPath = rootPath;

            String folderName = sanitizeFilename(folder.getTitle());
            Path dirPath = parentPath.resolve(folderName);

            // Handle duplication
            int counter = 1;
            while (Files.exists(dirPath)) {
                dirPath = parentPath.resolve(folderName + " (" + counter + ")");
                counter++;
            }

            try {
                Files.createDirectories(dirPath);
                String newId = FileSystemNames.normalizeId(rootPath.relativize(dirPath).toString());
                idToPathMap.put(newId, dirPath);
                folder.setId(newId);
                return newId;
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to create folder directory: " + dirPath, e);
                throw new DataAccessException("Could not create folder: " + folder.getTitle(), e);
            }
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    @Override
    public void updateFolder(Folder folder) {
        FileSystemIoLock.LOCK.lock();
        try {
        // Rename logic
        Path currentPath = resolveFolderPath(folder.getId());
        if (currentPath == null || !Files.exists(currentPath))
            return;

        String newName = sanitizeFilename(folder.getTitle());
        if (!currentPath.getFileName().toString().equals(newName)) {
            Path newPath = currentPath.resolveSibling(newName);
            if (!Files.exists(newPath)) {
                try {
                    List<String> metadataDocumentIds = collectMetadataDocumentIds(currentPath);
                    Files.move(currentPath, newPath);
                    moveDocumentMetadataEntries(metadataDocumentIds,
                            FileSystemNames.normalizeId(rootPath.relativize(currentPath).toString()),
                            FileSystemNames.normalizeId(rootPath.relativize(newPath).toString()));
                    // Update Cache: Removing old ID and adding new one is tricky for recursive
                    // children headers
                    // Easiest is to refresh cache fully or update recursively.
                    // For now, refreshing cache is safer though invalidates other IDs if we held
                    // them
                    refreshCache();
                    folder.setId(FileSystemNames.normalizeId(rootPath.relativize(newPath).toString()));
                } catch (IOException e) {
                    logger.warning("Failed to rename folder: " + e.getMessage());
                    throw new DataAccessException("Could not rename folder: " + folder.getId(), e);
                }
            }
        }
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    @Override
    public void deleteFolder(String id) {
        FileSystemIoLock.LOCK.lock();
        try {
        if (id == null || id.isBlank()) {
            return;
        }
        requireInsideVault(id);
        String normalizedId = id.replace("\\", "/");

        Path path = idToPathMap.get(id);
        if (path == null) {
            for (Map.Entry<String, Path> entry : idToPathMap.entrySet()) {
                String key = entry.getKey() == null ? "" : entry.getKey().replace("\\", "/");
                if (key.equals(normalizedId)) {
                    path = entry.getValue();
                    break;
                }
            }
        }
        if (path == null) {
            Path candidate = rootPath.resolve(normalizedId.replace("/", File.separator));
            if (Files.exists(candidate) && Files.isDirectory(candidate)) {
                path = candidate;
            }
        }
        if (path != null && Files.exists(path)) {
            try {
                List<String> metadataDocumentIds = collectMetadataDocumentIds(path);
                Path trashRoot = rootPath.resolve(FileSystemNames.TRASH_DIR);
                if (!Files.exists(trashRoot)) {
                    Files.createDirectories(trashRoot);
                }

                // Use the relative ID to preserve structure in trash
                Path targetPath = trashRoot.resolve(normalizedId.replace("/", File.separator));

                // Ensure target parent directories exist in trash
                if (targetPath.getParent() != null && !Files.exists(targetPath.getParent())) {
                    Files.createDirectories(targetPath.getParent());
                }

                // Handle duplication
                if (Files.exists(targetPath)) {
                    String name = targetPath.getFileName().toString();
                    targetPath = targetPath.getParent().resolve(name + "_" + System.currentTimeMillis());
                }

                Files.move(path, targetPath);
                moveDocumentMetadataEntries(metadataDocumentIds,
                        normalizedId,
                        FileSystemNames.normalizeId(rootPath.relativize(targetPath).toString()));

                // Update cache
                idToPathMap.remove(id);
                // Also remove subfolders from cache using locale/OS-neutral ID matching
                idToPathMap.keySet().removeIf(k -> {
                    String normalizedKey = k.replace("\\", "/");
                    return normalizedKey.equals(normalizedId) || normalizedKey.startsWith(normalizedId + "/");
                });

            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to move folder to trash: " + path, e);
                throw new DataAccessException("Failed to delete folder", e);
            }
        }
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    @Override
    public Folder fetchTrashFolders() {
        FileSystemIoLock.LOCK.lock();
        try {
            // Title should be a localized string or just "Trash", not ".trash"
            Folder trashRootFolder = new Folder(FileSystemNames.TRASH_DIR, "Trash");
            Path trashPath = rootPath.resolve(FileSystemNames.TRASH_DIR);

            if (Files.exists(trashPath)) {
                loadSubFoldersRec(trashRootFolder, trashPath);
            }
            return trashRootFolder;
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    private void loadSubFoldersRec(Folder parent, Path currentPath) {
        try (Stream<Path> stream = Files.list(currentPath)) {
            stream.filter(Files::isDirectory)
                    .forEach(p -> {
                        // ID must be relative to rootPath with forward slashes
                        String id = rootPath.relativize(p).toString().replace("\\", "/");

                        // Title MUST be just the directory name
                        String title = p.getFileName().toString();

                        Folder sub = new Folder(id, title);
                        parent.add(sub);
                        sub.setParent(parent);

                        // Do NOT add to idToPathMap - these are trash folders
                        // and must not pollute the main folder cache

                        // Recursively load subfolders
                        loadSubFoldersRec(sub, p);
                    });
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error loading trash subfolders for " + currentPath, e);
        }
    }

    @Override
    public void restoreFolder(String id) {
        FileSystemIoLock.LOCK.lock();
        try {
        if (id == null || id.isBlank()) {
            return;
        }
        requireInsideVault(id);
        String normalizedId = id.replace("\\", "/");
        // ID is relative path like .trash/MyFolder or .trash/Sub/MyFolder
        Path srcPath = rootPath.resolve(normalizedId.replace("/", File.separator));
        if (!Files.exists(srcPath))
            throw new DataAccessException("Folder not found in trash: " + id, null);

        // Calculate original relative path by removing .trash/ prefix
        if (normalizedId.equals(FileSystemNames.TRASH_DIR)) {
            return; // Cannot restore the trash itself
        }
        String originalRelativePath = FileSystemNames.originalPathOfTrashed(normalizedId);

        Path targetPath = rootPath.resolve(originalRelativePath.replace("/", File.separator));

        // If target already exists, append timestamp or similar to avoid conflict
        if (Files.exists(targetPath)) {
            String name = srcPath.getFileName().toString();
            targetPath = targetPath.getParent().resolve(name + "_restored_" + System.currentTimeMillis());
        }

        try {
            // Ensure parent directory exists
            if (targetPath.getParent() != null && !Files.exists(targetPath.getParent())) {
                Files.createDirectories(targetPath.getParent());
            }

            List<String> metadataDocumentIds = collectMetadataDocumentIds(srcPath);
            Files.move(srcPath, targetPath);
            moveDocumentMetadataEntries(metadataDocumentIds,
                    normalizedId,
                    FileSystemNames.normalizeId(rootPath.relativize(targetPath).toString()));
            refreshCache();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to restore folder: " + id, e);
            throw new DataAccessException("Failed to restore folder", e);
        }
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    @Override
    public void permanentlyDeleteFolder(String id) {
        FileSystemIoLock.LOCK.lock();
        try {
        if (id == null || id.isBlank()) {
            return;
        }
        requireInsideVault(id);
        String normalizedId = id.replace("\\", "/");
        Path path = rootPath.resolve(normalizedId.replace("/", File.separator)); // Should be in .trash
        if (Files.exists(path)) {
            List<String> metadataDocumentIds = collectMetadataDocumentIds(path);
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                // File.delete reports a refusal by returning false: what is still there was not deleted.
                if (Files.exists(path)) {
                    throw new IOException("files left in " + path);
                }

                for (String documentId : metadataDocumentIds) {
                    metadataStore.deleteDocumentMetadata(documentId);
                }

                idToPathMap.remove(id);
                // Also remove subfolders from cache using locale/OS-neutral ID matching
                idToPathMap.keySet().removeIf(k -> {
                    String normalizedKey = k.replace("\\", "/");
                    return normalizedKey.equals(normalizedId) || normalizedKey.startsWith(normalizedId + "/");
                });
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to permanently delete folder: " + id, e);
                throw new DataAccessException("Could not delete folder: " + id, e);
            }
        }
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    @Override
    public Folder getFolderById(String id) {
        if (id == null)
            return null;
        if (ROOT_ID.equals(id)) {
            return new Folder(ROOT_ID, ROOT_TITLE);
        }
        Path path = resolveFolderPath(id);
        if (path != null) {
            String normalizedId = FileSystemNames.normalizeId(id);
            return new Folder(normalizedId, path.getFileName().toString());
        }
        return null;
    }

    @Override
    public Folder getFolderByNoteId(String noteId) {
        // NoteID is relative path "Folder/Note.md"
        // Return folder "Folder"
        String normalizedNoteId = FileSystemNames.normalizeId(noteId);
        Path notePath = Paths.get(normalizedNoteId.replace("/", File.separator));
        Path parent = notePath.getParent();
        if (parent == null)
            return getFolderById(ROOT_ID); // Root folder

        return getFolderById(FileSystemNames.normalizeId(parent.toString()));
    }

    @Override
    public List<Folder> fetchAllFoldersAsList() {
        return idToPathMap.entrySet().stream()
                .filter(e -> !e.getKey().equals(ROOT_ID) && !e.getKey().isEmpty()) // Exclude ROOT
                .filter(e -> !e.getValue().getFileName().toString().startsWith(".")) // Exclude hidden
                .map(e -> new Folder(FileSystemNames.normalizeId(e.getKey()), e.getValue().getFileName().toString()))
                .collect(Collectors.toList());
    }

    @Override
    public Folder fetchAllFoldersAsTree() {
        Folder root = new Folder(ROOT_ID, ROOT_TITLE);
        loadSubFolders(root);
        return root;
    }

    @Override
    public void addNote(Folder folder, Note note) {
        FileSystemIoLock.LOCK.lock();
        try {
            if (folder == null || note == null) {
                return;
            }
            requireInsideVault(note.getId());

            String normalizedNoteId = FileSystemNames.normalizeId(note.getId());
            Path sourcePath = rootPath.resolve(normalizedNoteId.replace("/", File.separator));
            if (!Files.exists(sourcePath)) {
                return;
            }

            Path targetDir;
            if (ROOT_ID.equals(folder.getId())) {
                targetDir = rootPath;
            } else {
                targetDir = resolveFolderPath(folder.getId());
            }

            if (targetDir == null || !Files.exists(targetDir)) {
                return;
            }

            Path requestedPath = targetDir.resolve(sourcePath.getFileName());
            if (sourcePath.equals(requestedPath)) {
                note.setParent(folder);
                return;
            }
            Path targetPath = uniqueDocumentTargetPath(requestedPath);

            try {
                if (!sourcePath.equals(targetPath)) {
                    Files.move(sourcePath, targetPath);
                    moveDocumentMetadataIfNeeded(
                            FileSystemNames.normalizeId(rootPath.relativize(sourcePath).toString()),
                            FileSystemNames.normalizeId(rootPath.relativize(targetPath).toString()),
                            targetPath);
                    String newId = FileSystemNames.normalizeId(rootPath.relativize(targetPath).toString());
                    note.setId(newId);
                    note.setParent(folder);
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to move note file to folder: " + folder.getId(), e);
                throw new DataAccessException("Could not move note into folder: " + folder.getId(), e);
            }
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    @Override
    public void moveNoteToRoot(Note note) {
        FileSystemIoLock.LOCK.lock();
        try {
            if (note == null || note.getId() == null) {
                return;
            }
            requireInsideVault(note.getId());

            String normalizedNoteId = FileSystemNames.normalizeId(note.getId());
            Path sourcePath = rootPath.resolve(normalizedNoteId.replace("/", File.separator));
            if (!Files.exists(sourcePath)) {
                return;
            }

            Path requestedPath = rootPath.resolve(sourcePath.getFileName());
            if (sourcePath.equals(requestedPath)) {
                note.setParent(getFolderById(ROOT_ID));
                return;
            }
            Path targetPath = uniqueDocumentTargetPath(requestedPath);

            try {
                Files.move(sourcePath, targetPath);
                moveDocumentMetadataIfNeeded(
                        FileSystemNames.normalizeId(rootPath.relativize(sourcePath).toString()),
                        FileSystemNames.normalizeId(rootPath.relativize(targetPath).toString()),
                        targetPath);
                note.setId(FileSystemNames.normalizeId(rootPath.relativize(targetPath).toString()));
                note.setParent(getFolderById(ROOT_ID));
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to move note file to root: " + note.getId(), e);
                throw new DataAccessException("Could not move note to the root: " + note.getId(), e);
            }
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    @Override
    public void removeNote(Folder folder, Note note) {
        FileSystemIoLock.LOCK.lock();
        try {
            if (folder == null || note == null || note.getId() == null) {
                return;
            }
            requireInsideVault(note.getId());

            String normalizedNoteId = FileSystemNames.normalizeId(note.getId());
            Path sourcePath = rootPath.resolve(normalizedNoteId.replace("/", File.separator));
            if (!Files.exists(sourcePath)) {
                return;
            }

            Path requestedPath = rootPath.resolve(sourcePath.getFileName());
            if (sourcePath.equals(requestedPath)) {
                note.setParent(getFolderById(ROOT_ID));
                return;
            }
            Path targetPath = uniqueDocumentTargetPath(requestedPath);

            try {
                Files.move(sourcePath, targetPath);
                moveDocumentMetadataIfNeeded(
                        FileSystemNames.normalizeId(rootPath.relativize(sourcePath).toString()),
                        FileSystemNames.normalizeId(rootPath.relativize(targetPath).toString()),
                        targetPath);
                note.setId(FileSystemNames.normalizeId(rootPath.relativize(targetPath).toString()));
                note.setParent(getFolderById(ROOT_ID));
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to remove note from folder: " + folder.getId(), e);
                throw new DataAccessException("Could not take note out of folder: " + folder.getId(), e);
            }
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    @Override
    public void loadNotes(Folder folder) {
        if (folder == null) {
            return;
        }

        Path path = ROOT_ID.equals(folder.getId()) ? rootPath : idToPathMap.get(folder.getId());
        if (!ROOT_ID.equals(folder.getId()) && path == null) {
            path = resolveFolderPath(folder.getId());
        }
        if (path == null || !Files.exists(path)) {
            return;
        }

        List<com.example.jylos.data.models.interfaces.Component> notes = new java.util.ArrayList<>();
        try (Stream<Path> stream = Files.list(path)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .forEach(p -> {
                        String id = rootPath.relativize(p).toString().replace("\\", "/");
                        String title = p.getFileName().toString().replaceFirst("\\.md$", "");
                        Note note = new Note(id, title, "");
                        note.setParent(folder);
                        notes.add(note);
                    });
        } catch (IOException e) {
            logger.warning("Error loading notes for folder " + folder.getTitle() + ": " + e.getMessage());
        }

        folder.addAll(notes);
    }

    @Override
    public void addSubFolder(Folder parent, Folder subFolder) {
        FileSystemIoLock.LOCK.lock();
        try {
            if (parent == null || subFolder == null || subFolder.getId() == null) {
                return;
            }

            Path parentPath = ROOT_ID.equals(parent.getId()) ? rootPath : resolveFolderPath(parent.getId());
            Path subPath = resolveFolderPath(subFolder.getId());
            if (parentPath == null || subPath == null || !Files.exists(subPath)) {
                return;
            }

            Path requestedPath = parentPath.resolve(subPath.getFileName());
            if (subPath.equals(requestedPath)) {
                subFolder.setParent(parent);
                return;
            }
            Path targetPath = uniqueDirectoryTargetPath(requestedPath);

            try {
                List<String> metadataDocumentIds = collectMetadataDocumentIds(subPath);
                Files.move(subPath, targetPath);
                moveDocumentMetadataEntries(metadataDocumentIds,
                        FileSystemNames.normalizeId(rootPath.relativize(subPath).toString()),
                        FileSystemNames.normalizeId(rootPath.relativize(targetPath).toString()));
                refreshCache();
                String newId = FileSystemNames.normalizeId(rootPath.relativize(targetPath).toString());
                subFolder.setId(newId);
                subFolder.setParent(parent);
            } catch (IOException e) {
                logger.warning("Failed to move subfolder under parent: " + e.getMessage());
                throw new DataAccessException("Could not move folder: " + subFolder.getId(), e);
            }
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    @Override
    public void moveFolderToRoot(Folder folder) {
        FileSystemIoLock.LOCK.lock();
        try {
            if (folder == null || folder.getId() == null || ROOT_ID.equals(folder.getId())) {
                return;
            }

            Path sourcePath = resolveFolderPath(folder.getId());
            if (sourcePath == null || !Files.exists(sourcePath)) {
                return;
            }
            Path requestedPath = rootPath.resolve(sourcePath.getFileName());
            if (sourcePath.equals(requestedPath)) {
                folder.setParent(null);
                return;
            }
            Path targetPath = uniqueDirectoryTargetPath(requestedPath);

            try {
                List<String> metadataDocumentIds = collectMetadataDocumentIds(sourcePath);
                Files.move(sourcePath, targetPath);
                moveDocumentMetadataEntries(metadataDocumentIds,
                        FileSystemNames.normalizeId(rootPath.relativize(sourcePath).toString()),
                        FileSystemNames.normalizeId(rootPath.relativize(targetPath).toString()));
                refreshCache();
                folder.setId(FileSystemNames.normalizeId(rootPath.relativize(targetPath).toString()));
                folder.setParent(null);
            } catch (IOException e) {
                logger.warning("Failed to move folder to root: " + e.getMessage());
                throw new DataAccessException("Could not move folder to the root: " + folder.getId(), e);
            }
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    @Override
    public void removeSubFolder(Folder parentFolder, Folder subFolder) {
        deleteFolder(subFolder.getId());
    }


    /** One {@code Files.list} of the folder: its directories, hidden ones left out. */
    @Override
    public List<Folder> fetchDirectSubfolders(Folder parent) {
        Path path = ROOT_ID.equals(parent.getId()) ? rootPath : resolveFolderPath(parent.getId());
        if (path == null || !Files.isDirectory(path)) {
            return List.of();
        }
        List<Folder> subfolders = new java.util.ArrayList<>();
        try (Stream<Path> stream = Files.list(path)) {
            stream.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .forEach(p -> {
                        Folder sub = new Folder(FileSystemNames.normalizeId(rootPath.relativize(p).toString()),
                                p.getFileName().toString());
                        sub.setParent(parent);
                        subfolders.add(sub);
                    });
        } catch (IOException e) {
            logger.warning("Error listing subfolders of " + parent.getTitle());
        }
        return subfolders;
    }

    @Override
    public void loadSubFolders(Folder folder, int maxDepth) {
        if (maxDepth <= 0)
            return;

        Path path = (folder.getId().equals(ROOT_ID)) ? rootPath : resolveFolderPath(folder.getId());
        if (path == null || !Files.exists(path))
            return;

        try (Stream<Path> stream = Files.list(path)) {
            stream.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith(".")) // Ignore hidden
                    .forEach(p -> {
                        String startPath = FileSystemNames.normalizeId(rootPath.relativize(p).toString());
                        Folder sub = new Folder(startPath, p.getFileName().toString());
                        sub.setParent(folder);
                        folder.add(sub);
                        loadSubFolders(sub, maxDepth - 1);
                    });
        } catch (IOException e) {
            logger.warning("Error loading subfolders for " + folder.getTitle());
        }
    }


    @Override
    public void loadParentFolders(Folder folder, int maxDepth) {
        if (folder == null || maxDepth <= 0) {
            return;
        }

        Folder current = folder;
        int depth = 0;
        while (depth < maxDepth) {
            Folder parent = getParentFolder(current.getId());
            if (parent == null) {
                break;
            }
            current.setParent(parent);
            current = parent;
            depth++;
        }
    }

    @Override
    public void loadParentFolder(Folder folder) {
        if (folder == null) {
            return;
        }
        folder.setParent(getParentFolder(folder.getId()));
    }

    @Override
    public Folder getParentFolder(String folderId) {
        Path path = resolveFolderPath(folderId);
        if (path != null) {
            Path parent = path.getParent();
            if (parent != null && parent.startsWith(rootPath)) {
                if (parent.equals(rootPath))
                    return getFolderById(ROOT_ID);
                return getFolderById(FileSystemNames.normalizeId(rootPath.relativize(parent).toString()));
            }
        }
        return null;
    }

    @Override
    public Folder getParentFolder(Folder folder) {
        return getParentFolder(folder.getId());
    }

    @Override
    public String getPathFolder(String idFolder) {
        Path path = resolveFolderPath(idFolder);
        return path != null ? path.toAbsolutePath().toString() : null;
    }

    @Override
    public boolean existsByTitle(String title) {
        return Files.exists(rootPath.resolve(sanitizeFilename(title)));
    }

    private String sanitizeFilename(String title) {
        return FileSystemNames.sanitize(title, "folder");
    }


    /** Rejects ids that would resolve outside the vault before any mutation touches the disk. */
    private void requireInsideVault(String id) {
        if (PathContainment.resolveInside(rootPath, FileSystemNames.normalizeId(id)).isEmpty()) {
            throw new InvalidParameterException("Id resolves outside the vault: " + id);
        }
    }

    private Path resolveFolderPath(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        if (PathContainment.resolveInside(rootPath, FileSystemNames.normalizeId(id)).isEmpty()) {
            return null;
        }
        Path path = idToPathMap.get(id);
        if (path != null) {
            return path;
        }
        String normalized = FileSystemNames.normalizeId(id);
        path = idToPathMap.get(normalized);
        if (path != null) {
            return path;
        }
        for (Map.Entry<String, Path> entry : idToPathMap.entrySet()) {
            if (FileSystemNames.normalizeId(entry.getKey()).equals(normalized)) {
                return entry.getValue();
            }
        }
        Path candidate = rootPath.resolve(normalized.replace("/", File.separator));
        if (Files.exists(candidate) && Files.isDirectory(candidate)) {
            return candidate;
        }
        return null;
    }


    private Path uniqueDocumentTargetPath(Path requestedPath) {
        if (requestedPath == null || !Files.exists(requestedPath)) {
            return requestedPath;
        }
        Path parent = requestedPath.getParent();
        String filename = requestedPath.getFileName().toString();
        int counter = 1;
        Path candidate;
        do {
            candidate = parent.resolve(FileSystemNames.withSuffix(filename, " (" + counter + ")"));
            counter++;
        } while (Files.exists(candidate));
        return candidate;
    }

    private Path uniqueDirectoryTargetPath(Path requestedPath) {
        if (requestedPath == null || !Files.exists(requestedPath)) {
            return requestedPath;
        }
        Path parent = requestedPath.getParent();
        String folderName = requestedPath.getFileName().toString();
        int counter = 1;
        Path candidate;
        do {
            candidate = parent.resolve(folderName + " (" + counter + ")");
            counter++;
        } while (Files.exists(candidate));
        return candidate;
    }

    private void moveDocumentMetadataIfNeeded(String previousId, String currentId, Path targetPath) {
        if (targetPath == null || !Files.isRegularFile(targetPath)) {
            return;
        }
        if (com.example.jylos.util.AttachmentType.fromName(targetPath.getFileName().toString())
                == com.example.jylos.util.AttachmentType.MARKDOWN) {
            return;
        }
        metadataStore.moveDocumentMetadata(previousId, currentId);
    }

    private List<String> collectMetadataDocumentIds(Path directoryRoot) {
        List<String> ids = new ArrayList<>();
        if (directoryRoot == null || !Files.exists(directoryRoot)) {
            return ids;
        }
        try (Stream<Path> walk = Files.walk(directoryRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> {
                        com.example.jylos.util.AttachmentType type =
                                com.example.jylos.util.AttachmentType.fromName(path.getFileName().toString());
                        return type.isAttachment();
                    })
                    .forEach(path -> ids.add(FileSystemNames.normalizeId(rootPath.relativize(path).toString())));
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to collect document metadata paths", e);
        }
        return ids;
    }

    private void moveDocumentMetadataEntries(List<String> documentIds, String previousPrefix, String currentPrefix) {
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        String oldPrefix = FileSystemNames.normalizeId(previousPrefix);
        String newPrefix = FileSystemNames.normalizeId(currentPrefix);
        for (String documentId : documentIds) {
            String normalizedDocumentId = FileSystemNames.normalizeId(documentId);
            String suffix = normalizedDocumentId.startsWith(oldPrefix)
                    ? normalizedDocumentId.substring(oldPrefix.length())
                    : normalizedDocumentId;
            metadataStore.moveDocumentMetadata(normalizedDocumentId, newPrefix + suffix);
        }
    }
}
