package com.minedota.hero;

import com.minedota.entity.AncientEntity;
import com.minedota.entity.BarrackEntity;
import com.minedota.entity.BotHeroEntity;
import com.minedota.entity.CreepEntity;
import com.minedota.entity.RangedCreepEntity;
import com.minedota.entity.TowerEntity;
import com.minedota.team.DotaTeam;
import com.minedota.team.TeamComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Strength / Agility hero-specific casts (design from minedota-heroes-abilities.xlsx).
 */
public final class StrAgiAbilities {
	public record CastResult(boolean handled, String message) {
		public static CastResult pass() {
			return new CastResult(false, null);
		}

		public static CastResult ok() {
			return new CastResult(true, null);
		}

		public static CastResult ok(String code) {
			return new CastResult(true, code);
		}

		public static CastResult fail(String msg) {
			return new CastResult(true, msg);
		}
	}

	private StrAgiAbilities() {
	}

	public static CastResult tryCast(ServerPlayerEntity player, AbilityDef ab, AbilitySlot slot,
			HeroDef hero, HeroProgress prog) {
		return switch (ab.id()) {
			case "berserkers_call" -> wrap(castCall(player, ab, prog.getRank(slot)));
			case "battle_hunger" -> wrap(castHunger(player, ab, prog.scaledAbilityPower(ab, slot, hero), prog.getRank(slot)));
			case "culling_blade" -> wrap(castCull(player, ab, prog.scaledAbilityPower(ab, slot, hero), prog.getRank(slot)));
			case "meat_hook" -> wrap(castHook(player, ab, prog.scaledAbilityPower(ab, slot, hero), prog.getRank(slot)));
			case "rot" -> wrap(castRot(player, prog, prog.getRank(slot)));
			case "dismember" -> wrap(castDismember(player, ab, prog, hero, slot));
			case "storm_hammer" -> wrap(castStormHammer(player, ab, prog.scaledAbilityPower(ab, slot, hero), prog.getRank(slot)));
			case "warcry" -> wrap(castWarcry(player, ab, prog.getRank(slot)));
			case "gods_strength" -> wrap(castGodsStrength(player, prog, prog.getRank(slot)));
			case "overwhelming_odds" -> wrap(castOdds(player, ab, prog, hero, slot));
			case "press_the_attack" -> wrap(castPta(player, ab, prog, hero, slot));
			case "duel" -> wrap(castDuel(player, ab, prog, prog.getRank(slot)));
			case "blade_fury" -> wrap(castBladeFury(player, ab, prog, hero, slot));
			case "healing_ward" -> wrap(castHealingWard(player, ab, prog.getRank(slot)));
			case "omnislash" -> wrap(castOmnislash(player, ab, prog, hero, slot));
			case "stifling_dagger" -> wrap(castDagger(player, ab, prog, hero, slot));
			case "gust" -> wrap(castGust(player, ab, prog.getRank(slot)));
			case "multishot" -> wrap(castMultishot(player, ab, prog, hero, slot));
			case "counterspell" -> wrap(castCounterspell(player, ab, prog.getRank(slot)));
			case "mana_void" -> wrap(castManaVoid(player, ab, prog.getRank(slot)));
			case "shrapnel" -> wrap(castShrapnel(player, ab, prog, hero, slot));
			case "assassinate" -> wrap(castAssassinate(player, ab, prog, hero, slot));
			default -> CastResult.pass();
		};
	}

	private static CastResult wrap(String errOrCode) {
		if (errOrCode == null) {
			return CastResult.ok();
		}
		if ("NO_CD".equals(errOrCode) || "CULL_RESET".equals(errOrCode)) {
			return CastResult.ok(errOrCode);
		}
		return CastResult.fail(errOrCode);
	}

	// —— Axe ——

	private static String castCall(ServerPlayerEntity player, AbilityDef ab, int rank) {
		ServerWorld world = player.getServerWorld();
		DotaTeam team = TeamComponent.getTeam(player);
		float rad = ab.radius() > 0 ? ab.radius() : 4f;
		int dur = durationTicks(ab, rank, 30, 10); // 1.5→3s
		int armorAmp = Math.min(3, Math.max(0, rank - 1)); // Resistance amplifier approx armor
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, dur, armorAmp, false, true));
		burst(world, player.getPos(), rad, ParticleTypes.ANGRY_VILLAGER, SoundEvents.ENTITY_RAVAGER_ROAR);
		for (LivingEntity e : enemies(world, player, team, rad)) {
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, dur, 2, false, true));
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, dur, 0, false, true));
			// Force look / slow — taunt approximation
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, dur, 1, false, true));
		}
		return null;
	}

	private static String castHunger(ServerPlayerEntity player, AbilityDef ab, float power, int rank) {
		ServerWorld world = player.getServerWorld();
		DotaTeam team = TeamComponent.getTeam(player);
		float range = ab.radius() > 0 ? ab.radius() : (7f + rank);
		LivingEntity target = nearestEnemy(world, player, team, range);
		if (target == null) {
			return "Battle Hunger: нет цели.";
		}
		int dur = durationTicks(ab, rank, 120, 20); // 6→9s
		float dps = Math.max(1f, power); // scaled already ~2-5
		float slow = 0.12f + 0.04f * (rank - 1);
		AbilityRuntime.startBattleHunger(target, player.getUuid(), dps, dur, slow, team);
		world.spawnParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 1, target.getZ(), 20, 0.3, 0.5, 0.3, 0.02);
		world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 0.7f, 0.8f);
		return null;
	}

	private static String castCull(ServerPlayerEntity player, AbilityDef ab, float power, int rank) {
		ServerWorld world = player.getServerWorld();
		DotaTeam team = TeamComponent.getTeam(player);
		float range = ab.radius() > 0 ? ab.radius() : 3f;
		LivingEntity target = nearestEnemy(world, player, team, range);
		if (target == null) {
			return "Culling Blade: нет цели.";
		}
		float threshold = 10f + 5f * rank; // 15/20/25
		boolean execute = target.getHealth() <= threshold;
		world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1f, 0.7f);
		if (execute) {
			float lethal = target.getHealth() + target.getAbsorptionAmount() + 50f;
			target.damage(world.getDamageSources().magic(), lethal);
			AbilityRuntime.markCullReset(player.getUuid());
			for (LivingEntity ally : allies(world, player, team, 6f)) {
				ally.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 80, 1, false, true));
			}
			player.sendMessage(net.minecraft.text.Text.literal("Culling Blade: EXECUTE"), true);
		} else {
			target.damage(world.getDamageSources().magic(), power);
		}
		return execute ? "CULL_RESET" : null;
	}

	// —— Pudge ——

	private static String castHook(ServerPlayerEntity player, AbilityDef ab, float power, int rank) {
		ServerWorld world = player.getServerWorld();
		DotaTeam team = TeamComponent.getTeam(player);
		float range = ab.radius() > 0 ? ab.radius() : (8f + 2f * (rank - 1));
		Vec3d start = player.getEyePos();
		Vec3d dir = AbilityGeometry.lookFlat(player);
		LivingEntity hit = null;
		for (int i = 1; i <= (int) (range * 2); i++) {
			Vec3d p = start.add(dir.multiply(i * 0.5));
			world.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0);
			Box box = new Box(p.x - 0.7, p.y - 0.7, p.z - 0.7, p.x + 0.7, p.y + 0.7, p.z + 0.7);
			for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, ent -> isEnemy(ent, player, team))) {
				if (isStructure(e)) {
					continue;
				}
				hit = e;
				break;
			}
			if (hit != null) {
				break;
			}
		}
		if (hit == null) {
			return "Meat Hook: мимо.";
		}
		hit.damage(world.getDamageSources().magic(), power);
		Vec3d pull = player.getPos().subtract(hit.getPos());
		hit.refreshPositionAndAngles(player.getX() + dir.x, player.getY(), player.getZ() + dir.z,
				hit.getYaw(), hit.getPitch());
		hit.setVelocity(0, 0.1, 0);
		hit.velocityModified = true;
		world.playSound(null, hit.getBlockPos(), SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE, SoundCategory.PLAYERS, 1f, 0.6f);
		return null;
	}

	private static String castRot(ServerPlayerEntity player, HeroProgress prog, int rank) {
		boolean on = prog.toggleRot();
		if (on) {
			float dps = 2f + rank; // 3/4/5/6
			AbilityRuntime.startRot(player.getUuid(), dps, 3.5f, TeamComponent.getTeam(player));
			player.sendMessage(net.minecraft.text.Text.literal("Rot: ON"), true);
		} else {
			AbilityRuntime.stopRot(player.getUuid());
			player.sendMessage(net.minecraft.text.Text.literal("Rot: OFF"), true);
		}
		return "NO_CD";
	}

	private static String castDismember(ServerPlayerEntity player, AbilityDef ab, HeroProgress prog,
			HeroDef hero, AbilitySlot slot) {
		ServerWorld world = player.getServerWorld();
		DotaTeam team = TeamComponent.getTeam(player);
		int rank = prog.getRank(slot);
		LivingEntity target = nearestEnemy(world, player, team, 2f);
		if (target == null || isStructure(target)) {
			return "Dismember: цель ≤2 бл.";
		}
		int channel = (int) (50 + rank * 6); // ~2.5→3.1s
		float tickDmg = prog.scaledAbilityPower(ab, slot, hero);
		AbilityRuntime.startDismember(player.getUuid(), target, tickDmg, channel, team);
		return null;
	}

	// —— Sven ——

	private static String castStormHammer(ServerPlayerEntity player, AbilityDef ab, float power, int rank) {
		ServerWorld world = player.getServerWorld();
		DotaTeam team = TeamComponent.getTeam(player);
		float range = ab.radius() > 0 ? ab.radius() : 10f;
		Vec3d start = player.getEyePos();
		Vec3d dir = AbilityGeometry.lookFlat(player);
		LivingEntity hit = null;
		for (int i = 1; i <= (int) range; i++) {
			Vec3d p = start.add(dir.multiply(i));
			world.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 3, 0.1, 0.1, 0.1, 0.01);
			Box box = new Box(p.x - 0.9, p.y - 0.9, p.z - 0.9, p.x + 0.9, p.y + 0.9, p.z + 0.9);
			for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, ent -> isEnemy(ent, player, team))) {
				hit = e;
				break;
			}
			if (hit != null) {
				break;
			}
		}
		if (hit == null) {
			return "Storm Hammer: мимо.";
		}
		int stun = durationTicks(ab, rank, 24, 6); // 1.2→2.1
		hit.damage(world.getDamageSources().magic(), power);
		hit.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, stun, 6, false, true));
		hit.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, stun, 2, false, true));
		hit.setVelocity(0, hit.getVelocity().y, 0);
		hit.velocityModified = true;
		world.playSound(null, hit.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.PLAYERS, 1f, 0.8f);
		return null;
	}

	private static String castWarcry(ServerPlayerEntity player, AbilityDef ab, int rank) {
		ServerWorld world = player.getServerWorld();
		DotaTeam team = TeamComponent.getTeam(player);
		int dur = durationTicks(ab, rank, 120, 20); // 6→9
		int resist = Math.min(2, rank / 2);
		burst(world, player.getPos(), 6, ParticleTypes.TOTEM_OF_UNDYING, SoundEvents.ENTITY_PILLAGER_CELEBRATE);
		for (LivingEntity ally : allies(world, player, team, 6f)) {
			ally.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, dur, resist, false, true));
			ally.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, dur, 0, false, true));
		}
		return null;
	}

	private static String castGodsStrength(ServerPlayerEntity player, HeroProgress prog, int rank) {
		int dur = switch (rank) {
			case 1 -> 12 * 20;
			case 2 -> 14 * 20;
			default -> 16 * 20;
		};
		float mult = switch (rank) {
			case 1 -> 0.80f;
			case 2 -> 1.20f;
			default -> 1.60f;
		};
		prog.startGodsStrength(dur, mult);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, dur, rank, false, true));
		player.getServerWorld().spawnParticles(ParticleTypes.FLAME,
				player.getX(), player.getY() + 1, player.getZ(), 40, 0.5, 0.8, 0.5, 0.05);
		player.getServerWorld().playSound(null, player.getBlockPos(),
				SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 0.5f, 1.4f);
		return null;
	}

	// —— Legion ——

	private static String castOdds(ServerPlayerEntity player, AbilityDef ab, HeroProgress prog,
			HeroDef hero, AbilitySlot slot) {
		ServerWorld world = player.getServerWorld();
		DotaTeam team = TeamComponent.getTeam(player);
		int rank = prog.getRank(slot);
		float rad = 5f;
		float base = 6f + 2f * rank; // 8/10/12/14
		float perCreep = 1.5f + 0.5f * rank; // 2/2.5/3/3.5
		float perHero = 4f + rank; // 5/6/7/8
		int creeps = 0;
		int heroes = 0;
		for (LivingEntity e : enemies(world, player, team, rad)) {
			if (e instanceof CreepEntity || e instanceof RangedCreepEntity) {
				creeps++;
			} else if (e instanceof ServerPlayerEntity || e instanceof BotHeroEntity) {
				heroes++;
			}
		}
		float dmg = base + perCreep * creeps + perHero * heroes;
		burst(world, player.getPos(), rad, ParticleTypes.SWEEP_ATTACK, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP);
		for (LivingEntity e : enemies(world, player, team, rad)) {
			e.damage(world.getDamageSources().magic(), dmg);
		}
		int asDur = (2 + rank) * 20; // 3→6
		prog.addAttackSpeedBonus(20 * rank, asDur); // 20/40/60/80 approx 20/30/40/50
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, asDur, Math.min(2, rank - 1), false, true));
		return null;
	}

	private static String castPta(ServerPlayerEntity player, AbilityDef ab, HeroProgress prog,
			HeroDef hero, AbilitySlot slot) {
		int rank = prog.getRank(slot);
		float healPerSec = 2f + rank; // 3→6
		int dur = 5 * 20;
		player.clearStatusEffects(); // soft dispel approximation
		player.heal(healPerSec * 2f);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, dur, rank, false, true));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, dur, Math.min(2, rank), false, true));
		prog.addAttackSpeedBonus(20 + 10 * rank, dur);
		player.getServerWorld().spawnParticles(ParticleTypes.HEART,
				player.getX(), player.getY() + 1.2, player.getZ(), 12, 0.4, 0.3, 0.4, 0.02);
		return null;
	}

	private static String castDuel(ServerPlayerEntity player, AbilityDef ab, HeroProgress prog, int rank) {
		ServerWorld world = player.getServerWorld();
		DotaTeam team = TeamComponent.getTeam(player);
		LivingEntity target = nearestEnemy(world, player, team, 2f);
		if (!(target instanceof ServerPlayerEntity) && !(target instanceof BotHeroEntity)) {
			return "Duel: только герой ≤2 бл.";
		}
		int dur = (int) ((2.5f + 0.5f * rank) * 20); // 3→4 for ranks 1-3 of ult — use 3/3.5/4
		if (rank == 1) {
			dur = 60;
		} else if (rank == 2) {
			dur = 70;
		} else {
			dur = 80;
		}
		AbilityRuntime.startDuel(player.getUuid(), target, dur, 2f + rank, team); // +ATK 3/4/5
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, dur, 0, false, true));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, dur, 0, false, true));
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 0.4f, 1.6f);
		return null;
	}

	// —— Juggernaut ——

	private static String castBladeFury(ServerPlayerEntity player, AbilityDef ab, HeroProgress prog,
			HeroDef hero, AbilitySlot slot) {
		int rank = prog.getRank(slot);
		int dur = (int) ((3.8f + 0.2f * rank) * 20);
		float dps = 3f + rank; // 4→7
		AbilityRuntime.startBladeFury(player.getUuid(), dps, 3.5f, dur, TeamComponent.getTeam(player));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, dur, 2, false, true));
		return null;
	}

	private static String castHealingWard(ServerPlayerEntity player, AbilityDef ab, int rank) {
		int dur = (6 + 2 * rank) * 20; // 8→14
		float pct = 0.01f + 0.01f * rank; // 2→5% ≈ 0.02→0.05
		AbilityRuntime.startHealingWard(player.getUuid(), pct, 5f, dur, TeamComponent.getTeam(player));
		player.getServerWorld().playSound(null, player.getBlockPos(),
				SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.6f, 1.4f);
		return null;
	}

	private static String castOmnislash(ServerPlayerEntity player, AbilityDef ab, HeroProgress prog,
			HeroDef hero, AbilitySlot slot) {
		ServerWorld world = player.getServerWorld();
		DotaTeam team = TeamComponent.getTeam(player);
		LivingEntity start = nearestEnemy(world, player, team, 6f);
		if (start == null) {
			return "Omnislash: нет цели ≤6 бл.";
		}
		int rank = prog.getRank(slot);
		int dur = (int) ((2.5f + 0.5f * rank) * 20); // 3/3.5/4
		AbilityRuntime.startOmnislash(player.getUuid(), start, dur, 5f, prog.getAttackDamage(hero), team);
		return null;
	}

	// —— PA ——

	private static String castDagger(ServerPlayerEntity player, AbilityDef ab, HeroProgress prog,
			HeroDef hero, AbilitySlot slot) {
		ServerWorld world = player.getServerWorld();
		DotaTeam team = TeamComponent.getTeam(player);
		int rank = prog.getRank(slot);
		float pct = 0.60f + 0.10f * rank; // 70→100%
		float raw = prog.getAttackDamage(hero) * pct;
		// Coup de Grace may crit
		if (prog.getRank(AbilitySlot.R) > 0 && rollCoup(prog.getRank(AbilitySlot.R))) {
			raw *= coupMult(prog.getRank(AbilitySlot.R));
		}
		Vec3d start = player.getEyePos();
		Vec3d dir = AbilityGeometry.lookFlat(player);
		LivingEntity hit = null;
		for (int i = 1; i <= 15; i++) {
			Vec3d p = start.add(dir.multiply(i));
			world.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
			Box box = new Box(p.x - 0.6, p.y - 0.6, p.z - 0.6, p.x + 0.6, p.y + 0.6, p.z + 0.6);
			for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, ent -> isEnemy(ent, player, team))) {
				hit = e;
				break;
			}
			if (hit != null) {
				break;
			}
		}
		if (hit == null) {
			return "Stifling Dagger: мимо.";
		}
		hit.damage(world.getDamageSources().magic(), raw);
		int slow = 20 + 5 * rank;
		hit.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, slow / 20, false, true));
		return null;
	}

	private static String castGust(ServerPlayerEntity player, AbilityDef ab, int rank) {
		ServerWorld world = player.getServerWorld();
		DotaTeam team = TeamComponent.getTeam(player);
		Vec3d origin = player.getPos();
		Vec3d look = AbilityGeometry.lookFlat(player);
		float range = 6f;
		double knock = 1.5 + 0.5 * rank;
		int silence = (int) ((1.5f + 0.5f * rank) * 20);
		burst(world, origin, range, ParticleTypes.CLOUD, SoundEvents.ENTITY_PHANTOM_FLAP);
		for (LivingEntity e : enemies(world, player, team, range)) {
			if (!AbilityGeometry.inHorizontalCone(origin, look, e.getPos(), range, 115)) {
				continue;
			}
			Vec3d push = e.getPos().subtract(origin);
			Vec3d flat = new Vec3d(push.x, 0, push.z);
			if (flat.lengthSquared() > 1e-4) {
				flat = flat.normalize().multiply(knock);
				e.addVelocity(flat.x, 0.2, flat.z);
				e.velocityModified = true;
			}
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, silence, 4, false, true));
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, silence, 4, false, true));
		}
		return null;
	}

	private static String castMultishot(ServerPlayerEntity player, AbilityDef ab, HeroProgress prog,
			HeroDef hero, AbilitySlot slot) {
		int rank = prog.getRank(slot);
		float pct = 0.60f + 0.20f * rank; // 80→140%
		float dmg = prog.getAttackDamage(hero) * pct;
		AbilityRuntime.startMultishot(player.getUuid(), dmg, hero.effectiveAttackRange(prog) + 4f,
				3, 4, TeamComponent.getTeam(player));
		return null;
	}

	private static String castCounterspell(ServerPlayerEntity player, AbilityDef ab, int rank) {
		int shield = (int) ((1.0f + 0.2f * rank) * 20);
		HeroProgress prog = HeroManager.get(player.getServer()).getProgress(player.getUuid());
		if (prog != null) {
			prog.armCounterspell(shield);
		}
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, shield, 3, false, true));
		player.getServerWorld().spawnParticles(ParticleTypes.END_ROD,
				player.getX(), player.getY() + 1, player.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
		return null;
	}

	private static String castManaVoid(ServerPlayerEntity player, AbilityDef ab, int rank) {
		ServerWorld world = player.getServerWorld();
		DotaTeam team = TeamComponent.getTeam(player);
		LivingEntity target = nearestEnemy(world, player, team, 8f);
		if (target == null) {
			return "Mana Void: нет цели.";
		}
		float missingPct = Math.max(0f, 1f - target.getHealth() / Math.max(1f, target.getMaxHealth()));
		float dmg = (5f + 3f * rank) + missingPct * (10f + 5f * rank); // 8/11/14 + 15/20/25% missing
		Box box = target.getBoundingBox().expand(5);
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, ent -> isEnemy(ent, player, team))) {
			e.damage(world.getDamageSources().magic(), dmg);
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 6, 6, false, true));
		}
		world.spawnParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 1, target.getZ(),
				40, 1.2, 0.8, 1.2, 0.05);
		world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.3f, 1.8f);
		return null;
	}

	private static String castShrapnel(ServerPlayerEntity player, AbilityDef ab, HeroProgress prog,
			HeroDef hero, AbilitySlot slot) {
		int rank = prog.getRank(slot);
		float dps = 2f + rank;
		int dur = (5 + rank) * 20; // 6→9
		Vec3d look = AbilityGeometry.lookFlat(player);
		double dist = Math.min(14, 8 + rank);
		Vec3d point = player.getPos().add(look.multiply(dist));
		AbilityRuntime.startShrapnel(point, player.getServerWorld().getRegistryKey(), dps, 3.5f, dur,
				0.10f + 0.05f * rank, TeamComponent.getTeam(player));
		return null;
	}

	private static String castAssassinate(ServerPlayerEntity player, AbilityDef ab, HeroProgress prog,
			HeroDef hero, AbilitySlot slot) {
		ServerWorld world = player.getServerWorld();
		DotaTeam team = TeamComponent.getTeam(player);
		LivingEntity target = nearestHero(world, player, team, 28f);
		if (target == null) {
			return "Assassinate: нет героя ≤28 бл.";
		}
		int rank = prog.getRank(slot);
		float dmg = 12f + 6f * rank; // 18/24/30
		AbilityRuntime.startAssassinate(player.getUuid(), target, dmg, 34, team); // 1.7s
		player.sendMessage(net.minecraft.text.Text.literal("Assassinate: прицел…"), true);
		return null;
	}

	// —— Passives (called from combat) ——

	public static float modifyOutgoingAttack(ServerPlayerEntity sp, HeroDef def, HeroProgress prog,
			LivingEntity target, float raw) {
		float dmg = raw;
		int r;

		// Blade Dance
		if ("jugg".equals(def.id()) && (r = prog.getRank(AbilitySlot.E)) > 0) {
			float chance = 0.15f + 0.05f * r; // 20→35
			if (sp.getRandom().nextFloat() < chance) {
				float bonus = 0.20f + 0.20f * r; // +40→100% → total 140→200
				dmg *= (1f + bonus);
				sp.getServerWorld().spawnParticles(ParticleTypes.CRIT,
						target.getX(), target.getY() + 1, target.getZ(), 15, 0.3, 0.4, 0.3, 0.05);
			}
		}
		// Coup de Grace
		if ("pa".equals(def.id()) && (r = prog.getRank(AbilitySlot.R)) > 0 && rollCoup(r)) {
			dmg *= coupMult(r);
			sp.getServerWorld().spawnParticles(ParticleTypes.ENCHANTED_HIT,
					target.getX(), target.getY() + 1, target.getZ(), 20, 0.3, 0.5, 0.3, 0.08);
		}
		// Mana Break stub
		if ("am".equals(def.id()) && (r = prog.getRank(AbilitySlot.Q)) > 0) {
			dmg *= (1f + 0.15f + 0.10f * r); // +25→55%
		}
		// Frost Arrows
		if ("drow".equals(def.id()) && (r = prog.getRank(AbilitySlot.Q)) > 0) {
			dmg += 1f + r; // +2→5
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, r / 2, false, true));
		}
		// Marksmanship
		if ("drow".equals(def.id()) && (r = prog.getRank(AbilitySlot.R)) > 0) {
			if (!enemyHeroNearby(sp, 3f) && sp.getRandom().nextFloat() < (0.15f + 0.15f * r)) {
				dmg += 2f + 3f * r; // +5/8/11
			}
		}
		// Headshot
		if ("sniper".equals(def.id()) && (r = prog.getRank(AbilitySlot.W)) > 0) {
			float chance = 0.10f + 0.10f * r;
			if (sp.getRandom().nextFloat() < chance) {
				dmg += 2f + r;
				Vec3d push = target.getPos().subtract(sp.getPos());
				Vec3d flat = new Vec3d(push.x, 0, push.z);
				if (flat.lengthSquared() > 1e-4 && !isStructure(target)) {
					flat = flat.normalize();
					target.addVelocity(flat.x, 0.15, flat.z);
					target.velocityModified = true;
				}
			}
		}
		return dmg;
	}

	/** Sven Great Cleave after primary hit. */
	public static void onAttackLanded(ServerPlayerEntity sp, HeroDef def, HeroProgress prog,
			LivingEntity primary, float rawDmg) {
		if (!"sven".equals(def.id())) {
			return;
		}
		int r = prog.getRank(AbilitySlot.W);
		if (r <= 0) {
			return;
		}
		float pct = 0.10f + 0.20f * r; // 30→90
		DotaTeam team = TeamComponent.getTeam(sp);
		Vec3d origin = sp.getPos();
		Vec3d look = AbilityGeometry.lookFlat(sp);
		Box box = sp.getBoundingBox().expand(2.5);
		for (LivingEntity e : sp.getServerWorld().getEntitiesByClass(LivingEntity.class, box,
				ent -> isEnemy(ent, sp, team) && ent != primary && !isStructure(ent))) {
			if (!AbilityGeometry.inHorizontalCone(origin, look, e.getPos(), 2.5, 115)) {
				continue;
			}
			e.damage(sp.getServerWorld().getDamageSources().playerAttack(sp), rawDmg * pct);
		}
	}

	/** Incoming damage hooks: Blur evasion, Counter Helix, Moment of Courage. */
	public static boolean tryEvade(ServerPlayerEntity victim, HeroDef def, HeroProgress prog) {
		if (!"pa".equals(def.id())) {
			return false;
		}
		int r = prog.getRank(AbilitySlot.E);
		if (r <= 0) {
			return false;
		}
		float fade = 11f - r; // 10→7
		if (enemyHeroNearby(victim, fade)) {
			return false;
		}
		float chance = 0.10f + 0.05f * r; // 15→30
		return victim.getRandom().nextFloat() < chance;
	}

	public static void onDamaged(ServerPlayerEntity victim, HeroDef def, HeroProgress prog,
			LivingEntity attacker) {
		if (attacker == null || !attacker.isAlive()) {
			return;
		}
		DotaTeam team = TeamComponent.getTeam(victim);
		if (TeamComponent.getTeam(attacker) != team.opposite()) {
			return;
		}

		if ("axe".equals(def.id())) {
			int r = prog.getRank(AbilitySlot.E);
			if (r > 0 && prog.tickHelixHit(r)) {
				float dmg = 6f + 2f * r; // 8→14
				Box box = victim.getBoundingBox().expand(3);
				for (LivingEntity e : victim.getServerWorld().getEntitiesByClass(LivingEntity.class, box,
						ent -> isEnemy(ent, victim, team))) {
					e.damage(victim.getServerWorld().getDamageSources().magic(), dmg);
				}
				victim.getServerWorld().spawnParticles(ParticleTypes.SWEEP_ATTACK,
						victim.getX(), victim.getY() + 1, victim.getZ(), 10, 1, 0.3, 1, 0.02);
			}
		}

		if ("legion".equals(def.id())) {
			int r = prog.getRank(AbilitySlot.E);
			if (r > 0 && prog.tryMomentOfCourage(r)) {
				float atk = prog.getAttackDamage(def);
				attacker.damage(victim.getServerWorld().getDamageSources().playerAttack(victim), atk);
				victim.heal(atk * (0.10f + 0.05f * r)); // 15→30%
			}
		}
	}

	public static void onHeroKill(ServerPlayerEntity killer, HeroDef def, HeroProgress prog) {
		if ("pudge".equals(def.id()) && prog.getRank(AbilitySlot.E) > 0) {
			prog.addFleshHeap(prog.getRank(AbilitySlot.E));
		}
		AbilityRuntime.onDuelKill(killer.getUuid(), prog);
	}

	// —— helpers ——

	private static boolean rollCoup(int rank) {
		float chance = 0.08f + 0.02f * rank; // 10/12/14
		return Math.random() < chance;
	}

	private static float coupMult(int rank) {
		return switch (rank) {
			case 1 -> 2.20f;
			case 2 -> 3.30f;
			default -> 4.40f;
		};
	}

	private static boolean enemyHeroNearby(ServerPlayerEntity sp, float radius) {
		DotaTeam team = TeamComponent.getTeam(sp);
		Box box = sp.getBoundingBox().expand(radius);
		for (LivingEntity e : sp.getServerWorld().getEntitiesByClass(LivingEntity.class, box,
				ent -> isEnemy(ent, sp, team))) {
			if (e instanceof ServerPlayerEntity || e instanceof BotHeroEntity) {
				return true;
			}
		}
		return false;
	}

	private static int durationTicks(AbilityDef ab, int rank, int base, int perRank) {
		if (ab.durationTicks() > 0 && rank <= 1) {
			return ab.durationTicks();
		}
		return base + Math.max(0, rank - 1) * perRank;
	}

	private static void burst(ServerWorld world, Vec3d pos, float radius,
			net.minecraft.particle.ParticleEffect particle, net.minecraft.sound.SoundEvent sound) {
		world.spawnParticles(particle, pos.x, pos.y + 1, pos.z, 30, radius * 0.35, 0.4, radius * 0.35, 0.04);
		world.playSound(null, pos.x, pos.y, pos.z, sound, SoundCategory.PLAYERS, 0.9f, 1f);
	}

	private static List<LivingEntity> enemies(ServerWorld world, ServerPlayerEntity player, DotaTeam team, float radius) {
		return world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(radius),
				e -> isEnemy(e, player, team));
	}

	private static List<LivingEntity> allies(ServerWorld world, ServerPlayerEntity player, DotaTeam team, float radius) {
		return world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(radius), e -> {
			if (!e.isAlive()) {
				return false;
			}
			DotaTeam t = TeamComponent.getTeam(e);
			return t == team && (e instanceof ServerPlayerEntity || e instanceof BotHeroEntity
					|| e == player);
		});
	}

	private static LivingEntity nearestEnemy(ServerWorld world, ServerPlayerEntity player, DotaTeam team, float radius) {
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (LivingEntity e : enemies(world, player, team, radius)) {
			double d = e.squaredDistanceTo(player);
			if (d < bestDist) {
				bestDist = d;
				best = e;
			}
		}
		return best;
	}

	private static LivingEntity nearestHero(ServerWorld world, ServerPlayerEntity player, DotaTeam team, float radius) {
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (LivingEntity e : enemies(world, player, team, radius)) {
			if (!(e instanceof ServerPlayerEntity) && !(e instanceof BotHeroEntity)) {
				continue;
			}
			double d = e.squaredDistanceTo(player);
			if (d < bestDist) {
				bestDist = d;
				best = e;
			}
		}
		return best;
	}

	private static boolean isStructure(LivingEntity e) {
		return e instanceof TowerEntity || e instanceof BarrackEntity || e instanceof AncientEntity;
	}

	private static boolean isEnemy(LivingEntity e, ServerPlayerEntity player, DotaTeam team) {
		if (!e.isAlive() || e == player || team == DotaTeam.NONE) {
			return false;
		}
		return TeamComponent.getTeam(e) == team.opposite();
	}
}
