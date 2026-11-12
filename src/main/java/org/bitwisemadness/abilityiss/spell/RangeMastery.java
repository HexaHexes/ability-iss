package org.bitwisemadness.abilityiss.spell;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.bitwisemadness.abilitycore.api.MasteryAxis;
import org.bitwisemadness.abilitycore.api.MasteryEffects;
import org.bitwisemadness.abilityhotbar.compat.CoreMasteryCompat;
import org.jetbrains.annotations.Nullable;

/**
 * The RANGE seam's read (owner's range brief): ISS's per-spell
 * {@code getRange} is where a ray/beam's reach is decided, and every one
 * of the eleven registered seam spells funnels its reads through here
 * so the caster's RANGE mastery multiplier lands exactly once - the
 * cast's ray, the tooltip's number and the pre-cast reach check all
 * move together.
 *
 * <p>The server's live answer comes from {@link MasteryEffects} for a
 * {@link ServerPlayer} (gameplay truth: a mob or non-player caster has
 * no mastery and reads raw). The client tooltip path - a viewer who is
 * not a ServerPlayer - reads hotbar's answered query snapshot instead,
 * which is empty until the menu has been opened once, so an unread
 * tooltip shows the raw number rather than a guess. A spell whose spec
 * declares no RANGE axis answers 1.0 from the same reads, so removing
 * the axis through a datapack overlay makes these mixins inert.</p>
 */
public final class RangeMastery {

    /** Registered instance lookup for the static-seam classes (cached). */
    private static final Map<Class<? extends AbstractSpell>, ResourceLocation> IDS =
            new ConcurrentHashMap<>();

    private RangeMastery() {
    }

    /** The boosted range for an instance-context seam (the spell is {@code this}). */
    public static float boosted(float range, AbstractSpell spell, LivingEntity caster) {
        return apply(range, SpellRegistry.REGISTRY.getKey(spell), caster);
    }

    /** The boosted range for a static-seam class (resolved once, cached). */
    public static float boosted(float range, Class<? extends AbstractSpell> spellType,
            LivingEntity caster) {
        return apply(range, idOf(spellType), caster);
    }

    /** The boosted range for the int-returning seams, rounded like their native math. */
    public static int boostedInt(int range, AbstractSpell spell, LivingEntity caster) {
        return applyInt(range, SpellRegistry.REGISTRY.getKey(spell), caster);
    }

    /** The int variant for a static-seam class (none ship one today; symmetric). */
    public static int boostedInt(int range, Class<? extends AbstractSpell> spellType,
            LivingEntity caster) {
        return applyInt(range, idOf(spellType), caster);
    }

    private static float apply(float range, @Nullable ResourceLocation id, LivingEntity caster) {
        double multiplier = multiplierFor(id, caster);
        return multiplier == 1.0 ? range : (float) (range * multiplier);
    }

    private static int applyInt(int range, @Nullable ResourceLocation id, LivingEntity caster) {
        double multiplier = multiplierFor(id, caster);
        return multiplier == 1.0 ? range : (int) Math.round(range * multiplier);
    }

    /** The multiplier one cast's range reads: server live, client snapshot, else raw. */
    private static double multiplierFor(@Nullable ResourceLocation id, LivingEntity caster) {
        if (id == null || caster == null) {
            return 1.0;
        }
        if (caster instanceof ServerPlayer player) {
            return MasteryEffects.rangeMultiplier(player, id);
        }
        // the local viewer's tooltip: hotbar's answered snapshot (empty
        // until the menu opens - raw is the honest fallback, never a guess)
        return CoreMasteryCompat.bonusesOf(id).stream()
                .filter(bonus -> MasteryAxis.RANGE.name().equals(bonus.axis()))
                .findFirst()
                .map(bonus -> bonus.multiplier())
                .orElse(1.0);
    }

    /**
     * The registered instance of one spell class - the static seams have
     * no {@code this} to resolve their id with, and ISS registers exactly
     * one instance per spell class.
     */
    private static ResourceLocation idOf(Class<? extends AbstractSpell> spellType) {
        return IDS.computeIfAbsent(spellType, type -> SpellRegistry.REGISTRY.stream()
                .filter(spell -> spell.getClass() == type)
                .map(spell -> SpellRegistry.REGISTRY.getKey(spell))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null));
    }
}
