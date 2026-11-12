package org.bitwisemadness.abilityiss.client;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.neoforged.fml.ModList;
import org.bitwisemadness.abilityiss.IssHotbar;
import org.bitwisemadness.abilityiss.config.IssClientConfig;
import org.bitwisemadness.abilityiss.spell.WikiDescriptions;
import org.jetbrains.annotations.Nullable;

/**
 * The periodic iron.wiki fetch (owner's periodic-check brief, option B):
 * one GET at launch and then every {@code menu.wikiRefreshHours} (0 =
 * never), off the render thread, ETag-cached so an unchanged page costs
 * a 304. The download lands in {@link WikiDescriptions#cachePath()} and
 * applies at the NEXT launch - the ability registry freezes at boot, so
 * there is no hot-swap, only the cache-first load in
 * {@code SpellCatalog}.
 *
 * <p>Politeness and safety rails: one fetch in flight, a 15s timeout, a
 * user agent naming the mod, the floor
 * ({@link WikiDescriptions#MIN_SPELLS}) before anything is written (a
 * markup change fails the fetch, never the pane), and any failure logged
 * once per session while the bundle stays the answer.</p>
 */
public final class WikiDescriptionRefresh {

    private static final String SOURCE = "https://iron.wiki/spells/";
    private static final long HOUR_MS = 3_600_000L;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static long nextFetchAt;
    private static volatile boolean inFlight;
    private static boolean failureLogged;

    private WikiDescriptionRefresh() {
    }

    /** The client tick's gate (IssClientEvents calls this every tick). */
    public static void onClientTick() {
        int hours = IssClientConfig.WIKI_REFRESH_HOURS.get();
        long now = System.currentTimeMillis();
        if (hours <= 0 || inFlight || now < nextFetchAt) {
            return;
        }
        // schedule before the attempt: a failed fetch waits the interval too
        nextFetchAt = now + hours * HOUR_MS;
        inFlight = true;
        CompletableFuture.runAsync(() -> {
            try {
                fetchOnce();
            } finally {
                inFlight = false;
            }
        });
    }

    /** One guarded fetch; every failure keeps the bundle as the answer. */
    static void fetchOnce() {
        try {
            String etag = WikiDescriptions.readEtag(WikiDescriptions.cachePath());
            HttpRequest.Builder request = HttpRequest.newBuilder(java.net.URI.create(SOURCE))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", userAgent())
                    .header("Accept", "text/html");
            if (etag != null && !etag.isBlank()) {
                request.header("If-None-Match", etag);
            }
            HttpResponse<String> response =
                    HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 304) {
                return; // the page is unchanged: the cache already holds it
            }
            ingest(WikiDescriptions.cachePath(), response.statusCode(), response.body(),
                    response.headers().firstValue("ETag").orElse(null));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            logFailureOnce(interrupted);
        } catch (RuntimeException | java.io.IOException failure) {
            logFailureOnce(failure);
        }
    }

    /**
     * The fetch's decision, pure enough to test without a network: only
     * a 200 whose body parses above the floor is written (ids
     * namespaced exactly like the bundled file); everything else -
     * errors, empty bodies, gutted markup - leaves the cache untouched.
     *
     * @param cache  where a good answer lands
     * @param status the response status
     * @param body   the response body
     * @param etag   the response's ETag, or null
     * @return whether the cache was written
     */
    static boolean ingest(Path cache, int status, String body,
            @Nullable String etag) {
        if (status != 200 || body == null || body.isBlank()) {
            return false;
        }
        Map<String, String> spells;
        try {
            spells = WikiDescriptions.parseSpells(body);
        } catch (RuntimeException markupChanged) {
            logFailureOnce(markupChanged);
            return false;
        }
        Map<String, String> namespaced = new LinkedHashMap<>();
        spells.forEach((id, prose) -> namespaced.put("irons_spellbooks:" + id, prose));
        try {
            WikiDescriptions.writeCache(cache, namespaced, etag);
            return true;
        } catch (RuntimeException unwritable) {
            logFailureOnce(unwritable);
            return false;
        }
    }

    private static String userAgent() {
        String version = ModList.get() == null ? "dev"
                : ModList.get().getModContainerById(IssHotbar.MOD_ID)
                        .map(container -> container.getModInfo().getVersion().toString())
                        .orElse("dev");
        return "ability-iss/" + version + " (+iron.wiki description refresh)";
    }

    private static void logFailureOnce(Exception failure) {
        if (!failureLogged) {
            failureLogged = true;
            IssHotbar.LOGGER.warn("Wiki description refresh failed ({}); the bundled "
                    + "descriptions stay - logged once per session", failure.toString());
        }
    }
}
