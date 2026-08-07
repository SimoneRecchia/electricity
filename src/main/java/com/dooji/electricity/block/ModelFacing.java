package com.dooji.electricity.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** How a model's own frame lands in the world, and the only copy of that arithmetic. */
public final class ModelFacing {
	private ModelFacing() {
	}

	/** Quarter turns from the facing a model was authored at to the facing it is placed at. */
	public static int quarters(Direction authored, Direction facing) {
		return (authored.get2DDataValue() - facing.get2DDataValue() + 4) % 4;
	}

	/** The same turn as an angle, which is what a pose and a vector want. */
	public static float degrees(Direction authored, Direction facing) {
		return 90.0f * quarters(authored, facing);
	}

	/** A world direction read back in the model's own frame: which of the model's sides ends up pointing that way. */
	public static Direction side(Direction direction, Direction authored, Direction facing) {
		return Direction.from2DDataValue(
				(authored.get2DDataValue() + direction.get2DDataValue() - facing.get2DDataValue() + 4) % 4);
	}

	/** A point in the model's own frame, turned onto the world about the block's vertical axis. */
	public static Vec3 turned(Vec3 point, Direction authored, Direction facing) {
		return point.yRot((float) Math.toRadians(degrees(authored, facing)));
	}
}
