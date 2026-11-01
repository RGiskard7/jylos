package com.example.jylos.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Snapshot of a note's backing file, taken when the note is opened or saved, so a later
 * save can tell whether something else (another app, a sync client, {@code git pull})
 * changed the file in between. The content hash decides, not the timestamps, because
 * sync clients often touch files without changing them.
 *
 * @param lastModifiedMillis file modification time
 * @param size file size in bytes
 * @param sha256 hex SHA-256 of the file content
 */
public record FileFingerprint(long lastModifiedMillis, long size, String sha256) {

    /** Fingerprint of {@code path}, or empty when it is not a readable regular file. */
    public static Optional<FileFingerprint> of(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            long modified = Files.getLastModifiedTime(path).toMillis();
            long size = Files.size(path);
            return Optional.of(new FileFingerprint(modified, size, sha256(path)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Whether both snapshots saw exactly the same bytes, regardless of timestamps. */
    public boolean sameContentAs(FileFingerprint other) {
        return other != null && size == other.size && sha256.equals(other.sha256);
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Every JDK's default provider is required to support SHA-256.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
