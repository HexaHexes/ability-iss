package org.bitwisemadness.abilityiss.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.bitwisemadness.abilitycore.api.MasteryEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The mastery COST seam's check half (REQ-CORE-12, closing #28):
 * {@code canBeCastedBy} compares {@code getManaCost(level)} against the
 * player's mana, so the affordability bar must carry the same discount
 * the charge does - a player who can pay the discounted price must not
 * be refused at the full one. The wrap composes safely with
 * tensura_iron_spells' own wrap on the same call site (both run in
 * either nesting order: scaling its 0/converted outputs by the discount
 * and converting our discounted number are each correct), and the
 * enclosing method's Player argument gives the per-caster read with no
 * guessing. Non-player callers fall back to the native number.
 */
@Mixin(AbstractSpell.class)
public abstract class CastManaCostMasteryMixin {

    @WrapOperation(method = "canBeCastedBy",
            at = @At(value = "INVOKE",
                    target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"))
    private int mastery$discountManaCheck(AbstractSpell spell, int level,
            Operation<Integer> original, @Local(argsOnly = true) Player player) {
        int mana = original.call(spell, level);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return mana;
        }
        ResourceLocation id = SpellRegistry.REGISTRY.getKey(spell);
        if (id == null) {
            return mana;
        }
        double multiplier = MasteryEffects.costMultiplier(serverPlayer, id);
        return multiplier == 1.0 ? mana : (int) Math.round(mana * multiplier);
    }
}
