package com.minedota.hero;

import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;

public final class CombatMath {
	private CombatMath() {
	}

	public static float mitigate(float rawDamage, float armor) {
		if (rawDamage <= 0f) {
			return 0f;
		}
		return rawDamage / (1f + 0.06f * Math.max(0f, armor));
	}

	public static float mitigateAgainst(LivingEntity target, float rawDamage, HeroProgress targetProgress, HeroDef targetHero) {
		if (targetProgress == null || targetHero == null || !(target instanceof ServerPlayerEntity)) {
			return rawDamage;
		}
		return mitigate(rawDamage, targetProgress.getArmor(targetHero));
	}
}
