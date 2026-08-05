package com.dooji.electricity.api.power;

import net.minecraft.resources.ResourceLocation;

/** One direct-current cable, described the way a cable datasheet describes one. */
public record DcCableSpec(
		ResourceLocation id,
		/** Model designation. */
		String displayName,
		/** Conductor cross-section, mm². */
		double crossSectionMm2,
		/** Insulation rating, volts DC. */
		double maxSystemVolts,
		/** Continuous current in free air at 60 °C ambient, amps. */
		double freeAirAmps,
		/** What fraction of that rating survives being buried. */
		double buriedFactor,
		/** Resistance of one conductor at 90 °C, ohms per km. */
		double ohmsPerKm,
		/** Whether this is trunk cable rather than string cable. */
		boolean trunk
) {
	public DcCableSpec {
		if (crossSectionMm2 <= 0.0) throw new IllegalArgumentException(id + ": a conductor has a cross-section");
		if (maxSystemVolts <= 0.0) throw new IllegalArgumentException(id + ": insulation has a rating");
		if (freeAirAmps <= 0.0) throw new IllegalArgumentException(id + ": a cable carries some current");
		if (buriedFactor <= 0.0 || buriedFactor > 1.0) throw new IllegalArgumentException(id + ": burying a cable does not improve it");
		if (ohmsPerKm <= 0.0) throw new IllegalArgumentException(id + ": copper has resistance");
	}

	/** What the cable will carry over a run that is partly buried, in amps. */
	public double ampacity(double buriedFraction) {
		if (buriedFraction <= 0.0) return freeAirAmps;

		return freeAirAmps * buriedFactor;
	}

	/** Resistance of the round trip over a run, ohms: out along one conductor and back along the other. */
	public double loopResistance(double metres) {
		return 2.0 * ohmsPerKm / 1000.0 * Math.max(0.0, metres);
	}

	/** Volts lost over the run at a given current. */
	public double voltageDrop(double amps, double metres) {
		return Math.max(0.0, amps) * loopResistance(metres);
	}

	/** Power burnt in the copper as a fraction of what is passing through it. */
	public double lossFraction(double amps, double volts, double metres) {
		if (volts <= 0.0 || amps <= 0.0) return 0.0;

		return Math.min(1.0, voltageDrop(amps, metres) / volts);
	}
}
