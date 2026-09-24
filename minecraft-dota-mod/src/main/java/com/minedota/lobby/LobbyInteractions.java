package com.minedota.lobby;

import com.minedota.hero.HeroManager;
import com.minedota.match.MatchManager;
import com.minedota.network.ModNetworking;
import com.minedota.team.DotaTeam;
import com.minedota.worldgen.ModWorldgen;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

public final class LobbyInteractions {
	private LobbyInteractions() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) {
				return ActionResult.PASS;
			}
			if (!ModWorldgen.isDotaWorld(world)) {
				return ActionResult.PASS;
			}

			BlockPos pos = hitResult.getBlockPos();
			LobbyMap.LobbyAction action = LobbyMap.actionAt(pos);
			if (action == LobbyMap.LobbyAction.NONE) {
				action = LobbyMap.actionAt(pos.up());
			}
			if (action == LobbyMap.LobbyAction.NONE) {
				return ActionResult.PASS;
			}

			MatchManager match = MatchManager.get(serverPlayer.getServer());
			Text result = switch (action) {
				case RADIANT -> match.selectTeamInLobby(serverPlayer, DotaTeam.RADIANT);
				case DIRE -> match.selectTeamInLobby(serverPlayer, DotaTeam.DIRE);
				case START -> match.startFromLobby(serverPlayer);
				case ADD_BOT -> match.addBotAgainst(serverPlayer);
				case HEROES -> {
					if (!match.canChangeHero()) {
						yield Text.literal("Сейчас нельзя выбрать героя.");
					}
					HeroManager.get(serverPlayer.getServer()).syncPicksTo(serverPlayer);
					ModNetworking.sendOpenHeroSelect(serverPlayer);
					yield Text.literal("Открыт выбор героя.");
				}
				default -> Text.empty();
			};
			if (!result.getString().isEmpty()) {
				serverPlayer.sendMessage(result, false);
			}
			return ActionResult.SUCCESS;
		});
	}
}
