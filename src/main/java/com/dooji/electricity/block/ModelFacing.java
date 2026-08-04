package com.dooji.electricity.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * How a model's own frame lands in the world, and the only copy of that arithmetic.
 *
 * A machine's geometry is modelled facing one way and placed facing another, so everything that reads the
 * model has to turn it: the renderer turns the pose, each block entity turns the points its wires hang
 * from, and {@link MachineShell} turns the cells its collision is cut into. Three kinds of code, one
 * quarter turn, and it used to be worked out separately in each of them - which is exactly how the
 * collision came to stand at a right angle to the electric cabin for as long as it existed.
 *
 * So the facing a model was authored at is declared once, as {@code AUTHORED} on its block, and the turn
 * is computed once, here. Anticlockwise seen from above, which is what {@code Axis.YP} does with a
 * positive angle and what {@link Vec3#yRot} does with a positive one.
 */
public final class ModelFacing {
	private ModelFacing() {
	}

	/**
	 * Quarter turns from the facing a model was authored at to the facing it is placed at.
	 *
	 * The game numbers the horizontal facings south, west, north, east, and the difference between two of
	 * those numbers is the number of quarter turns between them. That is the whole trick.
	 */
	public static int quarters(Direction authored, Direction facing) {
		return (authored.get2DDataValue() - facing.get2DDataValue() + 4) % 4;
	}

	/** The same turn as an angle, which is what a pose and a vector want. */
	public static float degrees(Direction authored, Direction facing) {
		return 90.0f * quarters(authored, facing);
	}

	/**
	 * A world direction read back in the model's own frame: which of the model's sides ends up pointing
	 * that way.
	 *
	 * The turn undone rather than applied, which is what naming a group takes: a machine's four cable
	 * entries are named for the model's own sides, and what has to be found is the one that will be
	 * facing the copper once the block is turned.
	 */
	public static Direction side(Direction direction, Direction authored, Direction facing) {
		return Direction.from2DDataValue(
				(authored.get2DDataValue() + direction.get2DDataValue() - facing.get2DDataValue() + 4) % 4);
	}

	/** A point in the model's own frame, turned onto the world about the block's vertical axis. */
	public static Vec3 turned(Vec3 point, Direction authored, Direction facing) {
		return point.yRot((float) Math.toRadians(degrees(authored, facing)));
	}
}
