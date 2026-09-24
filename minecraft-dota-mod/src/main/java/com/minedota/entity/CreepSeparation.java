package com.minedota.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Soft separation so lane creeps don't stack in one block while attacking a tower.
 */
public final class CreepSeparation {
	private static final double MIN_DIST = 0.9;
	private static final double STRENGTH = 0.28;

	private CreepSeparation() {
	}

	public static void tick(PathAwareEntity creep) {
		if (creep.getWorld().isClient || !creep.isAlive()) {
			return;
		}
		Box box = creep.getBoundingBox().expand(0.55);
		var others = creep.getWorld().getOtherEntities(creep, box, CreepSeparation::isLaneCreep);
		if (others.isEmpty()) {
			return;
		}
		double px = 0;
		double pz = 0;
		int n = 0;
		for (Entity other : others) {
			double dx = creep.getX() - other.getX();
			double dz = creep.getZ() - other.getZ();
			double len2 = dx * dx + dz * dz;
			if (len2 > MIN_DIST * MIN_DIST) {
				continue;
			}
			double len = Math.sqrt(len2);
			if (len < 1e-3) {
				float yaw = creep.getYaw() * ((float) Math.PI / 180f);
				dx = -MathHelper.sin(yaw + n);
				dz = MathHelper.cos(yaw + n);
				len = 1;
			}
			double push = (MIN_DIST - len) * STRENGTH;
			px += dx / len * push;
			pz += dz / len * push;
			n++;
		}
		if (n == 0) {
			return;
		}
		Vec3d v = creep.getVelocity();
		creep.setVelocity(v.x + px, v.y, v.z + pz);
		creep.velocityModified = true;
	}

	private static boolean isLaneCreep(Entity e) {
		return e instanceof CreepEntity || e instanceof RangedCreepEntity;
	}

	/** Vanilla entity push — only between lane creeps. */
	public static void pushAwayIfCreep(PathAwareEntity self, Entity other) {
		if (!isLaneCreep(other)) {
			return;
		}
		self.pushAwayFrom(other);
	}
}
