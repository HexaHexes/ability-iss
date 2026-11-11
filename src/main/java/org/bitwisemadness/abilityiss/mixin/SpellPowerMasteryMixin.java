package org.bitwisemadness.abilityiss.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.bitwisemadness.abilitycore.api.MasteryEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The mastery POWER seam (REQ-CORE-12): every damage/heal spell scales
 * through {@code getSpellPower(int, Entity)} - the entity is the power
 * SOURCE (ISS reads its spell-power attribute from it), i.e. the caster
 * by convention, so a ServerPlayer parameter makes the boost per-player.
 * Non-player entities (mob casts, and any caller passing something else)
 * are left untouched - never a guess. The injection fails loudly at
 * apply time if upstream renames the method (defaultRequire 1).
 */
@Mixin(AbstractSpell.class)
public abstract class SpellPowerMasteryMixin {

    @ModifyReturnValue(method = "getSpellPower", at = @At("RETURN"))
    private float mastery$boostPower(float original, int spellLevel, Entity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return original;
        }
        ResourceLocation id = SpellRegistry.REGISTRY.getKey((AbstractSpell) (Object) this);
        if (id == null) {
            return original;
        }
        double multiplier = MasteryEffects.powerMultiplier(player, id);
        return multiplier == 1.0 ? original : (float) (original * multiplier);
    }
}
