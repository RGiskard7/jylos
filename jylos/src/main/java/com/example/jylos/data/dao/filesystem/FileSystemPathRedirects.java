package com.example.jylos.data.dao.filesystem;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Where paths went when folders were renamed or moved during this session, shared by a vault's
 * folder and note DAOs.
 *
 * <p>In a folder vault a folder or note is known by its path, and that path changes when a
 * folder above it is renamed or moved. What was read before (a row of the Explorer, the note
 * open in the editor) still holds the old path for a while; when such a path is no longer on
 * disk, {@link #current} says where it is now, so renaming that folder or saving that note
 * reaches the right place instead of doing nothing or writing a copy under the old folder.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
final class FileSystemPathRedirects {

    /** Enough for a long session; the oldest changes are dropped first. */
    private static final int LIMIT = 1000;

    private final List<String[]> changes = new ArrayList<>();

    /** The folder at {@code from} is now at {@code to} (both relative to the vault). */
    synchronized void record(String from, String to) {
        if (from == null || to == null) {
            return;
        }
        String source = FileSystemNames.normalizeId(from);
        String target = FileSystemNames.normalizeId(to);
        if (source.isEmpty() || source.equals(target)) {
            return;
        }
        changes.add(new String[] {source, target});
        if (changes.size() > LIMIT) {
            changes.remove(0);
        }
    }

    /** Where {@code id} is now, after every change recorded since it was read, in order. */
    synchronized String current(String id) {
        if (id == null) {
            return null;
        }
        String result = FileSystemNames.normalizeId(id);
        for (String[] change : changes) {
            if (result.equals(change[0])) {
                result = change[1];
            } else if (result.startsWith(change[0] + "/")) {
                result = change[1] + result.substring(change[0].length());
            }
        }
        return result;
    }

    /**
     * The id of the file or folder {@code id} under {@code root}: the same if it is there, else
     * where it went; if neither exists, the same.
     */
    String locateId(Path root, String id) {
        String normalized = FileSystemNames.normalizeId(id);
        if (Files.exists(root.resolve(normalized.replace("/", File.separator)))) {
            return normalized;
        }
        String moved = current(normalized);
        return Files.exists(root.resolve(moved.replace("/", File.separator))) ? moved : normalized;
    }

    /** The file or folder {@code id} under {@code root}, as {@link #locateId} finds it. */
    Path locate(Path root, String id) {
        return root.resolve(locateId(root, id).replace("/", File.separator));
    }
}
