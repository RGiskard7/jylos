package com.example.jylos.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.dao.filesystem.FrontmatterHandler;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.util.EnexConverter;

/**
 * Imports notes from external sources into the current storage backend:
 *
 * <ul>
 *   <li><b>Obsidian vault</b> — walks a folder of {@code .md} files (skipping
 *       {@code .obsidian/}, {@code .trash/} and other dot-directories), parses the
 *       Obsidian-compatible YAML frontmatter with {@link FrontmatterHandler}, and
 *       recreates the relative folder hierarchy.</li>
 *   <li><b>Evernote {@code .enex}</b> — parses the export XML (with external entity
 *       resolution disabled) and converts each note's ENML body to Markdown via
 *       {@link EnexConverter}; titles and tags are preserved, binary attachments are
 *       not (a placeholder marks where they were).</li>
 * </ul>
 *
 * <p>Import is additive and per-note fault tolerant: a note that fails to import is
 * recorded in {@link ImportResult#errors()} and does not abort the rest. Storage goes
 * through {@link NoteService}/{@link FolderService}, so both SQLite and vault modes
 * work the same.</p>
 *
 * <p>Importing a vault again brings only what is not there yet: a folder already at the same
 * place is used as it is, and a note whose title is already in that folder is left alone,
 * whatever it says now in either place (counted in {@link ImportResult#notesSkipped()}).</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
public class ImportService {

    private static final Logger logger = LoggerConfig.getLogger(ImportService.class);

    private final NoteService noteService;
    private final FolderService folderService;

    /**
     * Outcome summary for an import run.
     *
     * @param notesImported number of notes imported successfully
     * @param foldersCreated number of folders created during the import
     * @param notesSkipped number of notes left alone because they were already there
     * @param errors non-fatal per-note errors encountered during the import
     */
    public record ImportResult(int notesImported, int foldersCreated, int notesSkipped, List<String> errors) {
    }

    public ImportService(NoteService noteService, FolderService folderService) {
        this.noteService = noteService;
        this.folderService = folderService;
    }

    /** The folder of the vault the notes are imported into, or none with the database. */
    public Optional<Path> storageRoot() {
        return noteService.getStorageRoot();
    }

    // ── Obsidian vault ───────────────────────────────────────────────────────

    /**
     * Imports every {@code .md} note under {@code vaultDir}, recreating the folder
     * hierarchy. Safe to call off the JavaFX thread (does I/O).
     */
    public ImportResult importObsidianVault(Path vaultDir) {
        List<String> errors = new ArrayList<>();
        int notes = 0;
        int folders = 0;
        int skipped = 0;
        // What is there before this run: the notes of a vault imported earlier are not brought
        // twice, while two notes of this vault with the same title in one folder still are.
        Map<String, Folder> folderCache = existingFoldersByPath();
        Set<String> existingNotes = existingNoteKeys();

        try (Stream<Path> walk = Files.walk(vaultDir)) {
            List<Path> mdFiles = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".md"))
                    .filter(p -> !isInHiddenDir(vaultDir, p))
                    .sorted()
                    .toList();

            for (Path file : mdFiles) {
                try {
                    String raw = Files.readString(file, StandardCharsets.UTF_8);
                    boolean hasFrontmatter = raw.stripLeading().startsWith("---");
                    Note parsed = FrontmatterHandler.parse(raw);
                    // Obsidian semantics: the file name IS the note title; an explicit
                    // frontmatter `title:` (non-standard but supported) wins when present.
                    String title = stripExtension(file.getFileName().toString());
                    if (hasFrontmatter && parsed.getTitle() != null && !parsed.getTitle().isBlank()) {
                        title = parsed.getTitle();
                    }
                    // Without frontmatter the parser keeps the whole file as the text.
                    String content = parsed.getContent() != null ? parsed.getContent() : "";

                    Note note = new Note(title, content);
                    note.setStatus(parsed.getStatus());
                    note.setCustomProperties(parsed.getCustomProperties());
                    for (Tag tag : parsed.getTags()) {
                        note.addTag(tag);
                    }

                    Path relParent = vaultDir.relativize(file).getParent();
                    if (existingNotes.contains(noteKey(folderKey(relParent), title))) {
                        skipped++;
                        continue;
                    }
                    Folder target = null;
                    if (relParent != null) {
                        int before = folderCache.size();
                        target = ensureFolderChain(relParent, folderCache);
                        folders += folderCache.size() - before;
                    }

                    Note created = noteService.createNote(note);
                    if (target != null) {
                        folderService.addNoteToFolder(target, created);
                    }
                    notes++;
                } catch (Exception e) {
                    errors.add(file.getFileName() + ": " + e.getMessage());
                    logger.warning("Obsidian import failed for " + file + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            errors.add("walk: " + e.getMessage());
        }
        return new ImportResult(notes, folders, skipped, errors);
    }

    /** The folders there now, by their path in the vault ({@code projects/inner}). */
    private Map<String, Folder> existingFoldersByPath() {
        Map<String, Folder> byPath = new HashMap<>();
        for (Folder folder : folderService.getAllFolders()) {
            String path = folder != null ? noteService.folderPath(folder.getId()) : "";
            if (!path.isEmpty()) {
                byPath.putIfAbsent(path, folder);
            }
        }
        return byPath;
    }

    /** The notes there now, as {@link #noteKey}: their folder's path and their title. */
    private Set<String> existingNoteKeys() {
        List<Note> notes = noteService.getAllNotes();
        LinkIndex paths = noteService.buildLinkIndex(notes);
        Set<String> keys = new HashSet<>();
        for (Note note : notes) {
            String path = note.getId() != null ? paths.pathOf(note.getId()) : null;
            if (path != null && note.getTitle() != null) {
                int slash = path.lastIndexOf('/');
                keys.add(noteKey(slash > 0 ? path.substring(0, slash) : "", note.getTitle()));
            }
        }
        return keys;
    }

    /** A folder of the vault being imported, as the path {@link NoteService#folderPath} gives. */
    private static String folderKey(Path relParent) {
        if (relParent == null) {
            return "";
        }
        StringBuilder key = new StringBuilder();
        for (Path part : relParent) {
            if (key.length() > 0) {
                key.append('/');
            }
            key.append(part);
        }
        return key.toString();
    }

    private static String noteKey(String folderPath, String title) {
        return folderPath + "\n" + title;
    }

    /** True when any path segment between the vault root and the file starts with a dot. */
    private static boolean isInHiddenDir(Path root, Path file) {
        Path rel = root.relativize(file);
        for (int i = 0; i < rel.getNameCount() - 1; i++) {
            if (rel.getName(i).toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    /** Finds or creates the folder chain for a relative path, caching by path key. */
    private Folder ensureFolderChain(Path relParent, Map<String, Folder> cache) {
        Folder parent = null;
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < relParent.getNameCount(); i++) {
            String name = relParent.getName(i).toString();
            if (key.length() > 0) {
                key.append('/');
            }
            key.append(name);
            String cacheKey = key.toString();
            Folder cached = cache.get(cacheKey);
            if (cached == null) {
                cached = parent == null
                        ? folderService.createFolder(name)
                        : folderService.createSubfolder(name, parent);
                cache.put(cacheKey, cached);
            }
            parent = cached;
        }
        return parent;
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    // ── Evernote .enex ───────────────────────────────────────────────────────

    /**
     * Imports the notes of an Evernote {@code .enex} export file. Bodies are converted
     * from ENML to Markdown; tags are preserved; attachments are not imported.
     * Safe to call off the JavaFX thread.
     */
    public ImportResult importEnex(Path enexFile) {
        List<String> errors = new ArrayList<>();
        int notes = 0;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Harden the XML parser: .enex declares an external DTD; never fetch it.
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);

            Document doc = factory.newDocumentBuilder().parse(enexFile.toFile());
            NodeList noteNodes = doc.getElementsByTagName("note");
            for (int i = 0; i < noteNodes.getLength(); i++) {
                try {
                    Element el = (Element) noteNodes.item(i);
                    String title = textOf(el, "title");
                    if (title == null || title.isBlank()) {
                        title = "Imported note " + (i + 1);
                    }
                    String content = EnexConverter.toMarkdown(textOf(el, "content"));

                    Note note = new Note(title, content);
                    NodeList tagNodes = el.getElementsByTagName("tag");
                    for (int t = 0; t < tagNodes.getLength(); t++) {
                        String tagName = tagNodes.item(t).getTextContent();
                        if (tagName != null && !tagName.isBlank()) {
                            note.addTag(new Tag(tagName.trim()));
                        }
                    }
                    noteService.createNote(note);
                    notes++;
                } catch (Exception e) {
                    errors.add("note " + (i + 1) + ": " + e.getMessage());
                    logger.warning("ENEX import failed for note " + (i + 1) + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            errors.add(enexFile.getFileName() + ": " + e.getMessage());
            logger.warning("ENEX import failed: " + e.getMessage());
        }
        return new ImportResult(notes, 0, 0, errors);
    }

    private static String textOf(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        return list.getLength() > 0 ? list.item(0).getTextContent() : null;
    }
}
