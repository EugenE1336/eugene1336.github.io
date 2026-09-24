package com.minedota.map;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Shared single-tree chop used by Tango and Tiny Tree Grab.
 * One trunk column + that tree's leaves (not a BFS jungle wipe).
 */
public final class DotaTrees {
	private DotaTrees() {
	}

	/**
	 * Destroy one tree at / near {@code pos}.
	 * @return true if a trunk was found and removed
	 */
	public static boolean eatOneTree(World world, BlockPos pos, PlayerEntity playerOrNull) {
		BlockPos trunk = findTrunk(world, pos);
		if (trunk == null) {
			return false;
		}
		int tx = trunk.getX();
		int tz = trunk.getZ();
		int minY = trunk.getY();
		int maxTrunkY = minY;
		for (int y = minY; y <= minY + 8; y++) {
			BlockPos p = new BlockPos(tx, y, tz);
			if (isLog(world.getBlockState(p))) {
				// setBlockState — без звука ломания на каждый блок (иначе оглушительный стек)
				world.setBlockState(p, Blocks.AIR.getDefaultState(), 3);
				maxTrunkY = y;
			}
		}
		int leafR = (maxTrunkY - minY) >= 4 ? 3 : 1;
		int leafTop = maxTrunkY + 3;
		for (BlockPos p : BlockPos.iterate(
				new BlockPos(tx - leafR, minY, tz - leafR),
				new BlockPos(tx + leafR, leafTop, tz + leafR))) {
			if (isLeaves(world.getBlockState(p))) {
				world.setBlockState(p, Blocks.AIR.getDefaultState(), 3);
			}
		}
		// Один тихий звук «съел дерево» (мастер 2% не должен орать)
		world.playSound(null, trunk, SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 0.08f, 1.25f);
		world.playSound(null, trunk, SoundEvents.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS, 0.06f, 1.15f);
		return true;
	}

	public static BlockPos findTrunk(World world, BlockPos pos) {
		if (isLog(world.getBlockState(pos))) {
			return pos.toImmutable();
		}
		BlockPos best = null;
		int bestDist = Integer.MAX_VALUE;
		for (BlockPos p : BlockPos.iterate(pos.add(-2, -3, -2), pos.add(2, 1, 2))) {
			if (!isLog(world.getBlockState(p))) {
				continue;
			}
			int d = Math.abs(p.getX() - pos.getX()) + Math.abs(p.getY() - pos.getY()) + Math.abs(p.getZ() - pos.getZ());
			if (d < bestDist) {
				bestDist = d;
				best = p.toImmutable();
			}
		}
		return best;
	}

	public static boolean isLog(BlockState state) {
		return state.isOf(Blocks.OAK_LOG) || state.isOf(Blocks.BIRCH_LOG) || state.isOf(Blocks.SPRUCE_LOG)
				|| state.isOf(Blocks.JUNGLE_LOG) || state.isOf(Blocks.ACACIA_LOG) || state.isOf(Blocks.DARK_OAK_LOG)
				|| state.isOf(Blocks.MANGROVE_LOG) || state.isOf(Blocks.CHERRY_LOG);
	}

	public static boolean isLeaves(BlockState state) {
		return state.isOf(Blocks.OAK_LEAVES) || state.isOf(Blocks.BIRCH_LEAVES) || state.isOf(Blocks.SPRUCE_LEAVES)
				|| state.isOf(Blocks.JUNGLE_LEAVES) || state.isOf(Blocks.ACACIA_LEAVES) || state.isOf(Blocks.DARK_OAK_LEAVES)
				|| state.isOf(Blocks.MANGROVE_LEAVES) || state.isOf(Blocks.CHERRY_LEAVES);
	}
}
