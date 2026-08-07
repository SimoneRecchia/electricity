package com.dooji.electricity.api.power;

import net.minecraft.resources.ResourceLocation;

/**
 * One transformer: what it steps between, and how much of it it will take.
 *
 * The two in the mod are the two a plant's output actually passes through - a machine unit at a
 * generator's foot and a substation unit at the grid connection - and everything different about them
 * follows from the ratio.
 */
public record TransformerSpec(
		ResourceLocation id,
		String displayName,
		Duty duty,
		/** Rating in kVA. */
		double ratingKva,
		/** Volts in and volts out, which is what makes it the machine it is. */
		double primaryVolts,
		double secondaryVolts,
		/** Full-load loss as a fraction: a transformer is the most efficient machine on a plant. */
		double lossFraction,
		/** No-load loss in kW, which it burns whether anything is drawing from it or not. */
		double coreLossKw,
		/** The model this is drawn as. */
		String modelName,
		/** How many fittings it has: three bushings on a machine unit, six on a substation one. */
		int bushings) {

	public enum Duty {
		/** Low voltage to medium: at the foot of every generator on the plant. */
		MACHINE,
		/** Medium voltage to high: the grid connection, and where a transmission line starts. */
		SUBSTATION
	}

	/** What a load of {@code kw} costs in losses, which is the core loss plus a share of the load. */
	public double lossKw(double kw) {
		return coreLossKw + Math.abs(kw) * lossFraction;
	}

	/** The ratio, for a tooltip: 0.8/33 reads better than two numbers in volts. */
	public String ratioText() {
		return String.format("%.3g/%.3g kV", primaryVolts / 1000.0, secondaryVolts / 1000.0);
	}
}
