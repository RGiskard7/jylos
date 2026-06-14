package com.example.jylos.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.insights.GraphAnalysisService;
import com.example.jylos.insights.GraphAnalysisService.GraphAnalysis;
import com.example.jylos.insights.NoteConnectivityInfo;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;

/**
 * Applies a parsed {@link SearchQuery} to a set of notes. Free text behaves exactly like
 * the previous simple search (title/body substring), so plain queries are unchanged;
 * operators add AND-combined filters on top.
 *
 * <p>Metadata that is expensive or graph-derived (tag membership, links, backlinks,
 * orphan status) is computed <em>lazily and once per search</em>, only when the query
 * actually uses it, reusing {@link TagService} and {@link GraphAnalysisService} (which
 * itself reuses the graph/link logic — no second link-resolution path). If a piece of
 * metadata can't be produced, the affected filter degrades to "no match" rather than
 * throwing.</p>
 *
 * <p>Works identically in SQLite and Markdown-vault modes; it operates on the note list
 * the caller already has, with full content supplied through a provider so the caller's
 * content cache is reused.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.1.0
 */
public final class AdvancedSearchService {

    private static final Logger logger = LoggerConfig.getLogger(AdvancedSearchService.class);
    private static final int SNIPPET_LENGTH = 160;

    private final NoteService noteService;
    private final TagService tagService;

    public AdvancedSearchService(NoteService noteService, TagService tagService) {
        this.noteService = noteService;
        this.tagService = tagService;
    }

    /** Parses {@code rawQuery} and filters {@code source}. */
    public List<SearchResult> search(String rawQuery, List<Note> source, Function<Note, String> contentProvider) {
        return search(SearchQueryParser.parse(rawQuery), source, contentProvider);
    }

    /**
     * Filters {@code source} by {@code query}.
     *
     * @param contentProvider returns a note's full, lowercased content (reuses the
     *                        caller's cache); may be null to read from the note directly
     */
    public List<SearchResult> search(SearchQuery query, List<Note> source, Function<Note, String> contentProvider) {
        if (source == null) {
            return List.of();
        }
        Function<Note, String> content = contentProvider != null ? contentProvider : AdvancedSearchService::lowerContent;
        Eval eval = new Eval(query, content);
        List<SearchResult> results = new ArrayList<>();
        for (Note note : source) {
            if (note == null) {
                continue;
            }
            if (query.isEmpty() || eval.matches(note)) {
                results.add(toResult(note));
            }
        }
        return results;
    }

    /** Convenience: parse + filter, returning just the matching notes. */
    public List<Note> searchNotes(String rawQuery, List<Note> source, Function<Note, String> contentProvider) {
        return search(rawQuery, source, contentProvider).stream().map(SearchResult::note).toList();
    }

    // ── Result building ──────────────────────────────────────────────────────────

    private SearchResult toResult(Note note) {
        return new SearchResult(note, snippet(note), folderName(note), tagTitles(note));
    }

    private String snippet(Note note) {
        String content = note.getContent();
        if (content == null || content.isBlank()) {
            return "";
        }
        String oneLine = content.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= SNIPPET_LENGTH ? oneLine : oneLine.substring(0, SNIPPET_LENGTH) + "…";
    }

    private static String folderName(Note note) {
        var parent = note.getParent();
        return parent != null && parent.getTitle() != null ? parent.getTitle() : "";
    }

    private List<String> tagTitles(Note note) {
        try {
            return tagService.getTagsForNote(note).stream()
                    .map(Tag::getTitle)
                    .filter(t -> t != null && !t.isBlank())
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String lowerContent(Note note) {
        String c = note.getContent();
        return c == null ? "" : c.toLowerCase(Locale.ROOT);
    }

    // ── Per-search evaluation (lazy metadata) ─────────────────────────────────────

    /** Evaluates one query against notes, memoizing the expensive lookups it needs. */
    private final class Eval {
        private final SearchQuery query;
        private final Function<Note, String> content;

        // Lazily populated only when the corresponding operator appears.
        private final Map<String, Set<String>> tagMembers = new HashMap<>();
        private Set<String> taggedNoteIds;
        private GraphAnalysis analysis;
        private Map<String, Integer> inbound;
        private Map<String, Integer> outbound;
        private Set<String> orphanIds;

        Eval(SearchQuery query, Function<Note, String> content) {
            this.query = query;
            this.content = content;
        }

        boolean matches(Note note) {
            for (SearchFilter filter : query.filters()) {
                boolean ok = test(filter, note);
                if (filter.negated()) {
                    ok = !ok;
                }
                if (!ok) {
                    return false;
                }
            }
            return true;
        }

        private boolean test(SearchFilter filter, Note note) {
            String value = filter.value();
            return switch (filter.field()) {
                case TEXT -> titleContains(note, value) || bodyContains(note, value);
                case TITLE -> titleContains(note, value);
                case BODY -> bodyContains(note, value);
                case TAG -> noteIdsForTag(value).contains(note.getId());
                case FOLDER -> folderMatches(note, value);
                case CREATED -> SearchDates.matches(value, note.getCreatedDate());
                case MODIFIED -> SearchDates.matches(value, note.getModifiedDate());
                case FAVORITE -> note.isFavorite() == "true".equals(value);
                case PRIVATE -> note.isPrivate() == "true".equals(value);
                case HAS -> hasMatches(note, value);
                case IS -> orphanIds().contains(note.getId()); // only "orphan" is parsed
            };
        }

        private boolean titleContains(Note note, String value) {
            String title = note.getTitle();
            return title != null && title.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT));
        }

        private boolean bodyContains(Note note, String value) {
            return content.apply(note).contains(value.toLowerCase(Locale.ROOT));
        }

        private boolean folderMatches(Note note, String value) {
            var parent = note.getParent();
            if (parent == null) {
                return false;
            }
            String name = parent.getTitle();
            String id = parent.getId();
            return (name != null && name.equalsIgnoreCase(value))
                    || (id != null && id.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT)));
        }

        private boolean hasMatches(Note note, String value) {
            return switch (value) {
                case "tag" -> taggedNoteIds().contains(note.getId());
                case "links" -> outbound().getOrDefault(note.getId(), 0) > 0;
                case "backlinks" -> inbound().getOrDefault(note.getId(), 0) > 0;
                default -> false;
            };
        }

        // ── lazy lookups ──

        private Set<String> noteIdsForTag(String tagTitle) {
            return tagMembers.computeIfAbsent(tagTitle.toLowerCase(Locale.ROOT), key -> {
                Set<String> ids = new HashSet<>();
                try {
                    Tag tag = tagService.getTagByTitle(tagTitle).orElse(null);
                    if (tag != null) {
                        for (Note n : tagService.getNotesWithTag(tag)) {
                            if (n != null && n.getId() != null) {
                                ids.add(n.getId());
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.fine("tag: filter degraded for '" + tagTitle + "': " + e.getMessage());
                }
                return ids;
            });
        }

        private Set<String> taggedNoteIds() {
            if (taggedNoteIds == null) {
                taggedNoteIds = new HashSet<>();
                try {
                    for (Tag tag : tagService.getAllTags()) {
                        for (Note n : tagService.getNotesWithTag(tag)) {
                            if (n != null && n.getId() != null) {
                                taggedNoteIds.add(n.getId());
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.fine("has:tag filter degraded: " + e.getMessage());
                }
            }
            return taggedNoteIds;
        }

        private GraphAnalysis analysis() {
            if (analysis == null) {
                try {
                    analysis = new GraphAnalysisService(noteService, tagService).analyze();
                } catch (Exception e) {
                    logger.fine("graph analysis for search degraded: " + e.getMessage());
                    analysis = new GraphAnalysis(0, 0, List.of(), List.of(), List.of());
                }
            }
            return analysis;
        }

        private Map<String, Integer> inbound() {
            ensureConnectivity();
            return inbound;
        }

        private Map<String, Integer> outbound() {
            ensureConnectivity();
            return outbound;
        }

        private void ensureConnectivity() {
            if (inbound != null) {
                return;
            }
            inbound = new HashMap<>();
            outbound = new HashMap<>();
            for (NoteConnectivityInfo c : analysis().connectivity()) {
                inbound.put(c.noteId(), c.inbound());
                outbound.put(c.noteId(), c.outbound());
            }
        }

        private Set<String> orphanIds() {
            if (orphanIds == null) {
                orphanIds = new HashSet<>();
                analysis().orphans().forEach(ref -> orphanIds.add(ref.noteId()));
            }
            return orphanIds;
        }
    }
}
