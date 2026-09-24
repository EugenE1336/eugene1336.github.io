package com.minedota.mixin;

import com.minedota.team.DotaTeam;
import com.minedota.team.TeamComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allied projectiles (ranged creep arrows, etc.) pass through teammates instead of bouncing.
 */
@Mixin(PersistentProjectileEntity.class)
public abstract class ProjectileAllyPassMixin {

	@Inject(method = "canHit", at = @At("HEAD"), cancellable = true)
	private void minedota$passThroughAllies(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
		Entity owner = self.getOwner();
		if (!(owner instanceof LivingEntity livingOwner) || !(entity instanceof LivingEntity livingHit)) {
			return;
		}
		DotaTeam a = TeamComponent.getTeam(livingOwner);
		DotaTeam b = TeamComponent.getTeam(livingHit);
		if (a != DotaTeam.NONE && a == b) {
			cir.setReturnValue(false);
		}
	}
}
