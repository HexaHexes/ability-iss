package org.bitwisemadness.abilityiss;

import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.tensura.client.screen.widgets.OverlayElement;
import io.github.manasmods.tensura.config.client.HudConfig;
import io.github.manasmods.tensura.handler.client.OverlayHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.bitwisemadness.abilityiss.client.WikiDescriptionRefresh;
import org.bitwisemadness.abilityiss.config.IssClientConfig;

/**
 * The client-only half of the HUD swap (owner: if both ability mods are
 * loaded, replace Tensura's ability charging HUD with ISS's). The
 * subscriber is dist-scoped so this class never loads on a server, and
 * every Tensura-typed read sits behind the {@code isLoaded} gate - the
 * guarded-callsite pattern.
 *
 * <p>The swap turns <b>ARR's own switch</b>: {@code HudConfig.abilities}
 * is read per frame across OverlayHandler, so its in-memory
 * {@code render = false} retires the ability/charging row for this
 * session (never written back to ARR's config file), and the ABILITIES
 * layer element is switched off as the belt-and-braces (its element id
 * is {@code hud.tensuraAbilitiesLayerId}, its participation behind
 * {@code hud.disableTensuraAbilitiesLayer}). ISS's own overlays -
 * already rendering - become the ability HUD.</p>
 *
 * <p>The owner's toggle is <b>live in both directions</b> (the audit's
 * finding: the first version latched after applying, so turning it off
 * mid-session never gave Tensura's row back): each tick compares the
 * toggle with what is actually applied, applies on a rising edge and
 * <b>restores ARR's own recorded answers</b> on a falling edge - a value
 * ARR had set to off before the swap stays off after it.</p>
 */
@EventBusSubscriber(modid = IssHotbar.MOD_ID, bus = EventBusSubscriber.Bus.GAME,
        value = Dist.CLIENT)
public final class IssClientEvents {

    /** What was applied, so restoring writes ARR's own answers back. */
    private static boolean hudSwapApplied;
    private static boolean configTouched;
    private static boolean originalAbilitiesRender = true;
    private static OverlayElement swappedLayer;
    private static boolean originalLayerRender = true;
    private static boolean tensuraAbsent;

    private IssClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        WikiDescriptionRefresh.onClientTick(); // before the tensura-only gate below
        if (tensuraAbsent) {
            return; // absent forever: nothing to replace
        }
        if (ModList.get() == null || !ModList.get().isLoaded("tensura")) {
            tensuraAbsent = true;
            return;
        }
        boolean wanted = IssClientConfig.REPLACE_TENSURA_CHARGING_HUD.get();
        if (wanted == hudSwapApplied) {
            return; // applied and wanted, or off and not applied
        }
        if (wanted) {
            applyTensuraSwap();
        } else {
            restoreTensuraSwap();
        }
    }

    /** Retires Tensura's ability row through ARR's own config + element. */
    private static void applyTensuraSwap() {
        try {
            HudConfig hud = ConfigRegistry.getConfig(HudConfig.class);
            if (hud != null && hud.abilities != null) {
                originalAbilitiesRender = hud.abilities.render; // ARR's own answer
                hud.abilities.render = false; // in-memory: re-applied each launch
                configTouched = true;
            }
        } catch (RuntimeException unreadable) {
            IssHotbar.LOGGER.warn(
                    "could not flip Tensura's abilities config, falling back to the layer");
        }
        OverlayElement abilities = abilitiesLayer();
        if (abilities != null && IssClientConfig.DISABLE_TENSURA_ABILITIES_LAYER.get()) {
            swappedLayer = abilities;
            originalLayerRender = abilities.isShouldRender();
            abilities.setShouldRender(false);
        }
        hudSwapApplied = true;
    }

    /** Puts ARR's own answers back - the toggle works in both directions. */
    private static void restoreTensuraSwap() {
        if (configTouched) {
            try {
                HudConfig hud = ConfigRegistry.getConfig(HudConfig.class);
                if (hud != null && hud.abilities != null) {
                    hud.abilities.render = originalAbilitiesRender;
                }
            } catch (RuntimeException unreadable) {
                IssHotbar.LOGGER.warn("could not restore Tensura's abilities config");
            }
            configTouched = false;
        }
        if (swappedLayer != null) {
            swappedLayer.setShouldRender(originalLayerRender);
            swappedLayer = null;
        }
        hudSwapApplied = false;
    }

    /** Tensura's ABILITIES layer element, or null (absent/unreadable). */
    private static OverlayElement abilitiesLayer() {
        try {
            // getElementById reads OverlayHandler's table: 0 status,
            // 1 status bars, 2 abilities, 3 analysis (the configured id)
            return OverlayHandler.getElementById(
                    IssClientConfig.TENSURA_ABILITIES_LAYER_ID.get());
        } catch (RuntimeException unreadable) {
            return null;
        }
    }
}
