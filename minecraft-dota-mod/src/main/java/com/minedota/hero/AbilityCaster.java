package com.minedota.hero;

import com.minedota.map.DotaMap;
import com.minedota.map.DotaTrees;
import com.minedota.team.DotaTeam;
import com.minedota.team.TeamComponent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;

/**
 * Executes ability effects with particles / sounds (DotA-flavoured approximations).
 */
public final class AbilityCaster {
	private AbilityCaster() {
	}

	/**
	 * @return null on success, error message if cast failed (no CD)
	 */
	public static String cast(ServerPlayerEntity player, AbilityDef ability, AbilitySlot slot, HeroDef hero, HeroProgress prog) {
		if (ability.type() == AbilityDef.EffectType.PASSIVE) {
			return "Пассивная способность (бафф уже действует).";
		}
		ServerWorld world = player.getServerWorld();
		DotaTeam team = TeamComponent.getTeam(player);
		float power = prog.scaledAbilityPower(ability, slot, hero);

		// Tiny-specific
		if ("avalanche".equals(ability.id())) {
			return castAvalanche(player, ability, power, prog.getRank(slot));
		}
		if ("toss".equals(ability.id())) {
			return castToss(player, team, ability, power, prog.getRank(slot));
		}
		if ("tree_grab".equals(ability.id())) {
			return castTreeGrab(player, ability, prog, hero, prog.getRank(slot));
		}

		switch (ability.type()) {
			case AOE_DAMAGE -> aoeDamage(world, player, team, ability, power, false);
			case AOE_STUN -> aoeDamage(world, player, team, ability, power, true);
			case AOE_DOT -> aoeDot(world, player, team, ability, power);
			case LINE_DAMAGE -> lineDamage(world, player, team, ability, power);
			case TARGET_NUKE -> targetNuke(world, player, team, ability, power);
			case DASH -> dash(player, ability, power);
			case BLINK -> {
				if ("phantom_strike".equals(ability.id())) {
					phantomStrike(player, ability);
				} else {
					blink(player, ability);
				}
			}
			case BUFF_SELF -> buffSelf(player, ability);
			case HEAL -> heal(player, ability, prog.scaledAbilityHeal(ability, slot, hero));
			case PULL_IN -> pullIn(world, player, team, ability, power);
			case PASSIVE -> {
				return "Пассивная способность.";
			}
		}
		return null;
	}

	/** Avalanche: cone 120° / 5 blocks; waves 3/4/5/6 by rank; every 0.5s stun 0.4s; total dmg = power. */
	private static String castAvalanche(ServerPlayerEntity player, AbilityDef ab, float power, int rank) {
		Vec3d origin = player.getPos();
		Vec3d look = AbilityGeometry.lookFlat(player);
		float range = ab.radius() > 0 ? ab.radius() : 5f;
		int interval = 10; // 0.5s
		int stun = 8; // 0.4s
		int waves = Math.max(3, Math.min(6, 2 + Math.max(1, rank))); // 3/4/5/6
		AbilityRuntime.startAvalanche(player, origin, look, power, waves, interval, stun, range, 120);
		player.getServerWorld().playSound(null, player.getBlockPos(),
				SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.5f, 0.8f);
		return null;
	}

	/** Toss: nearest ≤1 block → lift 5+(rank-1)*2, damage on land. */
	private static String castToss(ServerPlayerEntity player, DotaTeam team, AbilityDef ab, float power, int rank) {
		float range = ab.radius() > 0 ? ab.radius() : 1f;
		LivingEntity target = nearestEnemy(player.getServerWorld(), player, team, range);
		if (target == null) {
			return "Нет врага в радиусе 1 блока.";
		}
		double lift = 5.0 + Math.max(0, rank - 1) * 2.0;
		AbilityRuntime.startToss(target, power, lift, team);
		player.getServerWorld().playSound(null, target.getBlockPos(),
				SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.PLAYERS, 0.8f, 1.4f);
		return null;
	}

	/**
	 * Tree Grab: look at tree ≤2 blocks → eat (charges). Re-press while holding → throw
	 * 10 blocks forward, AoE 2 around impact; starts CD.
	 * @return "TREE_GRAB_OK" (no CD) / "TREE_THROW_OK" (start CD) / error
	 */
	private static String castTreeGrab(ServerPlayerEntity player, AbilityDef ab, HeroProgress prog,
			HeroDef hero, int rank) {
		if (prog.hasTreeGrab()) {
			return throwHeldTree(player, ab, prog, hero, rank);
		}
		float maxDist = ab.radius() > 0 ? ab.radius() : 2f;
		ServerWorld world = player.getServerWorld();
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1f);
		var hit = world.raycast(new RaycastContext(
				eye, eye.add(look.multiply(maxDist + 0.5)),
				RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return "Смотри на дерево в 2 блоках.";
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = world.getBlockState(pos);
		if (!DotaMap.isTreeBlock(state) && !DotaTrees.isLog(state) && !DotaTrees.isLeaves(state)) {
			return "Нужно дерево (смотри на него, ≤2 блока).";
		}
		if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > (maxDist + 0.35) * (maxDist + 0.35)) {
			return "Подойди ближе к дереву (2 блока).";
		}
		if (!DotaTrees.eatOneTree(world, pos, player)) {
			return "Нужно дерево (смотри на него, ≤2 блока).";
		}
		prog.startTreeGrab(rank);
		int hits = prog.getTreeGrabHitsLeft();
		world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
				pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 12, 0.4, 0.8, 0.4, 0.02);
		player.sendMessage(net.minecraft.text.Text.literal(
				"Tree Grab: дерево съедено, " + hits + " ударов. E — бросок."), true);
		return "TREE_GRAB_OK";
	}

	/**
	 * Throw held tree: 10 blocks forward (look yaw), damage in 2-block radius at impact.
	 * Clears remaining charges.
	 */
	private static String throwHeldTree(ServerPlayerEntity player, AbilityDef ab, HeroProgress prog,
			HeroDef hero, int rank) {
		ServerWorld world = player.getServerWorld();
		DotaTeam team = TeamComponent.getTeam(player);
		final double throwRange = 10.0;
		final float aoe = 2.0f;
		Vec3d flat = AbilityGeometry.lookFlat(player);
		double destX = player.getX() + flat.x * throwRange;
		double destZ = player.getZ() + flat.z * throwRange;
		Double landY = findBlinkLandingY(world, destX, destZ, player.getY());
		if (landY == null) {
			// Still throw to air point at player height if blocked
			landY = player.getY();
		}
		Vec3d impact = new Vec3d(destX, landY + 0.5, destZ);

		// Trail particles
		Vec3d start = player.getPos().add(0, 1.2, 0);
		Vec3d delta = impact.subtract(start);
		int steps = 16;
		for (int i = 1; i <= steps; i++) {
			double t = i / (double) steps;
			Vec3d p = start.add(delta.multiply(t));
			world.spawnParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.01);
			world.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
		}

		float dmg = prog.getAttackDamage(hero) * (0.85f + 0.15f * Math.max(1, rank));
		Box box = new Box(impact.x - aoe, impact.y - aoe, impact.z - aoe,
				impact.x + aoe, impact.y + aoe + 1.5, impact.z + aoe);
		int hitCount = 0;
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box,
				ent -> isEnemy(ent, player, team))) {
			e.damage(world.getDamageSources().playerAttack(player), dmg);
			hitCount++;
		}

		world.spawnParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 1, 0, 0, 0, 0);
		world.spawnParticles(ParticleTypes.SWEEP_ATTACK, impact.x, impact.y + 0.5, impact.z, 12, 0.8, 0.3, 0.8, 0.02);
		world.playSound(null, impact.x, impact.y, impact.z,
				SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.7f, 1.3f);
		world.playSound(null, impact.x, impact.y, impact.z,
				SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.PLAYERS, 1.2f, 0.7f);

		prog.clearTreeGrab();
		player.sendMessage(net.minecraft.text.Text.literal(
				"Tree Grab: бросок 10 бл., AoE 2 — попало " + hitCount), true);
		return "TREE_THROW_OK";
	}

	private static void aoeDamage(ServerWorld world, ServerPlayerEntity player, DotaTeam team, AbilityDef ab,
			float power, boolean stun) {
		burst(world, player.getPos(), ab.radius(), ParticleTypes.CRIT, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP);
		for (LivingEntity e : enemies(world, player, team, ab.radius())) {
			e.damage(world.getDamageSources().magic(), power);
			if (stun) {
				e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, Math.max(20, ab.durationTicks()), 4));
				e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, Math.max(20, ab.durationTicks()), 1));
			}
		}
	}

	private static void aoeDot(ServerWorld world, ServerPlayerEntity player, DotaTeam team, AbilityDef ab, float power) {
		burst(world, player.getPos(), ab.radius(), ParticleTypes.SCULK_SOUL, SoundEvents.ENTITY_WITCH_THROW);
		for (LivingEntity e : enemies(world, player, team, ab.radius())) {
			e.damage(world.getDamageSources().magic(), power);
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, Math.max(40, ab.durationTicks()), 1));
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, Math.max(40, ab.durationTicks() / 2), 0));
		}
	}

	private static void lineDamage(ServerWorld world, ServerPlayerEntity player, DotaTeam team, AbilityDef ab, float power) {
		Vec3d start = player.getEyePos();
		Vec3d dir = player.getRotationVec(1f).normalize();
		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1f, 0.8f);
		for (int i = 1; i <= (int) ab.radius(); i++) {
			Vec3d p = start.add(dir.multiply(i));
			world.spawnParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 4, 0.15, 0.15, 0.15, 0.01);
			Box box = new Box(p.x - 0.9, p.y - 0.9, p.z - 0.9, p.x + 0.9, p.y + 0.9, p.z + 0.9);
			for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, ent -> isEnemy(ent, player, team))) {
				e.damage(world.getDamageSources().magic(), power);
				if (ab.durationTicks() > 0) {
					e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ab.durationTicks(), 2));
				}
			}
		}
	}

	private static void targetNuke(ServerWorld world, ServerPlayerEntity player, DotaTeam team, AbilityDef ab, float power) {
		LivingEntity target = nearestEnemy(world, player, team, ab.radius() > 0 ? ab.radius() : 10);
		if (target == null) {
			player.sendMessage(net.minecraft.text.Text.literal("Нет цели в радиусе."), true);
			return;
		}
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
				target.getX(), target.getY() + 1, target.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
		world.playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1f, 1.2f);
		target.damage(world.getDamageSources().magic(), power);
		if (ab.durationTicks() > 0) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ab.durationTicks(), 5));
		}
	}

	private static void dash(ServerPlayerEntity player, AbilityDef ab, float power) {
		Vec3d look = player.getRotationVec(1f);
		Vec3d flat = new Vec3d(look.x, 0, look.z);
		if (flat.lengthSquared() < 1e-6) {
			float yawRad = player.getYaw() * ((float) Math.PI / 180f);
			flat = new Vec3d(-MathHelper.sin(yawRad), 0, MathHelper.cos(yawRad));
		}
		flat = flat.normalize().multiply(Math.max(1.2, ab.radius() * 0.35));
		player.addVelocity(flat.x, 0.15, flat.z);
		player.velocityModified = true;
		ServerWorld world = player.getServerWorld();
		world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 20, 0.4, 0.2, 0.4, 0.05);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.PLAYERS, 0.7f, 1.5f);
		if (power > 0) {
			for (LivingEntity e : enemies(world, player, TeamComponent.getTeam(player), 3)) {
				e.damage(world.getDamageSources().magic(), power);
			}
		}
	}

	/**
	 * PA Phantom Strike: blink onto a unit under the crosshair (ally or enemy creep/hero).
	 */
	private static void phantomStrike(ServerPlayerEntity player, AbilityDef ab) {
		ServerWorld world = player.getServerWorld();
		float range = Math.max(6f, ab.radius());
		LivingEntity target = findStrikeTarget(world, player, range);
		if (target == null) {
			player.sendMessage(net.minecraft.text.Text.literal("Phantom Strike: нет цели (крип/герой).")
					.formatted(net.minecraft.util.Formatting.GRAY), true);
			return;
		}

		double tx = target.getX();
		double tz = target.getZ();
		double ty = target.getY();
		// Land slightly beside target (not inside)
		Vec3d away = player.getPos().subtract(target.getPos());
		Vec3d flat = new Vec3d(away.x, 0, away.z);
		if (flat.lengthSquared() < 0.01) {
			float yawRad = player.getYaw() * ((float) Math.PI / 180f);
			flat = new Vec3d(-MathHelper.sin(yawRad), 0, MathHelper.cos(yawRad));
		}
		flat = flat.normalize().multiply(1.1);
		double destX = tx + flat.x;
		double destZ = tz + flat.z;
		Double landY = findBlinkLandingY(world, destX, destZ, ty);
		if (landY == null) {
			landY = findBlinkLandingY(world, tx, tz, ty);
		}
		if (landY == null) {
			landY = ty;
		}

		world.spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 40, 0.5, 0.5, 0.5, 0.2);
		player.networkHandler.requestTeleport(destX, landY, destZ, player.getYaw(), player.getPitch());
		player.fallDistance = 0f;
		world.playSound(null, destX, landY, destZ, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1.2f);
		world.spawnParticles(ParticleTypes.PORTAL, destX, landY + 1, destZ, 40, 0.5, 0.5, 0.5, 0.2);
		if (ab.durationTicks() > 0) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, ab.durationTicks(), 0));
		}
	}

	private static LivingEntity findStrikeTarget(ServerWorld world, ServerPlayerEntity player, float range) {
		Vec3d start = player.getEyePos();
		Vec3d look = player.getRotationVec(1f);
		Vec3d end = start.add(look.multiply(range));
		Box box = player.getBoundingBox().stretch(look.multiply(range)).expand(1.0);
		var hit = net.minecraft.entity.projectile.ProjectileUtil.raycast(
				player, start, end, box,
				e -> e instanceof LivingEntity living && living.isAlive() && living != player
						&& isStrikeUnit(living),
				range * range);
		if (hit != null && hit.getEntity() instanceof LivingEntity living) {
			return living;
		}
		LivingEntity best = null;
		double bestDist = range * range;
		Box area = player.getBoundingBox().expand(range);
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, area, AbilityCaster::isStrikeUnit)) {
			if (e == player || !e.isAlive()) {
				continue;
			}
			double d = player.squaredDistanceTo(e);
			if (d < bestDist) {
				bestDist = d;
				best = e;
			}
		}
		return best;
	}

	private static boolean isStrikeUnit(LivingEntity e) {
		return e instanceof ServerPlayerEntity
				|| e instanceof com.minedota.entity.CreepEntity
				|| e instanceof com.minedota.entity.RangedCreepEntity;
	}

	/**
	 * Blink: horizontal forward along look yaw (not ender-pearl into look point).
	 * Lands on ground; small natural rises OK (≤3); trees / solids stop the path.
	 */
	private static void blink(ServerPlayerEntity player, AbilityDef ab) {
		ServerWorld world = player.getServerWorld();
		double maxDist = Math.max(4, ab.radius());
		Vec3d look = player.getRotationVec(1f);
		Vec3d flat = new Vec3d(look.x, 0, look.z);
		if (flat.lengthSquared() < 1e-6) {
			float yawRad = player.getYaw() * ((float) Math.PI / 180f);
			flat = new Vec3d(-MathHelper.sin(yawRad), 0, MathHelper.cos(yawRad));
		}
		flat = flat.normalize();

		double startX = player.getX();
		double startY = player.getY();
		double startZ = player.getZ();

		Vec3d best = null;
		final double step = 0.5;
		for (double d = step; d <= maxDist + 1e-6; d += step) {
			double cx = startX + flat.x * d;
			double cz = startZ + flat.z * d;
			Double landY = findBlinkLandingY(world, cx, cz, startY);
			if (landY == null) {
				break; // obstacle (tree / wall / no footing) — stop like DotA blink
			}
			if (landY - startY > 3.0) {
				break; // cliff too high — not a small relief
			}
			if (startY - landY > 4.0) {
				break; // deep hole
			}
			best = new Vec3d(cx, landY, cz);
		}

		if (best == null) {
			player.sendMessage(net.minecraft.text.Text.literal("Blink: нет места впереди.").formatted(net.minecraft.util.Formatting.GRAY), true);
			return;
		}

		world.spawnParticles(ParticleTypes.PORTAL, startX, startY + 1, startZ, 40, 0.5, 0.5, 0.5, 0.2);
		player.networkHandler.requestTeleport(best.x, best.y, best.z, player.getYaw(), player.getPitch());
		player.fallDistance = 0f;
		world.playSound(null, best.x, best.y, best.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
		world.spawnParticles(ParticleTypes.PORTAL, best.x, best.y + 1, best.z, 40, 0.5, 0.5, 0.5, 0.2);
		if (ab.durationTicks() > 0) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, ab.durationTicks(), 0));
		}
	}

	/** Feet Y for standing at (x,z) near preferY; null if blocked / trees / no standable surface. */
	private static Double findBlinkLandingY(ServerWorld world, double x, double z, double preferY) {
		int bx = MathHelper.floor(x);
		int bz = MathHelper.floor(z);
		int base = MathHelper.floor(preferY);

		// Prefer landing near current height; allow slight climb for relief
		for (int y = base + 3; y >= base - 2; y--) {
			BlockPos feet = new BlockPos(bx, y, bz);
			BlockPos below = feet.down();
			BlockPos head = feet.up();

			BlockState floor = world.getBlockState(below);
			BlockState atFeet = world.getBlockState(feet);
			BlockState atHead = world.getBlockState(head);

			// Trees are hard blockers for this column
			if (DotaMap.isTreeBlock(floor) || DotaMap.isTreeBlock(atFeet) || DotaMap.isTreeBlock(atHead)
					|| DotaMap.isTreeBlock(world.getBlockState(below.up(2)))) {
				return null;
			}

			if (!canStandOn(floor, world, below)) {
				continue;
			}
			if (!isBlinkBodyClear(world, feet) || !isBlinkBodyClear(world, head)) {
				continue;
			}
			if (!atFeet.getFluidState().isEmpty()) {
				continue;
			}

			return (double) y;
		}

		// Entire column unusable near preferY — treat as obstacle
		return null;
	}

	private static boolean canStandOn(BlockState state, ServerWorld world, BlockPos pos) {
		if (state.isAir() || DotaMap.isTreeBlock(state)) {
			return false;
		}
		if (state.isOf(Blocks.DIRT_PATH) || state.isOf(Blocks.FARMLAND)
				|| state.isOf(Blocks.BLUE_WOOL) || state.isOf(Blocks.PACKED_ICE) || state.isOf(Blocks.ICE) || state.isOf(Blocks.BLUE_ICE)
				|| state.isOf(Blocks.SNOW_BLOCK) || state.isOf(Blocks.GRASS_BLOCK)
				|| state.isOf(Blocks.DIRT) || state.isOf(Blocks.COARSE_DIRT) || state.isOf(Blocks.PODZOL)
				|| state.isOf(Blocks.STONE) || state.isOf(Blocks.COBBLESTONE)
				|| state.isOf(Blocks.LIME_CONCRETE) || state.isOf(Blocks.RED_CONCRETE)
				|| state.isOf(Blocks.EMERALD_BLOCK) || state.isOf(Blocks.REDSTONE_BLOCK)
				|| state.isOf(Blocks.IRON_BLOCK) || state.isOf(Blocks.OBSIDIAN)
				|| state.isOf(Blocks.SAND) || state.isOf(Blocks.BLACKSTONE)
				|| state.isOf(Blocks.STONE_BRICKS) || state.isOf(Blocks.TERRACOTTA)
				|| state.isOf(Blocks.LIME_TERRACOTTA) || state.isOf(Blocks.RED_TERRACOTTA)
				|| state.isOf(Blocks.GOLD_BLOCK) || state.isOf(Blocks.MAGMA_BLOCK)) {
			return true;
		}
		return Block.isFaceFullSquare(state.getCollisionShape(world, pos), Direction.UP);
	}

	private static boolean isBlinkBodyClear(ServerWorld world, BlockPos pos) {
		BlockState s = world.getBlockState(pos);
		if (DotaMap.isTreeBlock(s)) {
			return false;
		}
		return !s.shouldSuffocate(world, pos);
	}

	private static void buffSelf(ServerPlayerEntity player, AbilityDef ab) {
		int dur = Math.max(60, ab.durationTicks());
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, dur, 1));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, dur, 1));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, dur, 0));
		ServerWorld world = player.getServerWorld();
		world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 25, 0.5, 0.8, 0.5, 0.1);
		world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.6f, 1.3f);
	}

	private static void heal(ServerPlayerEntity player, AbilityDef ab, float healAmount) {
		player.heal(healAmount);
		if (ab.durationTicks() > 0) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, ab.durationTicks(), 1));
		}
		ServerWorld world = player.getServerWorld();
		world.spawnParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.2, player.getZ(), 10, 0.4, 0.3, 0.4, 0.02);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1f, 0.7f);
	}

	private static void pullIn(ServerWorld world, ServerPlayerEntity player, DotaTeam team, AbilityDef ab, float power) {
		burst(world, player.getPos(), ab.radius(), ParticleTypes.REVERSE_PORTAL, SoundEvents.ENTITY_ENDERMAN_SCREAM);
		Vec3d center = player.getPos();
		for (LivingEntity e : enemies(world, player, team, ab.radius())) {
			Vec3d pull = center.subtract(e.getPos()).normalize().multiply(0.9);
			e.addVelocity(pull.x, 0.25, pull.z);
			e.velocityModified = true;
			e.damage(world.getDamageSources().magic(), power);
		}
	}

	private static void burst(ServerWorld world, Vec3d pos, float radius, net.minecraft.particle.ParticleEffect particle,
			net.minecraft.sound.SoundEvent sound) {
		world.spawnParticles(particle, pos.x, pos.y + 1, pos.z, 40, radius * 0.4, 0.5, radius * 0.4, 0.05);
		world.playSound(null, pos.x, pos.y, pos.z, sound, SoundCategory.PLAYERS, 1f, 1f);
	}

	private static List<LivingEntity> enemies(ServerWorld world, ServerPlayerEntity player, DotaTeam team, float radius) {
		Box box = player.getBoundingBox().expand(radius);
		return world.getEntitiesByClass(LivingEntity.class, box, e -> isEnemy(e, player, team));
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

	private static boolean isEnemy(LivingEntity e, ServerPlayerEntity player, DotaTeam team) {
		if (!e.isAlive() || e == player || team == DotaTeam.NONE) {
			return false;
		}
		return TeamComponent.getTeam(e) == team.opposite();
	}
}
