package com.minedota.hero;

import com.minedota.entity.AncientEntity;
import com.minedota.entity.BarrackEntity;
import com.minedota.entity.BotHeroEntity;
import com.minedota.entity.CreepEntity;
import com.minedota.entity.RangedCreepEntity;
import com.minedota.entity.TowerEntity;
import com.minedota.match.MatchManager;
import com.minedota.team.DotaTeam;
import com.minedota.team.TeamComponent;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Hero auto-attack (melee + ranged) + death → kill rewards.
 */
public final class HeroCombat {
	private HeroCombat() {
	}

	public static void register() {
		AttackEntityCallback.EVENT.register(HeroCombat::onAttack);
		ServerLivingEntityEvents.AFTER_DEATH.register(KillRewards::onEntityDeath);
	}

	/** Called from LivingEntityDamageMixin after a successful hit. */
	public static void notifyDamaged(ServerPlayerEntity victim, LivingEntity attacker) {
		HeroManager hm = HeroManager.get(victim.getServer());
		HeroDef def = hm.getHero(victim.getUuid());
		HeroProgress prog = hm.getProgress(victim.getUuid());
		if (def == null || prog == null) {
			return;
		}
		StrAgiAbilities.onDamaged(victim, def, prog, attacker);
	}

	private static ActionResult onAttack(PlayerEntity player, net.minecraft.world.World world, Hand hand,
			Entity entity, net.minecraft.util.hit.EntityHitResult hitResult) {
		if (world.isClient || !(player instanceof ServerPlayerEntity sp)) {
			return ActionResult.PASS;
		}
		if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
			return ActionResult.PASS;
		}
		MatchManager match = MatchManager.get(sp.getServer());
		if (!match.isInGame()) {
			return ActionResult.PASS;
		}
		HeroManager hm = HeroManager.get(sp.getServer());
		HeroDef def = hm.getHero(sp.getUuid());
		if (def == null) {
			return ActionResult.PASS;
		}
		// Ranged heroes use HERO_ATTACK packet (custom range); still allow close hits here
		boolean ok = tryHeroAttack(sp, target);
		return ok ? ActionResult.FAIL : ActionResult.FAIL;
	}

	/**
	 * @return true if attack was processed (hit or rejected with message)
	 */
	public static boolean tryHeroAttack(ServerPlayerEntity sp, LivingEntity target) {
		MatchManager match = MatchManager.get(sp.getServer());
		if (!match.isInGame()) {
			return false;
		}
		HeroManager hm = HeroManager.get(sp.getServer());
		HeroDef def = hm.getHero(sp.getUuid());
		HeroProgress prog = hm.getProgress(sp.getUuid());
		if (def == null || prog == null) {
			return false;
		}
		if (prog.isAwaitingRespawn()) {
			return true;
		}

		DotaTeam team = TeamComponent.getTeam(sp);
		if (team == DotaTeam.NONE || !isValidHeroTarget(target, team)) {
			return true;
		}

		double distSq = sp.squaredDistanceTo(target);
		float maxRange = def.effectiveAttackRange(prog) + 0.75f;
		if (distSq > maxRange * maxRange) {
			sp.sendMessage(Text.literal("Слишком далеко").formatted(Formatting.GRAY), true);
			return true;
		}

		if (!prog.canAttack()) {
			sp.sendMessage(Text.literal("Атака КД").formatted(Formatting.GRAY), true);
			return true;
		}

		float raw = prog.getAttackDamage(def);
		raw = StrAgiAbilities.modifyOutgoingAttack(sp, def, prog, target, raw);
		HeroProgress tProg = null;
		HeroDef tDef = null;
		if (target instanceof ServerPlayerEntity tp) {
			tProg = hm.getProgress(tp.getUuid());
			tDef = hm.getHero(tp.getUuid());
		}
		float dmg = CombatMath.mitigateAgainst(target, raw, tProg, tDef);

		prog.startAttackCooldown(def);
		target.damage(sp.getServerWorld().getDamageSources().playerAttack(sp), dmg);

		StrAgiAbilities.onAttackLanded(sp, def, prog, target, dmg);

		ServerWorld sw = sp.getServerWorld();
		if (prog.hasTreeGrab()) {
			applyTreeGrabCleave(sp, prog, team, dmg * 0.85f, target);
			if (prog.consumeTreeGrabHit()) {
				hm.finishTreeGrab(sp);
			} else {
				sp.sendMessage(Text.literal("Tree Grab: " + prog.getTreeGrabHitsLeft() + " уд.")
						.formatted(Formatting.GREEN), true);
			}
		}

		if (def.ranged()) {
			playRangedFx(sw, sp, target);
		} else {
			sw.spawnParticles(ParticleTypes.CRIT,
					target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
					8, 0.2, 0.3, 0.2, 0.05);
			sw.playSound(null, target.getX(), target.getY(), target.getZ(),
					SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 0.9f, 1.0f);
		}
		hm.syncState(sp);
		return true;
	}

	/** Cone 135° / 6 blocks — Tree Grab splash (excludes primary target). */
	private static void applyTreeGrabCleave(ServerPlayerEntity sp, HeroProgress prog,
			DotaTeam team, float splashDmg, LivingEntity primary) {
		ServerWorld world = sp.getServerWorld();
		Vec3d origin = sp.getPos();
		Vec3d look = AbilityGeometry.lookFlat(sp);
		double range = 6.0;
		Box box = sp.getBoundingBox().expand(range);
		world.spawnParticles(ParticleTypes.SWEEP_ATTACK, sp.getX(), sp.getY() + 1, sp.getZ(), 8, 0.8, 0.3, 0.8, 0.02);
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box,
				ent -> isValidHeroTarget(ent, team) && ent != primary)) {
			if (!AbilityGeometry.inHorizontalCone(origin, look, e.getPos(), range, 135)) {
				continue;
			}
			HeroProgress tProg = null;
			HeroDef tDef = null;
			if (e instanceof ServerPlayerEntity tp) {
				tProg = HeroManager.get(sp.getServer()).getProgress(tp.getUuid());
				tDef = HeroManager.get(sp.getServer()).getHero(tp.getUuid());
			}
			float d = CombatMath.mitigateAgainst(e, splashDmg, tProg, tDef);
			e.damage(world.getDamageSources().playerAttack(sp), d);
		}
	}

	private static void playRangedFx(ServerWorld sw, ServerPlayerEntity sp, LivingEntity target) {
		Vec3d from = sp.getEyePos();
		Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0);
		Vec3d delta = to.subtract(from);
		int steps = Math.max(4, (int) (delta.length() * 2));
		for (int i = 1; i <= steps; i++) {
			double t = i / (double) steps;
			Vec3d p = from.add(delta.multiply(t));
			sw.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
		sw.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
				SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 0.8f, 1.1f);
		sw.spawnParticles(ParticleTypes.ENCHANTED_HIT,
				target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
				6, 0.2, 0.3, 0.2, 0.02);
	}

	public static boolean isValidHeroTarget(LivingEntity target, DotaTeam attackerTeam) {
		if (!target.isAlive() || attackerTeam == DotaTeam.NONE) {
			return false;
		}
		DotaTeam tt = TeamComponent.getTeam(target);
		if (tt != attackerTeam.opposite()) {
			return false;
		}
		return target instanceof ServerPlayerEntity
				|| target instanceof BotHeroEntity
				|| target instanceof CreepEntity
				|| target instanceof RangedCreepEntity
				|| target instanceof TowerEntity
				|| target instanceof BarrackEntity
				|| target instanceof AncientEntity;
	}
}
