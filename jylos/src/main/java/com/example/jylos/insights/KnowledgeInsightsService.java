package com.example.jylos.insights;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;

/**
 * Builds the vault-wide {@link KnowledgeHealthReport}: combines the structural graph
 * analysis ({@link GraphAnalysisService}) with tag information from {@link TagService}
 * to surface orphan notes, broken links, the most-connected notes, untagged notes,
 * tag usage and an explainable health score.
 *
 * <p>Runs read-only; callers should invoke {@link #generateReport()} off the JavaFX
 * thread for large vaults. It reuses existing services and the existing graph/link
 * logic — it introduces no second link-resolution path.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.1.0
 */
public final class KnowledgeInsightsService {

    private static final Logger logger = LoggerConfig.getLogger(KnowledgeInsightsService.class);

    private static final int TOP_CONNECTED = 10;
    private static final int TOP_TAGS = 15;

    private final NoteService noteService;
    private final TagService tagService;
    private final GraphAnalysisService graphAnalysis;

    public KnowledgeInsightsService(NoteService noteService, TagService tagService) {
        this.noteService = noteService;
        this.tagService = tagService;
        this.graphAnalysis = new GraphAnalysisService(noteService, tagService);
    }

    /** Computes the full insights report for the current vault/database. */
    public KnowledgeHealthReport generateReport() {
        GraphAnalysisService.GraphAnalysis analysis = graphAnalysis.analyze();

        List<KnowledgeHealthReport.NoteRef> untagged = notesWithoutTags();
        List<KnowledgeHealthReport.TagUsage> tagUsage = tagUsage();
        int totalTags = safeTagCount();

        List<NoteConnectivityInfo> mostConnected = analysis.connectivity().stream()
                .filter(c -> c.total() > 0)
                .limit(TOP_CONNECTED)
                .toList();

        int totalNotes = analysis.totalNotes();
        int totalLinks = analysis.totalLinks();
        double avg = totalNotes > 0 ? (double) totalLinks / totalNotes : 0.0;

        Health health = healthScore(totalNotes, analysis.orphans().size(),
                untagged.size(), analysis.brokenLinks().size());

        return new KnowledgeHealthReport(
                totalNotes, totalLinks, totalLinks, totalTags, avg,
                health.score(), health.orphanPenalty(), health.untaggedPenalty(), health.brokenPenalty(),
                mostConnected, analysis.orphans(), analysis.brokenLinks(), untagged, tagUsage);
    }

    /**
     * Intentionally simple, explainable graph-health score in {@code [0, 100]}. This is
     * <strong>orientation, not an absolute metric</strong>: it only flags the three
     * things that most often erode a knowledge base.
     *
     * <pre>
     *   start at 100
     *   − up to 40, proportional to the share of orphan notes
     *   − up to 20, proportional to the share of untagged notes
     *   − 5 per broken link, capped at 25
     *   clamp to [0, 100]
     * </pre>
     *
     * An empty vault scores 100 (nothing is wrong yet).
     */
    public static Health healthScore(int totalNotes, int orphans, int untagged, int brokenLinks) {
        if (totalNotes <= 0) {
            return new Health(100, 0, 0, 0);
        }
        double orphanRatio = (double) orphans / totalNotes;
        double untaggedRatio = (double) untagged / totalNotes;
        int orphanPenalty = (int) Math.round(40 * orphanRatio);
        int untaggedPenalty = (int) Math.round(20 * untaggedRatio);
        int brokenPenalty = Math.min(25, Math.max(0, brokenLinks) * 5);
        int score = 100 - orphanPenalty - untaggedPenalty - brokenPenalty;
        score = Math.max(0, Math.min(100, score));
        return new Health(score, orphanPenalty, untaggedPenalty, brokenPenalty);
    }

    /** Health score plus the individual penalties, so the UI can explain it. */
    public record Health(int score, int orphanPenalty, int untaggedPenalty, int brokenPenalty) {
    }

    // ── internals ───────────────────────────────────────────────────────────────

    private List<KnowledgeHealthReport.NoteRef> notesWithoutTags() {
        List<KnowledgeHealthReport.NoteRef> result = new ArrayList<>();
        for (Note note : noteService.getAllNotes()) {
            if (note == null || note.getId() == null) {
                continue;
            }
            try {
                if (noteService.getNoteTags(note).isEmpty()) {
                    result.add(new KnowledgeHealthReport.NoteRef(note.getId(), titleOf(note)));
                }
            } catch (Exception e) {
                logger.fine("Insights: could not read tags for " + note.getId() + ": " + e.getMessage());
            }
        }
        return result;
    }

    private List<KnowledgeHealthReport.TagUsage> tagUsage() {
        List<KnowledgeHealthReport.TagUsage> usage = new ArrayList<>();
        try {
            for (Tag tag : tagService.getMostUsedTags(TOP_TAGS)) {
                if (tag != null && tag.getTitle() != null) {
                    usage.add(new KnowledgeHealthReport.TagUsage(tag.getTitle(), tagService.getNoteCountForTag(tag)));
                }
            }
        } catch (Exception e) {
            logger.fine("Insights: could not compute tag usage: " + e.getMessage());
        }
        return usage;
    }

    private int safeTagCount() {
        try {
            return tagService.getTagCount();
        } catch (Exception e) {
            return 0;
        }
    }

    private static String titleOf(Note note) {
        String title = note.getTitle();
        return (title != null && !title.isBlank()) ? title : "(untitled)";
    }
}
