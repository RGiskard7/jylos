package com.example.jylos.plugin.builtin.dataview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;

/**
 * Warm metadata index over the vault, rebuilt lazily and invalidated by note events.
 *
 * <h2>Why an index</h2>
 * <p>Queries need each note's <em>full</em> body: an inline field or a task can sit
 * anywhere in the file, while the notes list only carries a truncated head. Reading every
 * note on every render would make the preview unusable on a real vault, so parsed pages
 * are cached per note and only re-read when that note's modified timestamp changes.
 * The first query after startup pays the full read; everything after it is a map lookup.</p>
 *
 * <h2>Exclusions</h2>
 * <p>Attachments and canvas files carry no Markdown metadata, and private notes are left
 * out deliberately: a query is rendered into a preview that may be exported or shared,
 * and silently surfacing the contents of a note the user marked private would leak it.</p>
 */
final class DataviewIndex implements PageSource {

    private static final List<String> NON_MARKDOWN_SUFFIXES = List.of(
            ".pdf", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp", ".canvas", ".mp4", ".mp3");

    private final NoteService noteService;
    private final FolderService folderService;

    /** noteId → parsed page, validated by the note's modified timestamp. */
    private final Map<String, CachedPage> cache = new ConcurrentHashMap<>();

    private volatile List<Page> snapshot;

    private record CachedPage(String modified, Page page) {
    }

    DataviewIndex(NoteService noteService, FolderService folderService) {
        this.noteService = Objects.requireNonNull(noteService, "noteService");
        this.folderService = folderService;
    }

    /** Drops the whole snapshot; individual page parses survive if still current. */
    void invalidate() {
        snapshot = null;
    }

    /** Forces the given note to be re-read and re-parsed on the next query. */
    void invalidate(String noteId) {
        if (noteId != null) {
            cache.remove(noteId);
        }
        snapshot = null;
    }

    void clear() {
        cache.clear();
        snapshot = null;
    }

    /**
     * Returns every indexable page. Safe to call off the JavaFX thread (it reads notes);
     * the preview render thread is exactly where it is meant to run.
     */
    @Override
    public List<Page> pages() {
        List<Page> current = snapshot;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (snapshot != null) {
                return snapshot;
            }
            List<Page> built = build();
            snapshot = built;
            return built;
        }
    }

    /** Finds a page by note title, case-insensitively — used to resolve {@code this}. */
    @Override
    public Page pageByTitle(String title) {
        if (title == null) {
            return null;
        }
        for (Page page : pages()) {
            if (page.title().equalsIgnoreCase(title)) {
                return page;
            }
        }
        return null;
    }

    private List<Page> build() {
        Map<String, String> foldersByNoteId = folderIndex();
        List<Page> built = new ArrayList<>();

        for (Note listed : noteService.getAllNotes()) {
            if (!isIndexable(listed)) {
                continue;
            }
            String id = listed.getId();
            String modified = listed.getModifiedDate();
            CachedPage cached = cache.get(id);
            if (cached != null && Objects.equals(cached.modified(), modified)) {
                built.add(cached.page());
                continue;
            }

            // The listed note carries a truncated body; the full read is what makes
            // fields and tasks below the head visible to queries.
            Note full = noteService.getNoteById(id).orElse(listed);
            if (!isIndexable(full)) {
                continue;
            }
            Page page = PageParser.parse(full, foldersByNoteId.getOrDefault(id, ""), pathOf(full));
            cache.put(id, new CachedPage(modified, page));
            built.add(page);
        }

        linkInlinks(built);
        return List.copyOf(built);
    }

    /** Resolves outgoing links to pages so {@code file.inlinks} works in both directions. */
    private static void linkInlinks(List<Page> pages) {
        Map<String, Page> byTitle = new HashMap<>();
        for (Page page : pages) {
            byTitle.put(page.title().toLowerCase(Locale.ROOT), page);
        }
        for (Page source : pages) {
            for (Link outlink : source.outlinks()) {
                Page target = byTitle.get(outlink.target().toLowerCase(Locale.ROOT));
                if (target != null && target != source) {
                    target.addInlink(source.title());
                }
            }
        }
    }

    private Map<String, String> folderIndex() {
        Map<String, String> byNoteId = new HashMap<>();
        if (folderService == null) {
            return byNoteId;
        }
        try {
            for (Folder folder : folderService.getAllFolders()) {
                if (folder == null) {
                    continue;
                }
                for (Note note : folderService.getNotesInFolder(folder)) {
                    if (note != null && note.getId() != null) {
                        byNoteId.put(note.getId(), folder.getTitle());
                    }
                }
            }
        } catch (RuntimeException e) {
            // A folder backend hiccup must not take the whole query down; queries that
            // do not use file.folder keep working, and those that do see an empty folder.
            return byNoteId;
        }
        return byNoteId;
    }

    private String pathOf(Note note) {
        try {
            return noteService.getNoteFilePath(note.getId())
                    .map(java.nio.file.Path::toString)
                    .orElse(note.getId());
        } catch (RuntimeException e) {
            return note.getId();
        }
    }

    private static boolean isIndexable(Note note) {
        if (note == null || note.getId() == null || note.isDeleted() || note.isPrivate()) {
            return false;
        }
        String title = note.getTitle();
        if (title == null) {
            return false;
        }
        String lower = title.toLowerCase(Locale.ROOT);
        for (String suffix : NON_MARKDOWN_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                return false;
            }
        }
        return true;
    }
}
