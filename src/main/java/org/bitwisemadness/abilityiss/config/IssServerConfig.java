package org.bitwisemadness.abilityiss.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * ISS's server policy (the fleet audit's iss entry):
 * {@code ability_iss-server.toml} - auto-synced, per-world overridable,
 * the only authority channel.
 *
 * <p><b>Read timing:</b> SERVER configs load at server start, so every
 * key here is read at <b>use time</b> (a menu listing, a held scan, a
 * cell seed) - never at registration. These keys answer on both sides
 * (the menu asks client-side through the synced copy; assignment
 * validation asks server-side), which is exactly why they are SERVER and
 * not CLIENT.</p>
 *
 * <p>Section names are deliberately {@code tab}, {@code spellbook} and
 * {@code held}: the config screen labels entries by leaf name, so they
 * must not collide with the CLIENT spec's {@code menu}/{@code bar}.</p>
 */
public final class IssServerConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** Whether the Spells tab lists every registered spell. */
    public static final ModConfigSpec.BooleanValue SHOW_ALL_SPELLS =
            BUILDER.comment("List every registered spell in the Spells tab (off = only "
                            + "spells the player has encountered, or is holding)")
                    .define("tab.showAllSpells", false);

    /** Whether an unheld spell's row greys out. */
    public static final ModConfigSpec.BooleanValue GREY_UNHELD_ROWS =
            BUILDER.comment("Grey a Spells-tab row whose spell is not being held right now "
                            + "(off = rows never grey for holding)")
                    .define("tab.greyUnheldRows", true);

    /** The spellbook bar's cell ceiling. */
    public static final ModConfigSpec.IntValue MAX_CELLS =
            BUILDER.comment("Highest number of cells the spellbook bar may show "
                            + "(server ceiling; the book's own capacity clamps below it)")
                    .defineInRange("spellbook.maxCells", 18, 1, 18);

    /** Whether curios slots count as holding a spell. */
    public static final ModConfigSpec.BooleanValue HELD_INCLUDE_CURIOS =
            BUILDER.comment("Curios slots count as holding a spell (grey/listing scans)")
                    .define("held.includeCurios", true);

    /** Whether armor slots count as holding a spell. */
    public static final ModConfigSpec.BooleanValue HELD_INCLUDE_ARMOR =
            BUILDER.comment("Armor slots count as holding a spell (the offhand always does)")
                    .define("held.includeArmor", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private IssServerConfig() {
    }
}
