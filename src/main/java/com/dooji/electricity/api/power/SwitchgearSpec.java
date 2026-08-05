package com.dooji.electricity.api.power;

import net.minecraft.resources.ResourceLocation;

/**
 * A switch in a line, and which of the two kinds it is.
 *
 * The distinction is the whole of it and it is not cosmetic. A <b>disconnector</b> exists to make a gap you
 * can see: two bare contacts in air with a blade between them. It cannot break load, because an arc drawn
 * across bare blades in air does not go out - so it is interlocked, and a linesman opens the breaker first.
 * A <b>circuit breaker</b> exists to break load and fault current, so its contacts part inside a sealed
 * vacuum bottle where nothing shows: all it can tell you is a mechanical flag, which is why a substation
 * needs both and in that order.
 *
 * @param ratedKw what the switch will carry: what a breaker trips above, and above which a disconnector's
 *                contacts are past the current they were built for.
 */
public record SwitchgearSpec(ResourceLocation id, String displayName, Duty duty,
		ConductorSpec.VoltageClass voltageClass, double volts, int poles, double ratedKw,
		String modelName) {

	public enum Duty {
		/** Isolates a circuit with a visible air gap, off load. */
		DISCONNECTOR,
		/** Makes and breaks load and fault current. */
		BREAKER,
	}

	/** Whether this switch may be operated with load on it. */
	public boolean breaksLoad() {
		return duty == Duty.BREAKER;
	}

	/** How many fittings it carries: one each side of every pole. */
	public int fittings() {
		return poles * 2;
	}
}
