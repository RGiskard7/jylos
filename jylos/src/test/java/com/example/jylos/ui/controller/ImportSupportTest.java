package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A folder is not imported into the vault it belongs to, nor into one inside it: its notes are
 * there already, or the import would copy the vault into itself.
 */
class ImportSupportTest {

    @Test
    void theOpenVaultItselfOverlaps(@TempDir Path root) throws Exception {
        Path vault = Files.createDirectories(root.resolve("vault"));

        assertTrue(ImportSupport.overlaps(vault, vault));
        assertTrue(ImportSupport.overlaps(vault, vault.resolve("sub/..")), "the same folder, written another way");
    }

    @Test
    void aFolderInsideTheOpenVaultOverlaps(@TempDir Path root) throws Exception {
        Path vault = Files.createDirectories(root.resolve("vault"));
        Path inside = Files.createDirectories(vault.resolve("projects/inner"));

        assertTrue(ImportSupport.overlaps(vault, inside));
    }

    @Test
    void aFolderHoldingTheOpenVaultOverlaps(@TempDir Path root) throws Exception {
        Path vault = Files.createDirectories(root.resolve("all/vault"));

        assertTrue(ImportSupport.overlaps(vault, root.resolve("all")));
    }

    @Test
    void aLinkToTheOpenVaultOverlaps(@TempDir Path root) throws Exception {
        Path vault = Files.createDirectories(root.resolve("vault"));
        Path link = Files.createSymbolicLink(root.resolve("link"), vault);

        assertTrue(ImportSupport.overlaps(vault, link));
    }

    @Test
    void aFolderBesideTheOpenVaultDoesNotOverlap(@TempDir Path root) throws Exception {
        Path vault = Files.createDirectories(root.resolve("vault"));
        Path beside = Files.createDirectories(root.resolve("vault-2"));
        Path other = Files.createDirectories(root.resolve("obsidian/notes"));

        assertFalse(ImportSupport.overlaps(vault, beside), "a name that starts like the vault's is another folder");
        assertFalse(ImportSupport.overlaps(vault, other));
    }
}
