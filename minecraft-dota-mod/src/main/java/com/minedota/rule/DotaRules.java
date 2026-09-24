package com.minedota.rule;

import com.minedota.item.ModItems;
import com.minedota.lobby.LobbyMap;
import com.minedota.map.DotaMap;
import com.minedota.match.MatchManager;
import com.minedota.worldgen.ModWorldgen;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class DotaRules {
	private DotaRules() {
	}

	public static void register() {
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (!ModWorldgen.isDotaWorld(world)) {
				return true;
			}
			if (player.isCreative()) {
				return true;
			}
			MinecraftServer server = world.getServer();
			if (server == null) {
				return false;
			}
			MatchManager match = MatchManager.get(server);
			if (match.isInLobbyArea() || LobbyMap.isInLobby(player.getX(), player.getZ())) {
				if (player instanceof ServerPlayerEntity serverPlayer) {
					serverPlayer.sendMessageToClient(
							Text.literal("В лобби ломать нельзя.").formatted(Formatting.RED), true);
				}
				return false;
			}
			ItemStack stack = player.getMainHandStack();
			if (stack.isOf(ModItems.TANGO) && DotaMap.isTreeBlock(state)) {
				return true;
			}
			if (player instanceof ServerPlayerEntity serverPlayer) {
				serverPlayer.sendMessageToClient(
						Text.literal("Копать нельзя. Деревья — только Tango.").formatted(Formatting.RED), true);
			}
			return false;
		});

		ServerTickEvents.END_WORLD_TICK.register(world -> {
			if (!ModWorldgen.isDotaWorld(world)) {
				return;
			}
			MatchManager match = MatchManager.get(world.getServer());
			for (ServerPlayerEntity player : world.getPlayers()) {
				enforceBorder(player, match);
			}
		});
	}

	private static void enforceBorder(ServerPlayerEntity player, MatchManager match) {
		double x = player.getX();
		double z = player.getZ();

		if (match.isInLobbyArea()) {
			if (LobbyMap.isInLobby(x, z)) {
				return;
			}
			player.networkHandler.requestTeleport(
					LobbyMap.SPAWN.getX() + 0.5, LobbyMap.WALK_Y, LobbyMap.SPAWN.getZ() + 0.5, 180f, player.getPitch());
			player.sendMessageToClient(Text.literal("Оставайся в лобби до Start.").formatted(Formatting.YELLOW), true);
			return;
		}

		if (match.isInGame()) {
			if (DotaMap.isInsidePlayable(x, z)) {
				return;
			}
			double nx = Math.max(-(DotaMap.HALF - 2), Math.min(DotaMap.HALF - 2, x));
			double nz = Math.max(-(DotaMap.HALF - 2), Math.min(DotaMap.HALF - 2, z));
			player.networkHandler.requestTeleport(nx, Math.max(player.getY(), DotaMap.WALK_Y), nz, player.getYaw(), player.getPitch());
			player.sendMessageToClient(Text.literal("Граница карты!").formatted(Formatting.YELLOW), true);
		}
	}
}
