package com.dooji.electricity.api.power;

import net.minecraft.resources.ResourceLocation;

/**
 * One direct-current combiner box, described the way a combiner box datasheet describes one.
 *
 * A field combiner is the least glamorous object on a solar farm and one of the few that is genuinely
 * load-bearing: it is a weatherproof enclosure on a post at the end of a group of rows, and what is
 * inside it is a fuse for every pole of every string, a surge protector, and one switch that can take
 * the whole group off the inverter so somebody can work on it.
 *
 * <h2>Why a plant has them at all</h2>
 *
 * Two reasons, and they are both arithmetic. A string inverter's own terminals are its fusing, so a
 * small plant needs nothing else - but a central inverter has bare busbars, and strings need fuses, so
 * something has to hold them. And sixteen strings each running two hundred metres of 6 mm² lose nearly
 * three percent, where the same sixteen paralleled at the end of the row and sent down one 240 mm²
 * trunk lose one. The box pays for itself in copper.
 *
 * <h2>The four ways a string is refused</h2>
 *
 * All four are on the datasheet, and any of them can be the one that bites:
 *
 * <ul>
 * <li>the <b>insulation</b>, if the string's open-circuit voltage in the cold is above it;</li>
 * <li>the <b>fuse holders</b>, since IEC 62548 wants a string fuse at 1.4 times the string's
 *     short-circuit current - so a box whose holders stop at twenty amps cannot protect a modern
 *     high-current module however many ways it has;</li>
 * <li>the <b>output switch</b>, which is the sum of everything in the box;</li>
 * <li>and the <b>ways</b> themselves, which is the only one anybody counts by eye.</li>
 * </ul>
 */
public record CombinerSpec(
		ResourceLocation id,
		/** Model designation, e.g. {@code CB-16}. A proper noun: never translated. */
		String displayName,
		/** Fused string inputs. Two fuses each on a floating array, one per pole, but one way per string. */
		int fusedInputs,
		/** Insulation rating, volts DC. */
		double maxSystemVolts,
		/**
		 * Largest fuse the holders in this box will take, amps.
		 *
		 * A range rather than one value, because that is how a box is bought: the holder is a physical
		 * size - 10 by 38 mm gPV takes up to thirty amps, 14 by 51 up to sixty-three - and the fuse that
		 * goes in it is chosen per plant from the modules. So what a datasheet fixes is the ceiling, and
		 * a box refuses a string when the fuse that string *needs* will not fit in its holders.
		 *
		 * gPV fuses are a different animal from the ones in a consumer unit: a photovoltaic string is a
		 * current source, so a fault in one string is fed by all the others in parallel, and the fuse has
		 * to break a direct current with no zero crossing to help it.
		 */
		double fuseAmps,
		/** Rating of the output load-break switch, amps. What everything in the box adds up to. */
		double outputAmps,
		/** A Type 2 surge protector across the output. On anything sold this decade. */
		boolean surgeProtection,
		/**
		 * Per-string current monitoring.
		 *
		 * What tells an operator that string fourteen has been down since Tuesday. It also draws a
		 * couple of watts, which is why the box has a self-consumption figure at all.
		 */
		boolean stringMonitoring,
		/** Ingress protection, as the label prints it. */
		String ingressProtection,
		/** What the monitoring and the auxiliaries draw, watts. */
		double selfConsumptionW
) {
	/**
	 * Fuse rating a string needs, as a multiple of its short-circuit current.
	 *
	 * IEC 62548 puts the string fuse between 1.4 times the string's short-circuit current and the
	 * module's own maximum series fuse rating. The lower bound is the one that matters here: a fuse
	 * chosen any tighter nuisance-trips on a bright cold day, when a string makes more current than its
	 * nameplate.
	 */
	public static final double FUSE_MARGIN = 1.4;

	public CombinerSpec {
		if (fusedInputs < 1) throw new IllegalArgumentException(id + ": a combiner box combines something");
		if (maxSystemVolts <= 0.0) throw new IllegalArgumentException(id + ": insulation has a rating");
		if (fuseAmps <= 0.0) throw new IllegalArgumentException(id + ": a fuse has a rating");
		if (outputAmps < fuseAmps) throw new IllegalArgumentException(id + ": the output carries at least one string");
	}

	/** What turned a string away. All four are on this datasheet, and any of them can be the one. */
	public enum Refusal {
		NONE,
		/** The string's cold open-circuit voltage is above what the box is insulated for. */
		INSULATION,
		/** No fuse fitted here can protect a string that makes this much current. */
		FUSE,
		/** Every way is used. */
		WAYS,
		/** The output switch, or the trunk behind it, will not carry another string. */
		OUTPUT_CURRENT
	}

	/**
	 * Whether one more array's strings will go in, and if not, which limit stopped them.
	 *
	 * One method rather than the same four tests in the box and in a cabinet with a DC section fitted,
	 * because they are the same four tests: what a combiner does is the same whether it is bolted to a
	 * post or built into a machine.
	 *
	 * The order is worst-first. An insulation or fuse mismatch is a wrong-product answer and no amount of
	 * rearranging fixes it; running out of ways or amps is a this-box-is-full answer and buying another
	 * one does. Reporting the first kind in preference to the second is what makes the message useful.
	 */
	public Refusal accepts(PvArraySpec array, int stringsUsed, double currentUsed, double currentCeiling) {
		if (!insulationFits(array.stringOpenCircuitVoltage(PvArraySpec.COLDEST_DESIGN_CELL_C))) return Refusal.INSULATION;
		if (!fuseFits(array.module().isc())) return Refusal.FUSE;
		if (stringsUsed + array.strings() > fusedInputs) return Refusal.WAYS;

		double amps = array.stringCurrent(PvModuleSpec.STC_IRRADIANCE, PvModuleSpec.STC_TEMPERATURE) * array.strings();
		if (currentUsed + amps > currentCeiling) return Refusal.OUTPUT_CURRENT;

		return Refusal.NONE;
	}

	/** Whether a fuse big enough for a string of this short-circuit current will fit in the holders. */
	public boolean fuseFits(double shortCircuitAmps) {
		return fuseAmps >= FUSE_MARGIN * shortCircuitAmps;
	}

	/** Whether a string at this voltage is one the box is insulated for. */
	public boolean insulationFits(double volts) {
		return volts <= maxSystemVolts;
	}

	/**
	 * How many strings of a given current the output switch will carry.
	 *
	 * The lower of the ways and the copper, which is the same shape of answer an inverter gives about
	 * its trackers - and for the same reason: the holes in a box are cheap and the switch behind them
	 * is not.
	 */
	public int stringsAccepted(double stringAmps) {
		if (stringAmps <= 0.0) return fusedInputs;

		return Math.max(0, Math.min(fusedInputs, (int) Math.floor(outputAmps / stringAmps)));
	}

	/** Self-consumption in kW, as a negative contribution the way an inverter's night draw is. */
	public double selfConsumptionKw() {
		return selfConsumptionW / 1000.0;
	}
}
