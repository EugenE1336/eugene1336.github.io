package com.minedota.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Heroes (players) never get knockback from any attack. */
@Mixin(LivingEntity.class)
public abstract class NoHeroKnockbackMixin {

	@Inject(method = "takeKnockback", at = @At("HEAD"), cancellable = true)
	private void minedota$noHeroKnockback(double strength, double x, double z, CallbackInfo ci) {
		if ((Object) this instanceof PlayerEntity) {
			ci.cancel();
		}
	}
}
