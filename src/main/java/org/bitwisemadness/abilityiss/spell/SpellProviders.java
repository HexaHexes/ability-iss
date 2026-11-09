package org.bitwisemadness.abilityiss.spell;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.abilitycore.api.AbilityUnlocks;
import org.bitwisemadness.abilitycore.api.VisibilityHandlers;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * The tab's per-player answers (owner's brief): the menu <b>lists</b> a
 * spell once ISS remembers it for the player (its own learned/found
 * record - persistent and client-synced, read through
 * {@link AbstractSpell#isLearned}, which carries ISS's side check), and
 * the row <b>greys</b> unless the spell is <i>currently held</i> -
 * main inventory, armor, offhand, every curios slot, or inscribed/scroll
 * form in any carried stack (books and scrolls are both
 * {@code ISpellContainer}s, so one read covers every carrier).
 *
 * <p>Both answers stay fail-soft: a failed store read answers "not
 * found" for visibility (held still lists it) and abstains for the grey
 * (unknown never greys, hotbar#153's never-guess). The held scan is
 * memoized per player tick - the menu asks every row every frame.</p>
 */
public final class SpellProviders {

    /** The per-tick held memo: rebuilt when the owner or tick moves. */
    private static volatile UUID heldOwner;
    private static volatile int heldTick = -1;
    private static volatile Set<ResourceLocation> heldIds = Set.of();

    private SpellProviders() {
    }

    /** Declares the tab's two providers for every registered spell. */
    public static void declareAll() {
        for (ResourceLocation id : SpellCatalog.registered()) {
            VisibilityHandlers.declare(id, SpellProviders::visible);
            AbilityUnlocks.declare(id, SpellProviders::unlockAnswer);
        }
    }

    /**
     * Menu visibility (core#124): found OR currently held lists; the
     * unknown context (no player) never hides, and an unreadable store
     * degrades to the held answer rather than hiding a real spell.
     */
    public static boolean visible(@Nullable Player player, ResourceLocation id) {
        if (player == null) {
            return true;
        }
        if (found(player, id)) {
            return true;
        }
        Boolean held = held(player, id);
        return held == null || held;
    }

    /**
     * The grey answer (core#117): held right now means learned/unlocked;
     * a failed read abstains (empty = unknown, never a guessed grey).
     */
    public static Optional<Boolean> unlockAnswer(
            @Nullable Player player, ResourceLocation id) {
        if (player == null) {
            return Optional.empty();
        }
        Boolean held = held(player, id);
        return held == null ? Optional.empty() : Optional.of(held);
    }

    /**
     * Whether ISS's record shows the spell as encountered (owner: only
     * those list). The learning record counts; non-learning spells are
     * auto-learned by ISS ({@code isSpellLearned} short-circuits true),
     * so for those only an actual hold counts - ISS keeps no other
     * record of them, and inventing one would guess.
     */
    private static boolean found(Player player, ResourceLocation id) {
        AbstractSpell spell = SpellRegistry.REGISTRY.get(id);
        if (spell == null) {
            return false;
        }
        try {
            return spell.requiresLearning() && spell.isLearned(player);
        } catch (RuntimeException unreadable) {
            return false;
        }
    }

    /**
     * Whether the spell sits in the player's inventories right now -
     * true/false, or null when a read fails (unknown). Memoized per
     * (player, tick): the menu asks every row every frame.
     */
    @Nullable
    private static Boolean held(Player player, ResourceLocation id) {
        try {
            UUID owner = player.getUUID();
            if (!owner.equals(heldOwner) || heldTick != player.tickCount) {
                Set<ResourceLocation> scanned = scanHeld(player);
                heldIds = scanned;
                heldOwner = owner;
                heldTick = player.tickCount;
            }
            return heldIds.contains(id);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /** One pass over every carried stack; the memo's builder. */
    private static Set<ResourceLocation> scanHeld(Player player) {
        Set<ResourceLocation> out = new HashSet<>();
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            collect(inventory.getItem(i), out);
        }
        // owner's brief: curios inventories count as held too
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            for (var stacks : handler.getCurios().values()) {
                var slots = stacks.getStacks();
                for (int i = 0; i < slots.getSlots(); i++) {
                    collect(slots.getStackInSlot(i), out);
                }
            }
        });
        return out;
    }

    /** One stack's spells - books, scrolls and tomes are all containers. */
    private static void collect(ItemStack stack, Set<ResourceLocation> out) {
        if (stack.isEmpty()) {
            return;
        }
        ISpellContainer container = ISpellContainer.get(stack);
        if (container == null) {
            return;
        }
        for (int i = 0; i < container.getMaxSpellCount(); i++) {
            SpellData data = container.getSpellAtIndex(i);
            if (data == null || data == SpellData.EMPTY || data.getSpell() == null) {
                continue;
            }
            ResourceLocation id = SpellRegistry.REGISTRY.getKey(data.getSpell());
            if (id != null) {
                out.add(id);
            }
        }
    }
}
