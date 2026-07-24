package com.example.jylos.data.dao.filesystem;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Writes text files through a same-directory temporary file before replacing
 * the target. This prevents truncate-first writes from corrupting vault files.
 */
final class FileSystemAtomicWriter {

    private FileSystemAtomicWriter() {
    }

    static void writeString(Path target, String content, Charset charset) throws IOException {
        if (target == null) {
            throw new IllegalArgumentException("Target path cannot be null");
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path directory = parent != null ? parent : target.toAbsolutePath().getParent();
        Path temp = Files.createTempFile(directory, target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, content != null ? content : "", charset,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
