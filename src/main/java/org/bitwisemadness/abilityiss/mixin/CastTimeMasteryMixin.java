package org.bitwisemadness.abilityiss.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.bitwisemadness.abilitycore.api.MasteryEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The mastery CAST_TIME seam (REQ-CORE-12, closing #28's cast-time
 * candidate): {@code getEffectiveCastTime(int, LivingEntity)} is the
 * number {@code attemptInitiateCast} sets the real cast up with, and the
 * tooltip reads the same method - one return-wrap therefore moves both
 * the cast and its display. The caster is a parameter, so the discount
 * is per-player with no guessing; non-player casts (mobs, commands) and
 * zero-length (instant) casts are left untouched. The injection fails
 * loudly at apply time if upstream renames the method (defaultRequire 1).
 */
@Mixin(AbstractSpell.class)
public abstract class CastTimeMasteryMixin {

    @ModifyReturnValue(method = "getEffectiveCastTime", at = @At("RETURN"))
    private int mastery$discountCastTime(int original, int spellLevel, LivingEntity caster) {
        if (original <= 0 || !(caster instanceof ServerPlayer player)) {
            return original;
        }
        ResourceLocation id = SpellRegistry.REGISTRY.getKey((AbstractSpell) (Object) this);
        if (id == null) {
            return original;
        }
        double multiplier = MasteryEffects.castTimeMultiplier(player, id);
        return multiplier == 1.0 ? original : (int) Math.round(original * multiplier);
    }
}
