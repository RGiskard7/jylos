package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.util.PathContainment;

class PathContainmentTest {

    @TempDir
    Path root;

    @Test
    void resolvesPlainRelativePathInsideRoot() {
        Optional<Path> resolved = PathContainment.resolveInside(root, "Folder/note.md");
        assertEquals(Optional.of(root.resolve("Folder").resolve("note.md").toAbsolutePath().normalize()), resolved);
    }

    @Test
    void acceptsDotDotThatStaysInsideRoot() {
        Optional<Path> resolved = PathContainment.resolveInside(root, "sub/../note.md");
        assertEquals(Optional.of(root.resolve("note.md").toAbsolutePath().normalize()), resolved);
    }

    @Test
    void rejectsDotDotEscapingRoot() {
        assertTrue(PathContainment.resolveInside(root, "../outside.md").isEmpty());
        assertTrue(PathContainment.resolveInside(root, "sub/../../outside.md").isEmpty());
    }

    @Test
    void rejectsBackslashTraversal() {
        assertTrue(PathContainment.resolveInside(root, "..\\outside.md").isEmpty());
    }

    @Test
    void rejectsAbsolutePaths() {
        String absolute = root.getParent().resolve("outside.md").toAbsolutePath().toString();
        assertTrue(PathContainment.resolveInside(root, absolute).isEmpty());
        assertTrue(PathContainment.resolveInside(root, "/etc/hosts").isEmpty());
        assertTrue(PathContainment.resolveInside(root, "C:/Windows/win.ini").isEmpty());
    }

    @Test
    void emptyOrNullRelativeResolvesToRootItself() {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        assertEquals(Optional.of(normalizedRoot), PathContainment.resolveInside(root, ""));
        assertEquals(Optional.of(normalizedRoot), PathContainment.resolveInside(root, null));
    }

    @Test
    void nullRootResolvesNothing() {
        assertTrue(PathContainment.resolveInside(null, "note.md").isEmpty());
    }

    @Test
    void isInsideIsTrueForDescendantsAndRootAndFalseForSiblings() {
        assertTrue(PathContainment.isInside(root, root.resolve("a/b.md")));
        assertTrue(PathContainment.isInside(root, root));
        assertFalse(PathContainment.isInside(root, root.resolve("../sibling/b.md")));
        // A sibling whose name merely starts with the root's name is still outside.
        assertFalse(PathContainment.isInside(root, root.resolveSibling(root.getFileName() + "-other").resolve("x.md")));
        assertFalse(PathContainment.isInside(root, null));
        assertFalse(PathContainment.isInside(null, root));
    }
}
