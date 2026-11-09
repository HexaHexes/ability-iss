package org.bitwisemadness.abilityiss.client;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.CooldownInstance;
import io.redspace.ironsspellbooks.capabilities.magic.PlayerCooldowns;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.abilitycore.api.AbilityAvailability;
import org.bitwisemadness.abilityhotbar.content.HotbarContentProvider;
import org.bitwisemadness.abilityiss.SpellCellOverlays;
import org.bitwisemadness.abilityiss.compat.SpellbookBarBridge;
import org.bitwisemadness.abilityiss.config.IssClientConfig;
import org.bitwisemadness.abilityiss.config.IssServerConfig;
import org.jetbrains.annotations.Nullable;

/**
 * What the spellbook bar shows (#3): the equipped book's capacity as
 * cells - each filled cell is one spell, capacity clamped into
 * {@code [1, 18]} (ContentPlan's contract), and no book at all means a
 * single empty cell behind {@link #hasContent()} being false, so the bar
 * never shows for a player wearing nothing.
 *
 * <p><b>Cells draw spell icons</b> (ability-hotbar#115's {@code iconId}
 * hook): an ISS spell has no item form - its icon is the texture
 * {@code getSpellIconResource()} names ({@code textures/gui/spell_icons/}).
 * The layer consults the icon only when the cell's stack is empty, and
 * these stacks are always empty by design. Empty cells (unfilled or locked
 * slots) render the hotbar's own chrome - a cell is a spell everywhere or
 * nowhere, the same predicate the server seeds by
 * ({@link SpellbookBarBridge#isSpellCell}).</p>
 *
 * <p><b>Per-cell overlays</b> (the {@code availabilityFor} hook): each
 * cell's cooldown sweep and castability tint come from ISS's own
 * client-synced state - {@link ClientMagicData#getCooldowns()}
 * (what ISS's HUD reads) and {@link ClientMagicData#getPlayerMana()} - so
 * the bar can never disagree with ISS's own display. There is deliberately
 * no cost badge: the renderer's badge is an unannotated black strip over
 * the icon (see {@link SpellCellOverlays}). The mapping itself is pure
 * {@link SpellCellOverlays}; this class only does the reads.</p>
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
        return Math.max(1, Math.min(container.getMaxSpellCount(),
                IssServerConfig.MAX_CELLS.get()));
    }

    @Override
    public ItemStack slotStack(int index) {
        // Spells are textures, not items - icons carry the cells (iconId);
        // the stack path stays empty so the item renderer never runs.
        return ItemStack.EMPTY;
    }

    @Override
    @Nullable
    public ResourceLocation iconId(int index) {
        var container = currentContainer();
        if (container == null || !SpellbookBarBridge.isSpellCell(container, index)) {
            return null;
        }
        return container.getSpellAtIndex(index).getSpell().getSpellIconResource();
    }

    @Override
    @Nullable
    public AbilityAvailability availabilityFor(@Nullable Player player, int index) {
        if (player == null) {
            // No player, no reads: the global tuple (book-has-spells) stays
            // the answer rather than a fabricated one.
            return null;
        }
        var container = currentContainer();
        if (container == null || !SpellbookBarBridge.isSpellCell(container, index)) {
            return null;
        }
        SpellData data = container.getSpellAtIndex(index);
        AbstractSpell spell = data.getSpell();
        PlayerCooldowns cooldowns = ClientMagicData.getCooldowns();
        CooldownInstance entry = cooldowns == null
                ? null
                : cooldowns.getSpellCooldowns().get(spell.getSpellId());
        // bar.showCooldownSweep off = report no cooldown, so the mapping
        // answers unknown and the renderer draws no sweep (never a fake one)
        boolean sweep = IssClientConfig.SHOW_COOLDOWN_SWEEP.get();
        int remaining = entry == null || !sweep ? 0 : entry.getCooldownRemaining();
        int total = entry == null || !sweep ? 0 : entry.getSpellCooldown();
        int manaCost = spell.getManaCost(data.getLevel());
        float mana = ClientMagicData.getPlayerMana();
        return SpellCellOverlays.forCell(remaining, total, manaCost, data.isLocked(), mana);
    }

    @Override
    public boolean hasContent() {
        if (!IssClientConfig.HIDE_WHEN_EMPTY.get()) {
            return true; // pack keeps the bar visible even when empty
        }
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
