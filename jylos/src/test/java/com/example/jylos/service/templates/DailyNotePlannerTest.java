package com.example.jylos.service.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.VaultSettings.DailyNotes;
import com.example.jylos.data.models.VaultSettings.NewNoteLocation;
import com.example.jylos.data.models.VaultSettings.NewNotes;

/**
 * Today's daily note, as Obsidian's Daily notes finds or makes it: named with its date format,
 * in its folder or, without one, in the folder new notes go to when that is a folder of its
 * own, else in the vault's root; the same path in another case is the same note; and its
 * template, a path with or without {@code .md}, has to be there.
 */
class DailyNotePlannerTest {

    private static final ZonedDateTime TODAY = LocalDateTime.parse("2026-11-19T14:05:00").atZone(ZoneId.of("Europe/Madrid"));
    private static final Locale ES = Locale.forLanguageTag("es");
    private static final List<String> FOLDERS = List.of("Diario", "Plantillas", "Entrada");
    private static final List<String> NOTES = List.of("Plantillas/diaria.md", "Diario/18-11-2026.md",
            "Diario/19-11-2026.md", "Otra/2026-11-19.md", "Plantillas/daily.md");
    private static final NewNotes IN_VIEW = new NewNotes(NewNoteLocation.CURRENT, "");

    private static DailyNotePlanner.Plan plan(DailyNotes settings, NewNotes newNotes, String legacyTemplate) {
        return DailyNotePlanner.plan(settings, newNotes, TODAY, ES, FOLDERS, NOTES, legacyTemplate);
    }

    @Test
    void byDefaultItIsTodaysDateInTheVaultsRoot() {
        DailyNotePlanner.Plan plan = plan(new DailyNotes("", "", ""), IN_VIEW, null);

        assertEquals("2026-11-19", plan.name());
        assertEquals("2026-11-19.md", plan.path(), "not the folder in view: Obsidian's daily note has none");
        assertNull(plan.existing(), "a note of that name in another folder is another note");
        assertNull(plan.template());
        assertNull(plan.problem());
    }

    @Test
    void itsFolderAndFormatName() {
        DailyNotePlanner.Plan plan = plan(new DailyNotes("/Diario/", "DD-MM-YYYY", ""), IN_VIEW, null);

        assertEquals("Diario/19-11-2026.md", plan.path());
        assertEquals("Diario/19-11-2026.md", plan.existing(), "it is there: it is opened");
    }

    @Test
    void theSamePathInAnotherCaseIsTheSameNote() {
        DailyNotePlanner.Plan plan = plan(new DailyNotes("diario", "DD-MM-YYYY", ""), IN_VIEW, null);

        assertEquals(DailyNotePlanner.Problem.FOLDER_NOT_FOUND, plan.problem(), "the folder must be named exactly");
        DailyNotePlanner.Plan inCase = DailyNotePlanner.plan(new DailyNotes("Diario", "dd-MM-YYYY", ""), IN_VIEW,
                TODAY, ES, FOLDERS, List.of("Diario/JU-11-2026.md"), null);
        assertEquals("Diario/JU-11-2026.md", inCase.existing(), "the note is found in any case");
    }

    @Test
    void withoutAFolderItGoesWhereNewNotesGoWhenThatIsAFolder() {
        assertEquals("Entrada/2026-11-19.md",
                plan(new DailyNotes("", "", ""), new NewNotes(NewNoteLocation.FOLDER, "Entrada"), null).path());
        assertEquals("2026-11-19.md",
                plan(new DailyNotes("", "", ""), new NewNotes(NewNoteLocation.FOLDER, "No existe"), null).path(),
                "a folder that is not there: the root");
        assertEquals("2026-11-19.md",
                plan(new DailyNotes("", "", ""), new NewNotes(NewNoteLocation.ROOT, ""), null).path());
    }

    @Test
    void aFormatWithSlashesPutsItInFoldersUnderItsOwn() {
        DailyNotePlanner.Plan plan = plan(new DailyNotes("Diario", "YYYY/MM/DD", ""), IN_VIEW, null);

        assertEquals("2026/11/19", plan.name());
        assertEquals("Diario/2026/11/19.md", plan.path());
    }

    @Test
    void itsTemplateIsAPathWithOrWithoutMdThatHasToBeThere() {
        assertEquals("Plantillas/diaria.md",
                plan(new DailyNotes("", "", "Plantillas/diaria"), IN_VIEW, null).template());
        assertEquals("Plantillas/diaria.md",
                plan(new DailyNotes("", "", "Plantillas/diaria.md"), IN_VIEW, null).template());
        DailyNotePlanner.Plan missing = plan(new DailyNotes("", "", "Plantillas/no-está"), IN_VIEW, null);
        assertEquals(DailyNotePlanner.Problem.TEMPLATE_NOT_FOUND, missing.problem());
        assertEquals("Plantillas/no-está.md", missing.problemPath());
    }

    @Test
    void withoutATemplateChosenJylosUsesTheDailyOfTheTemplatesFolder() {
        assertEquals("Plantillas/daily.md",
                plan(new DailyNotes("", "", ""), IN_VIEW, "Plantillas/daily.md").template());
    }

    @Test
    void aFolderThatIsNotThereIsSaid() {
        DailyNotePlanner.Plan plan = plan(new DailyNotes("Diarios", "", ""), IN_VIEW, null);

        assertEquals(DailyNotePlanner.Problem.FOLDER_NOT_FOUND, plan.problem());
        assertEquals("Diarios", plan.problemPath());
    }
}
