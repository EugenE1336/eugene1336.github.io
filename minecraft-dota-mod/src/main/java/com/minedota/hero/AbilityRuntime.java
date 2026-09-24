package com.minedota.hero;

import com.minedota.team.DotaTeam;
import com.minedota.team.TeamComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Delayed ability pulses (Avalanche waves, Toss landing).
 */
public final class AbilityRuntime {
	private static final List<AvalanchePulse> AVALANCHES = new ArrayList<>();
	private static final List<TossFlight> TOSSES = new ArrayList<>();

	private AbilityRuntime() {
	}

	public static void startAvalanche(ServerPlayerEntity caster, Vec3d origin, Vec3d lookFlat,
			float totalDamage, int waveCount, int intervalTicks, int stunTicks, float range) {
		startAvalanche(caster, origin, lookFlat, totalDamage, waveCount, intervalTicks, stunTicks, range, 120);
	}

	public static void startAvalanche(ServerPlayerEntity caster, Vec3d origin, Vec3d lookFlat,
			float totalDamage, int waveCount, int intervalTicks, int stunTicks, float range, double coneDeg) {
		if (waveCount <= 0) {
			return;
		}
		float per = totalDamage / waveCount;
		AVALANCHES.add(new AvalanchePulse(caster.getUuid(), caster.getServerWorld().getRegistryKey(),
				origin, lookFlat.normalize(), per, waveCount, 0, intervalTicks, stunTicks, range, coneDeg,
				TeamComponent.getTeam(caster)));
	}

	public static void startToss(LivingEntity target, float damage, double lift, DotaTeam casterTeam) {
		// Buildings are not tossable (towers / barracks / ancient)
		if (target instanceof com.minedota.entity.TowerEntity
				|| target instanceof com.minedota.entity.BarrackEntity
				|| target instanceof com.minedota.entity.AncientEntity) {
			return;
		}
		int floorY = (int) Math.floor(target.getY());
		double x = target.getX();
		double z = target.getZ();
		target.refreshPositionAndAngles(x, target.getY() + lift, z, target.getYaw(), target.getPitch());
		target.setVelocity(0, 0.05, 0);
		target.velocityModified = true;
		target.fallDistance = 0;
		ServerWorld world = (ServerWorld) target.getWorld();
		TOSSES.add(new TossFlight(target, world.getRegistryKey(), damage, floorY, 100, casterTeam));
	}

	/** @deprecated use overload with caster team */
	public static void startToss(LivingEntity target, float damage, double lift) {
		startToss(target, damage, lift, TeamComponent.getTeam(target).opposite());
	}

	public static void tick(MinecraftServer server) {
		tickAvalanches(server);
		tickTosses(server);
	}

	private static void tickAvalanches(MinecraftServer server) {
		Iterator<AvalanchePulse> it = AVALANCHES.iterator();
		while (it.hasNext()) {
			AvalanchePulse p = it.next();
			if (p.ticksToNext > 0) {
				p.ticksToNext--;
				continue;
			}
			ServerWorld world = server.getWorld(p.worldKey);
			if (world == null) {
				it.remove();
				continue;
			}
			fireAvalancheWave(world, p);
			p.wavesLeft--;
			if (p.wavesLeft <= 0) {
				it.remove();
			} else {
				p.ticksToNext = p.intervalTicks;
			}
		}
	}

	private static void fireAvalancheWave(ServerWorld world, AvalanchePulse p) {
		world.playSound(null, p.origin.x, p.origin.y, p.origin.z,
				SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.35f, 1.4f);
		for (int i = 1; i <= 8; i++) {
			double dist = p.range * i / 8.0;
			for (int a = -2; a <= 2; a++) {
				float yaw = (float) Math.atan2(p.look.z, p.look.x) + a * 0.15f;
				double px = p.origin.x + Math.cos(yaw) * dist;
				double pz = p.origin.z + Math.sin(yaw) * dist;
				world.spawnParticles(ParticleTypes.CLOUD, px, p.origin.y + 0.4, pz, 2, 0.15, 0.1, 0.15, 0.01);
				world.spawnParticles(ParticleTypes.CRIT, px, p.origin.y + 0.2, pz, 1, 0.05, 0.05, 0.05, 0);
			}
		}
		Box box = new Box(p.origin.x - p.range, p.origin.y - 2, p.origin.z - p.range,
				p.origin.x + p.range, p.origin.y + 4, p.origin.z + p.range);
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, ent -> isEnemy(ent, p.team))) {
			if (!AbilityGeometry.inHorizontalCone(p.origin, p.look, e.getPos(), p.range, p.coneDeg)) {
				continue;
			}
			e.damage(world.getDamageSources().magic(), p.damagePerWave);
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, p.stunTicks, 6, false, true));
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, p.stunTicks, 2, false, true));
			e.setVelocity(0, e.getVelocity().y, 0);
			e.velocityModified = true;
		}
	}

	private static void tickTosses(MinecraftServer server) {
		Iterator<TossFlight> it = TOSSES.iterator();
		while (it.hasNext()) {
			TossFlight t = it.next();
			t.ticksLeft--;
			ServerWorld world = server.getWorld(t.worldKey);
			LivingEntity living = t.target;
			if (world == null || t.ticksLeft <= 0 || living == null || !living.isAlive() || living.isRemoved()) {
				it.remove();
				continue;
			}
			if (!living.isOnGround()) {
				t.wasAirborne = true;
				continue;
			}
			if (t.wasAirborne && living.getY() <= t.startY + 1.0) {
				Vec3d land = living.getPos();
				Box box = new Box(land.x - 2, land.y - 1, land.z - 2, land.x + 2, land.y + 3, land.z + 2);
				for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box,
						ent -> isEnemy(ent, t.casterTeam) && ent.squaredDistanceTo(land) <= 4.0)) {
					e.damage(world.getDamageSources().magic(), t.damage);
				}
				world.spawnParticles(ParticleTypes.EXPLOSION,
						land.x, land.y, land.z, 8, 0.6, 0.2, 0.6, 0.02);
				world.playSound(null, land.x, land.y, land.z,
						SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.5f, 1.2f);
				it.remove();
			}
		}
	}

	private static boolean isEnemy(LivingEntity ent, DotaTeam team) {
		if (team == DotaTeam.NONE || !ent.isAlive()) {
			return false;
		}
		return TeamComponent.getTeam(ent) == team.opposite();
	}

	private static final class AvalanchePulse {
		final UUID casterId;
		final net.minecraft.registry.RegistryKey<net.minecraft.world.World> worldKey;
		final Vec3d origin;
		final Vec3d look;
		final float damagePerWave;
		int wavesLeft;
		int ticksToNext;
		final int intervalTicks;
		final int stunTicks;
		final float range;
		final double coneDeg;
		final DotaTeam team;

		AvalanchePulse(UUID casterId, net.minecraft.registry.RegistryKey<net.minecraft.world.World> worldKey,
				Vec3d origin, Vec3d look, float damagePerWave, int wavesLeft, int ticksToNext,
				int intervalTicks, int stunTicks, float range, double coneDeg, DotaTeam team) {
			this.casterId = casterId;
			this.worldKey = worldKey;
			this.origin = origin;
			this.look = look;
			this.damagePerWave = damagePerWave;
			this.wavesLeft = wavesLeft;
			this.ticksToNext = ticksToNext;
			this.intervalTicks = intervalTicks;
			this.stunTicks = stunTicks;
			this.range = range;
			this.coneDeg = coneDeg;
			this.team = team;
		}
	}

	private static final class TossFlight {
		final LivingEntity target;
		final net.minecraft.registry.RegistryKey<net.minecraft.world.World> worldKey;
		final float damage;
		final int startY;
		int ticksLeft;
		boolean wasAirborne;
		final DotaTeam casterTeam;

		TossFlight(LivingEntity target, net.minecraft.registry.RegistryKey<net.minecraft.world.World> worldKey,
				float damage, int startY, int ticksLeft, DotaTeam casterTeam) {
			this.target = target;
			this.worldKey = worldKey;
			this.damage = damage;
			this.startY = startY;
			this.ticksLeft = ticksLeft;
			this.casterTeam = casterTeam == null ? DotaTeam.NONE : casterTeam;
		}
	}
}
