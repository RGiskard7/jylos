package com.example.jylos.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Note;
import com.example.jylos.util.AttachmentType;

/**
 * The files a canvas's file nodes point to. A file node names a vault-relative path, as
 * Obsidian writes it ({@code Folder/Note.md}, {@code Assets/photo.png}).
 */
public final class CanvasReferences {

    private static final Logger logger = LoggerConfig.getLogger(CanvasReferences.class);

    private final NoteService notes;
    private final Path vaultRoot;

    /**
     * @param notes     the vault's notes
     * @param vaultRoot the vault's folder, or {@code null} when unknown
     */
    public CanvasReferences(NoteService notes, Path vaultRoot) {
        this.notes = notes;
        this.vaultRoot = vaultRoot;
    }

    /** The vault's folder, from a note's file and its vault-relative id. */
    public static Path vaultRootFor(Path notePath, String noteId) {
        Path root = notePath.getParent();
        String id = noteId == null ? "" : noteId.replace('\\', '/');
        long up = id.chars().filter(c -> c == '/').count();
        for (long i = 0; i < up && root != null; i++) {
            root = root.getParent();
        }
        return root;
    }

    /** The file a reference points to: directly under the vault root, else the note it names. */
    public Path resolve(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        String normalizedRef = ref.trim();
        if (vaultRoot != null) {
            try {
                Path p = vaultRoot.resolve(normalizedRef).normalize();
                if (Files.exists(p)) {
                    return p;
                }
            } catch (Exception ignored) {
                // fall through to the note-store lookup
            }
        }
        if (notes == null) {
            return null;
        }
        Path byId = notes.getNoteFilePath(normalizedRef).orElse(null);
        if (byId != null) {
            return byId;
        }
        return noteNamed(normalizedRef).flatMap(note -> notes.getNoteFilePath(note.getId())).orElse(null);
    }

    /**
     * The reference as stored in the canvas: vault-relative with {@code /}. {@code ""} for an
     * absolute path outside the vault, or a file with an extension that is nowhere.
     */
    public String canonical(String ref) {
        if (ref == null || ref.isBlank()) {
            return "";
        }
        String normalizedRef = ref.trim().replace('\\', '/');
        Path resolved = resolve(normalizedRef);
        if (resolved == null) {
            try {
                Path absolute = Path.of(normalizedRef).normalize();
                if (absolute.isAbsolute() && vaultRoot != null && absolute.startsWith(vaultRoot.normalize())) {
                    return vaultRoot.normalize().relativize(absolute).toString().replace('\\', '/');
                }
                if (absolute.isAbsolute()) {
                    return "";
                }
            } catch (Exception ignored) {
                // fall through to the note lookup
            }
            Optional<Note> note = noteNamed(normalizedRef);
            if (note.isPresent() && note.get().getId() != null && !note.get().getId().isBlank()) {
                return note.get().getId().replace('\\', '/');
            }
            return normalizedRef.contains("/") || AttachmentType.extensionOf(normalizedRef).isEmpty()
                    ? normalizedRef
                    : "";
        }
        if (vaultRoot != null) {
            try {
                return vaultRoot.relativize(resolved).toString().replace('\\', '/');
            } catch (Exception ignored) {
                // fall through to the note lookup
            }
        }
        Optional<Note> note = noteNamed(normalizedRef);
        if (note.isPresent() && note.get().getId() != null && !note.get().getId().isBlank()) {
            return note.get().getId().replace('\\', '/');
        }
        return normalizedRef;
    }

    /** The reference a canvas stores for the note {@code noteId} (dropped onto it, picked). */
    public String refOfNote(String noteId) {
        if (noteId == null || noteId.isBlank()) {
            return "";
        }
        if (notes != null) {
            Path path = notes.getNoteFilePath(noteId).orElse(null);
            if (path != null && vaultRoot != null) {
                try {
                    return vaultRoot.relativize(path).toString().replace('\\', '/');
                } catch (Exception ignored) {
                    // fall through to generic canonicalization
                }
            }
        }
        return canonical(noteId);
    }

    /**
     * The link "open note" follows for a file node: its path in the vault, so the note it
     * names opens and not another with the same name.
     */
    public String linkToOpen(String ref) {
        return canonical(ref);
    }

    /**
     * What a file node can point to: {@code titles} (the vault's notes) plus every attachment
     * of the vault (image, PDF, canvas) outside the trash, sorted case-insensitively.
     */
    public List<String> suggestions(List<String> titles) {
        LinkedHashSet<String> references = new LinkedHashSet<>();
        if (titles != null) {
            references.addAll(titles);
        }
        if (vaultRoot != null && Files.isDirectory(vaultRoot)) {
            try (Stream<Path> stream = Files.walk(vaultRoot)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> !path.startsWith(vaultRoot.resolve(".trash")))
                        .map(path -> vaultRoot.relativize(path).toString().replace('\\', '/'))
                        .filter(ref -> !ref.isBlank())
                        .filter(ref -> AttachmentType.fromName(ref) != AttachmentType.MARKDOWN)
                        .forEach(references::add);
            } catch (Exception e) {
                logger.fine("Could not enumerate canvas file references: " + e.getMessage());
            }
        }
        List<String> sorted = new ArrayList<>(references);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        return sorted;
    }

    /** The note a reference names, resolved as a link is (path, {@code .md}, then the rest). */
    private Optional<Note> noteNamed(String ref) {
        return notes != null ? notes.resolveLink(ref, null) : Optional.empty();
    }
}
