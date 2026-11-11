package org.bitwisemadness.abilityiss.spell;

import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.bitwisemadness.abilitycore.api.MasteryEffects;
import org.bitwisemadness.abilityiss.IssHotbar;
import org.jetbrains.annotations.Nullable;

/**
 * The mastery COST seam's charge half (REQ-CORE-12, closing #28): ISS
 * deducts exactly what the cast event says - {@code castSpell} posts
 * {@link SpellOnCastEvent}, then {@code setMana(max(0, mana -
 * event.getManaCost()))} - so discounting here moves the real charge.
 *
 * <p><b>Priority is what makes the tensura_iron_spells composition
 * safe</b> (verified against its bytecode): t_i_s registers its own
 * listener at {@code EventPriority.LOWEST}, i.e. last, and converts
 * whatever the field holds into its magicule charge - a default-priority
 * listener therefore always runs FIRST, and its discount flows into the
 * magicule conversion. The row quote ({@link SpellCatalog#liveCostsFor})
 * rounds with the same {@code Math.round} on the same base, so the
 * check, the charge and the display can never disagree.</p>
 */
@EventBusSubscriber(modid = IssHotbar.MOD_ID)
public final class MasteryCostEvents {

    private MasteryCostEvents() {
    }

    @SubscribeEvent
    public static void onSpellCast(SpellOnCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return; // mob/command casts carry no player mastery
        }
        ResourceLocation id = spellId(event.getSpellId());
        if (id == null) {
            return;
        }
        double multiplier = MasteryEffects.costMultiplier(player, id);
        if (multiplier == 1.0) {
            return; // undiscounted: leave the event's own number alone
        }
        // the ORIGINAL, not the current field: this listener runs before
        // any LOWEST-priority conversion, but the final field is the
        // authority for what ISS deducts either way
        event.setManaCost(
                (int) Math.round(event.getOriginalManaCost() * multiplier));
    }

    /** The event's spell id, or null when it does not parse (never guessed). */
    @Nullable
    private static ResourceLocation spellId(String raw) {
        return raw == null ? null : ResourceLocation.tryParse(raw);
    }
}
