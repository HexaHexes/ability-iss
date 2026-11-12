package org.bitwisemadness.abilityiss.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.spells.blood.RayOfSiphoningSpell;
import net.minecraft.world.entity.LivingEntity;
import org.bitwisemadness.abilityiss.spell.RangeMastery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The RANGE seam for RayOfSiphoning (owner's range brief): the one of
 * the twelve whose getRange is a static {@code (int)} - no entity to
 * read a caster from - so the multiplier lands at its CALL SITES: the
 * cast's ray end and the tooltip's number, each capturing the enclosing
 * method's LivingEntity. Its AI check (shouldAIStopCasting) stays raw on
 * purpose - a mob has no mastery. Applies loudly at load if upstream
 * renames the method (defaultRequire 1).
 */
@Mixin(RayOfSiphoningSpell.class)
public abstract class RayOfSiphoningRangeMixin {

    @WrapOperation(method = "onCast", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/spells/blood/RayOfSiphoningSpell;getRange(I)F"))
    private float mastery$rangeOnCast(int level, Operation<Float> original,
            @Local(argsOnly = true) LivingEntity caster) {
        return RangeMastery.boosted(original.call(level), (AbstractSpell) (Object) this, caster);
    }

    @WrapOperation(method = "getUniqueInfo", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/spells/blood/RayOfSiphoningSpell;getRange(I)F"))
    private float mastery$rangeTooltip(int level, Operation<Float> original,
            @Local(argsOnly = true) LivingEntity caster) {
        return RangeMastery.boosted(original.call(level), (AbstractSpell) (Object) this, caster);
    }
}
