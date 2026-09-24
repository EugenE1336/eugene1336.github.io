package com.minedota.team;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class TeamComponent {
	public static final String NBT_KEY = "MineDotaTeam";

	private TeamComponent() {
	}

	public static DotaTeam getTeam(Entity entity) {
		if (entity instanceof TeamHolder holder) {
			return holder.getDotaTeam();
		}
		if (entity instanceof PlayerEntity player && player.getScoreboardTeam() != null) {
			AbstractTeam scoreTeam = player.getScoreboardTeam();
			return DotaTeam.fromId(scoreTeam.getName());
		}
		return DotaTeam.NONE;
	}

	public static void setPlayerTeam(ServerPlayerEntity player, DotaTeam team) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		Scoreboard scoreboard = server.getScoreboard();
		ensureScoreboardTeams(scoreboard);

		Team previous = scoreboard.getPlayerTeam(player.getEntityName());
		if (previous != null) {
			scoreboard.removePlayerFromTeam(player.getEntityName(), previous);
		}
		if (team != DotaTeam.NONE) {
			Team target = scoreboard.getTeam(team.getId());
			if (target != null) {
				scoreboard.addPlayerToTeam(player.getEntityName(), target);
			}
		}
	}

	public static void ensureScoreboardTeams(Scoreboard scoreboard) {
		for (DotaTeam dotaTeam : new DotaTeam[]{DotaTeam.RADIANT, DotaTeam.DIRE}) {
			Team team = scoreboard.getTeam(dotaTeam.getId());
			if (team == null) {
				team = scoreboard.addTeam(dotaTeam.getId());
				team.setDisplayName(dotaTeam.getDisplayName());
				team.setColor(dotaTeam.getColor());
				team.setFriendlyFireAllowed(false);
				team.setShowFriendlyInvisibles(true);
			}
		}
	}

	public interface TeamHolder {
		DotaTeam getDotaTeam();

		void setDotaTeam(DotaTeam team);
	}
}
