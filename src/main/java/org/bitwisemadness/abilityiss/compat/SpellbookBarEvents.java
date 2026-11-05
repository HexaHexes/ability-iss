package org.bitwisemadness.abilityiss.compat;

import io.redspace.ironsspellbooks.api.events.InscribeSpellEvent;
import io.redspace.ironsspellbooks.compat.Curios;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.bitwisemadness.abilityiss.IssHotbar;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

/**
 * The spellbook bar's event half (#3): registration at common setup (bar,
 * scope, handler, availability) and the definition on core's registry, plus
 * re-seeding whenever the bar's content truth changes - player login, the
 * book's contents changing (inscription), and equipping or unequipping the
 * spellbook itself. Signatures are vanilla/NeoForge/ISS/curios types only,
 * and every listener is gated on the event it needs.
 *
 * <p>Routing is not spelled out: {@code AutomaticEventSubscriber} places
 * each listener from its event type - {@code FMLCommonSetupEvent} and
 * {@code RegisterEvent} on the mod bus, the rest on the game bus.</p>
 */
@EventBusSubscriber(modid = IssHotbar.MOD_ID)
public final class SpellbookBarEvents {

    private SpellbookBarEvents() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        SpellbookBarBridge.register();
        SpellbookBarBridge.registerHandlers();
    }

    @SubscribeEvent
    public static void onRegisterAbilityRegistry(RegisterEvent event) {
        SpellbookBarBridge.registerDefinitions(event);
    }

    /** Login seeds the bar before the player can wheel to it (#3). */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpellbookBarBridge.seed(player);
        }
    }

    /**
     * The book's contents changed (#3): inscription carries the player but
     * no stack, so the re-seed is whole-bar and idempotent - Curios stays
     * the source of truth for what the cells show.
     */
    @SubscribeEvent
    public static void onInscribe(InscribeSpellEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpellbookBarBridge.seed(player);
        }
    }

    /**
     * Equip/unequip re-derives the whole bar (a cheap idempotent
     * overwrite). Filtered to ISS's spellbook slot - every other curios
     * slot in the game also fires this event.
     */
    @SubscribeEvent
    public static void onCurioChange(CurioChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (Curios.SPELLBOOK_SLOT.equals(event.getIdentifier())) {
            SpellbookBarBridge.seed(player);
        }
    }
}
