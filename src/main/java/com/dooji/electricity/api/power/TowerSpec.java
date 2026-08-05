package com.dooji.electricity.api.power;

import net.minecraft.resources.ResourceLocation;

/**
 * One lattice transmission tower.
 *
 * {@code duty} is the only thing that changes the geometry; the rest is what a line built from it can do.
 */
public record TowerSpec(
		ResourceLocation id,
		String displayName,
		Duty duty,
		/** The system voltage the line is strung at, in volts. */
		double systemVolts,
		/** How many circuits the tower carries: a Donaumast is two, three phases a side. */
		int circuits,
		/** The longest span this duty will hold, in blocks. */
		double maxSpan) {

	/**
	 * What the tower is for, structurally.
	 *
	 * A line is nine suspension towers to one tension tower, with a terminal at each end - which is why
	 * the suspension one is the cheap one to craft and the terminal the expensive one.
	 */
	public enum Duty {
		/** Holds the conductor up and nothing else: vertical chains, single-braced. */
		SUSPENSION,
		/** Takes the difference between the pulls either side: horizontal chains, double-braced. */
		TENSION,
		/** Takes the whole pull of the line on one side, stayed back against it. */
		TERMINAL
	}

	public String modelName() {
		return switch (duty) {
			case SUSPENSION -> "lattice_suspension";
			case TENSION -> "lattice_tension";
			case TERMINAL -> "lattice_terminal";
		};
	}
}
