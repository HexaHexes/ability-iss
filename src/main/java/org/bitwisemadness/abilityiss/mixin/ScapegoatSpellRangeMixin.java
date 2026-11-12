package org.bitwisemadness.abilityiss.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.spells.evocation.ScapegoatSpell;
import net.minecraft.world.entity.LivingEntity;
import org.bitwisemadness.abilityiss.spell.RangeMastery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The RANGE seam for ScapegoatSpell (owner's range brief): its {@code getRange}
 * already receives the entity, so ONE return-wrap moves every read -
 * the cast's ray, the tooltip's number, the pre-cast reach check - with
 * the caller's own entity as the multiplier source (a ServerPlayer's
 * live mastery, anything else raw). Applies loudly at load if upstream
 * renames the method (defaultRequire 1).
 */
@Mixin(ScapegoatSpell.class)
public abstract class ScapegoatSpellRangeMixin {

    @ModifyReturnValue(method = "getRange", at = @At("RETURN"))
    private int mastery$boostRange(int original, int level,
            LivingEntity caster) {
        return RangeMastery.boostedInt(original, (AbstractSpell) (Object) this, caster);
    }
}
