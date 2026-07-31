package com.dooji.electricity.client.wire;

import net.minecraft.world.phys.Vec3;

public class WirePhysics {
	public static final float DEFLECTION_COEFFICIENT = 0.005f;
	public static final float LENGTH_COEFFICIENT = 0.02f;
	public static final float WIRE_RADIUS = 0.025f;
	public static boolean shouldUseDeflection(Vec3 start, Vec3 end) {
		Vec3 direction = end.subtract(start);
		double horizontalDistance = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
		return horizontalDistance > 0.1;
	}

	public static float getYaw(Vec3 vec) {
		return (float) Math.toDegrees(Math.atan2(vec.x, vec.z));
	}

	public static float getPitch(Vec3 vec) {
		double xz = Math.sqrt(vec.x * vec.x + vec.z * vec.z);
		return (float) Math.toDegrees(Math.atan2(vec.y, xz));
	}
}
