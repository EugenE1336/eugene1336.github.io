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
		if (a == v) {
			return false;
		}

		// PA Blur evasion
		if (entity instanceof net.minecraft.server.network.ServerPlayerEntity victim) {
			var hm = com.minedota.hero.HeroManager.get(victim.getServer());
			var def = hm.getHero(victim.getUuid());
			var prog = hm.getProgress(victim.getUuid());
			if (def != null && prog != null && com.minedota.hero.StrAgiAbilities.tryEvade(victim, def, prog)) {
				victim.getServerWorld().spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD,
						victim.getX(), victim.getY() + 1, victim.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
				return false;
			}
			// AM Counterspell: block one spell-like hit
			if (def != null && prog != null && "am".equals(def.id()) && prog.consumeCounterspell()) {
				livingAtk.damage(victim.getServerWorld().getDamageSources().magic(), amount * 0.5f);
				return false;
			}
		}
		return true;
	}
}
