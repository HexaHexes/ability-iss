package org.bitwisemadness.abilityiss.spell;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

/**
 * The iron.wiki description cache's pure half (owner's periodic-check
 * brief, option B): where the cache lives, how it parses, and how the
 * fetched HTML becomes entries - no HTTP, no client types, so every rule
 * is unit-testable headless. {@link SpellCatalog} reads the cache first
 * and falls back to the bundled copy; the client-side fetcher
 * (WikiDescriptionRefresh) writes it with {@link #writeCache}.
 *
 * <p>The cache has no TTL on purpose: a file that passed the floor
 * ({@link #MIN_SPELLS} spells) came from a successful fetch and is newer
 * than the bundle it replaces - the fetch cadence
 * ({@code menu.wikiRefreshHours}) is what keeps it fresh, and the
 * bundle remains every reader's fallback.</p>
 */
public final class WikiDescriptions {

    /** The floor: below this the parse is a markup change, never a refresh. */
    public static final int MIN_SPELLS = 100;

    private WikiDescriptions() {
    }

    /**
     * Where the fetcher writes and the loader prefers: the config dir's
     * {@code ability_iss/wiki_descriptions_cache.json}. Resolved lazily
     * with a plain-path fallback - FMLPaths needs the FML bootstrap, and
     * the unit tests that read descriptions never run it (the fallback
     * simply never exists, so they keep the bundled copy).
     *
     * @return the cache file's path
     */
    public static Path cachePath() {
        try {
            return FMLPaths.CONFIGDIR.get()
                    .resolve("ability_iss/wiki_descriptions_cache.json");
        } catch (RuntimeException notBootstrapped) {
            return Path.of("config", "ability_iss", "wiki_descriptions_cache.json");
        }
    }

    /**
     * The stored response ETag for the next If-None-Match, or null when
     * the cache is absent or carries none.
     *
     * @param path the cache file
     * @return the ETag, or null
     */
    @Nullable
    public static String readEtag(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(path)) {
            JsonElement etag = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().get("_etag");
            return etag != null && etag.isJsonPrimitive() ? etag.getAsString() : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * The cache file's entries, or null when it cannot be trusted: the
     * file is absent, unparseable, or holds fewer spells than the floor
     * (a gutted cache must never beat the bundle).
     *
     * @param path the cache file
     * @return id -> prose, or null to fall back to the bundled copy
     */
    @Nullable
    public static Map<String, String> readCache(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(path)) {
            Map<String, String> entries = readJson(in);
            return entries.size() >= MIN_SPELLS ? entries : null;
        } catch (Exception e) {
            return null; // one bad file: the bundle stays the answer
        }
    }

    /**
     * The JSON walk the bundled copy and the cache share: underscore keys
     * are provenance metadata, non-text values are skipped, everything
     * else is an id -> prose entry. Entry-level problems skip; a
     * malformed document throws to the caller.
     *
     * @param in the open JSON stream (closed by the caller)
     * @return the entries (possibly empty)
     */
    public static Map<String, String> readJson(InputStream in) {
        JsonObject root = JsonParser.parseReader(
                new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String key = entry.getKey();
            if (key.isEmpty() || key.charAt(0) == '_') {
                continue; // provenance metadata, not a spell
            }
            try {
                String prose = entry.getValue().getAsString();
                if (!prose.isBlank()) {
                    out.put(key, prose);
                }
            } catch (RuntimeException badValue) {
                // a non-text value is skipped; the floor catches a mass loss
            }
        }
        return out;
    }

    /**
     * Writes the cache in the bundled file's shape: provenance first
     * ({@code _source}, {@code _retrieved}, and the fetch's {@code _etag}
     * when it answered), then every id sorted, UTF-8 without BOM.
     *
     * @param path   where to write (directories are created)
     * @param spells id -> prose
     * @param etag   the response's ETag for the next If-None-Match, or null
     */
    public static void writeCache(Path path, Map<String, String> spells,
            @Nullable String etag) {
        List<String> sorted = spells.keySet().stream().sorted().toList();
        StringBuilder json = new StringBuilder("{\n");
        json.append("  \"_source\": \"https://iron.wiki/spells/\",\n");
        json.append("  \"_retrieved\": \"")
                .append(LocalDate.now(ZoneOffset.UTC))
                .append("\",\n");
        if (etag != null && !etag.isBlank()) {
            json.append("  \"_etag\": ").append(quote(etag)).append(",\n");
        }
        for (int i = 0; i < sorted.size(); i++) {
            String key = sorted.get(i);
            json.append("  ").append(quote(key)).append(": ")
                    .append(quote(spells.get(key)))
                    .append(i + 1 < sorted.size() ? ",\n" : "\n");
        }
        json.append("}\n");
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, json.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot write the wiki cache to " + path, e);
        }
    }

    /**
     * The page's spell prose - the checked-in generator's rules in Java:
     * the id rides the spell's icon path, the prose is the styled margin
     * div after the stat table, bullet lists keep their lines, tags are
     * stripped. Fails LOUDLY below {@link #MIN_SPELLS}: a markup change
     * must never silently gut the pane.
     *
     * @param html the fetched page
     * @return id -> prose (bare ids - the caller namespaces them)
     */
    public static Map<String, String> parseSpells(String html) {
        Pattern containers = Pattern.compile("<div class=\"spell-container\">");
        Pattern image = Pattern.compile("/img/spells/([a-z0-9_]+)\\.png");
        Pattern proseDiv = Pattern.compile(
                "<div style=\"margin-top: 7px; margin-left: 5px; "
                        + "margin-right: 10px\">\\s*(.*?)\\s*</div>",
                Pattern.DOTALL);
        Pattern listItem = Pattern.compile(
                "<li>(.*?)(?=<li>|</ul>\\s*$)", Pattern.DOTALL);

        Matcher blocks = containers.matcher(html);
        Map<String, String> out = new LinkedHashMap<>();
        int start = -1;
        while (blocks.find()) {
            if (start >= 0) {
                collect(html.substring(start, blocks.start()), image, proseDiv,
                        listItem, out);
            }
            start = blocks.start();
        }
        if (start >= 0) {
            collect(html.substring(start), image, proseDiv, listItem, out);
        }
        if (out.size() < MIN_SPELLS) {
            throw new IllegalStateException("parsed only " + out.size()
                    + " spells (minimum " + MIN_SPELLS + ") - the page's markup "
                    + "has changed. Fix the parser; do not ship a gutted file.");
        }
        return out;
    }

    private static void collect(String chunk, Pattern image, Pattern proseDiv,
            Pattern listItem, Map<String, String> out) {
        Matcher idMatch = image.matcher(chunk);
        if (!idMatch.find()) {
            return;
        }
        String id = idMatch.group(1);
        if (out.containsKey(id)) {
            return;
        }
        Matcher proseMatch = proseDiv.matcher(chunk);
        if (!proseMatch.find()) {
            return;
        }
        String raw = proseMatch.group(1);
        String prose;
        if (raw.contains("<ul>")) {
            Matcher items = listItem.matcher(raw);
            StringBuilder bullets = new StringBuilder();
            while (items.find()) {
                String item = stripTags(items.group(1));
                if (!item.isBlank()) {
                    if (bullets.length() > 0) {
                        bullets.append('\n');
                    }
                    bullets.append("- ").append(item);
                }
            }
            prose = bullets.toString();
        } else {
            prose = stripTags(raw);
        }
        prose = decodeEntities(prose).trim();
        if (!prose.isBlank()) {
            out.put(id, prose);
        }
    }

    private static String stripTags(String raw) {
        return raw.replaceAll("<br\\s*/?>", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** The handful of entities a prose block can carry (the page ships none today). */
    private static String decodeEntities(String text) {
        return text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'");
    }

    private static String quote(String value) {
        return new JsonPrimitive(value).toString();
    }
}
