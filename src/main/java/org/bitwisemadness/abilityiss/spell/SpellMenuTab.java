package org.bitwisemadness.abilityiss.spell;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilityhotbar.registry.MenuTabs;

/**
 * The tab (owner's brief): <b>all the spells the player has found</b>,
 * with a category per ISS school (the {@code type:} strip, ordered from
 * ISS's own school registry) - and <b>informational only</b>: every row
 * is inert to press, drag, toggle and cycle, so the tab can never slot
 * an ability into a hotbar. The content rides the mod's own handler
 * (owner's handler directive): the registered spell ids, narrowed by
 * core's visibility (found/held) and the tab's category membership on
 * top. Text-only tab: ISS ships no menu sprite for this family (the
 * resolve-or-hide rule keeps it a clean label, never a broken box).
 */
public final class SpellMenuTab {

    /** The tab id (the registering mod's namespace, MenuTabs' rule). */
    public static final ResourceLocation TAB_ID =
            ResourceLocation.fromNamespaceAndPath("ability_iss", "spells");

    /** Display name for the tab's text. */
    public static final String TAB_NAME_KEY = "ability_iss.tab.spells";

    private static boolean registered;

    private SpellMenuTab() {
    }

    /** Registers the spell tab + its school strip - idempotent, setup-time. */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        MenuTabs.register(TAB_ID, TAB_NAME_KEY, null, null,
                (id, definition) -> SpellCatalog.CATEGORY.equals(definition.category()),
                player -> SpellCatalog.registered(),
                true);
        MenuTabs.subCategoryOrder(TAB_ID, SpellCatalog.schoolOrder());
    }
}
