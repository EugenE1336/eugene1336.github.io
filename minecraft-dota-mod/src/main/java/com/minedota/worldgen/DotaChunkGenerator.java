package com.minedota.worldgen;

import com.minedota.map.DotaMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.structure.StructureSet;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class DotaChunkGenerator extends ChunkGenerator {
	public static final Codec<DotaChunkGenerator> CODEC = RecordCodecBuilder.create(instance ->
			instance.group(
					BiomeSource.CODEC.fieldOf("biome_source").forGetter(DotaChunkGenerator::getBiomeSource)
			).apply(instance, DotaChunkGenerator::new)
	);

	public DotaChunkGenerator(BiomeSource biomeSource) {
		super(biomeSource);
	}

	@Override
	protected Codec<? extends ChunkGenerator> getCodec() {
		return CODEC;
	}

	@Override
	public StructurePlacementCalculator createStructurePlacementCalculator(
			RegistryWrapper<StructureSet> structureSetRegistry, NoiseConfig noiseConfig, long seed) {
		return StructurePlacementCalculator.create(noiseConfig, seed, biomeSource, java.util.stream.Stream.empty());
	}

	@Override
	public void carve(ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess,
			StructureAccessor structureAccessor, Chunk chunk, GenerationStep.Carver carverStep) {
		// no caves / canyons
	}

	@Override
	public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) {
		// surface already in populateNoise
	}

	@Override
	public void generateFeatures(StructureWorldAccess world, Chunk chunk, StructureAccessor structureAccessor) {
		// NO vanilla biome features (lava lakes, ores, geodes, vegetation…)
	}

	@Override
	public void populateEntities(ChunkRegion region) {
		// no vanilla mobs
	}

	@Override
	public int getWorldHeight() {
		return 128;
	}

	@Override
	public int getSeaLevel() {
		return DotaMap.WALK_Y;
	}

	@Override
	public int getMinimumY() {
		return 0;
	}

	@Override
	public int getSpawnHeight(HeightLimitView world) {
		return DotaMap.WALK_Y;
	}

	@Override
	public CompletableFuture<Chunk> populateNoise(Executor executor, Blender blender, NoiseConfig noiseConfig,
			StructureAccessor structureAccessor, Chunk chunk) {
		ChunkPos chunkPos = chunk.getPos();
		// Far void: skip writing 16×16×128 — leave default air (huge load win)
		if (!DotaMap.chunkTouchesGenerated(chunkPos.x, chunkPos.z)) {
			return CompletableFuture.completedFuture(chunk);
		}

		BlockPos.Mutable mutable = new BlockPos.Mutable();
		Heightmap ocean = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
		Heightmap surface = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);

		int minY = getMinimumY();
		int maxY = minY + getWorldHeight();

		for (int lx = 0; lx < 16; lx++) {
			for (int lz = 0; lz < 16; lz++) {
				int x = chunkPos.getStartX() + lx;
				int z = chunkPos.getStartZ() + lz;
				for (int y = minY; y < maxY; y++) {
					BlockState state = DotaMap.getBlock(x, y, z);
					chunk.setBlockState(mutable.set(x, y, z), state, false);
					ocean.trackUpdate(lx, y, lz, state);
					surface.trackUpdate(lx, y, lz, state);
				}
			}
		}
		return CompletableFuture.completedFuture(chunk);
	}

	@Override
	public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
		if (!DotaMap.isGeneratedColumn(x, z)) {
			return getMinimumY();
		}
		return DotaMap.surfaceY(x, z) + 1;
	}

	@Override
	public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
		BlockState[] states = new BlockState[getWorldHeight()];
		for (int i = 0; i < states.length; i++) {
			states[i] = DotaMap.getBlock(x, getMinimumY() + i, z);
		}
		return new VerticalBlockSample(getMinimumY(), states);
	}

	@Override
	public void getDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
		text.add("MineDota map");
	}
}
