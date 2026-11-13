package org.bitwisemadness.abilityiss.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * The client-branch unlock verdict (owner's cast-to-unlock brief): an
 * answered store is complete server truth (an absent ability and a
 * stored zero both read never-earned), an unanswered store reads
 * unknown - the never-guess rule. The store itself and its fence live
 * in hotbar's CoreMasteryCompat, covered by hotbar's own tests.
 */
class SchoolMasteryUnlocksTest {

    private static final ResourceLocation MASTERY =
            ResourceLocation.fromNamespaceAndPath("ability_iss", "mastery_blood");

    @Test
    void unansweredStoreIsNeverGuessed() {
        assertTrue(SchoolMasteryUnlocks.learnedFrom(false, 5.0).isEmpty(),
                "before any exchange the client must never guess");
        assertTrue(SchoolMasteryUnlocks.learnedFor(null, MASTERY).isEmpty(),
                "a null player abstains");
    }

    @Test
    void answeredStoreWithXpIsLearned() {
        assertEquals(Optional.of(true), SchoolMasteryUnlocks.learnedFrom(true, 5.0),
                "a routed school share is the permanence proof");
    }

    @Test
    void answeredStoreWithoutXpIsNeverEarned() {
        assertEquals(Optional.of(false), SchoolMasteryUnlocks.learnedFrom(true, 0.0),
                "an absent ability (no entry) and a stored zero both read never-earned");
    }
}
