package com.minedota.hero;

import com.minedota.entity.BarrackEntity;
import com.minedota.entity.CreepEntity;
import com.minedota.entity.RangedCreepEntity;
import com.minedota.entity.TowerEntity;
import com.minedota.match.MatchManager;
import com.minedota.network.ModNetworking;
import com.minedota.team.DotaTeam;
import com.minedota.team.TeamComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * XP / gold distribution on kills.
 */
public final class KillRewards {
	private KillRewards() {
	}

	public static void onEntityDeath(LivingEntity dead, DamageSource source) {
		if (dead.getWorld().isClient || dead.getWorld().getServer() == null) {
			return;
		}
		MinecraftServer server = dead.getWorld().getServer();
		MatchManager match = MatchManager.get(server);
		if (!match.isInGame()) {
			return;
		}

		ServerPlayerEntity killer = findKillerPlayer(source);
		if (dead instanceof CreepEntity || dead instanceof RangedCreepEntity) {
			rewardCreep(server, dead, killer);
		} else if (dead instanceof TowerEntity tower) {
			rewardTower(server, tower);
		} else if (dead instanceof BarrackEntity) {
			// Gold/message handled in MatchManager.onBarrackDestroyed
		} else if (dead instanceof ServerPlayerEntity victim) {
			rewardHeroKill(server, victim, killer);
		}
	}

	private static ServerPlayerEntity findKillerPlayer(DamageSource source) {
		if (source.getAttacker() instanceof ServerPlayerEntity p) {
			return p;
		}
		if (source.getSource() instanceof ServerPlayerEntity p) {
			return p;
		}
		return null;
	}

	private static void rewardCreep(MinecraftServer server, LivingEntity creep, ServerPlayerEntity killer) {
		DotaTeam creepTeam = TeamComponent.getTeam(creep);
		DotaTeam enemyOfCreep = creepTeam.opposite();
		if (enemyOfCreep == DotaTeam.NONE) {
			return;
		}
		List<ServerPlayerEntity> nearby = alliesInRadius(server, creep.getPos(), enemyOfCreep, ProgressionConstants.REWARD_RADIUS);
		if (nearby.isEmpty() && killer != null && TeamComponent.getTeam(killer) == enemyOfCreep) {
			nearby = List.of(killer);
		}
		HeroManager hm = HeroManager.get(server);
		for (ServerPlayerEntity p : nearby) {
			HeroProgress prog = hm.getProgress(p.getUuid());
			if (prog == null) {
				continue;
			}
			int gained = prog.addXp(ProgressionConstants.CREEP_XP);
			prog.addGold(ProgressionConstants.CREEP_GOLD);
			if (gained > 0) {
				playLevelUp(p, prog.getLevel());
			}
			hm.applyHeroStats(p);
			hm.syncState(p);
		}
	}

	private static void rewardTower(MinecraftServer server, TowerEntity tower) {
		DotaTeam winners = tower.getDotaTeam().opposite();
		if (winners == DotaTeam.NONE) {
			return;
		}
		HeroManager hm = HeroManager.get(server);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (TeamComponent.getTeam(p) != winners) {
				continue;
			}
			HeroProgress prog = hm.getProgress(p.getUuid());
			if (prog == null) {
				continue;
			}
			prog.addGold(ProgressionConstants.TOWER_GOLD);
			hm.syncState(p);
			p.sendMessage(Text.literal("+" + ProgressionConstants.TOWER_GOLD + "g башня")
					.formatted(Formatting.GOLD), true);
		}
	}

	private static void rewardHeroKill(MinecraftServer server, ServerPlayerEntity victim, ServerPlayerEntity killer) {
		HeroManager hm = HeroManager.get(server);
		HeroProgress victimProg = hm.getProgress(victim.getUuid());
		int victimLevel = victimProg != null ? victimProg.getLevel() : 1;
		DotaTeam victimTeam = TeamComponent.getTeam(victim);
		DotaTeam winnerTeam = victimTeam.opposite();
		if (winnerTeam == DotaTeam.NONE) {
			return;
		}

		int xpPool = ProgressionConstants.HERO_XP_BASE
				+ ProgressionConstants.HERO_XP_PER_LEVEL * victimLevel;
		int killGold = ProgressionConstants.HERO_GOLD_BASE
				+ ProgressionConstants.HERO_GOLD_PER_LEVEL * victimLevel;
		int assistPool = ProgressionConstants.ASSIST_GOLD_PER_LEVEL * victimLevel;

		Vec3d pos = victim.getPos();
		List<ServerPlayerEntity> nearby = alliesInRadius(server, pos, winnerTeam, ProgressionConstants.REWARD_RADIUS);

		// Ensure killer gets credit even if somehow outside radius
		if (killer != null && TeamComponent.getTeam(killer) == winnerTeam) {
			boolean inList = nearby.stream().anyMatch(p -> p.getUuid().equals(killer.getUuid()));
			if (!inList) {
				nearby = new ArrayList<>(nearby);
				nearby.add(killer);
			}
		}

		if (nearby.isEmpty() && killer != null && TeamComponent.getTeam(killer) == winnerTeam) {
			nearby = List.of(killer);
		}

		int n = nearby.size();
		if (n > 0) {
			int xpEach = xpPool / n;
			int xpRem = xpPool % n;
			for (int i = 0; i < n; i++) {
				ServerPlayerEntity p = nearby.get(i);
				HeroProgress prog = hm.getProgress(p.getUuid());
				if (prog == null) {
					continue;
				}
				int xp = xpEach + (i < xpRem ? 1 : 0);
				int gained = prog.addXp(xp);
				if (gained > 0) {
					playLevelUp(p, prog.getLevel());
				}
				hm.applyHeroStats(p);
			}
		}

		if (killer != null && TeamComponent.getTeam(killer) == winnerTeam) {
			HeroProgress kp = hm.getProgress(killer.getUuid());
			if (kp != null) {
				kp.addGold(killGold);
				kp.addKill();
				killer.sendMessage(Text.literal("+" + killGold + "g килл (ур." + victimLevel + ")")
						.formatted(Formatting.GOLD), true);
			}
			MatchManager.get(server).addTeamKill(winnerTeam);
		}

		List<ServerPlayerEntity> assists = new ArrayList<>();
		for (ServerPlayerEntity p : nearby) {
			if (killer != null && p.getUuid().equals(killer.getUuid())) {
				continue;
			}
			assists.add(p);
		}
		if (!assists.isEmpty() && assistPool > 0) {
			int each = assistPool / assists.size();
			int rem = assistPool % assists.size();
			for (int i = 0; i < assists.size(); i++) {
				ServerPlayerEntity p = assists.get(i);
				HeroProgress prog = hm.getProgress(p.getUuid());
				if (prog == null) {
					continue;
				}
				int g = each + (i < rem ? 1 : 0);
				prog.addGold(g);
				prog.addAssist();
				p.sendMessage(Text.literal("+" + g + "g ассист").formatted(Formatting.YELLOW), true);
			}
		}

		for (ServerPlayerEntity p : nearby) {
			hm.syncState(p);
		}
		if (killer != null) {
			hm.syncState(killer);
		}

		// Respawn timer for victim
		if (victimProg != null) {
			victimProg.addDeath();
			victimProg.beginRespawn();
			hm.syncState(victim);
		}
	}

	private static List<ServerPlayerEntity> alliesInRadius(MinecraftServer server, Vec3d pos, DotaTeam team, double radius) {
		List<ServerPlayerEntity> out = new ArrayList<>();
		double r2 = radius * radius;
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (TeamComponent.getTeam(p) != team) {
				continue;
			}
			HeroProgress prog = HeroManager.get(server).getProgress(p.getUuid());
			if (prog != null && prog.isAwaitingRespawn()) {
				continue;
			}
			if (p.squaredDistanceTo(pos) <= r2) {
				out.add(p);
			}
		}
		return out;
	}

	private static void playLevelUp(ServerPlayerEntity player, int newLevel) {
		player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				net.minecraft.sound.SoundEvents.ENTITY_PLAYER_LEVELUP,
				net.minecraft.sound.SoundCategory.PLAYERS, 0.85f, 1.15f);
		player.sendMessage(Text.literal("Уровень " + newLevel + "!").formatted(Formatting.GREEN), true);
	}
}
