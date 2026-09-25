package com.minedota.mixin;

import com.minedota.hero.HeroCombat;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * After successful damage — Counter Helix / Moment of Courage hooks.
 */
@Mixin(LivingEntity.class)
public class LivingEntityDamageMixin {

	@Inject(method = "damage", at = @At("RETURN"))
	private void minedota$afterDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) {
			return;
		}
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self instanceof ServerPlayerEntity victim) || victim.getWorld().isClient) {
			return;
		}
		if (!(source.getAttacker() instanceof LivingEntity attacker)) {
			return;
		}
		HeroCombat.notifyDamaged(victim, attacker);
	}
}
