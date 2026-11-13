package org.bitwisemadness.abilityiss.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.sync.MasteryQueryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The client-branch unlock cache (owner's cast-to-unlock brief): the
 * arrived snapshot answers for ITS OWN target player only, an absent
 * ability reads as never-earned (the snapshot is complete server
 * truth), non-ABILITY pools never unlock, and no snapshot yet reads as
 * unknown - the never-guess rule.
 */
class SchoolMasteryUnlocksTest {

    private static final ResourceLocation MASTERY =
            ResourceLocation.fromNamespaceAndPath("ability_iss", "mastery_blood");
    private static final ResourceLocation OTHER =
            ResourceLocation.fromNamespaceAndPath("ability_iss", "mastery_fire");

    @BeforeEach
    void clearSnapshot() {
        SchoolMasteryUnlocks.reset();
    }

    private static MasteryQueryResponse abilitySnapshot(
            String target, ResourceLocation id, double xp) {
        return new MasteryQueryResponse(target, List.of(
                new MasteryQueryResponse.Entry(
                        "ABILITY", id.toString(), xp, 1, 0.5, List.of())));
    }

    @Test
    void noSnapshotYetIsUnknownNeverUnlearned() {
        assertTrue(SchoolMasteryUnlocks.cachedLearned(
                        UUID.randomUUID().toString(), MASTERY).isEmpty(),
                "before any exchange the client must never guess");
        assertTrue(SchoolMasteryUnlocks.learnedFor(null, MASTERY).isEmpty(),
                "a null player abstains");
    }

    @Test
    void snapshotAnswersForItsOwnTargetOnly() {
        String target = UUID.randomUUID().toString();
        SchoolMasteryUnlocks.accept(abilitySnapshot(target, MASTERY, 5.0));
        assertEquals(Optional.of(true),
                SchoolMasteryUnlocks.cachedLearned(target, MASTERY));
        assertTrue(SchoolMasteryUnlocks.cachedLearned(
                        UUID.randomUUID().toString(), MASTERY).isEmpty(),
                "a reply for another player never answers ours");
    }

    @Test
    void absentAbilityInAnArrivedSnapshotIsNeverEarned() {
        String target = UUID.randomUUID().toString();
        SchoolMasteryUnlocks.accept(abilitySnapshot(target, MASTERY, 5.0));
        assertEquals(Optional.of(false),
                SchoolMasteryUnlocks.cachedLearned(target, OTHER),
                "the snapshot is complete server truth: no entry = no XP");
    }

    @Test
    void categoryPoolsNeverUnlock() {
        String target = UUID.randomUUID().toString();
        SchoolMasteryUnlocks.accept(new MasteryQueryResponse(target, List.of(
                new MasteryQueryResponse.Entry(
                        "CATEGORY", "ability_iss:mastery", 99.0, 5, 0.5, List.of()))));
        assertEquals(Optional.of(false),
                SchoolMasteryUnlocks.cachedLearned(target, MASTERY),
                "only ABILITY entries carry the unlock verdict");
    }

    @Test
    void zeroXpSnapshotEntryIsNotLearned() {
        String target = UUID.randomUUID().toString();
        SchoolMasteryUnlocks.accept(abilitySnapshot(target, MASTERY, 0.0));
        assertEquals(Optional.of(false),
                SchoolMasteryUnlocks.cachedLearned(target, MASTERY),
                "a stored zero is still not unlocked");
    }
}
