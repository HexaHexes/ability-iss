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
import org.bitwisemadness.abilitycore.api.MasteryAxis;
import org.bitwisemadness.abilitycore.api.MasteryDeclarations;
import org.bitwisemadness.abilitycore.api.MasterySpec;
import org.jetbrains.annotations.Nullable;

/**
 * One mastery ability per ISS school (owner's school-mastery brief): a
 * passive row that never casts - no costs, no handler, its own curves
 * never apply (they are the pane's display contract) - whose OWN level
 * is the school's mastery. Every spell of that school declares
 * a share to it ({@link SpellMastery#specFor}), so each grant routes
 * {@code mastery.schoolShare} of its XP to the school ability, and the
 * SAME share weights the school ability's level into that spell's
 * aggregate: core's share math is one number both ways (routing fraction
 * = aggregate weight), so the school level feeds the spell's
 * COST/CAST_TIME/POWER/RANGE curves exactly like its own level.
 *
 * <p>Ids are {@code ability_iss:mastery_<school-path>} in the owning
 * mod's namespace - never ISS's spell namespace, so a future spell can
 * never clash. The category is its own ({@code ability_iss:mastery})
 * and the row also carries {@code type:<school>} so it files under its
 * school's chip; the Spells tab lists both categories (owner's tab
 * brief), and visibility stays core's default: only declared
 * <em>spell</em> ids are found/held gated, so a school ability is
 * always listed. Registered at ISS's spell-registry event beside
 * {@link SpellCatalog}.</p>
 *
 * <p>The activation is {@link ActivationType.NONE} (owner's NONE brief):
 * the pane omits the Activation section entirely, and hotbar's
 * informational gate lets the row slot even under the informational
 * Spells tab. Unlock is cast-to-unlock ({@link SchoolMasteryUnlocks}):
 * the school ability's own XP pool - fed by the routed share of every
 * school cast - is the permanent unlock record.</p>
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
     * NONE is the deliberate passive (owner's NONE brief): the pane
     * skips the Activation section, nothing ever activates it, and the
     * informational gate lets it slot. The row carries its school's
     * {@code type:} chip tag and a flourished per-school description.
     * Called after {@link SpellCatalog#register} at the spell-registry
     * event.
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
                    ActivationType.NONE,
                    List.of(),
                    ConsumptionStrategies.ALL,
                    AmountResolvers.STATIC,
                    Set.of("mastery", "type:" + school.getPath()),
                    Map.of("description", descriptionFor(school.getPath()))));
            REGISTERED.add(id);
        }
    }

    /**
     * Declares each school ability's spec: passive - level only, no
     * shares (its level reaches the spells through their shares, never
     * through its own curves), the default cap, and the four pane axes
     * (owner's mastery-bonuses brief): COST/CAST_TIME/POWER/RANGE on
     * core's default curves, so the pane's bonus read answers with the
     * curve at the school's own level. DURATION stays undeclared - ISS
     * has no caster-scoped seam for it. Idempotent.
     */
    public static void declare() {
        for (ResourceLocation id : REGISTERED) {
            if (!MasteryDeclarations.has(id)) {
                MasteryDeclarations.declare(id, MasterySpec.gained(0.0)
                        .withEffect(MasteryAxis.COST)
                        .withEffect(MasteryAxis.CAST_TIME)
                        .withEffect(MasteryAxis.POWER)
                        .withEffect(MasteryAxis.RANGE));
            }
        }
    }

    /**
     * The school path as a display name ({@code dark_blood} -&gt;
     * {@code Dark Blood}): each underscore segment capitalized with
     * {@link Character#toUpperCase(char)} - locale-free by construction,
     * so the same path always yields the same bytes on every host.
     *
     * @param schoolPath the school's path segment ({@code blood})
     * @return the prettified name (the path unchanged when empty)
     */
    public static String prettifySchool(String schoolPath) {
        if (schoolPath.isEmpty()) {
            return schoolPath;
        }
        StringBuilder out = new StringBuilder(schoolPath.length() + 4);
        for (String word : schoolPath.split("_", -1)) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0)));
            out.append(word, 1, word.length());
        }
        return out.toString();
    }

    /**
     * The pane's flourished, per-school description (owner's brief): a
     * generic template with the school's name woven through it, naming
     * the school and HOW it affects - every cast of that school feeds
     * the mastery, and every level bends those spells' cost, casting,
     * force and reach. Prose with spaces, so hotbar's pane renders it
     * literally instead of chasing it as a translation key.
     *
     * @param schoolPath the school's path segment ({@code blood})
     * @return the description text baked into the definition
     */
    static String descriptionFor(String schoolPath) {
        String school = prettifySchool(schoolPath);
        return "Where the " + school + " school holds sway, this mastery keeps "
                + "score: every spell you cast of it feeds this mastery, and every "
                + "level it claims returns to those spells as one - cheaper to "
                + "invoke, swifter to release, mightier in force, and farther in reach.";
    }

    @Nullable
    private static AbstractSpell resolve(ResourceLocation spellId) {
        return SpellRegistry.REGISTRY.getOptional(
                ResourceKey.create(SpellRegistry.SPELL_REGISTRY_KEY, spellId))
                .orElse(null);
    }
}
