package org.bitwisemadness.abilityiss.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The school mastery's flourished description (owner's brief): the
 * school name is prettified from the path - deterministic and
 * locale-free - and the prose always carries spaces, so hotbar's pane
 * renders it literally instead of chasing a translation key.
 */
class SchoolMasteryDescriptionTest {

    @Test
    void prettifyCapitalizesEachUnderscoreSegment() {
        assertEquals("Blood", SchoolMastery.prettifySchool("blood"));
        assertEquals("Eldritch", SchoolMastery.prettifySchool("eldritch"));
        assertEquals("Dark Blood", SchoolMastery.prettifySchool("dark_blood"));
        assertEquals("", SchoolMastery.prettifySchool(""));
    }

    @Test
    void descriptionNamesTheSchoolAndStaysProse() {
        String description = SchoolMastery.descriptionFor("blood");
        assertTrue(description.contains("Blood"),
                "the school is named: " + description);
        assertTrue(description.contains(" "),
                "spaces keep the pane off the translation-key path");
        assertFalse(description.matches("[a-z0-9_]+\\.[a-z0-9_.]+"),
                "key-shaped prose would be swallowed as a translation key");
        // deterministic: the same path always yields the same bytes
        assertEquals(description, SchoolMastery.descriptionFor("blood"));
    }
}
