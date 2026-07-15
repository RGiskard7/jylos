package com.example.jylos.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.example.jylos.config.LoggerConfig;

/**
 * Local, per-note version history: before a note's stored content is overwritten,
 * the previous content is written as a snapshot file under
 * {@code history/<note-key>/<timestamp>.md} in the app data directory.
 *
 * <p>Design decisions:</p>
 * <ul>
 *   <li><b>Stored content, verbatim.</b> Snapshots capture exactly what was persisted
 *       — for private notes that is the {@code JENC1:} ciphertext, so history never
 *       leaks plaintext of encrypted notes to disk.</li>
 *   <li><b>Coalescing window.</b> Saves within {@link #minIntervalMs} of the latest
 *       snapshot don't create a new one (autosave fires every couple of seconds;
 *       without this the history would be noise).</li>
 *   <li><b>Bounded.</b> At most {@link #maxSnapshotsPerNote} snapshots per note;
 *       oldest are pruned.</li>
 *   <li><b>Fail-safe.</b> History is best effort: any I/O error is logged and never
 *       blocks the save itself.</li>
 * </ul>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
public class NoteHistoryService {

    private static final Logger logger = LoggerConfig.getLogger(NoteHistoryService.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    /** A single snapshot: when it was taken and where it lives. */
    public record Snapshot(Instant timestamp, Path file) {

        /** Human-readable local timestamp for UI lists. */
        public String displayTime() {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .format(LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault()));
        }
    }

    private final Path historyRoot;
    private final int maxSnapshotsPerNote;
    private final long minIntervalMs;
    private final Clock clock;

    /** Production defaults: 50 snapshots per note, 60s coalescing window. */
    public NoteHistoryService(Path historyRoot) {
        this(historyRoot, 50, 60_000);
    }

    /** Fully parameterised (used by tests with {@code minIntervalMs = 0}). */
    public NoteHistoryService(Path historyRoot, int maxSnapshotsPerNote, long minIntervalMs) {
        this(historyRoot, maxSnapshotsPerNote, minIntervalMs, Clock.systemDefaultZone());
    }

    /** Fully deterministic constructor for tests that need stable timestamps. */
    NoteHistoryService(Path historyRoot, int maxSnapshotsPerNote, long minIntervalMs, Clock clock) {
        this.historyRoot = historyRoot;
        this.maxSnapshotsPerNote = maxSnapshotsPerNote;
        this.minIntervalMs = minIntervalMs;
        this.clock = clock;
    }

    /**
     * Records {@code previousContent} as a snapshot for {@code noteId}, unless it is
     * null/blank-equal to the latest snapshot or falls inside the coalescing window.
     * Never throws.
     */
    public void snapshot(String noteId, String previousContent) {
        if (noteId == null || noteId.isBlank() || previousContent == null) {
            return;
        }
        try {
            Path dir = noteDir(noteId);
            Files.createDirectories(dir);

            List<Snapshot> existing = list(noteId);
            if (!existing.isEmpty()) {
                Snapshot latest = existing.get(0);
                long age = clock.millis() - latest.timestamp().toEpochMilli();
                if (age < minIntervalMs) {
                    return; // coalesce rapid autosaves
                }
                String latestContent = Files.readString(latest.file(), StandardCharsets.UTF_8);
                if (latestContent.equals(previousContent)) {
                    return; // nothing new to record
                }
            }

            String stamp = STAMP.format(LocalDateTime.now(clock));
            Files.writeString(dir.resolve(stamp + ".md"), previousContent, StandardCharsets.UTF_8);
            prune(dir);
        } catch (Exception e) {
            logger.warning("History snapshot failed for " + noteId + ": " + e.getMessage());
        }
    }

    /** Returns the snapshots for a note, newest first. Never throws. */
    public List<Snapshot> list(String noteId) {
        Path dir = noteDir(noteId);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            List<Snapshot> snapshots = new ArrayList<>();
            files.filter(p -> p.getFileName().toString().endsWith(".md")).forEach(p -> {
                try {
                    String name = p.getFileName().toString();
                    LocalDateTime time = LocalDateTime.parse(
                            name.substring(0, name.length() - 3), STAMP);
                    snapshots.add(new Snapshot(time.atZone(ZoneId.systemDefault()).toInstant(), p));
                } catch (Exception ignored) {
                    // foreign file in the history dir — skip
                }
            });
            snapshots.sort(Comparator.comparing(Snapshot::timestamp).reversed());
            return snapshots;
        } catch (IOException e) {
            logger.warning("History list failed for " + noteId + ": " + e.getMessage());
            return List.of();
        }
    }

    /** Reads a snapshot's content. */
    public String read(Snapshot snapshot) throws IOException {
        return Files.readString(snapshot.file(), StandardCharsets.UTF_8);
    }

    private void prune(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> ordered = files
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (int i = 0; i < ordered.size() - maxSnapshotsPerNote; i++) {
                Files.deleteIfExists(ordered.get(i));
            }
        }
    }

    /** Maps a note id (may contain path separators in vault mode) to a safe dir name. */
    private Path noteDir(String noteId) {
        String safe = noteId.replaceAll("[^a-zA-Z0-9._-]", "_");
        // Guard against collisions after sanitising (e.g. "a/b" vs "a_b"): suffix a hash.
        safe = safe + "-" + Integer.toHexString(noteId.hashCode());
        return historyRoot.resolve(safe);
    }
}
