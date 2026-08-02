package com.dooji.electricity.api.power;

import net.minecraft.resources.ResourceLocation;

/**
 * One direct-current cable, described the way a cable datasheet describes one.
 *
 * A cable is the only part of a photovoltaic plant whose behaviour depends on how far the player
 * ran it, which is what makes it worth modelling at all. Everything else here is a machine with a
 * nameplate; this is a length of copper, and its length is the whole of what it does to the power
 * passing through it.
 *
 * <h2>Two products, because a real plant uses two</h2>
 *
 * Solar cable - H1Z2Z2-K or PV1-F, tinned copper, cross-linked polyolefin, rated 1.5 kV DC - runs
 * from the strings, clipped along the racking. It is thin, because a string carries under twenty
 * amps. What comes out of a combiner box carries the sum of everything in it, so the trunk that
 * leaves one is two orders of magnitude bigger in section and cannot be terminated in a plug.
 *
 * That is why the two do not connect to each other: an MC4 connector does not take 240 mm², and one
 * rule stated once beats a page of exceptions.
 *
 * <h2>Why buried costs something</h2>
 *
 * Because a cable's rating is a thermal limit, not an electrical one, and the ground is a worse place
 * to shed heat into than moving air. IEC 60364-5-52 tabulates this as reference method D against
 * method E, and the ratio between them is {@link #buriedFactor}. A trench is tidier, is what a real
 * plant does with its trunk, and takes a fifth of the cable's capacity to do it - which is a real
 * trade rather than a cosmetic choice.
 */
public record DcCableSpec(
		ResourceLocation id,
		/** Model designation, e.g. {@code SC-6}. A proper noun: never translated. */
		String displayName,
		/** Conductor cross-section, mm². The number a cable is bought by. */
		double crossSectionMm2,
		/** Insulation rating, volts DC. 1500 on anything modern, because that is what a string reaches. */
		double maxSystemVolts,
		/**
		 * Continuous current in free air at 60 °C ambient, amps.
		 *
		 * Sixty because that is what a cable clipped under a module in the sun actually sits in, and it
		 * is the ambient every solar cable datasheet quotes its rating at. A cable rated in a 30 °C
		 * laboratory would be a third optimistic about a solar farm.
		 */
		double freeAirAmps,
		/**
		 * What fraction of that rating survives being buried.
		 *
		 * Reference method D against reference method E, which for a large section is about four fifths.
		 * Thinner cable loses less by being buried than thick cable does, because the heat it has to get
		 * rid of falls faster than its surface area.
		 */
		double buriedFactor,
		/**
		 * Resistance of one conductor at 90 °C, ohms per km.
		 *
		 * At 90 °C rather than at 20, because that is the conductor temperature the rating above is
		 * defined at and a cable working at its rating is at it. IEC 60228 tabulates 20 °C; copper's
		 * temperature coefficient turns that into this, and the difference is 27 percent - which is far
		 * too much to leave out of a loss figure.
		 */
		double ohmsPerKm,
		/**
		 * Whether this is trunk cable rather than string cable.
		 *
		 * The distinction is the termination and not the size: string cable ends in a plug and goes into
		 * an array or a tracker's terminals, trunk cable is lugged onto a busbar. It is the field that
		 * decides which end of a plant a cable belongs at.
		 */
		boolean trunk
) {
	public DcCableSpec {
		if (crossSectionMm2 <= 0.0) throw new IllegalArgumentException(id + ": a conductor has a cross-section");
		if (maxSystemVolts <= 0.0) throw new IllegalArgumentException(id + ": insulation has a rating");
		if (freeAirAmps <= 0.0) throw new IllegalArgumentException(id + ": a cable carries some current");
		if (buriedFactor <= 0.0 || buriedFactor > 1.0) throw new IllegalArgumentException(id + ": burying a cable does not improve it");
		if (ohmsPerKm <= 0.0) throw new IllegalArgumentException(id + ": copper has resistance");
	}

	/**
	 * What the cable will carry over a run that is partly buried, in amps.
	 *
	 * The buried fraction decides it and not the total length, because a cable is only as good as its
	 * worst-cooled metre: a run that dives into a trench for ten metres of a hundred is derated over
	 * the whole hundred, since that is where it will overheat.
	 */
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

	/**
	 * Power burnt in the copper as a fraction of what is passing through it.
	 *
	 * The same number as the voltage drop expressed as a fraction of the voltage, and not by
	 * coincidence: the loss is I²R and the power is VI, so the ratio is IR over V. Which is why an
	 * electrician sizing a cable talks about percent volt drop and means percent loss.
	 */
	public double lossFraction(double amps, double volts, double metres) {
		if (volts <= 0.0 || amps <= 0.0) return 0.0;

		return Math.min(1.0, voltageDrop(amps, metres) / volts);
	}
}
