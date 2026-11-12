package org.bitwisemadness.abilityiss.spell;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.spells.blood.RayOfSiphoningSpell;
import io.redspace.ironsspellbooks.spells.eldritch.EldritchBlastSpell;
import io.redspace.ironsspellbooks.spells.eldritch.SonicBoomSpell;
import io.redspace.ironsspellbooks.spells.eldritch.TelekinesisSpell;
import io.redspace.ironsspellbooks.spells.evocation.FangSwirlSpell;
import io.redspace.ironsspellbooks.spells.evocation.FirecrackerSpell;
import io.redspace.ironsspellbooks.spells.evocation.GustSpell;
import io.redspace.ironsspellbooks.spells.evocation.ScapegoatSpell;
import io.redspace.ironsspellbooks.spells.ice.RayOfFrostSpell;
import io.redspace.ironsspellbooks.spells.lightning.ChainLightningSpell;
import io.redspace.ironsspellbooks.spells.nature.StompSpell;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.MasteryAxis;
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
 * <p><b>Mastery improves the spells</b> (the owner's directive, closing
 * #28): every spec declares COST, CAST_TIME and POWER through core's
 * default curve (server config: {@code mastery.costReductionPerLevel}
 * etc.). Each axis bites at its seam - COST at the check
 * ({@code canBeCastedBy}'s mana read), the charge
 * ({@code SpellOnCastEvent}, where ISS deducts) and the row quote
 * ({@link SpellCatalog#liveCostsFor}, rounding identically so all three
 * can never disagree); CAST_TIME on {@code getEffectiveCastTime}'s
 * return (the caster is a parameter, and the tooltip reads the same
 * method); POWER on {@code getSpellPower}'s return. RANGE joins for the
 * eleven registered {@code getRange} seam spells ({@link #RANGE_SEAM}
 * is the mixin coverage): every read of that number - the cast's ray,
 * the tooltip's number, the pre-cast reach check - returns through a
 * RangeMastery wrap with the caller's own entity. DURATION stays
 * undeclared: no caster-scoped duration seam exists yet (filed).
 * {@code docs/spell-inventory.md} categorises all 113 spells by
 * activation and effect family - the basis for these choices.</p>
 */
public final class SpellMastery {

    /** Every grant's share routed to the spell's school category. */
    public static final double SCHOOL_SHARE = 0.5;

    /** The gain handler's name (registered once; resolves at first award). */
    public static final String GAIN_HANDLER = "ability_iss:spell_xp";

    /**
     * The spells whose class carries ISS's getRange seam - EXACTLY the
     * eleven registered classes the RangeMastery mixins target (one
     * return-wrap each, plus RayOfSiphoning's two call-site wraps;
     * ISS's SoulfireRaySpell ships getRange but is never registered, so
     * it has no spec to declare). This set is the mixin coverage
     * contract: a class listed here without a mixin would show a pane
     * bonus nothing applies, a mixin without the declaration reads
     * inert. The gametest pins both directions.
     */
    public static final Set<Class<? extends AbstractSpell>> RANGE_SEAM = Set.of(
            RayOfSiphoningSpell.class,
            EldritchBlastSpell.class,
            SonicBoomSpell.class,
            TelekinesisSpell.class,
            FangSwirlSpell.class,
            FirecrackerSpell.class,
            GustSpell.class,
            ScapegoatSpell.class,
            RayOfFrostSpell.class,
            ChainLightningSpell.class,
            StompSpell.class);

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
            MasterySpec spec = MasterySpec.gained(0.0).withGainHandler(GAIN_HANDLER)
                    // the three seams this slice builds: check+charge+
                    // row quote, the effective cast time, the power
                    // scaling - all default-curve (server config)
                    .withEffect(MasteryAxis.COST)
                    .withEffect(MasteryAxis.CAST_TIME)
                    .withEffect(MasteryAxis.POWER);
            if (RANGE_SEAM.contains(spell.getClass())) {
                // RANGE joins only for the twelve mixin-covered spells:
                // the pane's bonus is never a promise without a seam
                spec = spec.withEffect(MasteryAxis.RANGE);
            }
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
