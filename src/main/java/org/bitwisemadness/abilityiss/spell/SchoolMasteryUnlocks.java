package org.bitwisemadness.abilityiss.spell;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.bitwisemadness.abilitycore.api.AbilityUnlocks;
import org.bitwisemadness.abilitycore.api.MasteryTarget;
import org.bitwisemadness.abilitycore.mastery.MasteryAttachments;
import org.bitwisemadness.abilityhotbar.compat.CoreMasteryCompat;
import org.jetbrains.annotations.Nullable;

/**
 * Cast-to-unlock for the school masteries (owner's brief): a school
 * mastery is <b>permanently unlocked by casting any spell of that
 * school</b>. The routed share of every school cast lands in the school
 * ability's own XP pool, and XP only ever grows - so "pool &gt; 0" is
 * both the unlock test and the permanence proof.
 *
 * <p>Two branches, one provider ({@link AbilityUnlocks.UnlockProvider}):
 * the SERVER reads the asking player's own attachment - authoritative,
 * always answerable, {@code false} before the first school cast (the
 * row greys, assignment refuses, the reconciler clears a stale slot).
 * The CLIENT cannot read attachments, so it answers from hotbar's
 * stored copy of the answered mastery snapshot - the same exchange the
 * menu's mastery badges read, fenced <i>there</i> to its own request,
 * so this class keeps no snapshot of its own. No answer yet reads as
 * <b>unknown</b> (empty), never a guessed verdict: the never-guess
 * rule.</p>
 *
 * <p>Declared once at mod setup beside the other declare registries
 * ({@link #register}). Core is a hard dependency of this mod, so no
 * {@code isLoaded} guard is needed.</p>
 */
public final class SchoolMasteryUnlocks {

    private SchoolMasteryUnlocks() {
    }

    /**
     * Declares every school ability's unlock provider (mod setup, after
     * the register event populated {@link SchoolMastery#registered()}).
     */
    public static void register() {
        for (ResourceLocation id : SchoolMastery.registered()) {
            AbilityUnlocks.declare(id, SchoolMasteryUnlocks::learnedFor);
        }
    }

    /**
     * The unlock answer for one school ability: server truth from the
     * attachment, client truth from hotbar's stored snapshot, unknown
     * when neither can answer.
     *
     * @param player    the asking player (null abstains - tests)
     * @param abilityId the school ability being asked about
     * @return learned / not learned, or empty when unknown
     */
    public static Optional<Boolean> learnedFor(
            @Nullable Player player, ResourceLocation abilityId) {
        if (player == null) {
            return Optional.empty();
        }
        if (player instanceof ServerPlayer serverPlayer) {
            double xp = MasteryAttachments.data(serverPlayer)
                    .xp(MasteryTarget.ability(abilityId).key());
            return Optional.of(xp > 0.0);
        }
        return learnedFrom(CoreMasteryCompat.answered(), CoreMasteryCompat.xpOf(abilityId));
    }

    /**
     * The client branch's verdict from store facts: an answered store
     * is complete server truth (an absent ability carries 0 XP and
     * reads never-earned), an unanswered one reads unknown - the
     * never-guess rule. Headless-testable; the fence around the store
     * itself lives in hotbar, whose own tests cover it (a reply for
     * another player is dropped there).
     *
     * @param answered whether hotbar's snapshot has arrived
     * @param xp       the stored own-pool XP hotbar reports for the id
     * @return learned / not learned, or empty when unknown
     */
    static Optional<Boolean> learnedFrom(boolean answered, double xp) {
        return answered ? Optional.of(xp > 0.0) : Optional.empty();
    }
}
