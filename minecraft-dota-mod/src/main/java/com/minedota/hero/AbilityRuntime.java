package com.minedota.hero;

import com.minedota.entity.BotHeroEntity;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Delayed / toggled / channeled ability pulses.
 */
public final class AbilityRuntime {
	private static final List<AvalanchePulse> AVALANCHES = new ArrayList<>();
	private static final List<TossFlight> TOSSES = new ArrayList<>();
	private static final List<BattleHungerFx> HUNGERS = new ArrayList<>();
	private static final List<RotFx> ROTS = new ArrayList<>();
	private static final List<DismemberFx> DISMEMBERS = new ArrayList<>();
	private static final List<BladeFuryFx> FURIES = new ArrayList<>();
	private static final List<HealingWardFx> WARDS = new ArrayList<>();
	private static final List<OmnislashFx> OMNIS = new ArrayList<>();
	private static final List<ShrapnelFx> SHRAPNELS = new ArrayList<>();
	private static final List<AssassinateFx> ASSASSINATES = new ArrayList<>();
	private static final List<MultishotFx> MULTISHOTS = new ArrayList<>();
	private static final List<DuelFx> DUELS = new ArrayList<>();
	private static final Set<UUID> CULL_RESET = new HashSet<>();

	private AbilityRuntime() {
	}

	public static void markCullReset(UUID id) {
		CULL_RESET.add(id);
	}

	public static boolean consumeCullReset(UUID id) {
		return CULL_RESET.remove(id);
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
		if (target instanceof com.minedota.entity.TowerEntity
				|| target instanceof com.minedota.entity.BarrackEntity
				|| target instanceof com.minedota.entity.AncientEntity) {
			return;
		}
		int floorY = (int) Math.floor(target.getY());
		target.refreshPositionAndAngles(target.getX(), target.getY() + lift, target.getZ(),
				target.getYaw(), target.getPitch());
		target.setVelocity(0, 0.05, 0);
		target.velocityModified = true;
		target.fallDistance = 0;
		ServerWorld world = (ServerWorld) target.getWorld();
		TOSSES.add(new TossFlight(target, world.getRegistryKey(), damage, floorY, 100, casterTeam));
	}

	public static void startToss(LivingEntity target, float damage, double lift) {
		startToss(target, damage, lift, TeamComponent.getTeam(target).opposite());
	}

	public static void startBattleHunger(LivingEntity target, UUID casterId, float dps, int duration,
			float slowFraction, DotaTeam team) {
		HUNGERS.removeIf(h -> h.target == target);
		HUNGERS.add(new BattleHungerFx(target, casterId, dps, duration, slowFraction, team));
	}

	public static void startRot(UUID casterId, float dps, float radius, DotaTeam team) {
		stopRot(casterId);
		ROTS.add(new RotFx(casterId, dps, radius, team));
	}

	public static void stopRot(UUID casterId) {
		ROTS.removeIf(r -> r.casterId.equals(casterId));
	}

	public static void startDismember(UUID casterId, LivingEntity target, float tickDmg, int duration, DotaTeam team) {
		DISMEMBERS.add(new DismemberFx(casterId, target, tickDmg, duration, team));
	}

	public static void startBladeFury(UUID casterId, float dps, float radius, int duration, DotaTeam team) {
		FURIES.removeIf(f -> f.casterId.equals(casterId));
		FURIES.add(new BladeFuryFx(casterId, dps, radius, duration, team));
	}

	public static void startHealingWard(UUID casterId, float pctMaxHp, float radius, int duration, DotaTeam team) {
		WARDS.removeIf(w -> w.casterId.equals(casterId));
		WARDS.add(new HealingWardFx(casterId, pctMaxHp, radius, duration, team));
	}

	public static void startOmnislash(UUID casterId, LivingEntity first, int duration, float searchRad,
			float hitDmg, DotaTeam team) {
		OMNIS.add(new OmnislashFx(casterId, first, duration, searchRad, hitDmg, team, 0));
	}

	public static void startShrapnel(Vec3d point, net.minecraft.registry.RegistryKey<net.minecraft.world.World> worldKey,
			float dps, float radius, int duration, float slow, DotaTeam team) {
		SHRAPNELS.add(new ShrapnelFx(point, worldKey, dps, radius, duration, slow, team));
	}

	public static void startAssassinate(UUID casterId, LivingEntity target, float damage, int windup, DotaTeam team) {
		ASSASSINATES.add(new AssassinateFx(casterId, target, damage, windup, team));
	}

	public static void startMultishot(UUID casterId, float arrowDmg, float range, int waves, int arrowsPerWave,
			DotaTeam team) {
		MULTISHOTS.add(new MultishotFx(casterId, arrowDmg, range, waves, arrowsPerWave, team, 0, 0));
	}

	public static void startDuel(UUID casterId, LivingEntity target, int duration, float bonusAtk, DotaTeam team) {
		DUELS.add(new DuelFx(casterId, target, duration, bonusAtk, team));
	}

	public static void onDuelKill(UUID killerId, HeroProgress prog) {
		for (DuelFx d : DUELS) {
			if (d.casterId.equals(killerId) && d.ticksLeft > 0) {
				prog.addDuelBonus(d.bonusAtk);
				d.ticksLeft = 0;
			}
		}
	}

	public static void tick(MinecraftServer server) {
		tickAvalanches(server);
		tickTosses(server);
		tickHungers(server);
		tickRots(server);
		tickDismembers(server);
		tickFuries(server);
		tickWards(server);
		tickOmnis(server);
		tickShrapnels(server);
		tickAssassinates(server);
		tickMultishots(server);
		tickDuels(server);
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
				world.spawnParticles(ParticleTypes.EXPLOSION, land.x, land.y, land.z, 8, 0.6, 0.2, 0.6, 0.02);
				it.remove();
			}
		}
	}

	private static void tickHungers(MinecraftServer server) {
		Iterator<BattleHungerFx> it = HUNGERS.iterator();
		while (it.hasNext()) {
			BattleHungerFx h = it.next();
			h.ticksLeft--;
			if (h.ticksLeft <= 0 || h.target == null || !h.target.isAlive()) {
				it.remove();
				continue;
			}
			if (h.ticksLeft % 20 == 0) {
				h.target.damage(h.target.getWorld().getDamageSources().magic(), h.dps);
				int amp = Math.max(0, Math.min(3, (int) (h.slow * 10)));
				h.target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 25, amp, false, true));
			}
		}
	}

	/** Clear hunger when victim gets a kill (called from KillRewards). */
	public static void clearHungerIfKiller(LivingEntity killer) {
		HUNGERS.removeIf(h -> h.target == killer);
	}

	private static void tickRots(MinecraftServer server) {
		Iterator<RotFx> it = ROTS.iterator();
		while (it.hasNext()) {
			RotFx r = it.next();
			ServerPlayerEntity caster = server.getPlayerManager().getPlayer(r.casterId);
			if (caster == null || !caster.isAlive()) {
				it.remove();
				continue;
			}
			if (server.getTicks() % 20 != 0) {
				continue;
			}
			ServerWorld world = caster.getServerWorld();
			for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
					caster.getBoundingBox().expand(r.radius), ent -> isEnemy(ent, r.team))) {
				e.damage(world.getDamageSources().magic(), r.dps);
			}
			caster.damage(world.getDamageSources().magic(), r.dps * 0.5f);
			world.spawnParticles(ParticleTypes.SCULK_SOUL, caster.getX(), caster.getY() + 0.5, caster.getZ(),
					6, 0.8, 0.3, 0.8, 0.01);
		}
	}

	private static void tickDismembers(MinecraftServer server) {
		Iterator<DismemberFx> it = DISMEMBERS.iterator();
		while (it.hasNext()) {
			DismemberFx d = it.next();
			d.ticksLeft--;
			ServerPlayerEntity caster = server.getPlayerManager().getPlayer(d.casterId);
			if (caster == null || d.target == null || !d.target.isAlive() || d.ticksLeft <= 0
					|| caster.squaredDistanceTo(d.target) > 9) {
				it.remove();
				continue;
			}
			d.target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 6, false, true));
			d.target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 10, 4, false, true));
			d.target.setVelocity(0, d.target.getVelocity().y, 0);
			d.target.velocityModified = true;
			if (d.ticksLeft % 10 == 0) {
				d.target.damage(caster.getServerWorld().getDamageSources().magic(), d.tickDmg);
				caster.getServerWorld().spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
						d.target.getX(), d.target.getY() + 1, d.target.getZ(), 4, 0.2, 0.3, 0.2, 0.02);
			}
		}
	}

	private static void tickFuries(MinecraftServer server) {
		Iterator<BladeFuryFx> it = FURIES.iterator();
		while (it.hasNext()) {
			BladeFuryFx f = it.next();
			f.ticksLeft--;
			ServerPlayerEntity caster = server.getPlayerManager().getPlayer(f.casterId);
			if (caster == null || f.ticksLeft <= 0) {
				it.remove();
				continue;
			}
			if (f.ticksLeft % 4 != 0) {
				continue;
			}
			ServerWorld world = caster.getServerWorld();
			float perTick = f.dps / 5f; // ~5 pulses/sec
			for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
					caster.getBoundingBox().expand(f.radius), ent -> isEnemy(ent, f.team))) {
				e.damage(world.getDamageSources().magic(), perTick);
			}
			world.spawnParticles(ParticleTypes.SWEEP_ATTACK, caster.getX(), caster.getY() + 1, caster.getZ(),
					4, 0.8, 0.2, 0.8, 0.02);
		}
	}

	private static void tickWards(MinecraftServer server) {
		Iterator<HealingWardFx> it = WARDS.iterator();
		while (it.hasNext()) {
			HealingWardFx w = it.next();
			w.ticksLeft--;
			ServerPlayerEntity caster = server.getPlayerManager().getPlayer(w.casterId);
			if (caster == null || w.ticksLeft <= 0) {
				it.remove();
				continue;
			}
			if (server.getTicks() % 20 != 0) {
				continue;
			}
			ServerWorld world = caster.getServerWorld();
			Box box = caster.getBoundingBox().expand(w.radius);
			for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, ent -> {
				DotaTeam t = TeamComponent.getTeam(ent);
				return ent.isAlive() && t == w.team
						&& (ent instanceof ServerPlayerEntity || ent instanceof BotHeroEntity);
			})) {
				e.heal(e.getMaxHealth() * w.pctMaxHp);
				world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + 1, e.getZ(),
						3, 0.2, 0.3, 0.2, 0.01);
			}
		}
	}

	private static void tickOmnis(MinecraftServer server) {
		Iterator<OmnislashFx> it = OMNIS.iterator();
		while (it.hasNext()) {
			OmnislashFx o = it.next();
			o.ticksLeft--;
			ServerPlayerEntity caster = server.getPlayerManager().getPlayer(o.casterId);
			if (caster == null || o.ticksLeft <= 0) {
				it.remove();
				continue;
			}
			o.hitCd--;
			if (o.hitCd > 0) {
				continue;
			}
			o.hitCd = 8;
			LivingEntity next = findOmniTarget(caster, o);
			if (next == null) {
				continue;
			}
			o.current = next;
			double ox = next.getX() + (caster.getRandom().nextDouble() - 0.5);
			double oz = next.getZ() + (caster.getRandom().nextDouble() - 0.5);
			caster.networkHandler.requestTeleport(ox, next.getY(), oz, caster.getYaw(), caster.getPitch());
			next.damage(caster.getServerWorld().getDamageSources().playerAttack(caster), o.hitDmg);
			caster.heal(0.5f);
			caster.getServerWorld().spawnParticles(ParticleTypes.CRIT, next.getX(), next.getY() + 1, next.getZ(),
					8, 0.2, 0.3, 0.2, 0.05);
		}
	}

	private static LivingEntity findOmniTarget(ServerPlayerEntity caster, OmnislashFx o) {
		LivingEntity pivot = o.current != null && o.current.isAlive() ? o.current : caster;
		Box box = pivot.getBoundingBox().expand(o.searchRad);
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : caster.getServerWorld().getEntitiesByClass(LivingEntity.class, box,
				ent -> isEnemy(ent, o.team))) {
			double d = e.squaredDistanceTo(pivot);
			boolean hero = e instanceof ServerPlayerEntity || e instanceof BotHeroEntity;
			double score = hero ? d * 0.5 : d;
			if (score < bestD) {
				bestD = score;
				best = e;
			}
		}
		return best;
	}

	private static void tickShrapnels(MinecraftServer server) {
		Iterator<ShrapnelFx> it = SHRAPNELS.iterator();
		while (it.hasNext()) {
			ShrapnelFx s = it.next();
			s.ticksLeft--;
			ServerWorld world = server.getWorld(s.worldKey);
			if (world == null || s.ticksLeft <= 0) {
				it.remove();
				continue;
			}
			if (s.ticksLeft % 20 != 0) {
				continue;
			}
			Box box = new Box(s.point.x - s.radius, s.point.y - 2, s.point.z - s.radius,
					s.point.x + s.radius, s.point.y + 4, s.point.z + s.radius);
			for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, ent -> isEnemy(ent, s.team))) {
				e.damage(world.getDamageSources().magic(), s.dps);
				int amp = Math.max(0, Math.min(3, (int) (s.slow * 10)));
				e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 25, amp, false, true));
			}
			world.spawnParticles(ParticleTypes.SMOKE, s.point.x, s.point.y + 0.5, s.point.z, 8, 1, 0.2, 1, 0.01);
		}
	}

	private static void tickAssassinates(MinecraftServer server) {
		Iterator<AssassinateFx> it = ASSASSINATES.iterator();
		while (it.hasNext()) {
			AssassinateFx a = it.next();
			a.ticksLeft--;
			ServerPlayerEntity caster = server.getPlayerManager().getPlayer(a.casterId);
			if (caster == null || a.target == null || !a.target.isAlive()) {
				it.remove();
				continue;
			}
			if (a.ticksLeft > 0) {
				caster.getServerWorld().spawnParticles(ParticleTypes.CRIT,
						a.target.getX(), a.target.getY() + a.target.getHeight(), a.target.getZ(),
						2, 0.1, 0.1, 0.1, 0);
				continue;
			}
			a.target.damage(caster.getServerWorld().getDamageSources().magic(), a.damage);
			if (!a.target.isAlive()) {
				HeroManager.get(server).clearCooldown(caster, AbilitySlot.R);
				caster.sendMessage(net.minecraft.text.Text.literal("Assassinate: kill — КД сброшен"), true);
			}
			caster.getServerWorld().playSound(null, a.target.getBlockPos(),
					SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.5f, 1.6f);
			it.remove();
		}
	}

	private static void tickMultishots(MinecraftServer server) {
		Iterator<MultishotFx> it = MULTISHOTS.iterator();
		while (it.hasNext()) {
			MultishotFx m = it.next();
			ServerPlayerEntity caster = server.getPlayerManager().getPlayer(m.casterId);
			if (caster == null) {
				it.remove();
				continue;
			}
			if (m.waveDelay > 0) {
				m.waveDelay--;
				continue;
			}
			fireMultishotWave(caster, m);
			m.wavesLeft--;
			if (m.wavesLeft <= 0) {
				it.remove();
			} else {
				m.waveDelay = 8;
			}
		}
	}

	private static void fireMultishotWave(ServerPlayerEntity caster, MultishotFx m) {
		ServerWorld world = caster.getServerWorld();
		Vec3d origin = caster.getEyePos();
		Vec3d look = AbilityGeometry.lookFlat(caster);
		double baseYaw = Math.atan2(look.z, look.x);
		for (int i = 0; i < m.arrowsPerWave; i++) {
			double offset = (i - (m.arrowsPerWave - 1) / 2.0) * 0.18;
			double yaw = baseYaw + offset;
			Vec3d dir = new Vec3d(Math.cos(yaw), 0, Math.sin(yaw));
			for (int step = 1; step <= (int) m.range; step++) {
				Vec3d p = origin.add(dir.multiply(step));
				world.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
				Box box = new Box(p.x - 0.5, p.y - 0.5, p.z - 0.5, p.x + 0.5, p.y + 0.5, p.z + 0.5);
				LivingEntity hit = null;
				for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, ent -> isEnemy(ent, m.team))) {
					hit = e;
					break;
				}
				if (hit != null) {
					hit.damage(world.getDamageSources().magic(), m.arrowDmg);
					hit.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1, false, true));
					break;
				}
			}
		}
	}

	private static void tickDuels(MinecraftServer server) {
		Iterator<DuelFx> it = DUELS.iterator();
		while (it.hasNext()) {
			DuelFx d = it.next();
			d.ticksLeft--;
			if (d.ticksLeft <= 0) {
				it.remove();
				continue;
			}
			ServerPlayerEntity caster = server.getPlayerManager().getPlayer(d.casterId);
			if (caster == null || d.target == null || !d.target.isAlive()) {
				it.remove();
				continue;
			}
			// Soft lockdown
			d.target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 10, 4, false, true));
			d.target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 2, false, true));
			caster.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 10, 4, false, true));
		}
	}

	private static boolean isEnemy(LivingEntity ent, DotaTeam team) {
		if (team == DotaTeam.NONE || !ent.isAlive()) {
			return false;
		}
		return TeamComponent.getTeam(ent) == team.opposite();
	}

	// —— records / state ——

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

	private static final class BattleHungerFx {
		final LivingEntity target;
		final UUID casterId;
		final float dps;
		int ticksLeft;
		final float slow;
		final DotaTeam team;

		BattleHungerFx(LivingEntity target, UUID casterId, float dps, int ticksLeft, float slow, DotaTeam team) {
			this.target = target;
			this.casterId = casterId;
			this.dps = dps;
			this.ticksLeft = ticksLeft;
			this.slow = slow;
			this.team = team;
		}
	}

	private static final class RotFx {
		final UUID casterId;
		final float dps;
		final float radius;
		final DotaTeam team;

		RotFx(UUID casterId, float dps, float radius, DotaTeam team) {
			this.casterId = casterId;
			this.dps = dps;
			this.radius = radius;
			this.team = team;
		}
	}

	private static final class DismemberFx {
		final UUID casterId;
		final LivingEntity target;
		final float tickDmg;
		int ticksLeft;
		final DotaTeam team;

		DismemberFx(UUID casterId, LivingEntity target, float tickDmg, int ticksLeft, DotaTeam team) {
			this.casterId = casterId;
			this.target = target;
			this.tickDmg = tickDmg;
			this.ticksLeft = ticksLeft;
			this.team = team;
		}
	}

	private static final class BladeFuryFx {
		final UUID casterId;
		final float dps;
		final float radius;
		int ticksLeft;
		final DotaTeam team;

		BladeFuryFx(UUID casterId, float dps, float radius, int ticksLeft, DotaTeam team) {
			this.casterId = casterId;
			this.dps = dps;
			this.radius = radius;
			this.ticksLeft = ticksLeft;
			this.team = team;
		}
	}

	private static final class HealingWardFx {
		final UUID casterId;
		final float pctMaxHp;
		final float radius;
		int ticksLeft;
		final DotaTeam team;

		HealingWardFx(UUID casterId, float pctMaxHp, float radius, int ticksLeft, DotaTeam team) {
			this.casterId = casterId;
			this.pctMaxHp = pctMaxHp;
			this.radius = radius;
			this.ticksLeft = ticksLeft;
			this.team = team;
		}
	}

	private static final class OmnislashFx {
		final UUID casterId;
		LivingEntity current;
		int ticksLeft;
		final float searchRad;
		final float hitDmg;
		final DotaTeam team;
		int hitCd;

		OmnislashFx(UUID casterId, LivingEntity current, int ticksLeft, float searchRad, float hitDmg,
				DotaTeam team, int hitCd) {
			this.casterId = casterId;
			this.current = current;
			this.ticksLeft = ticksLeft;
			this.searchRad = searchRad;
			this.hitDmg = hitDmg;
			this.team = team;
			this.hitCd = hitCd;
		}
	}

	private static final class ShrapnelFx {
		final Vec3d point;
		final net.minecraft.registry.RegistryKey<net.minecraft.world.World> worldKey;
		final float dps;
		final float radius;
		int ticksLeft;
		final float slow;
		final DotaTeam team;

		ShrapnelFx(Vec3d point, net.minecraft.registry.RegistryKey<net.minecraft.world.World> worldKey,
				float dps, float radius, int ticksLeft, float slow, DotaTeam team) {
			this.point = point;
			this.worldKey = worldKey;
			this.dps = dps;
			this.radius = radius;
			this.ticksLeft = ticksLeft;
			this.slow = slow;
			this.team = team;
		}
	}

	private static final class AssassinateFx {
		final UUID casterId;
		final LivingEntity target;
		final float damage;
		int ticksLeft;
		final DotaTeam team;

		AssassinateFx(UUID casterId, LivingEntity target, float damage, int ticksLeft, DotaTeam team) {
			this.casterId = casterId;
			this.target = target;
			this.damage = damage;
			this.ticksLeft = ticksLeft;
			this.team = team;
		}
	}

	private static final class MultishotFx {
		final UUID casterId;
		final float arrowDmg;
		final float range;
		int wavesLeft;
		final int arrowsPerWave;
		final DotaTeam team;
		int waveDelay;

		MultishotFx(UUID casterId, float arrowDmg, float range, int wavesLeft, int arrowsPerWave,
				DotaTeam team, int waveDelay, int unused) {
			this.casterId = casterId;
			this.arrowDmg = arrowDmg;
			this.range = range;
			this.wavesLeft = wavesLeft;
			this.arrowsPerWave = arrowsPerWave;
			this.team = team;
			this.waveDelay = waveDelay;
		}
	}

	private static final class DuelFx {
		final UUID casterId;
		final LivingEntity target;
		int ticksLeft;
		final float bonusAtk;
		final DotaTeam team;

		DuelFx(UUID casterId, LivingEntity target, int ticksLeft, float bonusAtk, DotaTeam team) {
			this.casterId = casterId;
			this.target = target;
			this.ticksLeft = ticksLeft;
			this.bonusAtk = bonusAtk;
			this.team = team;
		}
	}
}
