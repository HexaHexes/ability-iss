package org.bitwisemadness.abilityiss.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig.Entry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

/**
 * Guards the config screen's localisation (the fleet's
 * {@code ConfigLocalisationTest} pattern): NeoForge's ConfigurationScreen
 * derives each label as {@code modId + ".configuration." + entryKey} with
 * the <b>leaf</b> key (its fallback after the value/level translation
 * keys), plus {@code .tooltip} - and renders the raw key when the entry
 * is missing. This test walks the spec exactly like the screen does and
 * fails when a label or tooltip has no lang entry, or when two entries
 * collide on one key (the screen labels by leaf name alone).
 */
class ConfigLocalisationTest {

    private static final String CONFIG_PREFIX = "ability_iss.configuration.";

    private static JsonObject loadLang() {
        try (InputStream in = ConfigLocalisationTest.class
                .getResourceAsStream("/assets/ability_iss/lang/en_us.json")) {
            if (in == null) {
                throw new IllegalStateException("en_us.json must be on the test classpath");
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every translation key the screen will look up for this spec. */
    private static List<String> screenKeys(ModConfigSpec spec) {
        List<String> keys = new ArrayList<>();
        collectSection(spec.getValues(), keys);
        return keys;
    }

    private static void collectSection(UnmodifiableConfig section, List<String> keys) {
        for (Entry entry : section.entrySet()) {
            // getRawValue() is generic: bind to Object before instanceof (char[] trap)
            Object raw = entry.getRawValue();
            keys.add(CONFIG_PREFIX + entry.getKey());
            keys.add(CONFIG_PREFIX + entry.getKey() + ".tooltip");
            if (raw instanceof UnmodifiableConfig subsection) {
                collectSection(subsection, keys);
            }
        }
    }

    @Test
    void everyScreenKeyHasANonEmptyLangEntry() {
        JsonObject lang = loadLang();
        for (String key : screenKeys(IssClientConfig.SPEC)) {
            assertTrue(lang.has(key), "missing from en_us.json: " + key);
            assertTrue(lang.get(key).isJsonPrimitive() && !lang.get(key).getAsString().isBlank(),
                    "blank translation for: " + key);
        }
    }

    @Test
    void noTwoScreenEntriesShareOneTranslationKey() {
        // the screen labels by leaf name only, so a section and a value
        // sharing a name (or two leaves in different sections) would show
        // one wrong label for both
        List<String> keys = screenKeys(IssClientConfig.SPEC);
        keys.removeIf(k -> k.endsWith(".tooltip"));

        Set<String> unique = new HashSet<>(keys);
        assertEquals(keys.size(), unique.size(),
                "duplicate label keys derived from the spec: " + keys);
    }

    @Test
    void theExistingHudKeyIsTheLeafNameTheScreenLooksUp() {
        // regression: the first iss key shipped as hudReplaceTensuraCharging
        // (section+leaf camelised), which the screen never asks for
        JsonObject lang = loadLang();
        assertFalse(lang.has(CONFIG_PREFIX + "hudReplaceTensuraCharging"),
                "the camelised section+leaf form is not what the screen derives");
        assertTrue(lang.has(CONFIG_PREFIX + "replaceTensuraCharging"),
                "the leaf name is what ConfigurationScreen derives");
    }
}
