package com.dooji.electricity.wire;

import net.minecraft.world.phys.Vec3;

/**
 * A block entity a wire can be hung from.
 *
 * Everything that wires touch asks the same four questions of a machine, and before this there was an
 * {@code instanceof} chain per question in eight files - so adding a machine meant finding all eight.
 * {@link InsulatorPartHelper} is the only place that still names a type.
 */
public interface InsulatorHost {
	/** The persistent id of each of this machine's fittings, in the order ObjDefinitions names them. */
	int[] getInsulatorIds();

	/** Where fitting {@code index} is in the world, which is where a wire ends. */
	Vec3 getWirePosition(int index);

	/**
	 * The name a saved connection stores for this kind of machine.
	 *
	 * Never change one: every wire in every existing world holds it.
	 */
	String fittingType();

	/** Whether power may flow from a fitting on this machine to one on that. */
	boolean feeds(InsulatorHost other);

	/** Whether a fitting is an output, an input, or either. */
	default String powerType(String partName) {
		return "bidirectional";
	}

	/** What the network delivered here, for a machine that shows a reading. */
	default void deliverPower(double power) {
	}
}
