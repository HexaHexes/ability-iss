package org.bitwisemadness.abilityiss.spell;

import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.abilitycore.config.ConfigDiagnostics;
import org.bitwisemadness.abilityhotbar.registry.MenuTabs;
import org.bitwisemadness.abilityiss.compat.SpellbookBarBridge;
import org.bitwisemadness.abilityiss.config.IssClientConfig;
import org.jetbrains.annotations.Nullable;

/**
 * The tab (owner's brief): <b>all the spells the player has found</b>,
 * with a category per ISS school (the {@code type:} strip, ordered from
 * ISS's own school registry) - and <b>informational only</b>: every row
 * is inert to press, drag, toggle and cycle, so the tab can never slot
 * an ability into a hotbar. The content rides the mod's own handler
 * (owner's handler directive): the registered spell ids, narrowed by
 * core's visibility (found/held) and the tab's category membership on
 * top.
 *
 * <p>The pack shapes the tab's display ({@code menu.*}): its icon
 * (empty = text-only label - the resolve-or-hide rule keeps a clean
 * label, never a broken box), its sprite, the school-chip texture
 * template ({@code %s} = the school path) and an explicit school order.
 * These read at common setup, which also runs on dedicated servers where
 * the CLIENT spec never loads - so every read goes through core's
 * {@code readOrDefault} and falls back to the shipped default.</p>
 */
public final class SpellMenuTab {

    /** The tab id (the registering mod's namespace, MenuTabs' rule). */
    public static final ResourceLocation TAB_ID =
            ResourceLocation.fromNamespaceAndPath("ability_iss", "spells");

    /** Display name for the tab's text. */
    public static final String TAB_NAME_KEY = "ability_iss.tab.spells";

    /** The shipped icon: the Divine Pearl (owner's brief). */
    private static final String DEFAULT_ICON = "irons_spellbooks:divine_pearl";

    /** The shipped chip template: each school's own scroll art. */
    private static final String DEFAULT_CHIP_TEMPLATE =
            "irons_spellbooks:textures/item/scroll_%s";

    private static boolean registered;

    private SpellMenuTab() {
    }

    /** Registers the spell tab, its school strip + icons - idempotent. */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        MenuTabs.register(TAB_ID, TAB_NAME_KEY, tabSprite(), tabIcon(tabIconId()),
                (id, definition) -> SpellCatalog.CATEGORY.equals(definition.category()),
                player -> SpellCatalog.registered(),
                true);
        // owner: this tab's strip shows the SPELLBOOK's cells (the
        // provider bar), not the assignable bar's
        MenuTabs.registerBarForTab(TAB_ID, SpellbookBarBridge.CURIO_BAR);
        MenuTabs.subCategoryOrder(TAB_ID, schoolOrder());
        registerChipIcons(chipTemplate());
    }

    /**
     * Every registered school's chip icon, from the pack's template -
     * derived from ISS's own school registry (the audit: never a
     * hardcoded school list, so a datapack-added school gets its art too).
     */
    private static void registerChipIcons(String template) {
        if (template.isEmpty()) {
            return; // pack turned chip icons off
        }
        for (var school : SchoolRegistry.REGISTRY) {
            try {
                MenuTabs.subCategoryIcon("type:" + school.getId().getPath(),
                        ResourceLocation.parse(String.format(template, school.getId().getPath())),
                        null);
            } catch (RuntimeException malformed) {
                // a bad template costs that chip's icon, never the tab
            }
        }
    }

    /** The configured school order, or ISS's registry order when empty. */
    private static List<String> schoolOrder() {
        List<? extends String> configured = ConfigDiagnostics.readOrDefault("menu.schoolOrder",
                () -> IssClientConfig.SCHOOL_ORDER.get(), List.of());
        return configured.isEmpty() ? SpellCatalog.schoolOrder() : List.copyOf(configured);
    }

    /** The configured tab sprite, or null (text-only label). */
    @Nullable
    private static ResourceLocation tabSprite() {
        String path = ConfigDiagnostics.readOrDefault("menu.sprite",
                () -> IssClientConfig.TAB_SPRITE.get(), "");
        if (path.isEmpty()) {
            return null;
        }
        try {
            return ResourceLocation.parse(path);
        } catch (RuntimeException malformed) {
            return null; // resolve-or-hide: a clean label, never a broken box
        }
    }

    /** The configured tab icon id, or null (no icon). */
    @Nullable
    private static String tabIconId() {
        String id = ConfigDiagnostics.readOrDefault("menu.icon",
                () -> IssClientConfig.TAB_ICON.get(), DEFAULT_ICON);
        return id.isEmpty() ? null : id;
    }

    /** The tab icon as a stack, or null when unresolvable (headless too). */
    @Nullable
    private static ItemStack tabIcon(@Nullable String id) {
        if (id == null) {
            return null;
        }
        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            ItemStack stack = new ItemStack(item);
            return stack.isEmpty() ? null : stack;
        } catch (Throwable unavailable) {
            return null; // headless fixtures run without bootstrapped registries
        }
    }

    /** The configured chip template; the shipped scroll art when unloaded. */
    private static String chipTemplate() {
        return ConfigDiagnostics.readOrDefault("menu.schoolChipTexture",
                () -> IssClientConfig.SCHOOL_CHIP_TEXTURE.get(), DEFAULT_CHIP_TEMPLATE);
    }
}
