package com.minedota.hero;

import net.minecraft.util.math.Vec3d;

/**
 * Shared cone / facing helpers for abilities and tree-grab cleave.
 */
public final class AbilityGeometry {
	private AbilityGeometry() {
	}

	/** Full cone angle in degrees (e.g. 60 → ±30 from look). Horizontal only. */
	public static boolean inHorizontalCone(Vec3d origin, Vec3d lookFlat, Vec3d point, double range, double fullAngleDeg) {
		Vec3d flatLook = new Vec3d(lookFlat.x, 0, lookFlat.z);
		if (flatLook.lengthSquared() < 1e-8) {
			return false;
		}
		flatLook = flatLook.normalize();
		Vec3d to = new Vec3d(point.x - origin.x, 0, point.z - origin.z);
		double distSq = to.lengthSquared();
		if (distSq > range * range) {
			return false;
		}
		if (distSq < 1e-6) {
			return true;
		}
		double cos = flatLook.dotProduct(to.normalize());
		double need = Math.cos(Math.toRadians(fullAngleDeg * 0.5));
		return cos >= need - 1e-6;
	}

	public static Vec3d lookFlat(net.minecraft.entity.LivingEntity entity) {
		Vec3d look = entity.getRotationVec(1f);
		Vec3d flat = new Vec3d(look.x, 0, look.z);
		if (flat.lengthSquared() < 1e-8) {
			float yaw = entity.getYaw() * ((float) Math.PI / 180f);
			flat = new Vec3d(-net.minecraft.util.math.MathHelper.sin(yaw), 0,
					net.minecraft.util.math.MathHelper.cos(yaw));
		}
		return flat.normalize();
	}
}
