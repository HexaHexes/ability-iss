package org.bitwisemadness.abilityiss.spell;

import com.mojang.logging.LogUtils;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.config.api.ManasConfig;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.AmountResolvers;
import org.bitwisemadness.abilitycore.api.ConsumptionStrategies;
import org.bitwisemadness.abilitycore.api.ResourceCost;
import org.bitwisemadness.abilitycore.sync.MasteryQueryResponse;
import org.bitwisemadness.abilityhotbar.client.LiveCosts;
import org.bitwisemadness.abilityhotbar.compat.CoreMasteryCompat;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

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

    /** The bundled iron.wiki prose (classpath; no runtime HTTP). */
    private static final String DESCRIPTIONS_RESOURCE =
            "/assets/ability_iss/spell_descriptions.json";

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final List<ResourceLocation> REGISTERED = new ArrayList<>();

    /** Loaded once per JVM at the first definition that asks. */
    private static volatile Map<String, String> descriptions;

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

    /**
     * The bundled spell descriptions (owner's iron.wiki brief): the
     * classpath JSON's id -> prose map, parsed once - no runtime HTTP.
     * A missing or malformed resource degrades to "no descriptions"
     * (logged once), never a registration failure, and {@code _}-prefixed
     * provenance keys are metadata, never spell entries.
     *
     * @return an immutable id -> prose map (empty when unavailable)
     */
    static Map<String, String> descriptions() {
        Map<String, String> loaded = descriptions;
        if (loaded != null) {
            return loaded;
        }
        synchronized (SpellCatalog.class) {
            if (descriptions == null) {
                descriptions = loadDescriptions();
            }
            return descriptions;
        }
    }

    private static Map<String, String> loadDescriptions() {
        // the fetcher's download wins when it can be trusted (option B):
        // a floor-passed cache is newer than the bundle it replaces, and
        // applies at THIS boot - the registry freezes right after
        Map<String, String> cached = WikiDescriptions.readCache(
                WikiDescriptions.cachePath());
        if (cached != null) {
            return cached;
        }
        try (InputStream in = SpellCatalog.class.getResourceAsStream(DESCRIPTIONS_RESOURCE)) {
            if (in == null) {
                LOGGER.error("bundled spell descriptions missing at {} - the pane shows "
                        + "spells without prose", DESCRIPTIONS_RESOURCE);
                return Map.of();
            }
            return WikiDescriptions.readJson(in);
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("bundled spell descriptions failed to read - the pane shows "
                    + "spells without prose", failure);
            return Map.of();
        }
    }

    /** One spell's menu definition: ISS's name/icon, cast shape, mana quote, bundled description. */
    static AbilityDefinition definitionFor(ResourceLocation spellId, AbstractSpell spell) {
        Set<String> tags = new LinkedHashSet<>(2);
        tags.add("spell");
        String school = schoolTag(spell);
        if (school != null) {
            tags.add(school);
        }
        String description = descriptions().get(spellId.toString());
        return new AbilityDefinition(
                nameKey(spellId),
                spell.getSpellIconResource(),
                CATEGORY,
                activationType(spell.getCastType()),
                costsFor(spell),
                ConsumptionStrategies.ALL,
                AmountResolvers.STATIC,
                Set.copyOf(tags),
                // the formal description slot (core#119): the bundled
                // iron.wiki prose when the page has the spell, absent
                // otherwise - the pane skips a section it cannot fill
                description == null ? Map.of() : Map.of("description", description));
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
     *
     * <p>The quote carries the <b>mastery COST discount</b> rounded with
     * the same {@code Math.round} on the same base as the check
     * ({@code CastManaCostMasteryMixin}) and the charge
     * ({@link MasteryCostEvents}) - all three can never disagree - and
     * with {@code tensura_iron_spells} in force the remainder is its
     * <b>magicule</b> conversion (its config loads after every
     * RegisterEvent - exactly why this re-reads at display time). The
     * discount comes from the menu's answered snapshot (server truth,
     * client-side); before any answer the quote shows the native number,
     * which is what an undiscounted charge would take.</p>
     */
    @Nullable
    static List<ResourceCost> liveCostsFor(AbstractSpell spell) {
        try {
            int mana = spell.getManaCost(Math.max(1, spell.getMinLevel()));
            if (mana <= 0) {
                return List.of();
            }
            int discounted = (int) Math.round(mana * masteryCostDiscount(spell));
            Double multiplier = tensuraMagiculeMultiplier();
            List<ResourceCost> converted =
                    multiplier == null ? null : magiculeCosts(discounted, multiplier);
            return converted != null
                    ? converted
                    : List.of(new ResourceCost("irons_spellbooks:mana", discounted));
        } catch (RuntimeException upstream) {
            return null;
        }
    }

    /**
     * The spell's current COST multiplier from the menu's answered
     * snapshot, or 1.0 when no answer has arrived yet (the snapshot is
     * solicited on menu open; rows only render while it is open, so the
     * quoted price converges within a frame of the answer).
     */
    private static double masteryCostDiscount(AbstractSpell spell) {
        ResourceLocation id = SpellRegistry.REGISTRY.getKey(spell);
        if (id == null) {
            return 1.0;
        }
        for (MasteryQueryResponse.Bonus bonus : CoreMasteryCompat.bonusesOf(id)) {
            if ("COST".equals(bonus.axis())) {
                return bonus.multiplier();
            }
        }
        return 1.0;
    }

    /**
     * The tensura_iron_spells repriced quote (pure): the charge is
     * {@code mana x multiplier} in magicule (its {@code onSpellCast}),
     * plus the mana remainder a sub-1 multiplier keeps - truncated
     * exactly as the charge truncates it. A non-positive or non-finite
     * multiplier means the conversion is off: null, and the caller
     * quotes native mana.
     *
     * @param mana       the spell's native mana cost
     * @param multiplier t_i_s's mana-to-magicule conversion
     * @return the quote, or null when the conversion is off
     */
    @Nullable
    static List<ResourceCost> magiculeCosts(int mana, double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier <= 0.0) {
            return null;
        }
        double magicule = mana * multiplier;
        List<ResourceCost> costs = new ArrayList<>(2);
        costs.add(new ResourceCost("tensura:magicule", magicule));
        int remainder = (int) (mana - magicule);
        if (remainder > 0) {
            costs.add(new ResourceCost("irons_spellbooks:mana", remainder));
        }
        return List.copyOf(costs);
    }

    /** tensura_iron_spells: the mod id and the config class it reads. */
    private static final String TINS_MOD_ID = "tensura_iron_spells";
    private static final String TINS_CONFIG_CLASS =
            "io.github.manasmods.tensura_iron.neoforge.IronConfig";

    /** One resolved t_i_s accessor (config class + multiplier field). */
    private record TinsConfig(Class<? extends ManasConfig> type, Field multiplier) {
    }

    private static volatile TinsConfig tinsConfig;
    private static volatile boolean tinsResolved;

    /**
     * tensura_iron_spells' live mana-to-magicule multiplier, or null when
     * the mod is absent, its config class moved, or its config is not
     * loaded yet (every fallback quotes native mana - fail-soft by the
     * LiveCosts contract). The accessor resolves once (a missing
     * third-party class is stable per session); the VALUE reads live, so
     * a config reload applies to the very next display.
     */
    @Nullable
    private static Double tensuraMagiculeMultiplier() {
        if (!ModList.get().isLoaded(TINS_MOD_ID)) {
            return null;
        }
        TinsConfig config = tinsConfig;
        if (config == null && !tinsResolved) {
            synchronized (SpellCatalog.class) {
                if (tinsConfig == null && !tinsResolved) {
                    tinsConfig = resolveTinsConfig();
                    tinsResolved = true;
                }
            }
            config = tinsConfig;
        }
        if (config == null) {
            return null;
        }
        try {
            ManasConfig loaded = ConfigRegistry.getConfig(config.type());
            return loaded == null ? null
                    : (double) config.multiplier().getFloat(loaded);
        } catch (RuntimeException | ReflectiveOperationException unreadable) {
            return null;
        }
    }

    /** The t_i_s config class + its public field, or null when absent. */
    @Nullable
    private static TinsConfig resolveTinsConfig() {
        try {
            Class<?> raw = Class.forName(TINS_CONFIG_CLASS);
            @SuppressWarnings("unchecked")
            Class<? extends ManasConfig> type = (Class<? extends ManasConfig>) raw;
            return new TinsConfig(type, raw.getField("manaConversionMultiplier"));
        } catch (ClassNotFoundException | NoSuchFieldException | SecurityException absent) {
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
