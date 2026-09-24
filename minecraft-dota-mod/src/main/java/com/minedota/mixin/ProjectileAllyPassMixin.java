package com.minedota.mixin;

import com.minedota.team.DotaTeam;
import com.minedota.team.TeamComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allied projectiles pass through teammates (heroes / creeps) instead of bouncing
 * when TeamDamage cancels the hit.
 */
@Mixin(ProjectileEntity.class)
public abstract class ProjectileAllyPassMixin {

	@Inject(method = "canHit", at = @At("HEAD"), cancellable = true)
	private void minedota$passThroughAllies(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		ProjectileEntity self = (ProjectileEntity) (Object) this;
		Entity owner = self.getOwner();
		if (owner == null || !(entity instanceof LivingEntity)) {
			return;
		}
		DotaTeam a = TeamComponent.getTeam(owner);
		DotaTeam b = TeamComponent.getTeam(entity);
		if (a != DotaTeam.NONE && a == b) {
			cir.setReturnValue(false);
		}
	}
}
