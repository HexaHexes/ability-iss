package org.bitwisemadness.abilityiss.spell;

import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.AmountResolvers;
import org.bitwisemadness.abilitycore.api.ConsumptionStrategies;
import org.bitwisemadness.abilitycore.api.ResourceCost;
import org.bitwisemadness.abilityhotbar.client.LiveCosts;
import org.jetbrains.annotations.Nullable;

/**
 * Every ISS spell as a menu-listed ability (owner's tab brief: the tab
 * lists all the spells the player has found). One definition per live
 * registry entry - ISS's own name key ({@code spell.<ns>.<path>}, the
 * lang family verified against the shipped file), its spell icon, the
 * category the tab filters by, ISS's own cast shape, and its mana quote
 * as display metadata (charging stays ISS's pipeline).
 *
 * <p>Registration timing mirrors the ability-tensura pattern (its
 * registration-timing gametest settled the question): definitions build
 * at <b>ISS's own spell-registry event</b> with a raw
 * {@link Registry#register} write into core's live registry - the
 * shapes need the spells populated, core's own event runs too early for
 * upstream registries, and NeoForge's {@code RegisterEvent} source shows
 * cross-key {@code register} is a silent no-op with eager suppliers.
 * The id list doubles as the tab's content handler and the providers'
 * declaration set.</p>
 */
public final class SpellCatalog {

    /** The category every spell definition carries (tab membership). */
    public static final ResourceLocation CATEGORY =
            ResourceLocation.fromNamespaceAndPath("ability_iss", "spell");

    private static final List<ResourceLocation> REGISTERED = new ArrayList<>();

    private SpellCatalog() {
    }

    /** The registered spell ids in registration order (tab content, providers). */
    public static List<ResourceLocation> registered() {
        return List.copyOf(REGISTERED);
    }

    /**
     * How many spells the registration saw - the gametest pins this equal
     * to ISS's live registry, so an empty read (listener order drift)
     * can never pass as a cold-but-fine run.
     */
    public static int registeredCount() {
        return REGISTERED.size();
    }

    /**
     * Registers one definition per ISS spell - called from ISS's spell
     * registry event (see the class doc for the timing/write rationale);
     * core owns duplicate rules, a clash fails loudly.
     */
    public static void register(Registry<AbilityDefinition> registry) {
        for (AbstractSpell spell : SpellRegistry.REGISTRY.stream().toList()) {
            ResourceLocation spellId = SpellRegistry.REGISTRY.getKey(spell);
            if (spellId == null) {
                continue; // unreachable for a live entry - never invent an id
            }
            Registry.register(registry, spellId, definitionFor(spellId, spell));
            // the pane's live-cost answer (hotbar#193): a repricing in
            // force since registration - tensura_iron_spells' mixin,
            // whose config loads after RegisterEvent - is what shows
            LiveCosts.declare(spellId, () -> liveCostsFor(spell));
            REGISTERED.add(spellId);
        }
    }

    /** One spell's menu definition: ISS's name/icon, cast shape, mana quote. */
    static AbilityDefinition definitionFor(ResourceLocation spellId, AbstractSpell spell) {
        Set<String> tags = new LinkedHashSet<>(2);
        tags.add("spell");
        String school = schoolTag(spell);
        if (school != null) {
            tags.add(school);
        }
        return new AbilityDefinition(
                nameKey(spellId),
                spell.getSpellIconResource(),
                CATEGORY,
                activationType(spell.getCastType()),
                costsFor(spell),
                ConsumptionStrategies.ALL,
                AmountResolvers.STATIC,
                Set.copyOf(tags),
                Map.of()); // ISS ships no per-spell description keys - the pane skips the section
    }

    /**
     * The spell's name translation key: ISS's {@code spell.<ns>.<path>}
     * family (the shipped lang reads
     * {@code spell.irons_spellbooks.fireball}). Package-private for the
     * registration gametest's pins.
     */
    static String nameKey(ResourceLocation spellId) {
        return "spell." + spellId.getNamespace() + "." + spellId.getPath();
    }

    /**
     * ISS's cast shape mapped onto core's vocabulary (owner's bytecode
     * brief): a cast bar LONG reads CHARGED, a held CONTINUOUS reads
     * CHANNELLED, and NONE/absent/unknown reads INSTANT - never UNKNOWN,
     * which would refuse an activatable spell.
     */
    static ActivationType activationType(@Nullable CastType castType) {
        if (castType == null) {
            return ActivationType.INSTANT;
        }
        return switch (castType) {
            case LONG -> ActivationType.CHARGED;
            case CONTINUOUS -> ActivationType.CHANNELLED;
            case NONE, INSTANT -> ActivationType.INSTANT;
        };
    }

    /**
     * The declared usage cost (owner: show the costs for all abilities):
     * the spell's mana quote at its minimum level - display metadata, ISS
     * charges in its own pipeline. A refusal by upstream degrades to no
     * cost: enrichment never fails a registration.
     */
    static List<ResourceCost> costsFor(AbstractSpell spell) {
        List<ResourceCost> live = liveCostsFor(spell);
        return live == null ? List.of() : live;
    }

    /**
     * The same quote re-read <b>now</b>, for the pane's live-cost answer:
     * null when upstream is unreadable, which is LiveCosts' signal to keep
     * the baked list instead of blanking the row. A spell that genuinely
     * costs nothing answers an empty list, not null - the distinction the
     * fallback contract turns on.
     */
    @Nullable
    static List<ResourceCost> liveCostsFor(AbstractSpell spell) {
        try {
            int mana = spell.getManaCost(Math.max(1, spell.getMinLevel()));
            return mana > 0
                    ? List.of(new ResourceCost("irons_spellbooks:mana", mana))
                    : List.of();
        } catch (RuntimeException upstream) {
            return null;
        }
    }

    /** The {@code type:} school tag for the subcategory strip, or none. */
    @Nullable
    private static String schoolTag(AbstractSpell spell) {
        // the spell's DECLARED default school (owner: the config-
        // manager read defaulted to one school for every spell - all
        // Evocation - so the strip never reads it)
        ResourceLocation school = schoolOf(spell);
        return school == null ? null : "type:" + school.getPath();
    }

    /**
     * The spell's declared default school (owner: the same read the strip
     * uses, raw) - mastery routes each spell's grants to this school's
     * category, or to the spell alone when the read fails. Public for the
     * mastery slice's gametest pins.
     */
    @Nullable
    public static ResourceLocation schoolOf(AbstractSpell spell) {
        try {
            return spell.getDefaultConfig().schoolResource;
        } catch (RuntimeException upstream) {
            return null;
        }
    }

    /**
     * The subcategory strip's school order: ISS's own registry order,
     * one {@code type:} tag per school - the menu's convention key or
     * prettified fallback renders each (Fire, Lightning, ...).
     */
    static List<String> schoolOrder() {
        return SchoolRegistry.REGISTRY.stream()
                .map(school -> "type:" + school.getId().getPath())
                .toList();
    }
}
