package com.minedota.combat;

import com.minedota.team.DotaTeam;
import com.minedota.team.TeamComponent;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.PersistentProjectileEntity;

/**
 * Blocks friendly fire between teammates (creeps / heroes / towers).
 */
public final class TeamDamage {
	private TeamDamage() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(TeamDamage::allow);
	}

	private static boolean allow(LivingEntity entity, DamageSource source, float amount) {
		Entity attacker = source.getAttacker();
		if (attacker == null) {
			attacker = source.getSource();
		}
		if (attacker instanceof PersistentProjectileEntity proj && proj.getOwner() != null) {
			attacker = proj.getOwner();
		}
		if (!(attacker instanceof LivingEntity livingAtk)) {
			return true;
		}
		DotaTeam a = TeamComponent.getTeam(livingAtk);
		DotaTeam v = TeamComponent.getTeam(entity);
		if (a == DotaTeam.NONE || v == DotaTeam.NONE) {
			return true;
		}
		// Same team → no damage
		return a != v;
	}
}
