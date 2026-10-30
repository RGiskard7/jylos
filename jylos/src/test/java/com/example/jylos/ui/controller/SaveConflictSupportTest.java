package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.jylos.service.FileFingerprint;

/**
 * When saving would overwrite a file that changed on disk since it was opened, the save
 * must not silently go through: a manual save asks the user, an autosave pauses.
 */
class SaveConflictSupportTest {

    private static final FileFingerprint OPENED = new FileFingerprint(1_000L, 10L, "aaa");

    @Test
    void unchangedFileProceeds() {
        assertEquals(SaveConflictSupport.Decision.PROCEED,
                SaveConflictSupport.decide(OPENED, Optional.of(new FileFingerprint(1_000L, 10L, "aaa")), false));
    }

    @Test
    void touchedButIdenticalFileProceeds() {
        assertEquals(SaveConflictSupport.Decision.PROCEED,
                SaveConflictSupport.decide(OPENED, Optional.of(new FileFingerprint(9_999L, 10L, "aaa")), true));
    }

    @Test
    void externallyEditedFileAsksOnManualSave() {
        assertEquals(SaveConflictSupport.Decision.CONFLICT_PROMPT,
                SaveConflictSupport.decide(OPENED, Optional.of(new FileFingerprint(2_000L, 12L, "bbb")), false));
    }

    @Test
    void externallyEditedFilePausesAutosave() {
        assertEquals(SaveConflictSupport.Decision.CONFLICT_PAUSE,
                SaveConflictSupport.decide(OPENED, Optional.of(new FileFingerprint(2_000L, 12L, "bbb")), true));
    }

    @Test
    void sameTimestampAndSizeButDifferentContentIsStillAConflict() {
        assertEquals(SaveConflictSupport.Decision.CONFLICT_PROMPT,
                SaveConflictSupport.decide(OPENED, Optional.of(new FileFingerprint(1_000L, 10L, "ccc")), false));
    }

    @Test
    void noBaselineProceeds() {
        // SQLite notes and anything opened without a backing file have nothing to compare.
        assertEquals(SaveConflictSupport.Decision.PROCEED,
                SaveConflictSupport.decide(null, Optional.of(new FileFingerprint(2_000L, 12L, "bbb")), false));
    }

    @Test
    void fileDeletedOnDiskProceedsAndRecreatesIt() {
        // Nothing on disk to lose: saving puts the editor's text back.
        assertEquals(SaveConflictSupport.Decision.PROCEED,
                SaveConflictSupport.decide(OPENED, Optional.empty(), true));
    }
}
