package org.bitwisemadness.abilityiss.mixin;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.bitwisemadness.abilitycore.api.MasteryAwards;
import org.bitwisemadness.abilitycore.api.MasteryTrigger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Award mastery for a successful cast (ability-iss's REQ-CORE-12 slice):
 * ISS's cast initiation is the <b>single funnel</b> every cast path
 * reaches - the spellbook bar's quick-cast through
 * {@code Utils.serverSideInitiateQuickCast}, the native keybind through
 * {@code serverSideInitiateCast}, and the spell wheel - so one
 * injection covers all of them with no double awards and no bar
 * dependency.
 *
 * <p>The grant fires only when initiation actually succeeds (the
 * refusal paths return false and contribute nothing) and only for a
 * {@link ServerPlayer} - the client branch of the same method answers
 * for a {@code LocalPlayer} and is skipped. The declaration's gain
 * handler supplies the amount (the live server config), and the
 * declaration's share list routes it; an undeclared spell is a no-op by
 * core's own opt-in rule.</p>
 */
@Mixin(AbstractSpell.class)
public abstract class SpellCastAwardMixin {

    @Inject(method = "attemptInitiateCast", at = @At("RETURN"))
    private void awardMasteryOnSuccessfulInitiation(ItemStack stack, int spellLevel,
            Level level, Player player, CastSource castSource, boolean fromSelection,
            String slot, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return; // initiation refused - the cast contributed nothing
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return; // the client branch answers for a LocalPlayer
        }
        ResourceLocation spellId = SpellRegistry.REGISTRY.getKey((AbstractSpell) (Object) this);
        if (spellId == null) {
            return; // unreachable for a live entry - never invent an id
        }
        MasteryAwards.award(serverPlayer, spellId, MasteryTrigger.ACTIVATION);
    }
}
