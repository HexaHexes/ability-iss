package org.bitwisemadness.abilityiss.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.bitwisemadness.abilitycore.config.ConfigDiagnostics;

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

    /** XP a spell grants per successful cast (read live at award time). */
    public static final ModConfigSpec.DoubleValue SPELL_XP_GAIN =
            BUILDER.comment("XP one spell cast grants (read live when the award lands, "
                            + "never at registration): the spell routes its school share "
                            + "to the school's mastery ability - packs retune the share or "
                            + "any other spec field through core's mastery_spec datapack overlay")
                    .defineInRange("mastery.spellXpGain", 10.0, 0.0, 1000000.0);

    /** The share of each spell grant routed to its school's mastery ability. */
    public static final ModConfigSpec.DoubleValue SCHOOL_SHARE =
            BUILDER.comment("Share of every spell's mastery grant routed to its school's "
                            + "mastery ability (0.20 = 20%). The same share weights the "
                            + "school ability's level into that spell's aggregate - one "
                            + "number both ways, so the school level feeds the spell's "
                            + "mastery bonuses at the same rate it is fed. A reload "
                            + "re-declares the shares: the config applies to the next cast")
                    .defineInRange("mastery.schoolShare", 0.20, 0.0, 1.0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    /**
     * The school share, safe at any time: the shipped default until the
     * server config loads, the file's value after. The registry-phase
     * declaration and the config-reload re-declaration both read through
     * here, so the declared shares can never disagree with the file.
     *
     * @return the share in {@code [0, 1]} (default 0.20)
     */
    public static double schoolShare() {
        return ConfigDiagnostics.readOrDefault("mastery.schoolShare",
                () -> SCHOOL_SHARE.get(), 0.20);
    }

    private IssServerConfig() {
    }
}
