package org.bitwisemadness.abilityiss.spell;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import org.bitwisemadness.abilitycore.api.AbilityUnlocks;
import org.bitwisemadness.abilitycore.api.MasteryTarget;
import org.bitwisemadness.abilitycore.mastery.MasteryAttachments;
import org.bitwisemadness.abilitycore.sync.MasteryQueryAnsweredEvent;
import org.bitwisemadness.abilitycore.sync.MasteryQueryResponse;
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
 * The CLIENT cannot read attachments, so it answers from the last
 * {@link MasteryQueryAnsweredEvent} snapshot - the same exchange the
 * menu's mastery badges read - fenced to that snapshot's own target
 * player. No snapshot for THIS player yet reads as <b>unknown</b>
 * (empty), never a guessed verdict: the never-guess rule.</p>
 *
 * <p>Declared once at mod setup beside the other declare registries
 * ({@link #register}); the listener rides NeoForge's game bus exactly
 * like hotbar's own snapshot receiver. Core is a hard dependency of
 * this mod, so no {@code isLoaded} guard is needed.</p>
 */
public final class SchoolMasteryUnlocks {

    /** The last snapshot's echoed target (the queried player's UUID). */
    @Nullable
    private static String snapshotTarget;

    /** That snapshot's {@code ABILITY} XP by id string; null = none yet. */
    @Nullable
    private static Map<String, Double> snapshotXp;

    private SchoolMasteryUnlocks() {
    }

    /**
     * Declares every school ability's unlock provider and subscribes the
     * snapshot cache (mod setup, after the register event populated
     * {@link SchoolMastery#registered()}).
     */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(SchoolMasteryUnlocks::onAnswered);
        for (ResourceLocation id : SchoolMastery.registered()) {
            AbilityUnlocks.declare(id, SchoolMasteryUnlocks::learnedFor);
        }
    }

    /**
     * The unlock answer for one school ability: server truth from the
     * attachment, client truth from the fenced snapshot, unknown when
     * neither can answer.
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
        return cachedLearned(player.getStringUUID(), abilityId);
    }

    /**
     * The client branch's verdict, headless-testable: the fenced
     * snapshot answers for ITS target player only - anyone else (or no
     * snapshot yet) reads as unknown, an absent ability reads as
     * never-earned (the snapshot is complete server truth).
     *
     * @param playerUuid the asking player's UUID string
     * @param abilityId  the school ability being asked about
     * @return learned / not learned, or empty when unknown
     */
    static Optional<Boolean> cachedLearned(String playerUuid, ResourceLocation abilityId) {
        String target = snapshotTarget;
        Map<String, Double> xpByAbility = snapshotXp;
        if (target == null || xpByAbility == null || !target.equals(playerUuid)) {
            return Optional.empty(); // no snapshot for THIS player - never guess
        }
        return Optional.of(xpByAbility.getOrDefault(abilityId.toString(), 0.0) > 0.0);
    }

    private static void onAnswered(MasteryQueryAnsweredEvent event) {
        accept(event.response());
    }

    /**
     * Accepts an arrived snapshot: {@code ABILITY} entries only (the
     * unlock verdict never rides category/subcategory pools), the echoed
     * target kept for the fence, the whole map swapped in one write.
     */
    static void accept(MasteryQueryResponse response) {
        Map<String, Double> xpByAbility = new HashMap<>();
        for (MasteryQueryResponse.Entry entry : response.entries()) {
            if ("ABILITY".equals(entry.kind())) {
                xpByAbility.put(entry.id(), entry.xp());
            }
        }
        snapshotTarget = response.targetPlayer();
        snapshotXp = Map.copyOf(xpByAbility);
    }

    /** Test seam: each test starts from no snapshot (JUnit has no order). */
    static void reset() {
        snapshotTarget = null;
        snapshotXp = null;
    }
}
