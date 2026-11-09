package org.bitwisemadness.abilityiss;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.bitwisemadness.abilityiss.client.ClientConfigScreen;
import org.bitwisemadness.abilityiss.config.IssClientConfig;
import org.bitwisemadness.abilityiss.config.IssServerConfig;
import org.slf4j.Logger;

/**
 * IssHotbar mod entry point.
 *
 * <p>The mod identity lives in {@code gradle.properties}; {@link #MOD_ID}
 * must match {@code mod_id} exactly.</p>
 */
@Mod(IssHotbar.MOD_ID)
public final class IssHotbar {

    public static final String MOD_ID = "ability_iss";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IssHotbar(IEventBus modBus, ModContainer modContainer) {
        // SERVER policy: synced to clients, the tab/grey/held answers read
        // it on both sides (the audit's iss slice)
        modContainer.registerConfig(ModConfig.Type.SERVER, IssServerConfig.SPEC,
                "ability_iss-server.toml");
        // CLIENT display preferences: the HUD swap toggle and the tab/bar
        // display keys live here
        modContainer.registerConfig(ModConfig.Type.CLIENT, IssClientConfig.SPEC,
                "ability_iss-client.toml");
        // Claim NeoForge's config screen before Configured can, so its
        // editor (which reverts unedited values on save) never handles our
        // file. Client-only: ConfigurationScreen must never load server-side.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientConfigScreen.register(modContainer);
        }
        LOGGER.info("Ability ISS initialized");
    }
}
