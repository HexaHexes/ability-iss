package org.bitwisemadness.abilityiss.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.redspace.ironsspellbooks.spells.eldritch.SonicBoomSpell;
import net.minecraft.world.entity.LivingEntity;
import org.bitwisemadness.abilityiss.spell.RangeMastery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The RANGE seam for SonicBoomSpell (owner's range brief): its {@code getRange}
 * already receives the entity, so ONE return-wrap moves every read -
 * the cast's ray, the tooltip's number, the pre-cast reach check - with
 * the caller's own entity as the multiplier source (a ServerPlayer's
 * live mastery, anything else raw). Applies loudly at load if upstream
 * renames the method (defaultRequire 1).
 */
@Mixin(SonicBoomSpell.class)
public abstract class SonicBoomSpellRangeMixin {

    @ModifyReturnValue(method = "getRange", at = @At("RETURN"))
    private static float mastery$boostRange(float original, int level,
            LivingEntity caster) {
        return RangeMastery.boosted(original, SonicBoomSpell.class, caster);
    }
}
