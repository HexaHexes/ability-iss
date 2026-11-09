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

    /**
     * The OverlayHandler element id of Tensura's ABILITIES layer - the
     * belt-and-braces switch sits behind it (ARR's own tableswitch: 0
     * status, 1 status bars, 2 abilities, 3 analysis), configurable so a
     * Tensura update that renumbers the layers cannot silently disable
     * the wrong row.
     */
    public static final ModConfigSpec.IntValue TENSURA_ABILITIES_LAYER_ID =
            BUILDER.comment("OverlayHandler element id of Tensura's abilities layer "
                    + "(0 status, 1 status bars, 2 abilities, 3 analysis)")
                    .defineInRange("hud.tensuraAbilitiesLayerId", 2, 0, 15);

    /** Whether the swap also switches the abilities layer element off. */
    public static final ModConfigSpec.BooleanValue DISABLE_TENSURA_ABILITIES_LAYER =
            BUILDER.comment("Also switch Tensura's abilities layer element off - the "
                    + "belt-and-braces beside its own config flag")
                    .define("hud.disableTensuraAbilitiesLayer", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private IssClientConfig() {
    }
}
