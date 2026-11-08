package org.bitwisemadness.abilityiss.spell;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

    /** ISS's nine schools, in registry order - each school's scroll art. */
    private static final String[] SCHOOLS = {
            "fire", "ice", "lightning", "holy", "ender",
            "blood", "evocation", "nature", "eldritch"};

    private static boolean registered;

    private SpellMenuTab() {
    }

    /** Registers the spell tab, its school strip + icons - idempotent. */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        // owner: the Divine Pearl is the tab's icon (empty headless -
        // the resolve-or-hide rule keeps the label, never a broken box)
        MenuTabs.register(TAB_ID, TAB_NAME_KEY, null, divinePearl(),
                (id, definition) -> SpellCatalog.CATEGORY.equals(definition.category()),
                player -> SpellCatalog.registered(),
                true);
        MenuTabs.subCategoryOrder(TAB_ID, SpellCatalog.schoolOrder());
        // owner: each school's own scroll texture is its chip icon
        for (String school : SCHOOLS) {
            MenuTabs.subCategoryIcon("type:" + school,
                    ResourceLocation.fromNamespaceAndPath(
                            "irons_spellbooks", "textures/item/scroll_" + school),
                    null);
        }
    }

    /** The Divine Pearl as the tab icon, or null when unresolvable. */
    private static ItemStack divinePearl() {
        try {
            Item item = BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "divine_pearl"));
            ItemStack stack = new ItemStack(item);
            return stack.isEmpty() ? null : stack;
        } catch (Throwable unavailable) {
            return null; // headless fixtures run without bootstrapped registries
        }
    }
}
