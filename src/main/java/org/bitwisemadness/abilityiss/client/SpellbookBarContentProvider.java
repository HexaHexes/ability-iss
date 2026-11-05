package org.bitwisemadness.abilityiss.client;

import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.abilityhotbar.content.HotbarContentProvider;
import org.bitwisemadness.abilityiss.compat.SpellbookBarBridge;

/**
 * What the spellbook bar shows (#3): the equipped book's capacity as
 * cells - each filled cell is one spell, capacity clamped into
 * {@code [1, 18]} (ContentPlan's contract), and no book at all means a
 * single empty cell behind {@link #hasContent()} being false, so the bar
 * never shows for a player wearing nothing.
 *
 * <p><b>Cells draw no icons yet</b>: spells are textures, not ItemStacks,
 * and the provider contract only renders stacks - the icon hook lands with
 * ability-hotbar#115 (recorded verdict: ISS's {@code getSpellIconResource}
 * returns a texture). Until then the bar shows its chrome, frame and
 * overlays; the stacks are deliberately empty.</p>
 *
 * <p>The frame follows the wheel's selection with no code here: the bar
 * layer supplies the stored bar's {@code selectedSlot} as the default
 * framed cell (hotbar#118's state-defaulted selection - the
 * pseudocore-curios#82 ruling generalized upstream) and clamps it into
 * this provider's cells, so {@code activeSlot()} stays at its default.
 * Activate-selected presses the cell the frame marks.</p>
 *
 * <p>Reads client-side every frame through ISS's own
 * {@link Utils#getPlayerSpellbookStack} (the curios inventory) - the same
 * stack the server seeds and casts from, so client and server can never
 * disagree about the cells.</p>
 */
public final class SpellbookBarContentProvider implements HotbarContentProvider {

    @Override
    public int slotCount() {
        var container = currentContainer();
        if (container == null) {
            // ContentPlan requires [1, 18]; no book is one empty cell
            // behind hasContent() = false, so the bar stays hidden.
            return 1;
        }
        return Math.max(1, Math.min(container.getMaxSpellCount(), SpellbookBarBridge.MAX_CELLS));
    }

    @Override
    public ItemStack slotStack(int index) {
        // Spells are textures, not items: cells stay blank until
        // ability-hotbar#115's icon hook lands (see class javadoc).
        return ItemStack.EMPTY;
    }

    @Override
    public boolean hasContent() {
        var container = currentContainer();
        return container != null && container.getActiveSpellCount() > 0;
    }

    private static ISpellContainer currentContainer() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        var book = Utils.getPlayerSpellbookStack(player);
        if (book == null || book.isEmpty()) {
            return null;
        }
        return ISpellContainer.get(book);
    }
}
