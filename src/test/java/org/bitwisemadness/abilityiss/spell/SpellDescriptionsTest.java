package org.bitwisemadness.abilityiss.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The bundled iron.wiki descriptions (owner's description brief): the
 * classpath resource parses, ships provenance, and rides the id -> prose
 * map keyed by namespaced spell ids. Every prose value contains a space -
 * hotbar's pane reads a space-less value as a translation key and would
 * never render it - and spells the wiki lacks stay description-less.
 */
class SpellDescriptionsTest {

    private static final String RESOURCE = "/assets/ability_iss/spell_descriptions.json";

    @Test
    void bundledDescriptionsLoadKeyedByNamepacedIds() {
        var descriptions = SpellCatalog.descriptions();
        assertFalse(descriptions.isEmpty(), "the bundled resource must load");
        assertTrue(descriptions.size() >= 100,
                "the wiki's prose must ride most spells, got " + descriptions.size());
        descriptions.forEach((id, prose) -> {
            assertTrue(id.startsWith("irons_spellbooks:"),
                    "namespaced spell ids only, got " + id);
            assertFalse(prose.isBlank(), "no blank prose for " + id);
            assertFalse(prose.matches("[a-z0-9_]+\\.[a-z0-9_.]+"),
                    "prose shaped like a translation key would be swallowed by the "
                            + "pane's key path and never render: " + id);
        });
    }

    @Test
    void provenanceShipsButNeverBecomesASpell() throws Exception {
        try (var in = SpellDescriptionsTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in, "the resource must ship on the test classpath");
            var root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("https://iron.wiki/spells/",
                    root.get("_source").getAsString(), "the source is attributed");
            assertEquals("2026-11-12", root.get("_retrieved").getAsString(),
                    "the retrieval date is recorded");
        }
        var descriptions = SpellCatalog.descriptions();
        assertFalse(descriptions.containsKey("_source"),
                "metadata keys never become spell entries");
        assertFalse(descriptions.containsKey("_retrieved"),
                "metadata keys never become spell entries");
    }

    @Test
    void knownProseRidesTheIdsAndUnmatchedSpellsStayAbsent() {
        var descriptions = SpellCatalog.descriptions();
        assertTrue(descriptions.get("irons_spellbooks:acupuncture").contains("blood needles"),
                "the wiki prose must ride its id, got "
                        + descriptions.get("irons_spellbooks:acupuncture"));
        assertTrue(descriptions.get("irons_spellbooks:counterspell").startsWith("- "),
                "a bullet-list spell keeps its lines");
        assertNull(descriptions.get("irons_spellbooks:none"),
                "placeholder ids never ship prose");
        assertNull(descriptions.get("irons_spellbooks:thunder_step"),
                "a spell the wiki lacks stays description-less");
        assertNull(descriptions.get("irons_spellbooks:void_tentacles"),
                "a spell the wiki lacks stays description-less");
    }
}
