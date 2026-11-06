package org.bitwisemadness.abilityiss.compat;

import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.bitwisemadness.abilitycore.AbilityCore;
import org.bitwisemadness.abilitycore.api.AbilityAvailability;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationOutcome;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationRequests;
import org.bitwisemadness.abilitycore.api.ActivationResult;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.AvailabilityProviders;
import org.bitwisemadness.abilitycore.api.AvailabilityState;
import org.bitwisemadness.abilitycore.api.ChargingModel;
import org.bitwisemadness.abilitycore.api.Metric;
import org.bitwisemadness.abilitycore.registry.AbilityRegistries;
import org.bitwisemadness.abilityhotbar.content.HotbarContentProviders;
import org.bitwisemadness.abilityhotbar.registry.AbilityBarScopes;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.bitwisemadness.abilityhotbar.state.SeededAssignments;
import org.bitwisemadness.abilityiss.client.SpellbookBarContentProvider;

/**
 * The Ability Iss hotbar seam: everything that touches Ability Hotbar and
 * Ability Core for the spellbook bar (#3).
 *
 * <p>The bar ({@link #CURIO_BAR}) is registered <b>non-assignable</b>: the
 * menu never lists it, players never assign to it - its cells are the
 * equipped spellbook's spells, seeded from the book's contents. The cast
 * verb ({@link #CAST_ID}) is scoped to the bar so it can never leak to
 * another bar, and each seeded cell names its spell <b>by index</b> through
 * {@code ActivationRequest.slotContext}.</p>
 *
 * <p>Casting is delegated to ISS itself: {@link Utils#serverSideInitiateQuickCast}
 * resolves the player's own spell selection (built from the curios-worn
 * book - {@link Utils#getPlayerSpellbookStack} reads Curios directly),
 * so mana, cooldown, cast-in-progress and refusal messaging stay ISS's
 * own pipeline. The handler only maps the boolean to the outcome
 * vocabulary - the press precedent: the domain messages the player, the
 * pipeline carries outcomes.</p>
 *
 * <p>All four dependencies are hard in the toml (curios, Iron's Spells,
 * Ability Core, Ability Hotbar), so no {@code isLoaded()} gates on the
 * wiring itself; the one dist check guards the client-only provider
 * construction (it reads {@code Minecraft}'s player, and the JVM verifies
 * whole classes at first use).</p>
 */
public final class SpellbookBarBridge {

    /** The addon's bar id: registered non-assignable, cells = spellbook spells. */
    public static final ResourceLocation CURIO_BAR = ResourceLocation.fromNamespaceAndPath(
            "ability_iss", "spellbook");

    /** Display name for the switch toast (and any future surface). */
    public static final String BAR_NAME_KEY = "ability_iss.bar.spellbook";

    /** The cast ability's id (registered on core's abilities registry). */
    public static final ResourceLocation CAST_ID = ResourceLocation.fromNamespaceAndPath(
            "ability_iss", "cast");

    /** ContentPlan's ceiling: no book cell count may exceed it. */
    public static final int MAX_CELLS = 18;

    private static boolean registered;
    private static boolean handlersRegistered;

    private SpellbookBarBridge() {
    }

    /** True while Ability Hotbar is present (required in the toml; defensive). */
    public static boolean isLoaded() {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded("ability_hotbar");
    }

    /**
     * Register the bar, the verb's scope and the client cell provider -
     * common setup, once. Order is load-bearing: the bar registers first
     * (the provider registration and the scope call validate it), and the
     * provider is constructed only on the client (link-safety: its bodies
     * read {@code Minecraft}'s player).
     *
     * <p>Deliberately NOT {@link HotbarContentProviders#registerContentBar}
     * (hotbar#116): that helper's supplier argument is evaluated on both
     * distributions, and linking the callsite loads the provider class -
     * whose verified bodies reference {@code LocalPlayer}, which the dist
     * cleaner forbids on a dedicated server. The crash is a
     * {@code BootstrapMethodError} at common setup, before the helper runs
     * a line (observed on the alpha.47 gate; filed upstream as
     * ability-hotbar#120 - the helper's dist safety covers invocation, not
     * linkage). The dist-gated two-call form below is the pattern that
     * boots on both distributions.</p>
     */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        HotbarRegistry.register(CURIO_BAR, BAR_NAME_KEY, false);
        AbilityBarScopes.register(CAST_ID, CURIO_BAR);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            HotbarContentProviders.register(CURIO_BAR, new SpellbookBarContentProvider());
        }
    }

    /**
     * Register the cast definition on core's registry - called from the
     * mod-bus {@code RegisterEvent} for core's abilities key. The icon is
     * ISS's own spellbook texture (spells have no item form - their icons
     * are textures, ability-hotbar#115).
     *
     * <p>The costs list is empty, the strategy is {@code "all"} (core's
     * documented default, resolved at registration) and the amount
     * resolver is {@code "static"} (the built-in identity): the cast
     * charges no core resources - mana is ISS's own, charged inside its
     * own pipeline (this handler is {@code ChargingModel.NATIVE}; the
     * hotbar#119 boundary says costs live with the domain). The
     * overlay's mana badge comes from the per-cell availability, not
     * from this declaration.</p>
     *
     * <p>Shape note: core's resource series reshaped this constructor
     * three times in a day - alpha.34 (costs list), alpha.35
     * (consumption strategy), alpha.39 (amount resolver) - and each
     * break crashed the older binary at {@code RegisterEvent} with
     * {@code NoSuchMethodError} in production (Tensura RPG,
     * 2026-11-06); hence the alpha.39 pin the toml documents.</p>
     */
    public static void registerDefinitions(RegisterEvent event) {
        event.register(AbilityRegistries.ABILITY_REGISTRY_KEY, CAST_ID,
                () -> new AbilityDefinition(
                        "ability_iss.ability.cast",
                        ResourceLocation.fromNamespaceAndPath("irons_spellbooks",
                                "textures/item/copper_spell_book.png"),
                        ResourceLocation.fromNamespaceAndPath("ability_iss", "spellbook"),
                        ActivationType.INSTANT,
                        List.of(),
                        "all",
                        "static",
                        Set.of("spell", "cast"),
                        Map.of()));
    }

    /**
     * Register the handler and the availability projection - common setup,
     * before any login can reach the pipeline. Idempotent (both of core's
     * registries fail loudly on duplicates).
     */
    public static void registerHandlers() {
        if (handlersRegistered) {
            return;
        }
        handlersRegistered = true;
        AbilityCore.HANDLERS.register(CAST_ID, SpellbookBarBridge::cast, ChargingModel.NATIVE);
        AvailabilityProviders.declare(CAST_ID, SpellbookBarBridge::availability);
    }

    /**
     * The native pipeline entry (#3): a spellbook-bar cell casting its own
     * spell - {@code slotContext} is the cell index, resolved against the
     * player's own spell selection by ISS's quick-cast. Fail-closed slot
     * dispatch is core's shared gate (ability-core#87,
     * {@link ActivationRequests#requireSlot}) run against the live book
     * capacity (0 with no book, so every cell refuses): a request without
     * a cell, a stale cell beyond that capacity, or an empty bar refuse
     * with core's own refusal - never an active-slot fallback - and a slot
     * ISS cannot resolve refuses instead of guessing.
     */
    private static ActivationResult cast(ActivationRequest request, ServerPlayer player) {
        var book = findBook(player);
        var container = book.isEmpty() ? null : ISpellContainer.get(book);
        var gate = ActivationRequests.requireSlot(request,
                container == null ? 0 : container.getMaxSpellCount());
        if (!gate.isValid()) {
            return gate.refusal();
        }
        if (!Utils.serverSideInitiateQuickCast(player, gate.slot())) {
            // ISS resolves the slot (null/empty/casting) and messages the
            // specific reason itself when it has one; we carry the outcome.
            return ActivationResult.refused(ActivationOutcome.REFUSED_UNAVAILABLE);
        }
        return ActivationResult.ACTED;
    }

    /**
     * The cast's overlay tuple (#3): whether a castable spell exists in the
     * equipped book. Cooldown is per-spell in ISS, so the ability-global
     * projection is honestly unknown - the contract renders nothing rather
     * than a lie (REQ-HOT-08). Per-spell overlays land with
     * ability-hotbar#115's per-cell availability.
     */
    private static AbilityAvailability availability(Player player, ResourceLocation id) {
        var book = findBook(player);
        var container = book.isEmpty() ? null : ISpellContainer.get(book);
        boolean castable = container != null && container.getActiveSpellCount() > 0;
        return new AbilityAvailability(
                Metric.unknown(),
                Metric.unknown(),
                castable ? AvailabilityState.available()
                        : AvailabilityState.unavailable("no spellbook"));
    }

    /**
     * The equipped spellbook: ISS's own accessor - it reads the curios
     * inventory directly, so this is the same stack the selection manager
     * builds the player's spell list from (one source of truth for cells,
     * seeds and casts). Empty stack when nothing is worn.
     */
    public static ItemStack findBook(Player player) {
        ItemStack book = Utils.getPlayerSpellbookStack(player);
        return book == null ? ItemStack.EMPTY : book;
    }

    /**
     * Seed the whole bar from the equipped book (#3): every cell whose book
     * slot holds a castable spell carries the cast verb; every other cell is
     * cleared (a smaller book or a removed spell must not leave stale
     * seeds). Per-slot, not a prefix count: a book with a gap (a spell
     * removed mid-book) seeds around the gap, because the bar's cells are
     * the book's spells <i>per slot</i> - the same predicate the client
     * provider draws cells by, so seed and display can never disagree.
     * Re-run on login, on inscription (the book's contents changed - the
     * event carries no stack, so the re-seed is whole-bar and idempotent)
     * and on equipping the spellbook itself (Curios is the source of
     * truth). Batched through {@code seedAll} (hotbar#116): same per-slot
     * semantics as the loop it replaces, one snapshot echo per call instead
     * of one per slot.
     */
    public static void seed(ServerPlayer player) {
        var book = findBook(player);
        var container = book.isEmpty() ? null : ISpellContainer.get(book);
        SeededAssignments.seedAll(player, CURIO_BAR, MAX_CELLS,
                cell -> isSpellCell(container, cell) ? CAST_ID.toString() : null);
    }

    /**
     * Whether one book slot holds a cell-worthy spell: present, not locked,
     * resolvable - the definition both the seeding (server) and the client
     * cell reads (icons, overlays) use, so a cell is a spell everywhere or
     * nowhere.
     */
    public static boolean isSpellCell(ISpellContainer container, int index) {
        if (container == null || index < 0 || index >= container.getMaxSpellCount()) {
            return false;
        }
        SpellData data = container.getSpellAtIndex(index);
        return data != null && data != SpellData.EMPTY
                && !data.isLocked() && data.getSpell() != null;
    }
}
