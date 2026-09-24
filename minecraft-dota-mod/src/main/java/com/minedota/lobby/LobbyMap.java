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
 * Control buttons live on the north wall (eye-level), not on the floor.
 */
public final class LobbyMap {
	public static final int CENTER_X = 0;
	public static final int CENTER_Z = 140;
	public static final int HALF = 9;
	public static final int FLOOR_Y = DotaMap.FLOOR_Y;
	public static final int WALK_Y = DotaMap.WALK_Y;
	/** Button height on wall (standing eye level). */
	public static final int BTN_Y = WALK_Y + 1;

	public static final BlockPos SPAWN = new BlockPos(CENTER_X, WALK_Y, CENTER_Z + 3);

	/** North wall column Z (outer). Buttons sit one block south (inside). */
	public static final int WALL_N_Z = CENTER_Z - HALF;
	public static final int BTN_N_Z = WALL_N_Z + 1;

	/**
	 * Wall panel blocks (colored concrete in the north wall) + stone buttons in front.
	 * L→R: Radiant | Heroes | Start | +Bot | Dire
	 */
	public static final BlockPos PAD_RADIANT = new BlockPos(CENTER_X - 4, BTN_Y, WALL_N_Z);
	public static final BlockPos PAD_HEROES = new BlockPos(CENTER_X - 2, BTN_Y, WALL_N_Z);
	public static final BlockPos PAD_START = new BlockPos(CENTER_X, BTN_Y, WALL_N_Z);
	public static final BlockPos PAD_ADD_BOT = new BlockPos(CENTER_X + 2, BTN_Y, WALL_N_Z);
	public static final BlockPos PAD_DIRE = new BlockPos(CENTER_X + 4, BTN_Y, WALL_N_Z);

	public static final BlockPos BTN_RADIANT = new BlockPos(CENTER_X - 4, BTN_Y, BTN_N_Z);
	public static final BlockPos BTN_HEROES = new BlockPos(CENTER_X - 2, BTN_Y, BTN_N_Z);
	public static final BlockPos BTN_START = new BlockPos(CENTER_X, BTN_Y, BTN_N_Z);
	public static final BlockPos BTN_ADD_BOT = new BlockPos(CENTER_X + 2, BTN_Y, BTN_N_Z);
	public static final BlockPos BTN_DIRE = new BlockPos(CENTER_X + 4, BTN_Y, BTN_N_Z);

	public static final BlockPos LABEL_RADIANT = new BlockPos(CENTER_X - 4, BTN_Y + 1, BTN_N_Z);
	public static final BlockPos LABEL_HEROES = new BlockPos(CENTER_X - 2, BTN_Y + 1, BTN_N_Z);
	public static final BlockPos LABEL_START = new BlockPos(CENTER_X, BTN_Y + 1, BTN_N_Z);
	public static final BlockPos LABEL_ADD_BOT = new BlockPos(CENTER_X + 2, BTN_Y + 1, BTN_N_Z);
	public static final BlockPos LABEL_DIRE = new BlockPos(CENTER_X + 4, BTN_Y + 1, BTN_N_Z);

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
		int y = pos.getY();
		int z = pos.getZ();
		// Wall buttons / pads: match by xz (unique on north wall); accept pad or button y
		if (z == BTN_N_Z || z == WALL_N_Z) {
			if (x == BTN_RADIANT.getX() && (y == BTN_Y || y == BTN_Y + 1)) {
				return LobbyAction.RADIANT;
			}
			if (x == BTN_DIRE.getX() && (y == BTN_Y || y == BTN_Y + 1)) {
				return LobbyAction.DIRE;
			}
			if (x == BTN_START.getX() && (y == BTN_Y || y == BTN_Y + 1)) {
				return LobbyAction.START;
			}
			if (x == BTN_HEROES.getX() && (y == BTN_Y || y == BTN_Y + 1)) {
				return LobbyAction.HEROES;
			}
			if (x == BTN_ADD_BOT.getX() && (y == BTN_Y || y == BTN_Y + 1)) {
				return LobbyAction.ADD_BOT;
			}
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
			if (wall) {
				return Blocks.POLISHED_ANDESITE.getDefaultState();
			}
			return Blocks.SMOOTH_QUARTZ.getDefaultState();
		}

		// North-wall control panel (colored concrete + buttons in front)
		if (z == WALL_N_Z && y == BTN_Y) {
			BlockState panel = wallPanelAt(x);
			if (panel != null) {
				return panel;
			}
		}
		if (z == BTN_N_Z && y == BTN_Y && isControlX(x)) {
			return wallButton(Direction.SOUTH);
		}

		if (y >= WALK_Y && y <= WALK_Y + 3) {
			if (wall) {
				// doorway on +Z side
				if (lz == HALF && Math.abs(lx) <= 1 && y <= WALK_Y + 2) {
					return Blocks.AIR.getDefaultState();
				}
				// Keep panel columns solid concrete instead of glass
				if (z == WALL_N_Z && y == BTN_Y && isControlX(x)) {
					BlockState panel = wallPanelAt(x);
					if (panel != null) {
						return panel;
					}
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
		return Blocks.AIR.getDefaultState();
	}

	private static boolean isControlX(int x) {
		return x == PAD_RADIANT.getX() || x == PAD_HEROES.getX() || x == PAD_START.getX()
				|| x == PAD_ADD_BOT.getX() || x == PAD_DIRE.getX();
	}

	private static BlockState wallPanelAt(int x) {
		if (x == PAD_RADIANT.getX()) {
			return Blocks.LIME_CONCRETE.getDefaultState();
		}
		if (x == PAD_DIRE.getX()) {
			return Blocks.RED_CONCRETE.getDefaultState();
		}
		if (x == PAD_START.getX()) {
			return Blocks.GOLD_BLOCK.getDefaultState();
		}
		if (x == PAD_HEROES.getX()) {
			return Blocks.PURPLE_CONCRETE.getDefaultState();
		}
		if (x == PAD_ADD_BOT.getX()) {
			return Blocks.CYAN_CONCRETE.getDefaultState();
		}
		return null;
	}

	public static BlockState wallButton(Direction facing) {
		return Blocks.STONE_BUTTON.getDefaultState()
				.with(WallMountedBlock.FACE, WallMountLocation.WALL)
				.with(WallMountedBlock.FACING, facing);
	}

	public enum LobbyAction {
		NONE, RADIANT, DIRE, START, HEROES, ADD_BOT
	}
}
