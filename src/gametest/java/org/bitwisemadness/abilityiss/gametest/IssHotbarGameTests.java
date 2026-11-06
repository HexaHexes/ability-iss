package org.bitwisemadness.abilityiss.gametest;

import com.mojang.authlib.GameProfile;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
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
import org.bitwisemadness.abilitycore.api.AvailabilityState;
import org.bitwisemadness.abilityhotbar.content.HotbarContentProviders;
import org.bitwisemadness.abilityhotbar.registry.AbilityBarScopes;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;
import org.bitwisemadness.abilityhotbar.state.SeededAssignments;
import org.bitwisemadness.abilityiss.IssHotbar;
import org.bitwisemadness.abilityiss.SpellCellOverlays;
import org.bitwisemadness.abilityiss.compat.SpellbookBarBridge;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

/**
 * GameTests for the spellbook bar (#3): the seam (registration, scope,
 * provider), the seeding contract (a worn book seeds its cells, an
 * unworn one clears them) and the cast handler's fail-closed refusals.
 *
 * <p>The success path of a cast is deliberately not asserted here: it
 * needs ISS's mana and selection state end-to-end, which belongs to the
 * author's in-game check (#3's acceptance) - these tests pin the wiring
 * and the refusals the pipeline must never get wrong.</p>
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
}
