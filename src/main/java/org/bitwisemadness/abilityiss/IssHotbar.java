package org.bitwisemadness.abilityiss;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.bitwisemadness.abilityiss.config.IssClientConfig;
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
        // CLIENT display preferences only - the HUD swap toggle lives here
        modContainer.registerConfig(ModConfig.Type.CLIENT, IssClientConfig.SPEC,
                "ability_iss-client.toml");
        LOGGER.info("Ability ISS initialized");
    }
}
