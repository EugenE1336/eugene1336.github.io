package com.minedota.lobby;

import com.minedota.map.DotaMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.WallMountedBlock;
import net.minecraft.block.enums.WallMountLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Lobby room south of the map (outside playable arena).
 */
public final class LobbyMap {
	public static final int CENTER_X = 0;
	public static final int CENTER_Z = 140;
	public static final int HALF = 9;
	public static final int FLOOR_Y = DotaMap.FLOOR_Y;
	public static final int WALK_Y = DotaMap.WALK_Y;

	public static final BlockPos SPAWN = new BlockPos(CENTER_X, WALK_Y, CENTER_Z + 3);

	/** Clickable pads (floor block) and buttons on top. */
	public static final BlockPos PAD_RADIANT = new BlockPos(CENTER_X - 5, FLOOR_Y - 1, CENTER_Z - 1);
	public static final BlockPos PAD_DIRE = new BlockPos(CENTER_X + 5, FLOOR_Y - 1, CENTER_Z - 1);
	public static final BlockPos PAD_START = new BlockPos(CENTER_X, FLOOR_Y - 1, CENTER_Z - 5);
	public static final BlockPos PAD_HEROES = new BlockPos(CENTER_X, FLOOR_Y - 1, CENTER_Z + 5);
	public static final BlockPos PAD_ADD_BOT = new BlockPos(CENTER_X - 5, FLOOR_Y - 1, CENTER_Z - 5);

	public static final BlockPos BTN_RADIANT = new BlockPos(CENTER_X - 5, FLOOR_Y, CENTER_Z - 1);
	public static final BlockPos BTN_DIRE = new BlockPos(CENTER_X + 5, FLOOR_Y, CENTER_Z - 1);
	public static final BlockPos BTN_START = new BlockPos(CENTER_X, FLOOR_Y, CENTER_Z - 5);
	public static final BlockPos BTN_HEROES = new BlockPos(CENTER_X, FLOOR_Y, CENTER_Z + 5);
	public static final BlockPos BTN_ADD_BOT = new BlockPos(CENTER_X - 5, FLOOR_Y, CENTER_Z - 5);

	public static final BlockPos LABEL_RADIANT = new BlockPos(CENTER_X - 5, FLOOR_Y + 1, CENTER_Z - 1);
	public static final BlockPos LABEL_DIRE = new BlockPos(CENTER_X + 5, FLOOR_Y + 1, CENTER_Z - 1);
	public static final BlockPos LABEL_START = new BlockPos(CENTER_X, FLOOR_Y + 1, CENTER_Z - 5);
	public static final BlockPos LABEL_HEROES = new BlockPos(CENTER_X, FLOOR_Y + 1, CENTER_Z + 5);
	public static final BlockPos LABEL_ADD_BOT = new BlockPos(CENTER_X - 5, FLOOR_Y + 1, CENTER_Z - 5);
	/** History board east wall of lobby. */
	public static final BlockPos HISTORY_HEADER = new BlockPos(CENTER_X + HALF - 1, FLOOR_Y + 3, CENTER_Z);
	public static final BlockPos HISTORY_FIRST = new BlockPos(CENTER_X + HALF - 1, FLOOR_Y + 2, CENTER_Z);

	private LobbyMap() {
	}

	public static boolean isInLobby(double x, double z) {
		return Math.abs(x - CENTER_X) <= HALF + 0.5 && Math.abs(z - CENTER_Z) <= HALF + 0.5;
	}

	public static boolean isLobbyColumn(int x, int z) {
		return Math.abs(x - CENTER_X) <= HALF && Math.abs(z - CENTER_Z) <= HALF;
	}

	public static boolean isLobbyButton(BlockPos pos) {
		return pos.equals(BTN_RADIANT) || pos.equals(BTN_DIRE) || pos.equals(BTN_START)
				|| pos.equals(BTN_HEROES) || pos.equals(BTN_ADD_BOT)
				|| pos.equals(PAD_RADIANT) || pos.equals(PAD_DIRE) || pos.equals(PAD_START)
				|| pos.equals(PAD_HEROES) || pos.equals(PAD_ADD_BOT);
	}

	public static LobbyAction actionAt(BlockPos pos) {
		int x = pos.getX();
		int z = pos.getZ();
		if (x == BTN_RADIANT.getX() && z == BTN_RADIANT.getZ()) {
			return LobbyAction.RADIANT;
		}
		if (x == BTN_DIRE.getX() && z == BTN_DIRE.getZ()) {
			return LobbyAction.DIRE;
		}
		if (x == BTN_START.getX() && z == BTN_START.getZ()) {
			return LobbyAction.START;
		}
		if (x == BTN_HEROES.getX() && z == BTN_HEROES.getZ()) {
			return LobbyAction.HEROES;
		}
		if (x == BTN_ADD_BOT.getX() && z == BTN_ADD_BOT.getZ()) {
			return LobbyAction.ADD_BOT;
		}
		return LobbyAction.NONE;
	}

	public static BlockState getBlock(int x, int y, int z) {
		if (!isLobbyColumn(x, z)) {
			return null;
		}
		if (y < 0 || y > 127) {
			return Blocks.AIR.getDefaultState();
		}
		if (y == 0) {
			return Blocks.BEDROCK.getDefaultState();
		}

		int lx = x - CENTER_X;
		int lz = z - CENTER_Z;
		boolean wall = Math.abs(lx) == HALF || Math.abs(lz) == HALF;

		if (y < FLOOR_Y - 1) {
			return Blocks.STONE.getDefaultState();
		}
		if (y == FLOOR_Y - 1) {
			if (x == PAD_RADIANT.getX() && z == PAD_RADIANT.getZ()) {
				return Blocks.LIME_CONCRETE.getDefaultState();
			}
			if (x == PAD_DIRE.getX() && z == PAD_DIRE.getZ()) {
				return Blocks.RED_CONCRETE.getDefaultState();
			}
			if (x == PAD_START.getX() && z == PAD_START.getZ()) {
				return Blocks.GOLD_BLOCK.getDefaultState();
			}
			if (x == PAD_HEROES.getX() && z == PAD_HEROES.getZ()) {
				return Blocks.PURPLE_CONCRETE.getDefaultState();
			}
			if (x == PAD_ADD_BOT.getX() && z == PAD_ADD_BOT.getZ()) {
				return Blocks.CYAN_CONCRETE.getDefaultState();
			}
			if (wall) {
				return Blocks.POLISHED_ANDESITE.getDefaultState();
			}
			return Blocks.SMOOTH_QUARTZ.getDefaultState();
		}
		if (y >= WALK_Y && y <= WALK_Y + 3) {
			if (wall) {
				// doorway on +Z side
				if (lz == HALF && Math.abs(lx) <= 1 && y <= WALK_Y + 2) {
					return Blocks.AIR.getDefaultState();
				}
				if (y == WALK_Y + 3) {
					return Blocks.GLASS.getDefaultState();
				}
				return Blocks.WHITE_STAINED_GLASS.getDefaultState();
			}
		}
		if (y == WALK_Y + 4) {
			return Blocks.SMOOTH_QUARTZ.getDefaultState();
		}

		// Floor buttons
		if (y == FLOOR_Y) {
			if (x == BTN_RADIANT.getX() && z == BTN_RADIANT.getZ()) {
				return floorButton();
			}
			if (x == BTN_DIRE.getX() && z == BTN_DIRE.getZ()) {
				return floorButton();
			}
			if (x == BTN_START.getX() && z == BTN_START.getZ()) {
				return floorButton();
			}
			if (x == BTN_HEROES.getX() && z == BTN_HEROES.getZ()) {
				return floorButton();
			}
			if (x == BTN_ADD_BOT.getX() && z == BTN_ADD_BOT.getZ()) {
				return floorButton();
			}
		}
		return Blocks.AIR.getDefaultState();
	}

	private static BlockState floorButton() {
		return Blocks.STONE_BUTTON.getDefaultState()
				.with(WallMountedBlock.FACE, WallMountLocation.FLOOR)
				.with(WallMountedBlock.FACING, Direction.NORTH);
	}

	public enum LobbyAction {
		NONE, RADIANT, DIRE, START, HEROES, ADD_BOT
	}
}
