package com.example.jylos.insights;

import java.util.List;

/**
 * Aggregate "Knowledge Insights" report over the whole vault: headline totals, the
 * actionable lists (most-connected / orphans / broken links / untagged notes / tag
 * usage) and a simple, explainable {@link #healthScore} with its penalty breakdown.
 *
 * <p>Built by {@link KnowledgeInsightsService} on top of {@link GraphAnalysisService}
 * (graph structure) and the existing {@code NoteService}/{@code TagService}. It is a
 * plain immutable snapshot — no behaviour, no UI.</p>
 *
 * @param totalNotes       number of existing notes
 * @param totalLinks       resolved directed note→note links
 * @param totalBacklinks   total backlinks (equals {@code totalLinks}: every resolved
 *                         link is exactly one note's backlink)
 * @param totalTags        number of distinct tags
 * @param avgLinksPerNote  {@code totalLinks / totalNotes} (0 when there are no notes)
 * @param healthScore      0–100 orientation score (see {@link KnowledgeInsightsService#healthScore})
 * @param orphanPenalty    points subtracted for orphan notes
 * @param untaggedPenalty  points subtracted for untagged notes
 * @param brokenPenalty    points subtracted for broken links
 * @param mostConnected    top notes by total connections (descending)
 * @param orphans          notes with no resolved links in or out
 * @param brokenLinks      links pointing to non-existent notes
 * @param notesWithoutTags notes that have no tags
 * @param tagUsage         most-used tags with their note counts (descending)
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.1.0
 */
public record KnowledgeHealthReport(
        int totalNotes,
        int totalLinks,
        int totalBacklinks,
        int totalTags,
        double avgLinksPerNote,
        int healthScore,
        int orphanPenalty,
        int untaggedPenalty,
        int brokenPenalty,
        List<NoteConnectivityInfo> mostConnected,
        List<NoteRef> orphans,
        List<BrokenLinkInfo> brokenLinks,
        List<NoteRef> notesWithoutTags,
        List<TagUsage> tagUsage) {

    /** A minimal clickable reference to a note (id + title). */
    public record NoteRef(String noteId, String title) {
    }

    /** A tag and how many notes carry it. */
    public record TagUsage(String tag, int count) {
    }
}
