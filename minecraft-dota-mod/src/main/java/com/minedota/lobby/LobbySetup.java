package com.minedota.lobby;

import com.minedota.MineDota;
import com.minedota.match.MatchRecord;
import com.minedota.worldgen.DotaWorldState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.List;

public final class LobbySetup {
	private static final String HISTORY_TAG = "minedota_match_history";

	private LobbySetup() {
	}

	public static void ensureLabels(ServerWorld world) {
		placeWallControls(world);
		clearOldFloorControls(world);

		spawnLabel(world, LobbyMap.LABEL_RADIANT, Text.literal("[ Radiant ]").formatted(Formatting.GREEN, Formatting.BOLD));
		spawnLabel(world, LobbyMap.LABEL_DIRE, Text.literal("[ Dire ]").formatted(Formatting.RED, Formatting.BOLD));
		spawnLabel(world, LobbyMap.LABEL_START, Text.literal("[ Start ]").formatted(Formatting.GOLD, Formatting.BOLD));
		spawnLabel(world, LobbyMap.LABEL_HEROES, Text.literal("[ Heroes ]").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
		spawnLabel(world, LobbyMap.LABEL_ADD_BOT, Text.literal("[ + Bot ]").formatted(Formatting.AQUA, Formatting.BOLD));

		ArmorStandEntity tip = findLabel(world, LobbyMap.SPAWN.up(2));
		if (tip == null) {
			spawnLabel(world, LobbyMap.SPAWN.up(2),
					Text.literal("Сторона → Герой / +Bot → Start").formatted(Formatting.YELLOW));
		}
		refreshMatchHistory(world);
		MineDota.LOGGER.info("Lobby wall controls ready");
	}

	/** North-wall panel: colored blocks + stone buttons facing into the room. */
	private static void placeWallControls(ServerWorld world) {
		placeOne(world, LobbyMap.PAD_RADIANT, Blocks.LIME_CONCRETE.getDefaultState(), LobbyMap.BTN_RADIANT);
		placeOne(world, LobbyMap.PAD_HEROES, Blocks.PURPLE_CONCRETE.getDefaultState(), LobbyMap.BTN_HEROES);
		placeOne(world, LobbyMap.PAD_START, Blocks.GOLD_BLOCK.getDefaultState(), LobbyMap.BTN_START);
		placeOne(world, LobbyMap.PAD_ADD_BOT, Blocks.CYAN_CONCRETE.getDefaultState(), LobbyMap.BTN_ADD_BOT);
		placeOne(world, LobbyMap.PAD_DIRE, Blocks.RED_CONCRETE.getDefaultState(), LobbyMap.BTN_DIRE);
	}

	private static void placeOne(ServerWorld world, BlockPos pad, net.minecraft.block.BlockState panel, BlockPos btn) {
		world.setBlockState(pad, panel);
		world.setBlockState(btn, LobbyMap.wallButton(Direction.SOUTH));
	}

	/** Remove leftover floor pads/buttons from older lobby layout. */
	private static void clearOldFloorControls(ServerWorld world) {
		int fy = LobbyMap.FLOOR_Y;
		int[][] old = {
				{LobbyMap.CENTER_X - 5, LobbyMap.CENTER_Z - 1},
				{LobbyMap.CENTER_X + 5, LobbyMap.CENTER_Z - 1},
				{LobbyMap.CENTER_X, LobbyMap.CENTER_Z - 5},
				{LobbyMap.CENTER_X, LobbyMap.CENTER_Z + 5},
				{LobbyMap.CENTER_X - 5, LobbyMap.CENTER_Z - 5},
		};
		for (int[] xz : old) {
			BlockPos floor = new BlockPos(xz[0], fy - 1, xz[1]);
			BlockPos btn = new BlockPos(xz[0], fy, xz[1]);
			BlockPos label = new BlockPos(xz[0], fy + 1, xz[1]);
			if (!LobbyMap.isLobbyButton(floor) && !LobbyMap.isLobbyButton(btn)) {
				world.setBlockState(floor, Blocks.SMOOTH_QUARTZ.getDefaultState());
				world.setBlockState(btn, Blocks.AIR.getDefaultState());
			}
			// Drop orphan floor labels
			for (ArmorStandEntity stand : world.getEntitiesByClass(ArmorStandEntity.class, new Box(label).expand(0.8),
					e -> e.getCommandTags().contains("minedota_lobby_label")
							&& !e.getCommandTags().contains(HISTORY_TAG))) {
				stand.discard();
			}
		}
	}

	public static void refreshMatchHistory(ServerWorld world) {
		clearHistoryLabels(world);
		spawnHistoryLabel(world, LobbyMap.HISTORY_HEADER,
				Text.literal("История матчей").formatted(Formatting.GOLD, Formatting.BOLD));
		List<MatchRecord> history = DotaWorldState.get(world).getMatchHistory();
		if (history.isEmpty()) {
			spawnHistoryLabel(world, LobbyMap.HISTORY_FIRST.down(0),
					Text.literal("(пока пусто)").formatted(Formatting.DARK_GRAY));
			return;
		}
		int i = 0;
		for (MatchRecord record : history) {
			BlockPos pos = LobbyMap.HISTORY_FIRST.down(i);
			spawnHistoryLabel(world, pos, Text.literal(record.toLobbyLine()).formatted(Formatting.GRAY));
			i++;
			if (i >= 8) {
				break;
			}
		}
	}

	private static void clearHistoryLabels(ServerWorld world) {
		Box box = new Box(LobbyMap.HISTORY_HEADER).expand(2, 10, 2);
		world.getEntitiesByClass(ArmorStandEntity.class, box,
				e -> e.getCommandTags().contains(HISTORY_TAG)).forEach(ArmorStandEntity::discard);
	}

	private static void spawnHistoryLabel(ServerWorld world, BlockPos pos, Text name) {
		ArmorStandEntity stand = new ArmorStandEntity(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		stand.setCustomName(name);
		stand.setCustomNameVisible(true);
		stand.setInvisible(true);
		stand.setNoGravity(true);
		stand.setInvulnerable(true);
		stand.setHideBasePlate(true);
		stand.addCommandTag(HISTORY_TAG);
		stand.addCommandTag("minedota_lobby_label");
		world.spawnEntity(stand);
	}

	private static void spawnLabel(ServerWorld world, BlockPos pos, Text name) {
		if (findLabel(world, pos) != null) {
			return;
		}
		ArmorStandEntity stand = new ArmorStandEntity(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		stand.setCustomName(name);
		stand.setCustomNameVisible(true);
		stand.setInvisible(true);
		stand.setNoGravity(true);
		stand.setInvulnerable(true);
		stand.setHideBasePlate(true);
		stand.addCommandTag("minedota_lobby_label");
		world.spawnEntity(stand);
	}

	private static ArmorStandEntity findLabel(ServerWorld world, BlockPos pos) {
		Box box = new Box(pos).expand(0.6);
		for (ArmorStandEntity stand : world.getEntitiesByClass(ArmorStandEntity.class, box,
				e -> e.getCommandTags().contains("minedota_lobby_label")
						&& !e.getCommandTags().contains(HISTORY_TAG))) {
			return stand;
		}
		return null;
	}
}
