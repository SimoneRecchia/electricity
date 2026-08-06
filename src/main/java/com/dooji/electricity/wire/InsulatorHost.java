package com.dooji.electricity.wire;

import com.dooji.electricity.api.power.ConductorSpec;
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

	/**
	 * Which internal bus a fitting is bonded to.
	  *
	 * Every fitting on one machine shares a bus, so power crosses it - that is what makes a pole a pole and
	 * a transformer a transformer. A switch is the one machine where it depends: open, its line side and its
	 * load side are two buses, and PowerNetwork then has two clusters at one position with nothing joining
	 * them. Without this a switch could not stop anything, because a cluster is keyed on the block.
	 */
	default int busOf(int index) {
		return 0;
	}

	/**
	 * Whether a conductor of this class may be strung to this fitting.
	  *
	 * A pole carries a street bundle or a medium-voltage line; a tower carries transmission and nothing
	 * else. Without this the five conductors are interchangeable, which is what makes them look like four
	 * too many - and a 400 kV quad bundle could be strung to a garden kiosk.
	 */
	default boolean takesConductor(ConductorSpec conductor, int index) {
		return true;
	}

	/** Whether a fitting is an output, an input, or either. */
	default String powerType(String partName) {
		return "bidirectional";
	}

	/** What the network delivered here, for a machine that shows a reading. */
	default void deliverPower(double power) {
	}

	/**
	 * What is flowing through this machine, in kW: the figure a power wrench reads off it.
	 *
	 * Every host already had this method - it is what {@link #deliverPower} stores - but it was not on the
	 * interface, so the wrench had to name three types and read nothing off the other eight.
	 */
	double getCurrentPower();
}
