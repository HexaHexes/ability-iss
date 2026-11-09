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

    // --- the Spells tab's display (menu.*; registration reads go through
    // core's readOrDefault because common setup also runs server-side) ---

    /** The tab's icon (empty = the shipped Divine Pearl). */
    public static final ModConfigSpec.ConfigValue<String> TAB_ICON =
            BUILDER.comment("The Spells tab's icon as an item id (empty = the Divine Pearl)")
                    .define("menu.icon", "irons_spellbooks:divine_pearl");

    /** The tab's menu sprite (empty = none: a text-only label). */
    public static final ModConfigSpec.ConfigValue<String> TAB_SPRITE =
            BUILDER.comment("The Spells tab's menu sprite as a texture path (empty = none)")
                    .define("menu.sprite", "");

    /** The school chip texture template (%s = the school path). */
    public static final ModConfigSpec.ConfigValue<String> SCHOOL_CHIP_TEXTURE =
            BUILDER.comment("School chip texture template, %s replaced by the school path "
                            + "(empty = no chip icons)")
                    .define("menu.schoolChipTexture",
                            "irons_spellbooks:textures/item/scroll_%s");

    /** Whether rows quote the spell's mana cost - DEFERRED: the cost is
     * baked into the definition at RegisterEvent, which runs before
     * config loads (the audit's documented decline). */

    /** The school strip's explicit order (empty = the registry's order). */
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> SCHOOL_ORDER =
            BUILDER.comment("School order for the tab's filter strip (empty = ISS's "
                            + "registry order); entries are type:<school> tags")
                    .defineListAllowEmpty("menu.schoolOrder", java.util.List.of(),
                            value -> value instanceof String text && !text.isBlank());

    // --- the spellbook bar's display (bar.*) ---

    /** Whether cells draw their cooldown sweep. */
    public static final ModConfigSpec.BooleanValue SHOW_COOLDOWN_SWEEP =
            BUILDER.comment("Draw the cooldown readiness sweep on spellbook cells")
                    .define("bar.showCooldownSweep", true);

    /** Whether the bar hides when the book has no castable spells. */
    public static final ModConfigSpec.BooleanValue HIDE_WHEN_EMPTY =
            BUILDER.comment("Hide the spellbook bar when the equipped book has no "
                            + "castable spells (off = it always shows)")
                    .define("bar.hideWhenEmpty", true);

    /** The Cast verb's icon texture - DEFERRED: baked at RegisterEvent
     * (the audit's documented decline), same as the mana quote. */

    public static final ModConfigSpec SPEC = BUILDER.build();

    private IssClientConfig() {
    }
}
