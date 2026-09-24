package com.minedota.hero;

import com.minedota.entity.BarrackEntity;
import com.minedota.entity.BotHeroEntity;
import com.minedota.entity.CreepEntity;
import com.minedota.entity.RangedCreepEntity;
import com.minedota.entity.TowerEntity;
import com.minedota.match.MatchManager;
import com.minedota.team.DotaTeam;
import com.minedota.team.TeamComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * XP / gold distribution on kills.
 * Bots level from creep/hero XP the same way as players (no gold economy).
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

		ServerPlayerEntity killerPlayer = findKillerPlayer(source);
		BotHeroEntity killerBot = findKillerBot(source);
		if (dead instanceof CreepEntity || dead instanceof RangedCreepEntity) {
			rewardCreep(server, dead, killerPlayer, killerBot);
		} else if (dead instanceof TowerEntity tower) {
			rewardTower(server, tower);
		} else if (dead instanceof BarrackEntity) {
			// Gold/message handled in MatchManager.onBarrackDestroyed
		} else if (dead instanceof BotHeroEntity bot) {
			rewardHeroKill(server, bot.getPos(), TeamComponent.getTeam(bot),
					Math.max(1, bot.getHeroLevel()), killerPlayer, killerBot, null);
			MatchManager.get(server).scheduleBotRespawn(bot);
		} else if (dead instanceof ServerPlayerEntity victim) {
			HeroManager hm = HeroManager.get(server);
			HeroProgress victimProg = hm.getProgress(victim.getUuid());
			int victimLevel = victimProg != null ? victimProg.getLevel() : 1;
			rewardHeroKill(server, victim.getPos(), TeamComponent.getTeam(victim),
					victimLevel, killerPlayer, killerBot, victim);
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

	private static BotHeroEntity findKillerBot(DamageSource source) {
		if (source.getAttacker() instanceof BotHeroEntity b) {
			return b;
		}
		if (source.getSource() instanceof BotHeroEntity b) {
			return b;
		}
		return null;
	}

	private static void rewardCreep(MinecraftServer server, LivingEntity creep,
			ServerPlayerEntity killerPlayer, BotHeroEntity killerBot) {
		DotaTeam creepTeam = TeamComponent.getTeam(creep);
		DotaTeam enemyOfCreep = creepTeam.opposite();
		if (enemyOfCreep == DotaTeam.NONE) {
			return;
		}
		List<ServerPlayerEntity> nearbyPlayers = alliesInRadius(server, creep.getPos(), enemyOfCreep,
				ProgressionConstants.REWARD_RADIUS);
		if (nearbyPlayers.isEmpty() && killerPlayer != null
				&& TeamComponent.getTeam(killerPlayer) == enemyOfCreep) {
			nearbyPlayers = List.of(killerPlayer);
		}
		List<BotHeroEntity> nearbyBots = allyBotsInRadius(server, creep.getPos(), enemyOfCreep,
				ProgressionConstants.REWARD_RADIUS);
		if (nearbyBots.isEmpty() && killerBot != null
				&& TeamComponent.getTeam(killerBot) == enemyOfCreep) {
			nearbyBots = List.of(killerBot);
		}

		HeroManager hm = HeroManager.get(server);
		for (ServerPlayerEntity p : nearbyPlayers) {
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
		for (BotHeroEntity bot : nearbyBots) {
			bot.addXp(ProgressionConstants.CREEP_XP);
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

	private static void rewardHeroKill(MinecraftServer server, Vec3d pos, DotaTeam victimTeam,
			int victimLevel, ServerPlayerEntity killerPlayer, BotHeroEntity killerBot,
			ServerPlayerEntity victimPlayer) {
		HeroManager hm = HeroManager.get(server);
		DotaTeam winnerTeam = victimTeam == null ? DotaTeam.NONE : victimTeam.opposite();
		if (winnerTeam == DotaTeam.NONE) {
			return;
		}
		victimLevel = Math.max(1, victimLevel);

		int xpPool = ProgressionConstants.HERO_XP_BASE
				+ ProgressionConstants.HERO_XP_PER_LEVEL * victimLevel;
		int killGold = ProgressionConstants.HERO_GOLD_BASE
				+ ProgressionConstants.HERO_GOLD_PER_LEVEL * victimLevel;
		int assistPool = ProgressionConstants.ASSIST_GOLD_PER_LEVEL * victimLevel;

		List<ServerPlayerEntity> nearbyPlayers = alliesInRadius(server, pos, winnerTeam,
				ProgressionConstants.REWARD_RADIUS);
		if (killerPlayer != null && TeamComponent.getTeam(killerPlayer) == winnerTeam) {
			boolean inList = nearbyPlayers.stream().anyMatch(p -> p.getUuid().equals(killerPlayer.getUuid()));
			if (!inList) {
				nearbyPlayers = new ArrayList<>(nearbyPlayers);
				nearbyPlayers.add(killerPlayer);
			}
		}

		List<BotHeroEntity> nearbyBots = allyBotsInRadius(server, pos, winnerTeam,
				ProgressionConstants.REWARD_RADIUS);
		if (killerBot != null && TeamComponent.getTeam(killerBot) == winnerTeam) {
			boolean inList = nearbyBots.stream().anyMatch(b -> b.getUuid().equals(killerBot.getUuid()));
			if (!inList) {
				nearbyBots = new ArrayList<>(nearbyBots);
				nearbyBots.add(killerBot);
			}
		}

		int shareCount = nearbyPlayers.size() + nearbyBots.size();
		if (shareCount > 0) {
			int xpEach = xpPool / shareCount;
			int xpRem = xpPool % shareCount;
			int idx = 0;
			for (ServerPlayerEntity p : nearbyPlayers) {
				HeroProgress prog = hm.getProgress(p.getUuid());
				if (prog == null) {
					idx++;
					continue;
				}
				int xp = xpEach + (idx < xpRem ? 1 : 0);
				int gained = prog.addXp(xp);
				if (gained > 0) {
					playLevelUp(p, prog.getLevel());
				}
				hm.applyHeroStats(p);
				idx++;
			}
			for (BotHeroEntity bot : nearbyBots) {
				int xp = xpEach + (idx < xpRem ? 1 : 0);
				bot.addXp(xp);
				idx++;
			}
		}

		if (killerPlayer != null && TeamComponent.getTeam(killerPlayer) == winnerTeam) {
			HeroProgress kp = hm.getProgress(killerPlayer.getUuid());
			if (kp != null) {
				kp.addGold(killGold);
				kp.addKill();
				killerPlayer.sendMessage(Text.literal("+" + killGold + "g килл (ур." + victimLevel + ")")
						.formatted(Formatting.GOLD), true);
			}
			MatchManager.get(server).addTeamKill(winnerTeam);
		} else if (killerBot != null && TeamComponent.getTeam(killerBot) == winnerTeam) {
			killerBot.addKill();
			MatchManager.get(server).addTeamKill(winnerTeam);
		}

		List<ServerPlayerEntity> assists = new ArrayList<>();
		for (ServerPlayerEntity p : nearbyPlayers) {
			if (killerPlayer != null && p.getUuid().equals(killerPlayer.getUuid())) {
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
		for (BotHeroEntity bot : nearbyBots) {
			if (killerBot != null && bot.getUuid().equals(killerBot.getUuid())) {
				continue;
			}
			bot.addAssist();
		}

		for (ServerPlayerEntity p : nearbyPlayers) {
			hm.syncState(p);
		}
		if (killerPlayer != null) {
			hm.syncState(killerPlayer);
		}

		if (victimPlayer != null) {
			HeroProgress victimProg = hm.getProgress(victimPlayer.getUuid());
			if (victimProg != null) {
				victimProg.addDeath();
				victimProg.beginRespawn();
				hm.syncState(victimPlayer);
			}
		}
	}

	private static List<ServerPlayerEntity> alliesInRadius(MinecraftServer server, Vec3d pos,
			DotaTeam team, double radius) {
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

	private static List<BotHeroEntity> allyBotsInRadius(MinecraftServer server, Vec3d pos,
			DotaTeam team, double radius) {
		List<BotHeroEntity> out = new ArrayList<>();
		ServerWorld world = server.getOverworld();
		if (world == null) {
			return out;
		}
		double r = radius;
		Box box = new Box(pos.x - r, pos.y - r, pos.z - r, pos.x + r, pos.y + r, pos.z + r);
		double r2 = r * r;
		for (BotHeroEntity bot : world.getEntitiesByClass(BotHeroEntity.class, box, b -> b.isAlive())) {
			if (TeamComponent.getTeam(bot) != team) {
				continue;
			}
			if (bot.squaredDistanceTo(pos) <= r2) {
				out.add(bot);
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
