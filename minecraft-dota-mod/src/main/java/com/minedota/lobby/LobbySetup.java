package com.minedota.lobby;

import com.minedota.MineDota;
import com.minedota.match.MatchRecord;
import com.minedota.worldgen.DotaWorldState;
import net.minecraft.block.Blocks;
import net.minecraft.block.WallMountedBlock;
import net.minecraft.block.enums.WallMountLocation;
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
		ensureHeroPad(world);
		ensureAddBotPad(world);

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
		MineDota.LOGGER.info("Lobby labels ready");
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

	private static void ensureHeroPad(ServerWorld world) {
		world.setBlockState(LobbyMap.PAD_HEROES, Blocks.PURPLE_CONCRETE.getDefaultState());
		world.setBlockState(LobbyMap.BTN_HEROES, Blocks.STONE_BUTTON.getDefaultState()
				.with(WallMountedBlock.FACE, WallMountLocation.FLOOR)
				.with(WallMountedBlock.FACING, Direction.NORTH));
	}

	private static void ensureAddBotPad(ServerWorld world) {
		world.setBlockState(LobbyMap.PAD_ADD_BOT, Blocks.CYAN_CONCRETE.getDefaultState());
		world.setBlockState(LobbyMap.BTN_ADD_BOT, Blocks.STONE_BUTTON.getDefaultState()
				.with(WallMountedBlock.FACE, WallMountLocation.FLOOR)
				.with(WallMountedBlock.FACING, Direction.NORTH));
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
