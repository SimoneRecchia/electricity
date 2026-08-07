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
	 * Whether power passes through this machine rather than stopping at it: a switch, and nothing else.
	 *
	 * Every machine's {@link #feeds} is a list of the machine types it may send to, and the switches are on
	 * none of those eight lists, because they were added to the mod after all of them were written. So
	 * nothing could send power *to* a disconnector or a breaker: a switch put in a line silently blocked it,
	 * and the machinery that makes a switch a switch - {@link #busOf}, the refusal to open under load, the
	 * trip - could never engage, because no power ever arrived.
	 *
	 * A switch has no role of its own; it takes the role of the line it is in, which is why it cannot be on a
	 * list of roles. What may reach it is settled by {@link #takesConductor}: a span to a switch can only be
	 * strung at the switch's own voltage, so only the machines at that voltage can be at the other end of it.
	 */
	default boolean passesThrough() {
		return false;
	}

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
