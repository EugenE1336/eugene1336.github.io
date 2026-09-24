package com.minedota.worldgen;

import com.minedota.MineDota;
import com.minedota.entity.AncientEntity;
import com.minedota.entity.BarrackEntity;
import com.minedota.entity.ModEntities;
import com.minedota.entity.TowerEntity;
import com.minedota.lobby.LobbyMap;
import com.minedota.lobby.LobbySetup;
import com.minedota.map.DotaMap;
import com.minedota.match.MatchManager;
import com.minedota.team.DotaTeam;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public final class ModWorldgen {
	/** Bump when tower/barrack/ancient/base coordinates change — forces respawn. */
	public static final int TOWER_LAYOUT_VERSION = 17;

	private ModWorldgen() {
	}

	public static void register() {
		Registry.register(Registries.CHUNK_GENERATOR, new Identifier(MineDota.MOD_ID, "dota"), DotaChunkGenerator.CODEC);

		ServerWorldEvents.LOAD.register((server, world) -> {
			if (world.getRegistryKey() != World.OVERWORLD) {
				return;
			}
			if (!isDotaWorld(world)) {
				return;
			}
			DotaWorldState state = DotaWorldState.get(world);
			if (!state.isEntitiesSpawned()) {
				spawnStructures(world);
				state.setEntitiesSpawned(true);
				state.setTowerLayoutVersion(TOWER_LAYOUT_VERSION);
				state.markDirty();
			} else if (state.getTowerLayoutVersion() < TOWER_LAYOUT_VERSION) {
				clearStructures(world);
				spawnTowersOnly(world);
				spawnBarracksOnly(world);
				spawnAncient(world, DotaMap.radiantAncient(), DotaTeam.RADIANT);
				spawnAncient(world, DotaMap.direAncient(), DotaTeam.DIRE);
				DotaMap.placeTowerPedestals(world);
				DotaMap.repairBaseGates(world);
				state.setTowerLayoutVersion(TOWER_LAYOUT_VERSION);
				state.markDirty();
				MineDota.LOGGER.info("Tower/barrack/ancient layout updated to v{}", TOWER_LAYOUT_VERSION);
			}
			if (!state.isLobbyReady()) {
				world.getChunk(LobbyMap.CENTER_X >> 4, LobbyMap.CENTER_Z >> 4);
				LobbySetup.ensureLabels(world);
				state.setLobbyReady(true);
				state.markDirty();
			}
			if (!state.isDebrisScrubbed()) {
				DotaMap.scrubVanillaDebris(world);
				state.setDebrisScrubbed(true);
				state.markDirty();
				MineDota.LOGGER.info("Scrubbed vanilla lava/ores from Dota map");
			}
			if (!state.isVoidOutsideScrubbed()) {
				DotaMap.scrubOutsideToVoid(world);
				state.setVoidOutsideScrubbed(true);
				state.markDirty();
			}
			applyWorldBorder(world);
			MatchManager.get(server).activateDotaWorld(world);
			DotaMap.repairBaseGates(world);
			world.setSpawnPos(LobbyMap.SPAWN, 180.0f);
			MineDota.LOGGER.info("Dota 2 world loaded — lobby ready");
		});
	}

	/** One square: arena + lobby. Stops infinite chunk generation past the map. */
	public static void applyWorldBorder(ServerWorld world) {
		double minX = DotaMap.genMinX();
		double maxX = DotaMap.genMaxX();
		double minZ = DotaMap.genMinZ();
		double maxZ = DotaMap.genMaxZ();
		double cx = (minX + maxX) / 2.0;
		double cz = (minZ + maxZ) / 2.0;
		double size = Math.max(maxX - minX, maxZ - minZ) + 4.0;
		var border = world.getWorldBorder();
		border.setCenter(cx, cz);
		border.setSize(size);
		border.setDamagePerBlock(0.0);
		border.setSafeZone(0.0);
		border.setWarningBlocks(2);
		MineDota.LOGGER.info("World border set to map square center=({}, {}) size={}", cx, cz, size);
	}

	public static boolean isDotaWorld(World world) {
		if (!(world instanceof ServerWorld serverWorld)) {
			return false;
		}
		return serverWorld.getChunkManager().getChunkGenerator() instanceof DotaChunkGenerator;
	}

	public static void spawnStructures(ServerWorld world) {
		spawnTowersOnly(world);
		spawnBarracksOnly(world);
		spawnAncient(world, DotaMap.radiantAncient(), DotaTeam.RADIANT);
		spawnAncient(world, DotaMap.direAncient(), DotaTeam.DIRE);
	}

	private static void spawnTowersOnly(ServerWorld world) {
		for (DotaMap.TowerSpot spot : DotaMap.radiantTowerSpots()) {
			spawnTower(world, spot, DotaTeam.RADIANT);
		}
		for (DotaMap.TowerSpot spot : DotaMap.direTowerSpots()) {
			spawnTower(world, spot, DotaTeam.DIRE);
		}
	}

	private static void spawnBarracksOnly(ServerWorld world) {
		for (DotaMap.BarrackSpot spot : DotaMap.radiantBarrackSpots()) {
			spawnBarrack(world, spot, DotaTeam.RADIANT);
		}
		for (DotaMap.BarrackSpot spot : DotaMap.direBarrackSpots()) {
			spawnBarrack(world, spot, DotaTeam.DIRE);
		}
	}

	private static void clearStructures(ServerWorld world) {
		Box box = new Box(-DotaMap.HALF, 0, -DotaMap.HALF, DotaMap.HALF, 128, DotaMap.HALF);
		world.getEntitiesByClass(TowerEntity.class, box, e -> true).forEach(e -> e.discard());
		world.getEntitiesByClass(BarrackEntity.class, box, e -> true).forEach(e -> e.discard());
		world.getEntitiesByClass(AncientEntity.class, box, e -> true).forEach(e -> e.discard());
	}

	private static void spawnTower(ServerWorld world, DotaMap.TowerSpot spot, DotaTeam team) {
		BlockPos pos = spot.pos();
		TowerEntity tower = ModEntities.TOWER.create(world);
		if (tower == null) {
			return;
		}
		int y = DotaMap.surfaceY(pos.getX(), pos.getZ()) + 1;
		tower.refreshPositionAndAngles(pos.getX() + 0.5, y, pos.getZ() + 0.5,
				team == DotaTeam.RADIANT ? -45f : 135f, 0f);
		tower.setDotaTeam(team);
		tower.setTowerMeta(spot.lane(), spot.tier());
		world.spawnEntity(tower);
		tower.clearFootprint(world);
	}

	private static void spawnBarrack(ServerWorld world, DotaMap.BarrackSpot spot, DotaTeam team) {
		BlockPos pos = spot.pos();
		BarrackEntity barrack = ModEntities.BARRACK.create(world);
		if (barrack == null) {
			return;
		}
		int y = DotaMap.surfaceY(pos.getX(), pos.getZ()) + 1;
		barrack.refreshPositionAndAngles(pos.getX() + 0.5, y, pos.getZ() + 0.5,
				team == DotaTeam.RADIANT ? -45f : 135f, 0f);
		barrack.setDotaTeam(team);
		barrack.setBarrackMeta(spot.lane(), spot.kind());
		world.spawnEntity(barrack);
		barrack.clearFootprint(world);
	}

	private static void spawnAncient(ServerWorld world, BlockPos pos, DotaTeam team) {
		AncientEntity ancient = ModEntities.ANCIENT.create(world);
		if (ancient == null) {
			return;
		}
		int y = DotaMap.surfaceY(pos.getX(), pos.getZ()) + 1;
		ancient.refreshPositionAndAngles(pos.getX() + 0.5, y, pos.getZ() + 0.5,
				team == DotaTeam.RADIANT ? -45f : 135f, 0f);
		ancient.setDotaTeam(team);
		ancient.setHealth(ancient.getMaxHealth());
		world.spawnEntity(ancient);
	}
}
