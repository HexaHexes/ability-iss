package org.bitwisemadness.abilityiss;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.core.Registry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.registry.AbilityRegistries;
import org.bitwisemadness.abilityiss.spell.SpellCatalog;
import org.bitwisemadness.abilityiss.spell.SpellMenuTab;
import org.bitwisemadness.abilityiss.spell.SpellProviders;

/**
 * The spell tab's mod-bus event half (owner's tab brief). Routing is
 * not spelled out: {@code AutomaticEventSubscriber} places each listener
 * from its event type - both here are mod-bus events.
 *
 * <p>Registration fires only for <b>ISS's own spell registry event</b>
 * and writes with raw {@link Registry#register} into core's live
 * registry - the ability-tensura pattern its timing gametest settled:
 * definitions must build against populated upstream entries, core's own
 * event runs too early for foreign registries, and NeoForge's
 * {@code RegisterEvent} source shows cross-key {@code register} is a
 * silent no-op with eager suppliers (registries stay open until
 * {@code freezeData()} runs after every event). Dependency order puts
 * ISS's own spell listener ahead of this one.</p>
 *
 * <p>Setup then declares the tab's providers (found/held visibility,
 * held-only grey) and the informational tab with its school strip.</p>
 */
@EventBusSubscriber(modid = IssHotbar.MOD_ID)
public final class SpellTabEvents {

    private SpellTabEvents() {
    }

    /** One definition per ISS spell, at the moment the spells exist. */
    @SubscribeEvent
    public static void onRegisterAbilities(RegisterEvent event) {
        if (!event.getRegistryKey().equals(SpellRegistry.SPELL_REGISTRY_KEY)) {
            return;
        }
        Registry<AbilityDefinition> registry = AbilityRegistries.registry();
        if (registry == null) {
            IssHotbar.LOGGER.error(
                    "core's ability registry is missing - spell catalog not registered");
            return;
        }
        SpellCatalog.register(registry);
    }

    /** Providers + tab after every spell id is tracked (registry phase precedes setup). */
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        SpellProviders.declareAll();
        SpellMenuTab.register();
    }
}
