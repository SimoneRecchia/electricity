package com.dooji.electricity.api.power;

import net.minecraft.resources.ResourceLocation;

/** One class of overhead line conductor */
public record ConductorSpec(
		ResourceLocation id,
		String displayName,
		VoltageClass voltageClass,
		double crossSectionMm2,
		double maxSystemVolts,
		double ampacity,
		double ohmsPerKm,
		int subConductors,
		double bundleSpacing,
		double radius,
		int colour,
		double sag,
		double maxSpan,
		int blocksPerItem
) {
	public ConductorSpec {
		if (crossSectionMm2 <= 0.0) throw new IllegalArgumentException(id + ": a conductor has a cross-section");
		if (subConductors < 1) throw new IllegalArgumentException(id + ": a phase has at least one wire in it");
		if (radius <= 0.0) throw new IllegalArgumentException(id + ": a conductor has a thickness");
		if (sag < 0.0) throw new IllegalArgumentException(id + ": a span does not dip upwards");
		if (maxSpan <= 0.0) throw new IllegalArgumentException(id + ": a span has a length");
		if (blocksPerItem < 1) throw new IllegalArgumentException(id + ": an item buys at least one block");
	}

	/** Which structures a conductor may be strung between, and what it is allowed to reach. */
	public enum VoltageClass {
		/** Aerial bundled cable on poles: the street. */
		LOW,
		/** Bare or covered conductor on poles and kiosks: the plant's own collector. */
		MEDIUM,
		/** Bundled ACSR on lattice towers: the transmission line. */
		HIGH
	}

	/** How many items a span of this many blocks costs, rounded up: no free metre at the end. */
	public int cost(double blocks) {
		return Math.max(1, (int) Math.ceil(blocks / blocksPerItem));
	}

	/** The whole phase's resistance over a length, bundle and all. */
	public double resistanceOhms(double blocks) {
		return ohmsPerKm * blocks / 1000.0;
	}
}
