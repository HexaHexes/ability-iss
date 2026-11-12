package org.bitwisemadness.abilityiss.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.bitwisemadness.abilityiss.spell.WikiDescriptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The fetch's decision (owner's periodic-check brief, option B): only a
 * 200 whose body parses above the floor is written - errors, empty
 * bodies and gutted markup all leave the cache untouched, and ids land
 * namespaced exactly like the bundled file.
 */
class WikiDescriptionRefreshTest {

    @TempDir
    Path temp;

    private static String container(String id, String prose) {
        return "<div class=\"spell-container\">"
                + "<h3 id=\"x\"><img src=\"/img/spells/" + id + ".png\"></h3>"
                + "<div style=\"margin-top: 7px; margin-left: 5px; margin-right: 10px\">"
                + prose + "</div></div>";
    }

    private static String page(int count) {
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < count; i++) {
            html.append(container("spell_" + i, "Prose for spell " + i + "."));
        }
        return html.toString();
    }

    @Test
    void ingestWritesOnlyTrustedAnswers() throws Exception {
        Path cache = temp.resolve("cache.json");

        assertFalse(WikiDescriptionRefresh.ingest(cache, 500, page(100), null),
                "an error status writes nothing");
        assertFalse(Files.exists(cache), "no file after a 500");

        assertFalse(WikiDescriptionRefresh.ingest(cache, 200,
                "<html>no spells on this page</html>", null),
                "markup below the floor writes nothing");
        assertFalse(Files.exists(cache), "no file after a gutted parse");

        assertFalse(WikiDescriptionRefresh.ingest(cache, 200, "  ", null),
                "an empty body writes nothing");
        assertFalse(Files.exists(cache), "no file after an empty body");

        assertTrue(WikiDescriptionRefresh.ingest(cache, 200, page(100), "\"v1\""),
                "a full parse writes the cache");
        Map<String, String> read = WikiDescriptions.readCache(cache);
        assertEquals(WikiDescriptions.MIN_SPELLS, read.size(),
                "the cache is readable and floor-passed");
        assertTrue(read.containsKey("irons_spellbooks:spell_0"),
                "ids arrive namespaced exactly like the bundled file");
        assertEquals("\"v1\"", WikiDescriptions.readEtag(cache),
                "the etag rides along for the next If-None-Match");
    }
}
