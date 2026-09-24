package com.minedota.entity;

import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * If a lane creep barely moves for 3+ seconds while trying to go somewhere,
 * sidestep / push around towers and skip a waypoint.
 */
public final class CreepStuckAssist {
	public static final int STUCK_SECONDS = 3;

	private CreepStuckAssist() {
	}

	public static final class State {
		public double sampleX = Double.NaN;
		public double sampleZ = Double.NaN;
		public int stuckSec;
	}

	/** Call once per tick from creep. {@code wantsMove} true when following path / not busy fighting a damageable target. */
	public static void tick(PathAwareEntity creep, State state, boolean wantsMove, Runnable onBypass) {
		if (creep.getWorld().isClient || !creep.isAlive()) {
			return;
		}
		// Sample once per second
		if (creep.age % 20 != 0) {
			return;
		}
		if (Double.isNaN(state.sampleX)) {
			state.sampleX = creep.getX();
			state.sampleZ = creep.getZ();
			state.stuckSec = 0;
			return;
		}
		double dx = creep.getX() - state.sampleX;
		double dz = creep.getZ() - state.sampleZ;
		state.sampleX = creep.getX();
		state.sampleZ = creep.getZ();
		if (!wantsMove) {
			state.stuckSec = 0;
			return;
		}
		if (dx * dx + dz * dz < 0.09) { // <0.3 blocks/s
			state.stuckSec++;
		} else {
			state.stuckSec = 0;
		}
		if (state.stuckSec >= STUCK_SECONDS) {
			state.stuckSec = 0;
			pushClearOfNearbySolids(creep);
			trySidestep(creep);
			if (onBypass != null) {
				onBypass.run();
			}
		}
	}

	/** Nudge off towers / barracks / ancients within ~3 blocks when stuck. */
	private static void pushClearOfNearbySolids(PathAwareEntity creep) {
		Box box = creep.getBoundingBox().expand(3.0);
		for (TowerEntity t : creep.getWorld().getEntitiesByClass(TowerEntity.class, box, e -> true)) {
			pushAwayFrom(creep, t.getX(), t.getZ(), 2.8);
		}
		for (BarrackEntity b : creep.getWorld().getEntitiesByClass(BarrackEntity.class, box, e -> true)) {
			pushAwayFrom(creep, b.getX(), b.getZ(), 2.8);
		}
		for (AncientEntity a : creep.getWorld().getEntitiesByClass(AncientEntity.class, box, e -> true)) {
			pushAwayFrom(creep, a.getX(), a.getZ(), 3.2);
		}
	}

	/** Radial push away from a tower/entity center to ~2.4 blocks. */
	public static void pushAwayFrom(PathAwareEntity creep, double ox, double oz, double minDist) {
		double dx = creep.getX() - ox;
		double dz = creep.getZ() - oz;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len >= minDist) {
			return;
		}
		if (len < 0.05) {
			float yaw = creep.getYaw() * ((float) Math.PI / 180f);
			dx = -MathHelper.sin(yaw);
			dz = MathHelper.cos(yaw);
			len = 1;
		}
		creep.refreshPositionAndAngles(
				ox + dx / len * Math.max(minDist, 2.4),
				creep.getY(),
				oz + dz / len * Math.max(minDist, 2.4),
				creep.getYaw(), creep.getPitch());
		creep.getNavigation().stop();
	}

	private static boolean trySidestep(PathAwareEntity creep) {
		World world = creep.getWorld();
		Vec3d look = creep.getRotationVector();
		double fx = look.x;
		double fz = look.z;
		double fl = Math.sqrt(fx * fx + fz * fz);
		if (fl < 1e-4) {
			float yaw = creep.getYaw() * ((float) Math.PI / 180f);
			fx = -MathHelper.sin(yaw);
			fz = MathHelper.cos(yaw);
			fl = 1;
		}
		fx /= fl;
		fz /= fl;
		double px = -fz;
		double pz = fx;
		double[][] tries = {
				{px * 3.0, pz * 3.0},
				{-px * 3.0, -pz * 3.0},
				{px * 4.5 + fx * 1.5, pz * 4.5 + fz * 1.5},
				{-px * 4.5 + fx * 1.5, -pz * 4.5 + fz * 1.5},
				{px * 5.5, pz * 5.5},
				{-px * 5.5, -pz * 5.5},
				{fx * 3.0, fz * 3.0},
				{fx * 4.0 + px * 2.0, fz * 4.0 + pz * 2.0},
				{fx * 4.0 - px * 2.0, fz * 4.0 - pz * 2.0}
		};
		for (double[] d : tries) {
			double nx = creep.getX() + d[0];
			double nz = creep.getZ() + d[1];
			if (canStand(world, nx, creep.getY(), nz)) {
				creep.refreshPositionAndAngles(nx, creep.getY(), nz, creep.getYaw(), creep.getPitch());
				creep.getNavigation().stop();
				return true;
			}
		}
		return false;
	}

	private static boolean canStand(World world, double x, double y, double z) {
		BlockPos feet = BlockPos.ofFloored(x, y, z);
		BlockPos below = feet.down();
		BlockPos head = feet.up();
		BlockState floor = world.getBlockState(below);
		BlockState at = world.getBlockState(feet);
		BlockState up = world.getBlockState(head);
		if (floor.isAir() || !floor.getFluidState().isEmpty()) {
			return false;
		}
		if (at.shouldSuffocate(world, feet) || up.shouldSuffocate(world, head)) {
			return false;
		}
		return at.isAir() || !at.blocksMovement();
	}
}
