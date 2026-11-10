package org.bitwisemadness.abilityiss.spell;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.MasteryDeclarations;
import org.bitwisemadness.abilitycore.api.MasteryGainHandlers;
import org.bitwisemadness.abilitycore.api.MasterySpec;
import org.bitwisemadness.abilitycore.api.MasteryTarget;
import org.bitwisemadness.abilityiss.config.IssServerConfig;

/**
 * The mastery contract for ISS's spells - ability-iss's REQ-CORE-12
 * consumer slice: one declaration per live registry entry, riding the
 * same ISS spell-registry event as {@link SpellCatalog} so the id sets
 * always line up (a spell that is a menu ability is a mastery
 * participant, and nothing else is).
 *
 * <p>Per cast a spell grants the configured base gain - through a gain
 * handler that reads {@code mastery.spellXpGain} <b>live</b> at award
 * time (IssServerConfig's read-at-use-time rule; a config reload applies
 * to the very next cast) - and routes {@link #SCHOOL_SHARE} of it to its
 * school's category mastery. Packs retune the share, the gain shape or
 * any effect through core's mastery_spec datapack overlay (core#138);
 * the server config owns the number.</p>
 *
 * <p>The COST effect axis is deliberately <b>not</b> declared: ISS's
 * mana cost reads through the player-less {@code AbstractSpell
 * .getManaCost(int)}, so core's per-caster discount has no seam to
 * apply through. Issue #28 carries that bytecode evidence and
 * {@code getEffectiveCastTime(int, LivingEntity)} - which does take the
 * caster - as the cast-time candidate.</p>
 */
public final class SpellMastery {

    /** Every grant's share routed to the spell's school category. */
    public static final double SCHOOL_SHARE = 0.5;

    /** The gain handler's name (registered once; resolves at first award). */
    public static final String GAIN_HANDLER = "ability_iss:spell_xp";

    private SpellMastery() {
    }

    /**
     * Declares every live spell's spec - called at ISS's spell-registry
     * event beside {@link SpellCatalog#register}. Idempotent: a re-fired
     * event (or a late-registered spell) declares only what is missing,
     * and an unparseable id is skipped rather than invented.
     */
    public static void declareAll() {
        for (AbstractSpell spell : SpellRegistry.REGISTRY.stream().toList()) {
            ResourceLocation spellId = SpellRegistry.REGISTRY.getKey(spell);
            if (spellId == null || MasteryDeclarations.has(spellId)) {
                continue;
            }
            MasterySpec spec = MasterySpec.gained(0.0).withGainHandler(GAIN_HANDLER);
            ResourceLocation school = SpellCatalog.schoolOf(spell);
            if (school != null) {
                spec = spec.withShare(MasteryTarget.category(school), SCHOOL_SHARE);
            }
            MasteryDeclarations.declare(spellId, spec);
        }
    }

    /**
     * Registers the config-live gain handler - called at common setup
     * (before any cast can award; names resolve at first use, so the
     * order against {@link #declareAll} is free).
     */
    public static void registerGainHandler() {
        if (MasteryGainHandlers.has(GAIN_HANDLER)) {
            return;
        }
        MasteryGainHandlers.declare(GAIN_HANDLER,
                (player, source, trigger) -> IssServerConfig.SPELL_XP_GAIN.get());
    }
}
