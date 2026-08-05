package com.dooji.electricity.api.power;

import net.minecraft.resources.ResourceLocation;

/**
 * One class of overhead line conductor, and everything that follows from which class it is.
 *
 * <h2>Why there are three and not one</h2>
 *
 * Because a real line uses a different conductor at every voltage, and the differences are not a matter
 * of gauge - they are different products doing different jobs.
 *
 * At <b>low voltage</b> a distributor runs <i>aerial bundled cable</i>: the phases are individually
 * insulated and twisted together round a bare messenger that carries the weight, so the whole thing is
 * one black bundle a hand's width across. It can be strung close to buildings and touch a tree without
 * consequence, which is exactly why it replaced bare wire on the street.
 *
 * At <b>medium voltage</b> it is one <i>bare or covered</i> aluminium-alloy conductor per phase, hung a
 * metre apart on pin insulators. Bare, because at 20 kV air is a cheaper insulator than polymer; covered
 * where the line runs through trees.
 *
 * At <b>high voltage</b> it is <i>aluminium conductor steel-reinforced</i>, and above about 220 kV each
 * phase is a <i>bundle</i> of two, three or four sub-conductors held apart by spacers. Not for current
 * capacity - for corona: one thick conductor has a field at its surface high enough to ionise the air,
 * and several thinner ones spread over a wider circle do not. Which is why a 400 kV line looks like it
 * has twelve wires on it and has three circuits' worth of phases.
 *
 * <h2>What the mod does with each figure</h2>
 *
 * The electrical ones feed the loss calculation the same way {@link DcCableSpec}'s do. The drawing ones
 * are here rather than in the renderer because a conductor's appearance <i>is</i> its specification: a
 * bundle of four is four wires because it is a bundle of four.
 *
 * @param id                the registry path, which is also the item and the sprite name
 * @param displayName       what a player sees, which is the real designation
 * @param voltageClass      LOW, MEDIUM or HIGH: which structures it may be strung between
 * @param crossSectionMm2   the aluminium area of one sub-conductor
 * @param maxSystemVolts    what its insulation - or its air clearance - is rated for
 * @param ampacity          what one sub-conductor carries in free air at 40 degrees
 * @param ohmsPerKm         direct-current resistance of the whole phase, bundle included
 * @param subConductors     how many wires make up one phase
 * @param bundleSpacing     how far apart they are held, in blocks
 * @param radius            one sub-conductor's drawn radius, in blocks
 * @param colour            packed 0xRRGGBB, which is grey for aluminium and black for insulated bundle
 * @param sag               how far a span dips at its middle, as a fraction of its length
 * @param maxSpan           the longest span it may be strung, in blocks
 * @param blocksPerItem     how many blocks of span one item buys
 */
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
