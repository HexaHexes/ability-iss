package org.bitwisemadness.abilityiss.gametest;

import com.mojang.authlib.GameProfile;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.compat.Curios;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.abilitycore.AbilityCore;
import org.bitwisemadness.abilitycore.api.AbilityUnlocks;
import org.bitwisemadness.abilitycore.api.ActivationOutcome;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.AvailabilityState;
import org.bitwisemadness.abilitycore.api.CooldownOwners;
import org.bitwisemadness.abilitycore.api.MasteryAwards;
import org.bitwisemadness.abilitycore.api.MasteryAxis;
import org.bitwisemadness.abilitycore.api.MasteryDeclarations;
import org.bitwisemadness.abilitycore.api.MasteryEffects;
import org.bitwisemadness.abilitycore.api.MasteryGainHandlers;
import org.bitwisemadness.abilitycore.api.MasteryTarget;
import org.bitwisemadness.abilitycore.api.MasteryTrigger;
import org.bitwisemadness.abilitycore.api.MasteryView;
import org.bitwisemadness.abilitycore.api.VisibilityHandlers;
import org.bitwisemadness.abilitycore.discovery.AbilityDiscovery;
import org.bitwisemadness.abilitycore.mastery.MasteryAttachments;
import org.bitwisemadness.abilityhotbar.client.LiveCosts;
import org.bitwisemadness.abilityhotbar.content.HotbarContentProviders;
import org.bitwisemadness.abilityhotbar.registry.AbilityBarScopes;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.bitwisemadness.abilityhotbar.registry.MenuTabs;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;
import org.bitwisemadness.abilityhotbar.state.SeededAssignments;
import org.bitwisemadness.abilityiss.IssHotbar;
import org.bitwisemadness.abilityiss.SpellCellOverlays;
import org.bitwisemadness.abilityiss.compat.SpellbookBarBridge;
import org.bitwisemadness.abilityiss.config.IssServerConfig;
import org.bitwisemadness.abilityiss.spell.SpellCatalog;
import org.bitwisemadness.abilityiss.spell.SpellMastery;
import org.bitwisemadness.abilityiss.spell.SpellMenuTab;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

/**
 * GameTests for the spellbook bar (#3): the seam (registration, scope,
 * provider), the seeding contract (a worn book seeds its cells, an
 * unworn one clears them) and the cast handler's fail-closed refusals.
 *
 * <p>The bar's own cast success path (quick-cast through ISS's
 * selection state) still belongs to the author's in-game check (#3's
 * acceptance); what these tests pin is the wiring, the refusals the
 * pipeline must never get wrong, and - since the mastery slice - the
 * per-spell declarations plus a directly-initiated cast landing its
 * award through the mixin.</p>
 */
@GameTestHolder(IssHotbar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IssHotbarGameTests {

    private IssHotbarGameTests() {
    }

    /** The mod is loaded and the empty structure template resolves. */
    @GameTest(template = "empty")
    public static void mod_loads(GameTestHelper helper) {
        helper.succeed();
    }

    /**
     * The bar's seam (#3): registered non-assignable, cast scoped to it,
     * cell provider attached - all through the staged hotbar dependency.
     */
    @GameTest(template = "empty")
    public static void spellbookBarIsRegisteredScopedAndProvided(GameTestHelper helper) {
        check(ModList.get().isLoaded("ability_hotbar"),
                "ability-hotbar must be staged for the spellbook-bar tests (#3)");

        var bar = HotbarRegistry.get(SpellbookBarBridge.CURIO_BAR);
        check(bar.isPresent(), "the spellbook bar must be registered");
        check(!bar.get().assignable(),
                "the spellbook bar must be non-assignable - the menu can never list it (#3)");

        var scope = AbilityBarScopes.scopeOf(SpellbookBarBridge.CAST_ID);
        check(scope.isPresent() && scope.get().equals(SpellbookBarBridge.CURIO_BAR),
                "cast must be scoped to the spellbook bar only");

        if (net.neoforged.fml.loading.FMLEnvironment.dist
                == net.neoforged.api.distmarker.Dist.CLIENT) {
            check(HotbarContentProviders.get(SpellbookBarBridge.CURIO_BAR).isPresent(),
                    "the cell provider must be registered for the bar (#3)");
        }

        check(AbilityUnlocks.has(SpellbookBarBridge.CAST_ID),
                "the unlock provider must be declared for cast (core#117)");

        check(CooldownOwners.ownerOf(SpellbookBarBridge.CAST_ID)
                        == CooldownOwners.Owner.HANDLER,
                "cast must defer its cooldown to the per-spell handler (alpha.45)");

        check(!VisibilityHandlers.visible(null, SpellbookBarBridge.CAST_ID),
                "cast is exclusive to its provider bar - never menu-visible (core#124)");

        helper.succeed();
    }

    /**
     * Seeding tracks the book (#3): a worn two-spell book seeds cells
     * 0 and 1 with cast, cell 2 stays clear, a gap in the book seeds
     * around itself (per-slot, not a prefix count), and unwearing clears
     * everything (hotbar#92's idempotent overwrite).
     */
    @GameTest(template = "empty")
    public static void seedingFollowsTheSpellbook(GameTestHelper helper) {
        var player = mockPlayer(helper);
        var stateKey = BarStateAttachments.BAR_STATE;
        var book = spellBookWith(helper, 2, 4);

        equipSpellbook(player, book);
        NeoForge.EVENT_BUS.post(new CurioChangeEvent(
                player, Curios.SPELLBOOK_SLOT, 0, ItemStack.EMPTY, book));

        var seeded = SeededAssignments.storedBar(
                player.getData(stateKey.get()), SpellbookBarBridge.CURIO_BAR);
        check(seeded.isPresent(), "a worn book must seed the bar's entry (#3)");
        var assignments = seeded.get().assignments();
        check(assignments.length >= 2
                        && SpellbookBarBridge.CAST_ID.equals(assignments[0].abilityId()),
                "cell 0 must carry cast, got "
                        + (assignments.length > 0 ? assignments[0].abilityId() : "no slots"));
        check(SpellbookBarBridge.CAST_ID.equals(assignments[1].abilityId()),
                "cell 1 must carry cast");
        check(assignments.length < 3 || assignments[2].abilityId() == null,
                "cell 2 (no spell) must stay unassigned");

        // A gap (spell removed mid-book): cells are the book's spells per
        // slot - the removed spell's cell clears, the spells after it seed.
        var gapped = spellBookWith(helper, 3, 4);
        var mutable = ISpellContainer.get(gapped).mutableCopy();
        check(mutable.removeSpellAtIndex(1), "the fixture gap must remove a spell");
        ISpellContainer.set(gapped, mutable.toImmutable());
        equipSpellbook(player, gapped);
        NeoForge.EVENT_BUS.post(new CurioChangeEvent(
                player, Curios.SPELLBOOK_SLOT, 0, ItemStack.EMPTY, gapped));
        var gappedBar = SeededAssignments.storedBar(
                player.getData(stateKey.get()), SpellbookBarBridge.CURIO_BAR);
        check(gappedBar.isPresent(), "the gapped book must still seed the bar's entry");
        var gappedCells = gappedBar.get().assignments();
        check(gappedCells.length > 0
                        && SpellbookBarBridge.CAST_ID.equals(gappedCells[0].abilityId()),
                "gap cell 0 must carry cast");
        check(gappedCells.length < 2 || gappedCells[1].abilityId() == null,
                "the removed spell's cell must stay clear (per-slot seed)");
        check(gappedCells.length < 3 || SpellbookBarBridge.CAST_ID.equals(gappedCells[2].abilityId()),
                "the spell after the gap must seed");

        equipSpellbook(player, ItemStack.EMPTY);
        NeoForge.EVENT_BUS.post(new CurioChangeEvent(
                player, Curios.SPELLBOOK_SLOT, 0, gapped, ItemStack.EMPTY));
        var cleared = SeededAssignments.storedBar(
                player.getData(stateKey.get()), SpellbookBarBridge.CURIO_BAR);
        check(cleared.isEmpty()
                        || cleared.get().assignments().length == 0
                        || cleared.get().assignments()[0].abilityId() == null,
                "an unworn book must clear its cells (#3)");

        helper.succeed();
    }

    /**
     * The cast handler refuses fail-closed (#3): no slot context, no book
     * (stale seed), a cell beyond the book's capacity and a cell the book
     * does not fill all refuse UNAVAILABLE instead of guessing. The ISS
     * success path is the author's in-game check.
     */
    @GameTest(template = "empty")
    public static void castRefusesFailClosed(GameTestHelper helper) {
        var player = mockPlayer(helper);
        var source = ActivationSource.DIRECT_SLOT;
        var handler = AbilityCore.HANDLERS.get(SpellbookBarBridge.CAST_ID).orElseThrow();

        var noSlot = handler.activate(
                ActivationRequest.of(SpellbookBarBridge.CAST_ID, source), player);
        check(noSlot.outcome() == ActivationOutcome.REFUSED_UNAVAILABLE,
                "a press without slotContext must refuse UNAVAILABLE, got " + noSlot.outcome());

        var noBook = handler.activate(
                ActivationRequest.of(SpellbookBarBridge.CAST_ID, source).withSlot(0), player);
        check(noBook.outcome() == ActivationOutcome.REFUSED_UNAVAILABLE,
                "a press with no spellbook must refuse UNAVAILABLE, got " + noBook.outcome());

        equipSpellbook(player, spellBookWith(helper, 1, 2));
        var beyondCapacity = handler.activate(
                ActivationRequest.of(SpellbookBarBridge.CAST_ID, source).withSlot(5), player);
        check(beyondCapacity.outcome() == ActivationOutcome.REFUSED_UNAVAILABLE,
                "a stale cell beyond the book's capacity must refuse UNAVAILABLE, got "
                        + beyondCapacity.outcome());

        var emptyCell = handler.activate(
                ActivationRequest.of(SpellbookBarBridge.CAST_ID, source).withSlot(1), player);
        check(emptyCell.outcome() == ActivationOutcome.REFUSED_UNAVAILABLE,
                "an unfilled cell must refuse UNAVAILABLE, got " + emptyCell.outcome());

        helper.succeed();
    }

    /**
     * The cooldown sweep's fraction direction (ability-iss#3, hotbar#115's
     * availabilityFor): readiness = elapsed, not remaining - 0 reads "just
     * cast" (the renderer's full cover), 1 reads "ready" (clear), no entry
     * reads unknown (REQ-HOT-08: no overlay, never a fake one), and a stale
     * entry clamps instead of drawing outside the renderer's [0,1].
     */
    @GameTest(template = "empty")
    public static void overlaySweepRunsByReadiness(GameTestHelper helper) {
        var none = SpellCellOverlays.forCell(0, 0, 25, false, 100f);
        check(none.cooldown().knownValue().isEmpty(),
                "no cooldown entry must leave the sweep unknown (REQ-HOT-08)");

        var half = SpellCellOverlays.forCell(500, 1000, 25, false, 100f);
        checkFraction(0.5, half.cooldown().knownValue(),
                "half-remaining must read 0.5 readiness");

        var fresh = SpellCellOverlays.forCell(1000, 1000, 25, false, 100f);
        checkFraction(0.0, fresh.cooldown().knownValue(),
                "just-cast must read 0.0 readiness (fully covered)");

        var stale = SpellCellOverlays.forCell(2000, 1000, 25, false, 100f);
        checkFraction(0.0, stale.cooldown().knownValue(),
                "a stale entry must clamp to 0, never draw a negative sweep");

        helper.succeed();
    }

    /**
     * No cost badge over spell icons (ability-iss#3): the bar's badge is
     * an unannotated 4px black strip across the cell bottom, which over a
     * spell icon reads as a rendering bug - exactly how it was found (pack
     * run, 2026-11-06). The per-cell cost therefore stays unknown
     * (REQ-HOT-08: no overlay rather than noise), for costly and free
     * spells alike.
     */
    @GameTest(template = "empty")
    public static void overlayNeverBadgesCostOverSpellIcons(GameTestHelper helper) {
        var costly = SpellCellOverlays.forCell(0, 0, 50, false, 100f);
        check(costly.cost().knownValue().isEmpty(),
                "a spell cell must not badge a cost - the badge renders as a black bar over the icon");

        var free = SpellCellOverlays.forCell(0, 0, 0, false, 100f);
        check(free.cost().knownValue().isEmpty(), "free spells stay unknown too");

        helper.succeed();
    }

    /**
     * The castability verdict (ability-iss#3): mana below cost dims the
     * cell (an Unavailable verdict, the renderer's tint), a locked spell
     * dims, but a cooling spell with mana does not - its sweep already
     * shows the cooldown, and ISS's own HUD does not dim for cooldowns
     * either.
     */
    @GameTest(template = "empty")
    public static void overlayDimsUnaffordableAndLockedOnly(GameTestHelper helper) {
        var broke = SpellCellOverlays.forCell(0, 0, 50, false, 10f);
        check(!broke.availability().isAvailable(), "mana below cost must dim");
        check(broke.availability() instanceof AvailabilityState.Unavailable,
                "the dim must be an Unavailable verdict");

        var locked = SpellCellOverlays.forCell(0, 0, 0, true, 100f);
        check(!locked.availability().isAvailable(), "a locked spell must dim");

        var coolingButRich = SpellCellOverlays.forCell(500, 1000, 25, false, 100f);
        check(coolingButRich.availability().isAvailable(),
                "cooldown has its own sweep - it must not also dim the cell");

        helper.succeed();
    }

    /** An ISS spellbook with {@code spells} distinct copies of spells and a set capacity. */
    private static ItemStack spellBookWith(GameTestHelper helper, int spells, int capacity) {
        var item = BuiltInRegistries.ITEM.getOptional(
                        ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "copper_spell_book"))
                .orElseThrow(() -> new GameTestAssertException(
                        "the ISS copper spell book must exist"));
        ItemStack book = new ItemStack(item);
        var mutable = ISpellContainer.getOrCreate(book).mutableCopy();
        mutable.setMaxSpellCount(capacity);
        // A book holds unique spells (ISS refuses a second copy), so the
        // fixture walks a pool of distinct spells.
        var pool = new io.redspace.ironsspellbooks.api.spells.AbstractSpell[]{
                SpellRegistry.ACUPUNCTURE_SPELL.get(),
                SpellRegistry.BLOOD_SLASH_SPELL.get(),
                SpellRegistry.BLOOD_NEEDLES_SPELL.get(),
                SpellRegistry.BLOOD_STEP_SPELL.get()
        };
        for (int i = 0; i < spells; i++) {
            mutable.addSpell(pool[i % pool.length], 1, false);
        }
        ISpellContainer.set(book, mutable.toImmutable());
        check(ISpellContainer.get(book).getActiveSpellCount() == spells,
                "the fixture book must hold " + spells + " spell(s), got "
                        + ISpellContainer.get(book).getActiveSpellCount());
        return book;
    }

    /** Drop a book into ISS's spellbook curios slot. */
    private static void equipSpellbook(ServerPlayer player, ItemStack book) {
        var inventory = CuriosApi.getCuriosInventory(player).orElse(null);
        check(inventory != null, "the joined player must have a Curios inventory");
        var slot = inventory.getCurios().get(Curios.SPELLBOOK_SLOT);
        check(slot != null,
                "the player must have ISS's spellbook slot (ISS registers it with Curios)");
        slot.getStacks().setStackInSlot(0, book);
    }

    /**
     * A real, connected ServerPlayer: constructed raw, then joined through
     * {@code PlayerList.placeNewPlayer} - the real join flow, which
     * transitions the connection to PLAY before login events fire.
     */
    private static ServerPlayer mockPlayer(GameTestHelper helper) {
        IssHotbar.LOGGER.info("spellbook-bar stage: mockPlayer building");
        var server = helper.getLevel().getServer();
        var profile = new GameProfile(UUID.randomUUID(), "AbilityIssBar");
        var player = new ServerPlayer(server, helper.getLevel(), profile,
                ClientInformation.createDefault());

        // A real netty channel so placeNewPlayer's packet writes land somewhere: a bare
        // Connection has a null channel and vanilla's first send NPEs. channelActive() assigns
        // Connection.channel, and the embedded channel supplies a non-null remote address for it.
        var embedded = new io.netty.channel.embedded.EmbeddedChannel();
        embedded.pipeline().addFirst(new io.netty.channel.ChannelInboundHandlerAdapter());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        try {
            connection.channelActive(embedded.pipeline().firstContext());
        } catch (Exception ignored) {
            // Best effort - the placement fallback below covers a partial join.
        }

        // NeoForge validates every custom payload against the connection's negotiated channels,
        // and our mock never ran the handshake - ad-hoc channels are the override.
        var channels = net.neoforged.neoforge.network.registration.ChannelAttributes
                .getOrCreateAdHocChannels(connection);
        for (var id : new String[]{
                "sync_data", "set_icons", "sync_curios", "sync_stack",
                "sync_modifiers", "sync_render", "sync_active_state",
                "page", "break", "grabbed_item", "quick_move"}) {
            channels.add(ResourceLocation.fromNamespaceAndPath("curios", id));
        }
        channels.add(ResourceLocation.fromNamespaceAndPath("neoforge", "advanced_open_screen"));
        channels.add(ResourceLocation.fromNamespaceAndPath("neoforge", "sync_attachments"));
        // ISS pushes config/cooldown/mana/player syncs on login (observed:
        // SpellConfigManager.onDatapackSync during placeNewPlayer); the
        // full payload list, since any of them may fire on a join.
        for (var id : new String[]{
                "absorption_particles", "add_motion_to_player", "blood_siphon_particles",
                "cancel_cast", "cast", "cast_error", "entity_event", "equipment_changed",
                "fiery_explosion_particles", "flamethrower_particles", "fortify_area_particles",
                "frost_step_particles", "guiding_bolt_manager_start_tracking",
                "guiding_bolt_manager_stop_tracking", "heal_particles", "learn_spell",
                "oakskin_particles", "on_cast_finished", "on_cast_started", "on_client_cast",
                "open_eldritch_screen", "play_player_animation", "quick_cast",
                "regen_cloud_particles", "remove_recast", "scroll_forge_select_spell",
                "select_spell", "shockwave_particles", "sync_all_camera_shake",
                "sync_animation", "sync_camera_shake", "sync_casting_mob_aiming_data",
                "sync_config", "sync_cooldown", "sync_cooldowns", "sync_entity_data",
                "sync_mana", "sync_player_data", "sync_recast", "sync_recasts",
                "sync_targeting_data", "teleport_particles", "update_casting_state"}) {
            channels.add(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", id));
        }
        // Seeding echoes one snapshot per seed; unlisted, placeNewPlayer
        // aborts on the mock connection (observed: cascading failures far
        // from the seeding test itself).
        channels.add(ResourceLocation.fromNamespaceAndPath(
                "ability_hotbar", "bar_state_snapshot"));

        try {
            server.getPlayerList().placeNewPlayer(connection, player,
                    CommonListenerCookie.createInitial(profile, false));
            IssHotbar.LOGGER.info("spellbook-bar stage: placeNewPlayer returned");
        } catch (RuntimeException joinNoise) {
            IssHotbar.LOGGER.info(
                    "spellbook-bar stage: placeNewPlayer aborted ({}), fetching from the level",
                    joinNoise.getMessage());
            // If placement completed before the failure, the player is usable.
            for (var placed : helper.getLevel().players()) {
                if (placed.getUUID().equals(profile.getId())) {
                    return placed;
                }
            }
            throw new GameTestAssertException(
                    "placeNewPlayer failed before the player was placed: " + joinNoise);
        }
        return player;
    }

    /**
     * The spell tab's catalog (owner's tab brief): one definition per ISS
     * spell, built against the POPULATED registry (the ability-tensura
     * registration-timing pattern), every activation shape a real
     * vocabulary value, every declared cost an honest mana quote, the
     * school strip ordered from ISS's schools, the unknown context
     * never hiding a spell (visibility's never-guess), and the bundled
     * iron.wiki description riding every spell the page has.
     */
    @GameTest(template = "empty")
    public static void spellCatalogRegistersEveryIssSpell(GameTestHelper helper) {
        var registered = SpellCatalog.registered();
        long live = SpellRegistry.REGISTRY.stream().count();
        check(live > 0, "ISS must stage its spell registry");
        check(registered.size() == (int) live,
                "every ISS spell must register exactly once (registered="
                        + registered.size() + " of " + live + ")");
        java.util.Set<String> schools = new java.util.HashSet<>();
        int withDescription = 0;
        // the pane's live-cost answer (hotbar#193): a decoy baked list
        // makes an undeclared spell quote itself, so the loop below can
        // tell a real declaration from its absence
        var decoy = new org.bitwisemadness.abilitycore.api.AbilityDefinition(
                "test.baked",
                ResourceLocation.fromNamespaceAndPath("ability_iss", "baked"),
                SpellCatalog.CATEGORY,
                ActivationType.INSTANT,
                java.util.List.of(new org.bitwisemadness.abilitycore.api.ResourceCost(
                        "test:baked", 4242)),
                org.bitwisemadness.abilitycore.api.ConsumptionStrategies.ALL,
                org.bitwisemadness.abilitycore.api.AmountResolvers.STATIC,
                java.util.Set.of(),
                java.util.Map.of());
        for (ResourceLocation id : registered) {
            var definition = AbilityDiscovery.get(id).orElse(null);
            check(definition != null, "definition must resolve: " + id);
            check(definition.activationType() != ActivationType.UNKNOWN,
                    "a spell's cast shape must map to the vocabulary: " + id);
            if (definition.description().isPresent()) {
                withDescription++;
            }
            if ("acupuncture".equals(id.getPath())) {
                check(definition.description()
                                .map(prose -> prose.contains("blood needles"))
                                .orElse(false),
                        "the bundled iron.wiki prose must ride acupuncture's definition");
            }
            definition.tags().stream().filter(tag -> tag.startsWith("type:"))
                    .forEach(schools::add);
            for (org.bitwisemadness.abilitycore.api.ResourceCost cost : definition.costs()) {
                check(Double.isFinite(cost.amount()) && cost.amount() >= 0,
                        "a declared mana quote must be non-negative: " + id);
                check("irons_spellbooks:mana".equals(cost.resourceId()),
                        "costs must name ISS's own currency: " + id);
            }
            check(VisibilityHandlers.visible(null, id),
                    "the unknown context must never hide a spell: " + id);
            check(LiveCosts.forDisplay(id, decoy).stream()
                            .noneMatch(cost -> "test:baked".equals(cost.resourceId())),
                    "the pane must quote the spell's live cost, not a baked list: " + id);
        }
        check(schools.size() > 1,
                "schools must be per-spell, not one default (owner saw all "
                        + "Evocation): " + schools);
        check(withDescription >= 100,
                "the bundled iron.wiki prose must ride most definitions, got "
                        + withDescription);
        check(MenuTabs.barForTab(SpellMenuTab.TAB_ID)
                        .filter(SpellbookBarBridge.CURIO_BAR::equals)
                        .isPresent(),
                "the Spells tab's strip must show the spellbook's provider bar");
        var strip = MenuTabs.subCategoryOrder(SpellMenuTab.TAB_ID);
        check(!strip.isEmpty(), "the school strip must carry ISS's schools");
        check(strip.stream().anyMatch(tag -> tag.startsWith("type:")),
                "the strip must be type-tag shaped: " + strip);
        helper.succeed();
    }

    /**
     * The mastery declarations ride the catalog (ability-iss's
     * REQ-CORE-12 slice): every menu ability declares mastery with the
     * config-live gain handler, the ACTIVATION trigger, no static base,
     * its school's category share - and no COST effect yet (ISS's cost
     * read is player-less; the follow-up issue carries the evidence).
     */
    @GameTest(template = "empty")
    public static void spellMasteryDeclarationsFollowTheCatalog(GameTestHelper helper) {
        var registered = SpellCatalog.registered();
        check(!registered.isEmpty(), "ISS must stage its spell registry");
        check(MasteryGainHandlers.has(SpellMastery.GAIN_HANDLER),
                "the config-live gain handler must be registered at setup");
        double expectedGain = IssServerConfig.SPELL_XP_GAIN.get();
        int withSchool = 0;
        for (ResourceLocation id : registered) {
            check(MasteryDeclarations.has(id), "every menu ability declares mastery: " + id);
            var spec = MasteryDeclarations.get(id).orElseThrow();
            check(SpellMastery.GAIN_HANDLER.equals(spec.gainHandler()),
                    "the gain comes from the config-live handler: " + id);
            check(spec.baseGain() == 0.0,
                    "the static base is the handler's fail-closed fallback: " + id);
            check(spec.triggers().equals(java.util.Set.of(MasteryTrigger.ACTIVATION)),
                    "casts grant; nothing else is declared yet: " + id);
            check(spec.effects().keySet().equals(java.util.Set.of(
                            MasteryAxis.COST, MasteryAxis.CAST_TIME, MasteryAxis.POWER)),
                    "every spell declares the three implemented effect axes: " + id);
            check(spec.shares().size() <= 1, "at most one school per spell: " + id);
            for (var share : spec.shares()) {
                check(share.target().kind() == MasteryTarget.Kind.CATEGORY,
                        "grants route to the school category: " + id);
                check(Math.abs(share.share() - SpellMastery.SCHOOL_SHARE) < 1e-9,
                        "the school share is the declared constant: " + id);
                withSchool++;
            }
        }
        double answered = MasteryGainHandlers.get(SpellMastery.GAIN_HANDLER).orElseThrow()
                .gain(null, registered.get(0), MasteryTrigger.ACTIVATION);
        check(answered == expectedGain,
                "the gain handler must answer the live config value, got " + answered);
        check(withSchool > 0, "spells route to schools (the school read must not fail)");
        helper.succeed();
    }

    /**
     * The award end-to-end: a real ISS cast initiation against the mock
     * player lands the grant through the mixin - the single funnel every
     * cast path (bar quick-cast, native keybind, wheel) reaches - split
     * between the spell's own target and its school category. The bar's
     * quick-path selection state stays the author's in-game check.
     */
    @GameTest(template = "empty")
    public static void spellMasteryCastAwardsThroughTheMixin(GameTestHelper helper) {
        var registered = SpellCatalog.registered();
        check(!registered.isEmpty(), "ISS must stage its spell registry");

        var player = mockPlayer(helper);
        var magicData = io.redspace.ironsspellbooks.api.magic.MagicData
                .getPlayerMagicData(player);
        magicData.setMana(9999.0f);
        var data = MasteryAttachments.data(player);
        check(data.entries().isEmpty(), "a fresh player starts with no mastery XP");

        // Not every ISS spell cooperates in a harness: targeted spells run
        // their own pre-cast target helper (no target = refused). Walk the
        // catalog for one that initiates; a hostile registry lists why not.
        var refusals = new StringBuilder();
        ResourceLocation spellId = null;
        AbstractSpell spell = null;
        for (ResourceLocation candidate : registered) {
            IssHotbar.LOGGER.info("mastery probe: {}", candidate);
            AbstractSpell candidateSpell = SpellRegistry.REGISTRY.getOptional(
                    net.minecraft.resources.ResourceKey.create(
                            SpellRegistry.SPELL_REGISTRY_KEY, candidate)).orElse(null);
            if (candidateSpell == null) {
                refusals.append(candidate).append("=unresolvable; ");
                continue;
            }
            // the learning gate reads this store server-side (canBeCastedBy
            // -> isLearned -> getSyncedData); a fresh mock knows nothing
            magicData.getSyncedData().learnSpell(candidateSpell);
            int castLevel = Math.max(1, candidateSpell.getMinLevel());
            var gate = candidateSpell.canBeCastedBy(castLevel,
                    io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK,
                    magicData, player);
            if (!gate.isSuccess()) {
                refusals.append(candidate).append("=gate:")
                        .append(gate.message == null ? gate.type : gate.message.getString())
                        .append("; ");
                continue;
            }
            if (!candidateSpell.checkPreCastConditions(player.level(), castLevel, player,
                    magicData)) {
                refusals.append(candidate).append("=preconditions; ");
                continue;
            }
            if (candidateSpell.attemptInitiateCast(ItemStack.EMPTY, castLevel,
                    player.level(), player,
                    io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK,
                    true, "")) {
                IssHotbar.LOGGER.info("mastery probe: {} -> initiated", candidate);
                spellId = candidate;
                spell = candidateSpell;
                break;
            }
            refusals.append(candidate).append("=initiate-false; ");
        }
        check(spellId != null, "no ISS spell initiates in the harness: " + refusals);

        // the mixin already fired on that successful initiation: the
        // configured gain split by the school share - or all to self when
        // the school read answered nothing
        double gain = IssServerConfig.SPELL_XP_GAIN.get();
        boolean hasSchool = SpellCatalog.schoolOf(spell) != null;
        double expectedOwn = hasSchool ? gain * (1.0 - SpellMastery.SCHOOL_SHARE) : gain;
        double own = data.xp(MasteryTarget.ability(spellId).key());
        check(Math.abs(own - expectedOwn) < 1e-9,
                "the mixin awards the configured gain (self part), got " + own);
        if (hasSchool) {
            double routed = data.xp(
                    MasteryTarget.category(SpellCatalog.schoolOf(spell)).key());
            check(Math.abs(routed - gain * SpellMastery.SCHOOL_SHARE) < 1e-9,
                    "the school category receives its share, got " + routed);
        }
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /** A fraction assert with tolerance - SpotBugs FE, and the honest shape. */
    private static void checkFraction(double expected, java.util.Optional<Double> actual,
            String message) {
        check(actual.isPresent() && Math.abs(actual.get() - expected) < 1e-9,
                message + ", got " + actual);
    }

    /**
     * Mastery improves the spells (REQ-CORE-12, closing #28): every spec
     * declares COST/CAST_TIME/POWER, so a mastered player casts the same
     * spell faster, stronger and - at the very same mana - cheaper at the
     * bar than a fresh one. The three seams are asserted directly against
     * their methods (no full cast chain needed), each with the fresh
     * player as the native-bar control, and the posted cast event's
     * charge lands on the same rounded discount the check and the row
     * quote use.
     */
    @GameTest(template = "empty")
    public static void masteryEffectsImproveTheSpell(GameTestHelper helper) {
        var registered = SpellCatalog.registered();
        check(!registered.isEmpty(), "ISS must stage its spell registry");
        ServerPlayer mastered = mockPlayer(helper);
        ServerPlayer fresh = mockPlayer(helper);
        // the raw mock arrives creative - the mana gate skips creatives
        // (CREATIVE_MANA_COST off), which would make both halves of the
        // bar assertion vacuous: pin survival so only the price decides
        fresh.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        mastered.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);

        // a LONG spell with a real mana price that a mock can gate through
        // (non-targeted: checkPreCastConditions' target helper refuses
        // targeted spells without a world target)
        ResourceLocation spellId = null;
        AbstractSpell spell = null;
        StringBuilder refusals = new StringBuilder();
        for (ResourceLocation candidate : registered) {
            AbstractSpell s = SpellRegistry.REGISTRY.getOptional(
                    net.minecraft.resources.ResourceKey.create(
                            SpellRegistry.SPELL_REGISTRY_KEY, candidate)).orElse(null);
            if (s == null || s.getCastType() != CastType.LONG || s.getManaCost(1) < 30) {
                continue;
            }
            var masteredData = io.redspace.ironsspellbooks.api.magic.MagicData
                    .getPlayerMagicData(mastered);
            var freshData = io.redspace.ironsspellbooks.api.magic.MagicData
                    .getPlayerMagicData(fresh);
            masteredData.setMana(9999.0f);
            freshData.setMana(9999.0f);
            masteredData.getSyncedData().learnSpell(s);
            freshData.getSyncedData().learnSpell(s);
            if (!s.checkPreCastConditions(mastered.level(), 1, mastered, masteredData)) {
                refusals.append(candidate).append("=pre; ");
                continue;
            }
            if (!s.canBeCastedBy(1, io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK,
                    masteredData, mastered).isSuccess()) {
                refusals.append(candidate).append("=gate; ");
                continue;
            }
            spellId = candidate;
            spell = s;
            break;
        }
        if (spellId == null || spell == null) {
            throw new net.minecraft.gametest.framework.GameTestAssertException(
                    "no LONG mock-cooperative spell found; refusals: " + refusals);
        }

        // the fresh player's native numbers (the control for every seam)
        int baseTime = spell.getEffectiveCastTime(1, fresh);
        check(baseTime > 0, "the chosen spell must have cast time to cut, got " + baseTime);
        float basePower = spell.getSpellPower(1, fresh);
        int baseMana = spell.getManaCost(1);

        // grant mastery as a raw pool (skips the gain handler: the seams
        // only need an aggregate level above zero)
        MasteryAwards.award(mastered, spellId, MasteryTrigger.ACTIVATION, 1000.0);

        // CAST_TIME: strictly faster
        int fastTime = spell.getEffectiveCastTime(1, mastered);
        check(fastTime < baseTime,
                "mastery must cut the cast time, got " + fastTime + " vs " + baseTime);

        // POWER: strictly stronger
        float strongPower = spell.getSpellPower(1, mastered);
        check(strongPower > basePower,
                "mastery must raise the spell power, got " + strongPower + " vs " + basePower);

        // COST at the check: one below the native bar, only the mastered passes
        var masteredData = io.redspace.ironsspellbooks.api.magic.MagicData
                .getPlayerMagicData(mastered);
        var freshData = io.redspace.ironsspellbooks.api.magic.MagicData
                .getPlayerMagicData(fresh);
        masteredData.setMana(baseMana - 1);
        freshData.setMana(baseMana - 1);
        boolean freshPasses = spell.canBeCastedBy(1,
                io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK,
                freshData, fresh).isSuccess();
        check(!freshPasses,
                "the fresh player must be refused a bar they cannot pay natively"
                        + " (mana=" + freshData.getMana()
                        + " cost=" + spell.getManaCost(1)
                        + " mult=" + MasteryEffects.costMultiplier(fresh, spellId)
                        + " recast=" + freshData.getPlayerRecasts()
                                .hasRecastForSpell(spell.getSpellId())
                        + " creative=" + fresh.isCreative()
                        + " consumes=" + io.redspace.ironsspellbooks.api.spells.CastSource
                                .SPELLBOOK.consumesMana() + ")");
        check(spell.canBeCastedBy(1, io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK,
                        masteredData, mastered).isSuccess(),
                "the mastered player must pass the discounted bar at the same mana");

        // COST at the charge: the posted event lands on the same discount
        var freshEvent = new SpellOnCastEvent(fresh, spellId.toString(), 1, baseMana,
                spell.getSchoolType(),
                io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK);
        NeoForge.EVENT_BUS.post(freshEvent);
        check(freshEvent.getManaCost() == baseMana,
                "the fresh charge must stay native, got " + freshEvent.getManaCost());
        var masteredEvent = new SpellOnCastEvent(mastered, spellId.toString(), 1, baseMana,
                spell.getSchoolType(),
                io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK);
        NeoForge.EVENT_BUS.post(masteredEvent);
        int expectedCharge = (int) Math.round((double) baseMana
                * MasteryEffects.costMultiplier(mastered, spellId));
        check(masteredEvent.getManaCost() == expectedCharge,
                "the mastered charge must be the discounted bar, got "
                        + masteredEvent.getManaCost() + " expected " + expectedCharge);
        check(masteredEvent.getManaCost() < baseMana,
                "and strictly below the native price");
        helper.succeed();
    }

    /**
     * Mastery follows the SPELL, not the item (owner's persistence
     * brief): keyed per registry id, the pool survives the spell
     * leaving the book and coming back, an arcane-anvil level upgrade
     * (ISS rewrites the container's SpellData level in place), and a
     * second physical book holding the same spell reads and grows the
     * very same pool - two copies can never diverge.
     */
    @GameTest(template = "empty")
    public static void masteryIsPerSpellNotPerInstance(GameTestHelper helper) {
        var registered = SpellCatalog.registered();
        check(!registered.isEmpty(), "ISS must stage its spell registry");
        ServerPlayer player = mockPlayer(helper);

        AbstractSpell spell = SpellRegistry.ACUPUNCTURE_SPELL.get();
        ResourceLocation spellId = SpellRegistry.REGISTRY.getKey(spell);
        check(spellId != null, "the fixture spell must resolve to a registry id");

        // the config-live gain, routed the way every cast routes it
        double gain = IssServerConfig.SPELL_XP_GAIN.get();
        boolean hasSchool = SpellCatalog.schoolOf(spell) != null;
        double perCast = hasSchool ? gain * (1.0 - SpellMastery.SCHOOL_SHARE) : gain;
        check(perCast > 0.0, "the fixture gain must be positive, got " + perCast);
        var data = MasteryAttachments.data(player);

        // book A holds the spell; a cast's grant lands on the spell's id
        ItemStack bookA = spellBookWith(helper, 1, 4);
        equipSpellbook(player, bookA);
        MasteryAwards.award(player, spellId, MasteryTrigger.ACTIVATION);
        double own = data.xp(MasteryTarget.ability(spellId).key());
        check(Math.abs(own - perCast) < 1e-9,
                "the first cast must land the routed gain, got " + own);

        // (1) the spell leaves the book: nothing may touch the pool
        var mutable = ISpellContainer.get(bookA).mutableCopy();
        check(mutable.removeSpell(spell), "the fixture spell must be removable");
        ISpellContainer.set(bookA, mutable.toImmutable());
        check(ISpellContainer.get(bookA).getActiveSpellCount() == 0,
                "the book must really be empty now");
        check(Math.abs(data.xp(MasteryTarget.ability(spellId).key()) - own) < 1e-9,
                "removing the spell from the book must not touch its mastery");

        // (2) ... and placed back: the same pool answers
        mutable = ISpellContainer.get(bookA).mutableCopy();
        check(mutable.addSpell(spell, 1, false), "the spell must be re-addable");
        ISpellContainer.set(bookA, mutable.toImmutable());
        check(Math.abs(data.xp(MasteryTarget.ability(spellId).key()) - own) < 1e-9,
                "re-adding the spell must find its old mastery");

        // (3) the arcane anvil's upgrade (level 1 -> 2 in the container):
        // the pool is untouched and the next cast grants the SAME gain -
        // the handler is config-flat, a level never scales the award
        mutable = ISpellContainer.get(bookA).mutableCopy();
        check(mutable.removeSpell(spell), "the spell must lift out for the anvil");
        check(mutable.addSpell(spell, 2, false), "the upgraded spell must fit");
        ISpellContainer.set(bookA, mutable.toImmutable());
        int idx = ISpellContainer.get(bookA).getIndexForSpell(spell);
        check(idx >= 0 && ISpellContainer.get(bookA).getSpellAtIndex(idx).getLevel() == 2,
                "the anvil's level upgrade must really land in the container");
        check(Math.abs(data.xp(MasteryTarget.ability(spellId).key()) - own) < 1e-9,
                "the level upgrade must not touch its mastery");
        MasteryAwards.award(player, spellId, MasteryTrigger.ACTIVATION);
        double afterSecond = data.xp(MasteryTarget.ability(spellId).key());
        check(Math.abs(afterSecond - own - perCast) < 1e-9,
                "the upgraded spell must still grant exactly the config gain, got "
                        + afterSecond);

        // (4) a SECOND physical book with the same spell: one id, one pool -
        // what book A's copy grew is what book B's copy reads
        ItemStack bookB = spellBookWith(helper, 1, 4);
        equipSpellbook(player, bookB);
        check(Math.abs(data.xp(MasteryTarget.ability(spellId).key()) - afterSecond) < 1e-9,
                "two books holding the same spell must share one mastery pool");
        MasteryAwards.award(player, spellId, MasteryTrigger.ACTIVATION);
        double afterThird = data.xp(MasteryTarget.ability(spellId).key());
        check(Math.abs(afterThird - afterSecond - perCast) < 1e-9,
                "a cast through the second copy must grow the same pool, got "
                        + afterThird);

        // the player-facing view reads that identical pool
        var view = MasteryView.of(player, spellId);
        check(Math.abs(view.ownXp() - afterThird) < 1e-9,
                "the mastery view must read the same pool, got " + view.ownXp());
        helper.succeed();
    }
}
