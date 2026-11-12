package org.bitwisemadness.abilityiss.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The cache's pure half (owner's periodic-check brief, option B): the
 * HTML parser keeps the checked-in generator's rules, the cache reader
 * trusts only floor-passed files (a gutted cache never beats the
 * bundle), and the writer round-trips in the bundled file's provenance
 * shape.
 */
class WikiDescriptionsTest {

    @TempDir
    Path temp;

    private static String container(String id, String prose) {
        return "<div class=\"spell-container\">"
                + "<h3 id=\"x\"><img src=\"/img/spells/" + id + ".png\"></h3>"
                + "<div style=\"margin-top: 7px; margin-left: 5px; margin-right: 10px\">"
                + prose + "</div></div>";
    }

    /** A page of plain containers: ids spell_0 .. spell_(count-1). */
    private static String page(int count) {
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < count; i++) {
            html.append(container("spell_" + i, "Prose for spell " + i + "."));
        }
        return html.toString();
    }

    @Test
    void parseKeepsTheProseRules() {
        StringBuilder html = new StringBuilder(page(98));
        html.append(container("spell_98", "A <b>bold</b> claim, stripped <i>clean</i>."));
        html.append(container("spell_99", "<ul><li>first<li>second</ul>"));
        Map<String, String> spells = WikiDescriptions.parseSpells(html.toString());
        assertEquals(WikiDescriptions.MIN_SPELLS, spells.size(), "the floor's worth");
        assertEquals("A bold claim, stripped clean .", spells.get("spell_98"),
                "tags are stripped to spaces (byte-parity with the PS generator, "
                        + "which collapses whitespace the same way)");
        assertEquals("- first\n- second", spells.get("spell_99"),
                "bullet lists keep their lines");
        assertTrue(spells.get("spell_0").startsWith("Prose for spell 0"),
                "plain prose rides verbatim");
    }

    @Test
    void parseFailsLoudlyBelowTheFloor() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> WikiDescriptions.parseSpells(page(WikiDescriptions.MIN_SPELLS - 1)));
        assertTrue(failure.getMessage().contains("markup"),
                "a markup change must say so: " + failure.getMessage());
    }

    @Test
    void readCacheTrustsOnlyFloorPassedFiles() throws Exception {
        Path cache = temp.resolve("cache.json");
        assertNull(WikiDescriptions.readCache(cache), "absent = no cache");

        Map<String, String> full = new LinkedHashMap<>();
        for (int i = 0; i < WikiDescriptions.MIN_SPELLS; i++) {
            full.put("irons_spellbooks:spell_" + i, "Prose " + i + ".");
        }
        WikiDescriptions.writeCache(cache, full, null);
        Map<String, String> read = WikiDescriptions.readCache(cache);
        assertEquals(WikiDescriptions.MIN_SPELLS, read.size(), "a full cache wins");
        assertFalse(read.containsKey("_source"), "provenance stays metadata");

        Path junk = temp.resolve("junk.json");
        Files.writeString(junk, "{ not json", StandardCharsets.UTF_8);
        assertNull(WikiDescriptions.readCache(junk), "malformed = no cache");

        Path shortFile = temp.resolve("short.json");
        Map<String, String> shortMap = new LinkedHashMap<>();
        shortMap.put("irons_spellbooks:spell_0", "Prose.");
        WikiDescriptions.writeCache(shortFile, shortMap, null);
        assertNull(WikiDescriptions.readCache(shortFile),
                "below the floor = no cache (a gutted file never beats the bundle)");
    }

    @Test
    void writeCacheRoundTripsWithProvenanceAndEtag() throws Exception {
        Path cache = temp.resolve("cache.json");
        Map<String, String> spells = new LinkedHashMap<>();
        for (int i = 0; i < WikiDescriptions.MIN_SPELLS; i++) {
            spells.put("irons_spellbooks:spell_" + i, "Prose " + i + ".");
        }
        WikiDescriptions.writeCache(cache, spells, "\"etag-123\"");
        String raw = Files.readString(cache, StandardCharsets.UTF_8);
        assertTrue(raw.startsWith("{\n  \"_source\": \"https://iron.wiki/spells/\","),
                "the bundled shape, provenance first");
        assertTrue(raw.contains("\"_retrieved\": \""),
                "the retrieval date rides along");
        assertFalse(raw.startsWith("\uFEFF"), "no BOM");
        assertEquals("\"etag-123\"", WikiDescriptions.readEtag(cache),
                "the etag survives for the next If-None-Match");
        assertEquals(WikiDescriptions.MIN_SPELLS,
                WikiDescriptions.readCache(cache).size(), "and the file reads back");
    }
}
