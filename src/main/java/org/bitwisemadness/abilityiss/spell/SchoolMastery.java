package org.bitwisemadness.abilityiss.spell;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.AmountResolvers;
import org.bitwisemadness.abilitycore.api.ConsumptionStrategies;
import org.bitwisemadness.abilitycore.api.MasteryDeclarations;
import org.bitwisemadness.abilitycore.api.MasterySpec;
import org.jetbrains.annotations.Nullable;

/**
 * One mastery ability per ISS school (owner's school-mastery brief): a
 * passive row that never casts - no costs, no effects, no handler - whose
 * OWN level is the school's mastery. Every spell of that school declares
 * a share to it ({@link SpellMastery#specFor}), so each grant routes
 * {@code mastery.schoolShare} of its XP to the school ability, and the
 * SAME share weights the school ability's level into that spell's
 * aggregate: core's share math is one number both ways (routing fraction
 * = aggregate weight), so the school level feeds the spell's
 * COST/CAST_TIME/POWER/RANGE curves exactly like its own level.
 *
 * <p>Ids are {@code ability_iss:mastery_<school-path>} in the owning
 * mod's namespace - never ISS's spell namespace, so a future spell can
 * never clash. The category is its own ({@code ability_iss:mastery}),
 * which keeps the abilities out of the Spells tab twice over (the tab's
 * content lists spell ids and its membership demands the spell
 * category), and visibility stays core's default: only declared
 * <em>spell</em> ids are found/held gated, so a school ability is
 * always listed. Registered at ISS's spell-registry event beside
 * {@link SpellCatalog}.</p>
 */
public final class SchoolMastery {

    /** The category every school-mastery ability carries. */
    public static final ResourceLocation CATEGORY =
            ResourceLocation.fromNamespaceAndPath("ability_iss", "mastery");

    /** The school abilities this registration produced, in school order. */
    private static final List<ResourceLocation> REGISTERED = new ArrayList<>();

    private SchoolMastery() {
    }

    /**
     * The mastery ability for one ISS school.
     *
     * @param school the school id ({@code irons_spellbooks:<path>}), or null
     * @return {@code ability_iss:mastery_<path>}, or null for a null school
     */
    @Nullable
    public static ResourceLocation abilityId(@Nullable ResourceLocation school) {
        if (school == null) {
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath("ability_iss",
                "mastery_" + school.getPath());
    }

    /** The name key for one school ability: {@code ability_iss.mastery.<path>}. */
    public static String nameKey(ResourceLocation school) {
        return "ability_iss.mastery." + school.getPath();
    }

    /** The registered school abilities (registration order), empty before {@link #register}. */
    public static List<ResourceLocation> registered() {
        return List.copyOf(REGISTERED);
    }

    /**
     * Registers one ability per school the catalog's spells belong to -
     * the catalog is the authority (a school with no spells has nothing
     * to master, so it gets no dead row). The icon is the school's first
     * spell's own icon: ISS's SchoolType ships no icon of its own.
     * INSTANT with no handler is the "no active effect": the pipeline's
     * ADAPTER phase refuses quietly (REFUSED_NO_ADAPTER), so a press
     * runs nothing. Called after {@link SpellCatalog#register} at the
     * spell-registry event.
     *
     * @param registry core's live abilities registry
     */
    public static void register(Registry<AbilityDefinition> registry) {
        Map<ResourceLocation, AbstractSpell> firstPerSchool = new LinkedHashMap<>();
        for (ResourceLocation spellId : SpellCatalog.registered()) {
            AbstractSpell spell = resolve(spellId);
            if (spell == null) {
                continue;
            }
            ResourceLocation school = SpellCatalog.schoolOf(spell);
            if (school != null) {
                firstPerSchool.putIfAbsent(school, spell);
            }
        }
        for (Map.Entry<ResourceLocation, AbstractSpell> entry : firstPerSchool.entrySet()) {
            ResourceLocation school = entry.getKey();
            AbstractSpell iconSpell = entry.getValue();
            ResourceLocation id = abilityId(school);
            if (school == null || iconSpell == null || id == null) {
                continue; // never null here; the guards keep the @Nullable chain honest
            }
            Registry.register(registry, id, new AbilityDefinition(
                    nameKey(school),
                    iconSpell.getSpellIconResource(),
                    CATEGORY,
                    ActivationType.INSTANT,
                    List.of(),
                    ConsumptionStrategies.ALL,
                    AmountResolvers.STATIC,
                    Set.of("mastery"),
                    Map.of())); // no description: the pane skips the section
            REGISTERED.add(id);
        }
    }

    /**
     * Declares each school ability's spec: passive - level only, no
     * effects (its level reaches the spells through their shares, never
     * through its own curves), no shares, the default cap. Idempotent.
     */
    public static void declare() {
        for (ResourceLocation id : REGISTERED) {
            if (!MasteryDeclarations.has(id)) {
                MasteryDeclarations.declare(id, MasterySpec.gained(0.0));
            }
        }
    }

    @Nullable
    private static AbstractSpell resolve(ResourceLocation spellId) {
        return SpellRegistry.REGISTRY.getOptional(
                ResourceKey.create(SpellRegistry.SPELL_REGISTRY_KEY, spellId))
                .orElse(null);
    }
}
