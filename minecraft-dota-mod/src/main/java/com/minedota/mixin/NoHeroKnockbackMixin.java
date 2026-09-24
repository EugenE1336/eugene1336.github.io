package com.minedota.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Heroes and lane creeps never get knockback from attacks. */
@Mixin(LivingEntity.class)
public abstract class NoHeroKnockbackMixin {

	@Inject(method = "takeKnockback", at = @At("HEAD"), cancellable = true)
	private void minedota$noHeroKnockback(double strength, double x, double z, CallbackInfo ci) {
		Object self = this;
		if (self instanceof PlayerEntity
				|| self instanceof com.minedota.entity.CreepEntity
				|| self instanceof com.minedota.entity.RangedCreepEntity
				|| self instanceof com.minedota.entity.BotHeroEntity) {
			ci.cancel();
		}
	}
}
