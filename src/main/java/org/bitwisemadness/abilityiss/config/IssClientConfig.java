package org.bitwisemadness.abilityiss.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * ISS's client preferences (the fleet audit's first iss entry):
 * display-only, never synced, never policy. The HUD swap toggle is the
 * owner's directive - when ability-tensura is ALSO loaded, Tensura's
 * ability/charging row gives way to ISS's own overlays (its cast bar,
 * spell bar and mana - the ISS look).
 */
public final class IssClientConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** The HUD swap master switch (default on - the directive's state). */
    public static final ModConfigSpec.BooleanValue REPLACE_TENSURA_CHARGING_HUD =
            BUILDER.comment("Replace Tensura's ability charging HUD with ISS's overlays "
                    + "when ability-tensura is loaded")
                    .define("hud.replaceTensuraCharging", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private IssClientConfig() {
    }
}
