package com.minedota.map;

import com.minedota.MineDota;
import com.minedota.team.DotaTeam;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.HashSet;
import java.util.Set;

/**
 * Keeps lane / base chunks loaded so creeps and towers simulate without players nearby.
 */
public final class LaneChunkLoader {
	private LaneChunkLoader() {
	}

	/** Force-load all lane paths, towers, barracks, ancients, T4 spawn pads (±2 chunk padding). */
	public static void forceLoadArena(ServerWorld world) {
		Set<Long> keys = new HashSet<>();
		for (DotaMap.CreepSpawn spawn : DotaMap.creepSpawns()) {
			add(keys, spawn.pos());
			for (BlockPos p : spawn.path()) {
				add(keys, p);
			}
			// Actual spawn is offset from T4 toward enemy
			DotaMap.TowerSpot t4 = DotaMap.findTowerSpot(spawn.team(), spawn.lane(), DotaMap.MAX_TOWER_TIER);
			if (t4 != null) {
				add(keys, t4.pos());
				add(keys, spawnPadNearT4(spawn, t4.pos()));
			}
		}
		for (DotaMap.TowerSpot t : DotaMap.radiantTowerSpots()) {
			add(keys, t.pos());
		}
		for (DotaMap.TowerSpot t : DotaMap.direTowerSpots()) {
			add(keys, t.pos());
		}
		for (DotaMap.BarrackSpot b : DotaMap.radiantBarrackSpots()) {
			add(keys, b.pos());
		}
		for (DotaMap.BarrackSpot b : DotaMap.direBarrackSpots()) {
			add(keys, b.pos());
		}
		add(keys, DotaMap.radiantAncient());
		add(keys, DotaMap.direAncient());
		add(keys, DotaMap.radiantSpawn());
		add(keys, DotaMap.direSpawn());

		Set<Long> padded = new HashSet<>();
		for (long key : keys) {
			int cx = ChunkPos.getPackedX(key);
			int cz = ChunkPos.getPackedZ(key);
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					padded.add(ChunkPos.toLong(cx + dx, cz + dz));
				}
			}
		}

		int forced = 0;
		for (long key : padded) {
			int cx = ChunkPos.getPackedX(key);
			int cz = ChunkPos.getPackedZ(key);
			world.setChunkForced(cx, cz, true);
			world.getChunk(cx, cz);
			forced++;
		}
		MineDota.LOGGER.info("Lane chunks force-loaded: {}", forced);
	}

	/** Same offset as MatchManager.creepSpawnNearT4 — keep chunks ready for spawn. */
	private static BlockPos spawnPadNearT4(DotaMap.CreepSpawn spawn, BlockPos t4) {
		int x = t4.getX();
		int z = t4.getZ();
		int forward = 4;
		return switch (spawn.lane()) {
			case TOP -> spawn.team() == DotaTeam.RADIANT
					? new BlockPos(x, t4.getY(), z - forward)
					: new BlockPos(x - forward, t4.getY(), z);
			case BOT -> spawn.team() == DotaTeam.RADIANT
					? new BlockPos(x + forward, t4.getY(), z)
					: new BlockPos(x, t4.getY(), z + forward);
			case MID -> spawn.team() == DotaTeam.RADIANT
					? new BlockPos(x + forward, t4.getY(), z - forward)
					: new BlockPos(x - forward, t4.getY(), z + forward);
		};
	}

	/** Ensure one spawn column is loaded before placing entities. */
	public static void ensureLoaded(ServerWorld world, BlockPos pos) {
		int cx = pos.getX() >> 4;
		int cz = pos.getZ() >> 4;
		world.setChunkForced(cx, cz, true);
		world.getChunk(cx, cz);
	}

	private static void add(Set<Long> keys, BlockPos pos) {
		keys.add(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4));
	}
}
