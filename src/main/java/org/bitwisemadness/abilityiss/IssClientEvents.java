package org.bitwisemadness.abilityiss;

import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.tensura.config.client.HudConfig;
import io.github.manasmods.tensura.handler.client.OverlayHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.bitwisemadness.abilityiss.config.IssClientConfig;

/**
 * The client-only half of the HUD swap (owner: if both ability mods are
 * loaded, replace Tensura's ability charging HUD with ISS's). The
 * subscriber is dist-scoped so this class never loads on a server, and
 * every Tensura-typed read sits behind the {@code isLoaded} gate
 * executed on the first tick - the guarded-callsite pattern.
 *
 * <p>The swap turns <b>ARR's own switch</b>: {@code HudConfig.abilities}
 * is read per frame across OverlayHandler, so its in-memory
 * {@code render = false} retires the ability/charging row for this
 * session (never written back to ARR's config file), and the ABILITIES
 * layer element is switched off as the belt-and-braces. ISS's own
 * overlays - already rendering - become the ability HUD. Applied once
 * config values are readable (first tick), and only while the owner's
 * toggle says so, so flipping the config later in the session still
 * takes effect.</p>
 */
@EventBusSubscriber(modid = IssHotbar.MOD_ID, bus = EventBusSubscriber.Bus.GAME,
        value = Dist.CLIENT)
public final class IssClientEvents {

    private static boolean hudSwapApplied;

    private IssClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (hudSwapApplied) {
            return;
        }
        if (!IssClientConfig.REPLACE_TENSURA_CHARGING_HUD.get()) {
            return; // owner's toggle off - keep checking (mid-session enable)
        }
        if (ModList.get() == null || !ModList.get().isLoaded("tensura")) {
            hudSwapApplied = true; // absent forever: nothing to replace
            return;
        }
        applyTensuraSwap();
        hudSwapApplied = true;
    }

    /** Retires Tensura's ability row through ARR's own config + element. */
    private static void applyTensuraSwap() {
        try {
            HudConfig hud = ConfigRegistry.getConfig(HudConfig.class);
            if (hud != null && hud.abilities != null) {
                hud.abilities.render = false; // in-memory: re-applied each launch
            }
        } catch (RuntimeException unreadable) {
            IssHotbar.LOGGER.warn(
                    "could not flip Tensura's abilities config, falling back to the layer");
        }
        // getElementById(2) is OverlayHandler's ABILITIES layer (its
        // tableswitch: 0 status, 1 status bars, 2 abilities, 3 analysis)
        var abilities = OverlayHandler.getElementById(2);
        if (abilities != null) {
            abilities.setShouldRender(false);
        }
    }
}
