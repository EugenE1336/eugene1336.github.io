package com.minedota.map;

import com.minedota.MineDota;
import com.minedota.entity.BarrackEntity;
import com.minedota.lobby.LobbyMap;
import com.minedota.team.DotaTeam;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed DotA-style map centered at (0, FLOOR_Y, 0). Lobby is south of the arena.
 */
public final class DotaMap {
	/** Playable half-extent (±). Bumped to fit corner fountains + side approaches. */
	public static final int HALF = 104;
	public static final int FLOOR_Y = 63;
	public static final int WALK_Y = 64;
	public static final int WALL_TOP = FLOOR_Y + 8;

	/** Radiant / Dire raised platform centers (between fountain corner and T3s). */
	public static final int R_BASE_X = -80;
	public static final int R_BASE_Z = 80;
	public static final int D_BASE_X = 80;
	public static final int D_BASE_Z = -80;
	public static final int BASE_HALF = 14;

	public static final int R_FOUNTAIN_X = -92;
	public static final int R_FOUNTAIN_Z = 92;
	public static final int D_FOUNTAIN_X = 92;
	public static final int D_FOUNTAIN_Z = -92;

	public static final int R_ANCIENT_X = -70;
	public static final int R_ANCIENT_Z = 70;
	public static final int D_ANCIENT_X = 70;
	public static final int D_ANCIENT_Z = -70;

	public enum Lane {
		TOP,
		MID,
		BOT;

		public String getRussianName() {
			return switch (this) {
				case TOP -> "Верхняя";
				case MID -> "Центральная";
				case BOT -> "Нижняя";
			};
		}

		/** Short English tag for nameplates: TOP / MID / BOT. */
		public String getTag() {
			return name();
		}
	}

	public record CreepSpawn(BlockPos pos, List<BlockPos> path, Lane lane, DotaTeam team) {
	}

	public record ArenaLayout(
			BlockPos center,
			BlockPos radiantSpawn,
			BlockPos direSpawn,
			BlockPos radiantAncient,
			BlockPos direAncient,
			List<CreepSpawn> creepSpawns
	) {
	}

	private DotaMap() {
	}

	public static ArenaLayout layout() {
		return new ArenaLayout(
				new BlockPos(0, WALK_Y, 0),
				radiantSpawn(),
				direSpawn(),
				radiantAncient(),
				direAncient(),
				creepSpawns());
	}

	public static boolean isInsidePlayable(double x, double z) {
		return Math.abs(x) < HALF - 0.5 && Math.abs(z) < HALF - 0.5;
	}

	/** Arena wall/interior or lobby — columns that should exist in the world. */
	public static boolean isGeneratedColumn(int x, int z) {
		if (LobbyMap.isLobbyColumn(x, z)) {
			return true;
		}
		return Math.abs(x) <= HALF && Math.abs(z) <= HALF;
	}

	/** Inclusive world AABB covering arena + lobby (for border / scrub). */
	public static int genMinX() {
		return -HALF;
	}

	public static int genMaxX() {
		return HALF;
	}

	public static int genMinZ() {
		return -HALF;
	}

	public static int genMaxZ() {
		return Math.max(HALF, LobbyMap.CENTER_Z + LobbyMap.HALF);
	}

	/**
	 * Strip previously generated bedrock/barrier outside the single map square.
	 * Only touches already-loaded chunks (no force-load — that would recreate lag).
	 */
	public static void scrubOutsideToVoid(ServerWorld world) {
		int padChunks = 8;
		int minCx = (genMinX() >> 4) - padChunks;
		int maxCx = (genMaxX() >> 4) + padChunks;
		int minCz = (genMinZ() >> 4) - padChunks;
		int maxCz = (genMaxZ() >> 4) + padChunks;
		BlockPos.Mutable mut = new BlockPos.Mutable();
		int cleared = 0;
		for (int cx = minCx; cx <= maxCx; cx++) {
			for (int cz = minCz; cz <= maxCz; cz++) {
				if (!world.isChunkLoaded(cx, cz)) {
					continue;
				}
				int x0 = cx << 4;
				int z0 = cz << 4;
				for (int lx = 0; lx < 16; lx++) {
					for (int lz = 0; lz < 16; lz++) {
						int x = x0 + lx;
						int z = z0 + lz;
						if (isGeneratedColumn(x, z)) {
							continue;
						}
						for (int y = 0; y < 128; y++) {
							mut.set(x, y, z);
							BlockState st = world.getBlockState(mut);
							if (!st.isAir()) {
								world.setBlockState(mut, Blocks.AIR.getDefaultState(), 2);
								cleared++;
							}
						}
					}
				}
			}
		}
		MineDota.LOGGER.info("Scrubbed outside map to void ({} blocks in loaded chunks)", cleared);
	}

	/** True if chunk AABB overlaps arena or lobby. */
	public static boolean chunkTouchesGenerated(int chunkX, int chunkZ) {
		int x0 = chunkX << 4;
		int z0 = chunkZ << 4;
		int x1 = x0 + 15;
		int z1 = z0 + 15;
		if (x1 < genMinX() || x0 > genMaxX() || z1 < genMinZ() || z0 > genMaxZ()) {
			return false;
		}
		// Coarse AABB hit — fine enough (void columns still cheap)
		return true;
	}

	public static boolean isTreeBlock(BlockState state) {
		Block b = state.getBlock();
		return b == Blocks.OAK_LOG || b == Blocks.OAK_LEAVES
				|| b == Blocks.BIRCH_LOG || b == Blocks.BIRCH_LEAVES
				|| b == Blocks.SPRUCE_LOG || b == Blocks.SPRUCE_LEAVES
				|| b == Blocks.JUNGLE_LOG || b == Blocks.JUNGLE_LEAVES
				|| b == Blocks.ACACIA_LOG || b == Blocks.ACACIA_LEAVES
				|| b == Blocks.DARK_OAK_LOG || b == Blocks.DARK_OAK_LEAVES
				|| b == Blocks.MANGROVE_LOG || b == Blocks.MANGROVE_LEAVES
				|| b == Blocks.CHERRY_LOG || b == Blocks.CHERRY_LEAVES;
	}

	/** Whole base platform raise (fountain + respawn zone). */
	public static final int BASE_RAISE = 2;

	public static BlockPos radiantSpawn() {
		// Near fountain, still on raised respawn pad
		return new BlockPos(R_FOUNTAIN_X + 4, FLOOR_Y - 1 + BASE_RAISE + 1, R_FOUNTAIN_Z - 4);
	}

	public static BlockPos direSpawn() {
		return new BlockPos(D_FOUNTAIN_X - 4, FLOOR_Y - 1 + BASE_RAISE + 1, D_FOUNTAIN_Z + 4);
	}

	public static BlockPos radiantAncient() {
		return new BlockPos(R_ANCIENT_X, FLOOR_Y - 1 + BASE_RAISE + 1, R_ANCIENT_Z);
	}

	public static BlockPos direAncient() {
		return new BlockPos(D_ANCIENT_X, FLOOR_Y - 1 + BASE_RAISE + 1, D_ANCIENT_Z);
	}

	/**
	 * Towers: T1 (outer, near river) → T2 → T3 → T4 (near base / barracks).
	 */
	public record TowerSpot(BlockPos pos, Lane lane, int tier) {
	}

	public record BarrackSpot(BlockPos pos, Lane lane, BarrackEntity.Kind kind) {
	}

	/** Highest tower tier on a lane (barracks unlock after this falls). */
	public static final int MAX_TOWER_TIER = 4;

	public static List<TowerSpot> radiantTowerSpots() {
		return List.of(
				// TOP x=-72: riverward → base
				new TowerSpot(new BlockPos(-72, WALK_Y, -50), Lane.TOP, 1),
				new TowerSpot(new BlockPos(-72, WALK_Y, -20), Lane.TOP, 2),
				new TowerSpot(new BlockPos(-72, WALK_Y, 12), Lane.TOP, 3),
				new TowerSpot(new BlockPos(-72, WALK_Y, 48), Lane.TOP, 4),
				// MID diagonal
				new TowerSpot(new BlockPos(-10, WALK_Y, 10), Lane.MID, 1),
				new TowerSpot(new BlockPos(-24, WALK_Y, 24), Lane.MID, 2),
				new TowerSpot(new BlockPos(-38, WALK_Y, 38), Lane.MID, 3),
				new TowerSpot(new BlockPos(-52, WALK_Y, 52), Lane.MID, 4),
				// BOT z=72
				new TowerSpot(new BlockPos(50, WALK_Y, 72), Lane.BOT, 1),
				new TowerSpot(new BlockPos(20, WALK_Y, 72), Lane.BOT, 2),
				new TowerSpot(new BlockPos(-12, WALK_Y, 72), Lane.BOT, 3),
				new TowerSpot(new BlockPos(-48, WALK_Y, 72), Lane.BOT, 4)
		);
	}

	public static List<TowerSpot> direTowerSpots() {
		// Dire TOP/BOT inverted vs Radiant naming (south lane = TOP, east lane = BOT)
		return List.of(
				new TowerSpot(new BlockPos(-50, WALK_Y, -72), Lane.TOP, 1),
				new TowerSpot(new BlockPos(-20, WALK_Y, -72), Lane.TOP, 2),
				new TowerSpot(new BlockPos(12, WALK_Y, -72), Lane.TOP, 3),
				new TowerSpot(new BlockPos(48, WALK_Y, -72), Lane.TOP, 4),
				new TowerSpot(new BlockPos(10, WALK_Y, -10), Lane.MID, 1),
				new TowerSpot(new BlockPos(24, WALK_Y, -24), Lane.MID, 2),
				new TowerSpot(new BlockPos(38, WALK_Y, -38), Lane.MID, 3),
				new TowerSpot(new BlockPos(52, WALK_Y, -52), Lane.MID, 4),
				new TowerSpot(new BlockPos(72, WALK_Y, 50), Lane.BOT, 1),
				new TowerSpot(new BlockPos(72, WALK_Y, 20), Lane.BOT, 2),
				new TowerSpot(new BlockPos(72, WALK_Y, -12), Lane.BOT, 3),
				new TowerSpot(new BlockPos(72, WALK_Y, -48), Lane.BOT, 4)
		);
	}

	/**
	 * Barracks behind T4 toward the base: melee + ranged of the same lane sit 2–3 blocks apart,
	 * flanking the lane so the center path stays clear.
	 */
	public static List<BarrackSpot> radiantBarrackSpots() {
		return List.of(
				// TOP T4 (-72,48): behind +Z, pair ±1.5 along X → 3 blocks apart
				new BarrackSpot(new BlockPos(-74, WALK_Y, 56), Lane.TOP, BarrackEntity.Kind.MELEE),
				new BarrackSpot(new BlockPos(-71, WALK_Y, 56), Lane.TOP, BarrackEntity.Kind.RANGED),
				// MID T4 (-52,52): behind toward base, pair along mid-perpendicular
				new BarrackSpot(new BlockPos(-58, WALK_Y, 55), Lane.MID, BarrackEntity.Kind.MELEE),
				new BarrackSpot(new BlockPos(-55, WALK_Y, 58), Lane.MID, BarrackEntity.Kind.RANGED),
				// BOT T4 (-48,72): behind -X, pair ±1.5 along Z → 3 blocks apart
				new BarrackSpot(new BlockPos(-56, WALK_Y, 71), Lane.BOT, BarrackEntity.Kind.MELEE),
				new BarrackSpot(new BlockPos(-56, WALK_Y, 74), Lane.BOT, BarrackEntity.Kind.RANGED)
		);
	}

	public static List<BarrackSpot> direBarrackSpots() {
		return List.of(
				// TOP T4 (48,-72): behind +X, pair ±1.5 along Z
				new BarrackSpot(new BlockPos(56, WALK_Y, -74), Lane.TOP, BarrackEntity.Kind.MELEE),
				new BarrackSpot(new BlockPos(56, WALK_Y, -71), Lane.TOP, BarrackEntity.Kind.RANGED),
				// MID T4 (52,-52)
				new BarrackSpot(new BlockPos(55, WALK_Y, -58), Lane.MID, BarrackEntity.Kind.MELEE),
				new BarrackSpot(new BlockPos(58, WALK_Y, -55), Lane.MID, BarrackEntity.Kind.RANGED),
				// BOT T4 (72,-48): behind -Z, pair ±1.5 along X
				new BarrackSpot(new BlockPos(71, WALK_Y, -56), Lane.BOT, BarrackEntity.Kind.MELEE),
				new BarrackSpot(new BlockPos(74, WALK_Y, -56), Lane.BOT, BarrackEntity.Kind.RANGED)
		);
	}

	public static BarrackSpot findBarrackSpot(DotaTeam team, Lane lane, BarrackEntity.Kind kind) {
		List<BarrackSpot> spots = team == DotaTeam.RADIANT ? radiantBarrackSpots() : direBarrackSpots();
		for (BarrackSpot s : spots) {
			if (s.lane() == lane && s.kind() == kind) {
				return s;
			}
		}
		return null;
	}

	public static TowerSpot findTowerSpot(DotaTeam team, Lane lane, int tier) {
		List<TowerSpot> spots = team == DotaTeam.RADIANT ? radiantTowerSpots() : direTowerSpots();
		for (TowerSpot s : spots) {
			if (s.lane() == lane && s.tier() == tier) {
				return s;
			}
		}
		return null;
	}

	/** @deprecated use radiantTowerSpots */
	public static List<BlockPos> radiantTowerPositions() {
		return radiantTowerSpots().stream().map(TowerSpot::pos).toList();
	}

	/** @deprecated use direTowerSpots */
	public static List<BlockPos> direTowerPositions() {
		return direTowerSpots().stream().map(TowerSpot::pos).toList();
	}

	public static List<CreepSpawn> creepSpawns() {
		List<CreepSpawn> list = new ArrayList<>();
		// Side-lane waypoints at ±71 (path painted at ±70 halfW=2 → 68..72; towers at ±72).
		// 1 block inward from tower line = dirt center, away from jungle trees.
		list.add(new CreepSpawn(wp(-71, 45), lanePath(
				-71, 45, -71, 20, -71, -10, -71, -40, -71, -55,
				-40, -71, -10, -71, 25, -71, 48, -71, 55, -71, 70, -71, D_ANCIENT_X, D_ANCIENT_Z
		), Lane.TOP, DotaTeam.RADIANT));
		list.add(new CreepSpawn(wp(-50, 50), lanePath(
				-50, 50, -40, 40, -25, 25, -10, 10, 0, 0,
				10, -10, 25, -25, 40, -40, 55, -55, D_ANCIENT_X, D_ANCIENT_Z
		), Lane.MID, DotaTeam.RADIANT));
		list.add(new CreepSpawn(wp(-45, 71), lanePath(
				-45, 71, -20, 71, 10, 71, 40, 71, 55, 71,
				71, 40, 71, 10, 71, -20, 71, -48, 71, -55, D_ANCIENT_X, D_ANCIENT_Z
		), Lane.BOT, DotaTeam.RADIANT));
		list.add(new CreepSpawn(wp(45, -71), lanePath(
				45, -71, 20, -71, -10, -71, -40, -71, -55, -71,
				-71, -40, -71, -10, -71, 20, -71, 48, -71, 55, R_ANCIENT_X, R_ANCIENT_Z
		), Lane.TOP, DotaTeam.DIRE));
		list.add(new CreepSpawn(wp(50, -50), lanePath(
				50, -50, 40, -40, 25, -25, 10, -10, 0, 0,
				-10, 10, -25, 25, -40, 40, -55, 55, R_ANCIENT_X, R_ANCIENT_Z
		), Lane.MID, DotaTeam.DIRE));
		list.add(new CreepSpawn(wp(71, -45), lanePath(
				71, -45, 71, -20, 71, 10, 71, 40, 71, 55,
				40, 71, 10, 71, -20, 71, -48, 71, -55, 71, R_ANCIENT_X, R_ANCIENT_Z
		), Lane.BOT, DotaTeam.DIRE));
		return List.copyOf(list);
	}

	private static BlockPos wp(int x, int z) {
		return new BlockPos(x, WALK_Y, z);
	}

	private static List<BlockPos> lanePath(int... xz) {
		List<BlockPos> key = new ArrayList<>();
		for (int i = 0; i + 1 < xz.length; i += 2) {
			key.add(wp(xz[i], xz[i + 1]));
		}
		List<BlockPos> dense = new ArrayList<>();
		for (int i = 0; i + 1 < key.size(); i++) {
			BlockPos a = key.get(i);
			BlockPos b = key.get(i + 1);
			int steps = Math.max(1, (int) Math.ceil(Math.sqrt(a.getSquaredDistance(b)) / 6.0));
			for (int s = 0; s < steps; s++) {
				double t = (double) s / steps;
				int x = (int) Math.round(a.getX() + (b.getX() - a.getX()) * t);
				int z = (int) Math.round(a.getZ() + (b.getZ() - a.getZ()) * t);
				dense.add(wp(x, z));
			}
		}
		dense.add(key.get(key.size() - 1));
		return List.copyOf(dense);
	}

	/** Top solid surface Y (grass / path / stone top). */
	public static int surfaceY(int x, int z) {
		int y = FLOOR_Y - 1;
		y += baseElevation(x, z);
		y += edgeMountainBoost(x, z);
		y += highgroundBoost(x, z);
		if (isWardPillarBase(x, z)) {
			y += 1;
		}
		return y;
	}

	/**
	 * Whole respawn base raised by {@link #BASE_RAISE}; 2-block-deep stairs down to lanes.
	 */
	private static int baseElevation(int x, int z) {
		if (isRadiantBase(x, z) || isDireBase(x, z)) {
			return BASE_RAISE;
		}
		if (isBaseStair(x, z)) {
			return 1;
		}
		return 0;
	}

	/** First stair tread (2 blocks deep) just outside lane-facing base edges + side approaches. */
	private static boolean isBaseStair(int x, int z) {
		if (isRadiantBase(x, z) || isDireBase(x, z)) {
			return false;
		}
		int edge = BASE_HALF + 1;
		int edge2 = BASE_HALF + 2;
		// Radiant: east (bot) + north (top) + NE mid
		int rlx = x - R_BASE_X;
		int rlz = z - R_BASE_Z;
		if (rlx >= edge && rlx <= edge2 && Math.abs(rlz) <= BASE_HALF) {
			return true;
		}
		if (rlz <= -edge && rlz >= -edge2 && Math.abs(rlx) <= BASE_HALF) {
			return true;
		}
		if (rlx >= edge && rlx <= edge2 + 2 && rlz <= -edge && rlz >= -edge2 - 2) {
			return true;
		}
		// Radiant TOP approach: west lane → base (from T3 toward platform)
		if (x >= -76 && x <= -68 && z >= 48 && z <= R_BASE_Z - BASE_HALF) {
			return true;
		}
		// Radiant BOT approach: south lane → base
		if (z >= 68 && z <= 76 && x >= R_BASE_X + BASE_HALF && x <= -48) {
			return true;
		}
		// Dire: west + south + SW mid
		int dlx = x - D_BASE_X;
		int dlz = z - D_BASE_Z;
		if (dlx <= -edge && dlx >= -edge2 && Math.abs(dlz) <= BASE_HALF) {
			return true;
		}
		if (dlz >= edge && dlz <= edge2 && Math.abs(dlx) <= BASE_HALF) {
			return true;
		}
		if (dlx <= -edge && dlx >= -edge2 - 2 && dlz >= edge && dlz <= edge2 + 2) {
			return true;
		}
		// Dire TOP approach: south lane → base
		if (z <= -68 && z >= -76 && x >= 48 && x <= D_BASE_X - BASE_HALF) {
			return true;
		}
		// Dire BOT approach: east lane → base
		if (x <= 76 && x >= 68 && z <= -48 && z >= D_BASE_Z + BASE_HALF) {
			return true;
		}
		return false;
	}

	private static int edgeMountainBoost(int x, int z) {
		int edge = HALF - Math.max(Math.abs(x), Math.abs(z));
		if (edge >= 18) {
			return 0;
		}
		if (isLane(x, z) || isRiver(x, z) || isRadiantBase(x, z) || isDireBase(x, z)
				|| isBaseExitCorridor(x, z) || isBaseStair(x, z)
				|| isNearTowerPlaza(x, z, 5)) {
			return 0;
		}
		int h = (18 - edge);
		if (edge < 6) {
			h += 3;
		}
		return Math.min(12, h / 2 + (hash(x, z) % 3));
	}

	private static int highgroundBoost(int x, int z) {
		if (isLane(x, z) || isRiver(x, z) || isRadiantBase(x, z) || isDireBase(x, z)
				|| isBaseExitCorridor(x, z) || isBaseStair(x, z) || isFountainPad(x, z) || isAncientPad(x, z)
				|| isNearTowerPlaza(x, z, 5)) {
			return 0;
		}
		int boost = 0;
		// Mid rune / river highgrounds
		if (near(x, z, -14, 30, 7) || near(x, z, 14, -30, 7)
				|| near(x, z, -30, 14, 7) || near(x, z, 30, -14, 7)) {
			boost = Math.max(boost, 4);
		}
		// Twin mid cliffs
		if (near(x, z, -8, 8, 4) || near(x, z, 8, -8, 4)) {
			boost = Math.max(boost, 5);
		}
		// Lane highgrounds (T1 areas)
		if (near(x, z, -55, -15, 6) || near(x, z, 55, 15, 6)
				|| near(x, z, -15, 55, 6) || near(x, z, 15, -55, 6)) {
			boost = Math.max(boost, 3);
		}
		// Jungle ramps
		if (near(x, z, -40, 25, 5) || near(x, z, 40, -25, 5)
				|| near(x, z, -25, 40, 5) || near(x, z, 25, -40, 5)
				|| near(x, z, -38, -22, 5) || near(x, z, 38, 22, 5)) {
			boost = Math.max(boost, 2 + hash(x, z) % 2);
		}
		// Ancient cliffs (outside enlarged platforms)
		if (near(x, z, R_BASE_X, R_BASE_Z, 12) && !isRadiantBase(x, z)) {
			boost = Math.max(boost, 2);
		}
		if (near(x, z, D_BASE_X, D_BASE_Z, 12) && !isDireBase(x, z)) {
			boost = Math.max(boost, 2);
		}
		return boost;
	}

	private static int hash(int x, int z) {
		return Math.floorMod(x * 374761393 + z * 668265263, 97);
	}

	public static BlockState getBlock(int x, int y, int z) {
		if (y < 0 || y > 127) {
			return Blocks.AIR.getDefaultState();
		}
		if (y == 0) {
			return Blocks.BEDROCK.getDefaultState();
		}

		BlockState lobby = LobbyMap.getBlock(x, y, z);
		if (lobby != null) {
			return lobby;
		}

		boolean inside = Math.abs(x) < HALF && Math.abs(z) < HALF;
		boolean border = Math.abs(x) == HALF || Math.abs(z) == HALF;

		// Beyond the single arena square (+ lobby above): void — no infinite bedrock/barrier grid to generate
		if (!inside && !border) {
			return Blocks.AIR.getDefaultState();
		}

		if (border) {
			if (y > 0 && y < FLOOR_Y) {
				return Blocks.STONE.getDefaultState();
			}
			if (y >= FLOOR_Y && y <= WALL_TOP) {
				return Blocks.STONE_BRICKS.getDefaultState();
			}
			return Blocks.AIR.getDefaultState();
		}

		int surf = surfaceY(x, z);

		if (isRiver(x, z)) {
			int iceY = FLOOR_Y - 1;
			if (y < iceY - 2) {
				return Blocks.STONE.getDefaultState();
			}
			if (y < iceY) {
				return Blocks.DIRT.getDefaultState();
			}
			if (y == iceY) {
				return isRiverBank(x, z) ? Blocks.SAND.getDefaultState() : Blocks.BLUE_WOOL.getDefaultState();
			}
			if (y > iceY) {
				return Blocks.AIR.getDefaultState();
			}
		}

		if (y < surf - 2) {
			return Blocks.STONE.getDefaultState();
		}
		if (y == surf - 2 || y == surf - 1) {
			if (y == surf - 1) {
				return Blocks.DIRT.getDefaultState();
			}
			return Blocks.STONE.getDefaultState();
		}
		if (y == surf) {
			if (isTowerPedestal(x, z)) {
				return Blocks.IRON_BLOCK.getDefaultState();
			}
			if (isAncientPad(x, z)) {
				return Blocks.OBSIDIAN.getDefaultState();
			}
			return surfaceAt(x, z);
		}

		// Above surface: features relative to surf
		BlockState feature = featureAt(x, y, z, surf);
		if (feature != null) {
			return feature;
		}
		return Blocks.AIR.getDefaultState();
	}

	private static boolean isTowerPedestal(int x, int z) {
		for (BlockPos t : radiantTowerPositions()) {
			if (t.getX() == x && t.getZ() == z) {
				return true;
			}
		}
		for (BlockPos t : direTowerPositions()) {
			if (t.getX() == x && t.getZ() == z) {
				return true;
			}
		}
		return false;
	}

	/** Flat plaza so creeps don't clip stone highground walls next to towers. */
	private static boolean isNearTowerPlaza(int x, int z, int r) {
		for (BlockPos t : radiantTowerPositions()) {
			if (Math.abs(t.getX() - x) <= r && Math.abs(t.getZ() - z) <= r) {
				return true;
			}
		}
		for (BlockPos t : direTowerPositions()) {
			if (Math.abs(t.getX() - x) <= r && Math.abs(t.getZ() - z) <= r) {
				return true;
			}
		}
		return false;
	}

	private static boolean isAncientPad(int x, int z) {
		return (Math.abs(x - R_ANCIENT_X) <= 1 && Math.abs(z - R_ANCIENT_Z) <= 1)
				|| (Math.abs(x - D_ANCIENT_X) <= 1 && Math.abs(z - D_ANCIENT_Z) <= 1);
	}

	private static BlockState surfaceAt(int x, int z) {
		if (isRadiantBase(x, z)) {
			if (isFountainPad(x, z)) {
				return Blocks.EMERALD_BLOCK.getDefaultState();
			}
			return Blocks.LIME_CONCRETE.getDefaultState();
		}
		if (isDireBase(x, z)) {
			if (isFountainPad(x, z)) {
				return Blocks.REDSTONE_BLOCK.getDefaultState();
			}
			return Blocks.RED_CONCRETE.getDefaultState();
		}
		if (isLane(x, z) || isBaseExitCorridor(x, z)) {
			return Blocks.DIRT_PATH.getDefaultState();
		}
		if (isBarracks(x, z)) {
			return (z > 0 ? Blocks.LIME_TERRACOTTA : Blocks.RED_TERRACOTTA).getDefaultState();
		}
		if (isRoshanPit(x, z)) {
			return Blocks.BLACKSTONE.getDefaultState();
		}
		if (isWardPillarBase(x, z)) {
			return Blocks.STONE.getDefaultState();
		}
		if (x == 0 && z == 0) {
			return Blocks.GOLD_BLOCK.getDefaultState();
		}
		if (edgeMountainBoost(x, z) > 0 || highgroundBoost(x, z) > 0) {
			return Blocks.STONE.getDefaultState();
		}
		if (treeRootAt(x, z) != null) {
			return Blocks.PODZOL.getDefaultState();
		}
		return Blocks.GRASS_BLOCK.getDefaultState();
	}

	private static BlockState featureAt(int x, int y, int z, int surf) {
		int rel = y - surf;
		if (rel < 1) {
			return null;
		}

		if (isRadiantBaseWall(x, z) && rel <= 3) {
			return Blocks.STONE_BRICK_WALL.getDefaultState();
		}
		if (isDireBaseWall(x, z) && rel <= 3) {
			return Blocks.STONE_BRICK_WALL.getDefaultState();
		}
		if (x == R_FOUNTAIN_X && z == R_FOUNTAIN_Z && rel == 1) {
			return Blocks.WATER.getDefaultState();
		}
		if (x == D_FOUNTAIN_X && z == D_FOUNTAIN_Z && rel == 1) {
			return Blocks.WATER.getDefaultState();
		}
		if (x == 0 && z == 0 && rel == 1) {
			return Blocks.LIGHTNING_ROD.getDefaultState();
		}
		if (isBarracksCenter(x, z) && rel == 1) {
			return Blocks.IRON_BLOCK.getDefaultState();
		}
		if (isWardPillar(x, z)) {
			if (rel <= 4) {
				return Blocks.STONE.getDefaultState();
			}
			if (rel == 5) {
				return Blocks.COBBLESTONE_WALL.getDefaultState();
			}
		}
		if (isRoshanPit(x, z)) {
			int lx = x + 18;
			int lz = z + 18;
			if ((Math.abs(lx) == 4 || Math.abs(lz) == 4) && rel <= 2) {
				return Blocks.BLACKSTONE.getDefaultState();
			}
			if (x == -18 && z == -18 && rel == 1) {
				return Blocks.MAGMA_BLOCK.getDefaultState();
			}
			if (x == -18 && z == -18 && rel == 2) {
				return Blocks.SKELETON_SKULL.getDefaultState();
			}
		}
		for (BlockPos t : radiantTowerPositions()) {
			if (t.getX() == x && t.getZ() == z && rel == 4) {
				return Blocks.LIME_BANNER.getDefaultState();
			}
		}
		for (BlockPos t : direTowerPositions()) {
			if (t.getX() == x && t.getZ() == z && rel == 4) {
				return Blocks.RED_BANNER.getDefaultState();
			}
		}
		return treeAt(x, y, z, surf);
	}

	/** Wider river along anti-diagonal. */
	private static boolean isRiver(int x, int z) {
		int i = (x + z) / 2;
		int w = x - z;
		return Math.abs(w) <= 4 && Math.abs(i) <= 72;
	}

	private static boolean isRiverBank(int x, int z) {
		int w = Math.abs(x - z);
		return (w == 4 || w == 5) && Math.abs((x + z) / 2) <= 72;
	}

	private static boolean isLane(int x, int z) {
		return nearSegment(x, z, -70, 70, 70, -70, 2)
				|| nearSegment(x, z, -70, 55, -70, -55, 2)
				|| nearSegment(x, z, -70, -55, -55, -70, 2)
				|| nearSegment(x, z, -55, -70, 55, -70, 2)
				|| nearSegment(x, z, -55, 70, 55, 70, 2)
				|| nearSegment(x, z, 55, 70, 70, 55, 2)
				|| nearSegment(x, z, 70, 55, 70, -55, 2);
	}

	private static boolean nearSegment(int x, int z, int x0, int z0, int x1, int z1, int halfWidth) {
		int dx = x1 - x0;
		int dz = z1 - z0;
		long len2 = (long) dx * dx + (long) dz * dz;
		if (len2 == 0) {
			return Math.abs(x - x0) <= halfWidth && Math.abs(z - z0) <= halfWidth;
		}
		double t = ((x - x0) * (double) dx + (z - z0) * (double) dz) / len2;
		if (t < 0 || t > 1) {
			return false;
		}
		double px = x0 + t * dx;
		double pz = z0 + t * dz;
		double dist2 = (x - px) * (x - px) + (z - pz) * (z - pz);
		return dist2 <= (halfWidth + 0.6) * (halfWidth + 0.6);
	}

	private static boolean isRadiantBase(int x, int z) {
		return Math.abs(x - R_BASE_X) <= BASE_HALF && Math.abs(z - R_BASE_Z) <= BASE_HALF;
	}

	private static boolean isDireBase(int x, int z) {
		return Math.abs(x - D_BASE_X) <= BASE_HALF && Math.abs(z - D_BASE_Z) <= BASE_HALF;
	}

	private static boolean isFountainPad(int x, int z) {
		return (Math.abs(x - R_FOUNTAIN_X) <= 2 && Math.abs(z - R_FOUNTAIN_Z) <= 2)
				|| (Math.abs(x - D_FOUNTAIN_X) <= 2 && Math.abs(z - D_FOUNTAIN_Z) <= 2);
	}

	/**
	 * Walls only on outer sides (map border). Lane-facing sides fully open.
	 */
	private static boolean isRadiantBaseWall(int x, int z) {
		if (!isRadiantBase(x, z)) {
			return false;
		}
		int lx = x - R_BASE_X;
		int lz = z - R_BASE_Z;
		return lx == -BASE_HALF || lz == BASE_HALF;
	}

	private static boolean isDireBaseWall(int x, int z) {
		if (!isDireBase(x, z)) {
			return false;
		}
		int lx = x - D_BASE_X;
		int lz = z - D_BASE_Z;
		return lx == BASE_HALF || lz == -BASE_HALF;
	}

	/** Corridors from base toward mid/top/bot + side approaches — no trees, walkable. */
	public static boolean isBaseExitCorridor(int x, int z) {
		if (x >= -80 && x <= -52 && z >= 52 && z <= 80) {
			return true;
		}
		if (x >= -76 && x <= -64 && z >= 48 && z <= 80) {
			return true;
		}
		if (z >= 64 && z <= 80 && x >= -80 && x <= -48) {
			return true;
		}
		if (x <= 80 && x >= 52 && z <= -52 && z >= -80) {
			return true;
		}
		if (x <= 76 && x >= 64 && z <= -48 && z >= -80) {
			return true;
		}
		if (z <= -64 && z >= -80 && x <= 80 && x >= 48) {
			return true;
		}
		return false;
	}

	/** Jungle ward cliffs near camp spots. */
	private static boolean isWardPillarBase(int x, int z) {
		return near(x, z, -42, 8, 1) || near(x, z, 42, -8, 1)
				|| near(x, z, -8, 42, 1) || near(x, z, 8, -42, 1)
				|| near(x, z, -30, -30, 1) || near(x, z, 30, 30, 1);
	}

	private static boolean isWardPillar(int x, int z) {
		return (x == -42 && z == 8) || (x == 42 && z == -8)
				|| (x == -8 && z == 42) || (x == 8 && z == -42)
				|| (x == -30 && z == -30) || (x == 30 && z == 30);
	}

	public static void repairBaseGates(ServerWorld world) {
		// Clear wall columns + trees in exit corridors / side approaches
		for (int x = -96; x <= -48; x++) {
			for (int z = 48; z <= 96; z++) {
				clearExitColumn(world, x, z);
			}
		}
		for (int x = 48; x <= 96; x++) {
			for (int z = -96; z <= -48; z++) {
				clearExitColumn(world, x, z);
			}
		}
		raiseBasePlatform(world, R_BASE_X, R_BASE_Z, true);
		raiseBasePlatform(world, D_BASE_X, D_BASE_Z, false);
		placeTowerPedestals(world);
		repairRiver(world);
	}

	/** River surface = blue wool (was ice). Converts old ice/water on existing worlds. */
	public static void repairRiver(ServerWorld world) {
		int riverY = FLOOR_Y - 1;
		for (int x = -HALF + 1; x < HALF; x++) {
			for (int z = -HALF + 1; z < HALF; z++) {
				if (!isRiver(x, z) || isRiverBank(x, z)) {
					continue;
				}
				BlockPos pos = new BlockPos(x, riverY, z);
				BlockState st = world.getBlockState(pos);
				if (st.isOf(Blocks.WATER)
						|| st.isOf(Blocks.ICE)
						|| st.isOf(Blocks.PACKED_ICE)
						|| st.isOf(Blocks.BLUE_ICE)
						|| st.isAir()
						|| !st.isOf(Blocks.BLUE_WOOL)) {
					world.setBlockState(pos, Blocks.BLUE_WOOL.getDefaultState());
				}
				BlockPos above = pos.up();
				if (world.getBlockState(above).isOf(Blocks.WATER)) {
					world.setBlockState(above, Blocks.AIR.getDefaultState());
				}
			}
		}
	}

	/** Raise whole respawn zone + stair treads down to lanes (old worlds). */
	private static void raiseBasePlatform(ServerWorld world, int cx, int cz, boolean radiant) {
		Block top = radiant ? Blocks.LIME_CONCRETE : Blocks.RED_CONCRETE;
		Block fountain = radiant ? Blocks.EMERALD_BLOCK : Blocks.REDSTONE_BLOCK;
		int fx = radiant ? R_FOUNTAIN_X : D_FOUNTAIN_X;
		int fz = radiant ? R_FOUNTAIN_Z : D_FOUNTAIN_Z;
		int ground = FLOOR_Y - 1;
		int span = BASE_HALF + 6;
		for (int dx = -span; dx <= span; dx++) {
			for (int dz = -span; dz <= span; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				int elev = baseElevation(x, z);
				if (elev <= 0 && !isBaseExitCorridor(x, z) && !isBaseStair(x, z)) {
					continue;
				}
				int surf = ground + Math.max(elev, isBaseExitCorridor(x, z) && elev == 0 ? 0 : elev);
				if (elev <= 0 && isBaseExitCorridor(x, z)) {
					surf = ground; // lane-level corridor approach
				}
				if (isBaseStair(x, z)) {
					surf = ground + 1;
				}
				if (isRadiantBase(x, z) || isDireBase(x, z)) {
					surf = ground + BASE_RAISE;
				}
				// fill under
				for (int y = ground; y < surf; y++) {
					world.setBlockState(new BlockPos(x, y, z), Blocks.DIRT.getDefaultState());
				}
				BlockState surface;
				if (isFountainPad(x, z)) {
					surface = fountain.getDefaultState();
				} else if (isRadiantBase(x, z) || isDireBase(x, z)) {
					surface = top.getDefaultState();
				} else if (elev == 1 || isLane(x, z) || isBaseExitCorridor(x, z) || isBaseStair(x, z)) {
					surface = Blocks.DIRT_PATH.getDefaultState();
				} else {
					continue;
				}
				world.setBlockState(new BlockPos(x, surf, z), surface);
				for (int y = surf + 1; y <= surf + 5; y++) {
					if (isFountainPad(x, z) && x == fx && z == fz && y == surf + 1) {
						world.setBlockState(new BlockPos(x, y, z), Blocks.WATER.getDefaultState());
						continue;
					}
					BlockState above = world.getBlockState(new BlockPos(x, y, z));
					if (isTreeBlock(above) || above.isOf(Blocks.STONE_BRICK_WALL)
							|| above.isOf(Blocks.COBBLESTONE_WALL) || above.isAir()
							|| above.isOf(Blocks.GRASS) || above.isOf(Blocks.TALL_GRASS)) {
						if (!(isFountainPad(x, z) && x == fx && z == fz && y == surf + 1)) {
							world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
						}
					}
				}
			}
		}
		world.setBlockState(new BlockPos(fx, ground + BASE_RAISE + 1, fz), Blocks.WATER.getDefaultState());
	}

	/** Iron block under tower; clear 4-block plaza (walls/stone/trees). */
	public static void placeTowerPedestals(ServerWorld world) {
		List<BlockPos> all = new ArrayList<>();
		all.addAll(radiantTowerPositions());
		all.addAll(direTowerPositions());
		for (BlockPos t : all) {
			int sx = t.getX();
			int sz = t.getZ();
			int sy = surfaceY(sx, sz);
			final int r = 4;
			for (BlockPos p : BlockPos.iterate(new BlockPos(sx - r, sy - 1, sz - r), new BlockPos(sx + r, sy + 6, sz + r))) {
				boolean center = p.getX() == sx && p.getZ() == sz && p.getY() == sy;
				if (center) {
					continue;
				}
				BlockState st = world.getBlockState(p);
				if (p.getY() > sy) {
					if (isTreeBlock(st)
							|| st.isOf(Blocks.STONE_BRICK_WALL) || st.isOf(Blocks.COBBLESTONE_WALL)
							|| st.isOf(Blocks.OAK_FENCE) || st.isOf(Blocks.IRON_BARS)
							|| st.isOf(Blocks.STONE) || st.isOf(Blocks.STONE_BRICKS)
							|| st.isOf(Blocks.COBBLESTONE) || st.isOf(Blocks.MOSSY_COBBLESTONE)
							|| st.isOf(Blocks.ANDESITE) || st.isOf(Blocks.DIRT) || st.isOf(Blocks.GRASS_BLOCK)
							|| st.isOf(Blocks.IRON_BLOCK) || st.isOf(Blocks.OBSIDIAN)
							|| st.isOf(Blocks.LIME_BANNER) || st.isOf(Blocks.RED_BANNER)
							|| st.isOf(Blocks.VINE) || st.isOf(Blocks.GRASS) || st.isOf(Blocks.TALL_GRASS)) {
						world.setBlockState(p, Blocks.AIR.getDefaultState());
					}
				} else if (p.getY() == sy) {
					if (st.isOf(Blocks.IRON_BLOCK) || st.isOf(Blocks.OBSIDIAN)
							|| st.isOf(Blocks.STONE) || st.isOf(Blocks.STONE_BRICKS)
							|| st.isOf(Blocks.COBBLESTONE) || st.isOf(Blocks.STONE_BRICK_WALL)
							|| st.isOf(Blocks.COBBLESTONE_WALL) || isTreeBlock(st)) {
						world.setBlockState(p, Blocks.DIRT_PATH.getDefaultState());
					}
				}
			}
			world.setBlockState(new BlockPos(sx, sy, sz), Blocks.IRON_BLOCK.getDefaultState());
			for (int y = sy + 1; y <= sy + 6; y++) {
				BlockPos p = new BlockPos(sx, y, sz);
				if (isTreeBlock(world.getBlockState(p))
						|| world.getBlockState(p).isOf(Blocks.STONE)
						|| world.getBlockState(p).isOf(Blocks.STONE_BRICKS)) {
					world.setBlockState(p, Blocks.AIR.getDefaultState());
				}
			}
		}
	}

	private static void clearExitColumn(ServerWorld world, int x, int z) {
		boolean radiant = isRadiantBase(x, z);
		boolean dire = isDireBase(x, z);
		boolean corridor = isBaseExitCorridor(x, z);
		if (!radiant && !dire && !corridor) {
			return;
		}
		int surf = surfaceY(x, z);
		for (int y = FLOOR_Y - 1; y <= surf + 12; y++) {
			BlockPos pos = new BlockPos(x, y, z);
			BlockState state = world.getBlockState(pos);
			boolean wall = state.isOf(Blocks.STONE_BRICK_WALL) || state.isOf(Blocks.COBBLESTONE_WALL)
					|| state.isOf(Blocks.OAK_FENCE) || state.isOf(Blocks.IRON_BARS)
					|| state.isOf(Blocks.STONE_BRICKS);
			boolean tree = isTreeBlock(state);

			if (radiant) {
				int lx = x - R_BASE_X;
				int lz = z - R_BASE_Z;
				if (wall && (lx == BASE_HALF || lz == -BASE_HALF)) {
					world.setBlockState(pos, Blocks.AIR.getDefaultState());
					continue;
				}
			}
			if (dire) {
				int lx = x - D_BASE_X;
				int lz = z - D_BASE_Z;
				if (wall && (lx == -BASE_HALF || lz == BASE_HALF)) {
					world.setBlockState(pos, Blocks.AIR.getDefaultState());
					continue;
				}
			}
			if (tree && (corridor || radiant || dire)) {
				world.setBlockState(pos, Blocks.AIR.getDefaultState());
			}
			if (wall && corridor) {
				world.setBlockState(pos, Blocks.AIR.getDefaultState());
			}
		}
	}

	/** Strip vanilla debris (lava lakes, fire, ores) from arena. */
	public static void scrubVanillaDebris(ServerWorld world) {
		for (int x = -HALF + 1; x < HALF; x++) {
			for (int z = -HALF + 1; z < HALF; z++) {
				for (int y = FLOOR_Y - 4; y <= WALL_TOP + 2; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					Block b = world.getBlockState(pos).getBlock();
					if (b != Blocks.LAVA && b != Blocks.FIRE && b != Blocks.SOUL_FIRE
							&& b != Blocks.COAL_ORE && b != Blocks.IRON_ORE && b != Blocks.COPPER_ORE
							&& b != Blocks.GOLD_ORE && b != Blocks.REDSTONE_ORE && b != Blocks.DIAMOND_ORE
							&& b != Blocks.LAPIS_ORE && b != Blocks.EMERALD_ORE
							&& b != Blocks.DEEPSLATE_COAL_ORE && b != Blocks.DEEPSLATE_IRON_ORE
							&& b != Blocks.DEEPSLATE_COPPER_ORE && b != Blocks.DEEPSLATE_GOLD_ORE
							&& b != Blocks.DEEPSLATE_DIAMOND_ORE) {
						continue;
					}
					int surf = surfaceY(x, z);
					if (y < surf) {
						world.setBlockState(pos, Blocks.STONE.getDefaultState());
					} else if (y == surf) {
						world.setBlockState(pos, surfaceAt(x, z));
					} else {
						world.setBlockState(pos, Blocks.AIR.getDefaultState());
					}
				}
			}
		}
	}

	private static boolean isBarracks(int x, int z) {
		for (BarrackSpot s : radiantBarrackSpots()) {
			if (near(x, z, s.pos().getX(), s.pos().getZ(), 2)) {
				return true;
			}
		}
		for (BarrackSpot s : direBarrackSpots()) {
			if (near(x, z, s.pos().getX(), s.pos().getZ(), 2)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isBarracksCenter(int x, int z) {
		for (BarrackSpot s : radiantBarrackSpots()) {
			if (s.pos().getX() == x && s.pos().getZ() == z) {
				return true;
			}
		}
		for (BarrackSpot s : direBarrackSpots()) {
			if (s.pos().getX() == x && s.pos().getZ() == z) {
				return true;
			}
		}
		return false;
	}

	private static boolean isRoshanPit(int x, int z) {
		return Math.abs(x + 18) <= 4 && Math.abs(z + 18) <= 4;
	}

	private static boolean near(int x, int z, int cx, int cz, int r) {
		return Math.abs(x - cx) <= r && Math.abs(z - cz) <= r;
	}

	private static int[] treeRootAt(int x, int z) {
		if (isLane(x, z) || isRiver(x, z) || isRadiantBase(x, z) || isDireBase(x, z)
				|| isBarracks(x, z) || isRoshanPit(x, z) || isWardPillar(x, z)
				|| isTowerPedestal(x, z) || isAncientPad(x, z) || isBaseExitCorridor(x, z)
				|| isFountainPad(x, z) || isBaseStair(x, z) || isNearTowerPlaza(x, z, 4)) {
			return null;
		}
		boolean inJungle =
				(x < -20 && z > 15 && z < 60 && x > -65)
						|| (z > 20 && x < -15 && x > -60 && z < 65)
						|| (x > 20 && z < -15 && z > -60 && x < 65)
						|| (z < -20 && x > 15 && x < 60 && z > -65)
						|| (x < -15 && z < -15 && x > -55 && z > -55)
						|| (x > 15 && z > 15 && x < 55 && z < 55)
						|| (Math.abs(x + z) > 25 && Math.abs(x - z) > 20 && Math.abs(x) < 70 && Math.abs(z) < 70);

		int h = hash(x, z);
		// ~half previous density
		int threshold = inJungle ? 19 : 6;
		if (h % 100 < threshold) {
			return new int[]{x, z};
		}
		if (inJungle && (hash(x + 1, z) % 100 < 10 || hash(x, z + 1) % 100 < 10)) {
			if (h % 100 < 28) {
				return new int[]{x, z};
			}
		}
		return null;
	}

	/** ~1/3 of trunks are large oaks. */
	private static boolean isBigOak(int rootX, int rootZ) {
		return hash(rootX, rootZ) % 3 == 0;
	}

	private static BlockState treeAt(int x, int y, int z, int surf) {
		int rel = y - surf;
		if (rel < 1) {
			return null;
		}

		int[] root = treeRootAt(x, z);
		if (root != null) {
			boolean big = isBigOak(root[0], root[1]);
			if (big) {
				// Tall trunk + canopy like fancy oak
				if (rel >= 1 && rel <= 6) {
					return Blocks.OAK_LOG.getDefaultState();
				}
				if (rel >= 5 && rel <= 9) {
					return Blocks.OAK_LEAVES.getDefaultState();
				}
				return null;
			}
			if (rel == 1 || rel == 2) {
				return Blocks.OAK_LOG.getDefaultState();
			}
			if (rel == 3 || rel == 4 || rel == 5) {
				return Blocks.OAK_LEAVES.getDefaultState();
			}
			return null;
		}

		// Canopy from nearby trunks (big = radius 2–3, small = 1)
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				int[] r = treeRootAt(x - dx, z - dz);
				if (r == null) {
					continue;
				}
				boolean big = isBigOak(r[0], r[1]);
				int cheb = Math.max(Math.abs(dx), Math.abs(dz));
				if (big) {
					if (cheb <= 3 && rel >= 5 && rel <= 9) {
						// outer ring thinner
						if (cheb == 3 && (rel < 6 || rel > 8 || (Math.abs(dx) + Math.abs(dz) > 4))) {
							continue;
						}
						return Blocks.OAK_LEAVES.getDefaultState();
					}
				} else if (cheb <= 1 && rel >= 3 && rel <= 5) {
					return Blocks.OAK_LEAVES.getDefaultState();
				}
			}
		}
		return null;
	}
}
