package com.dooji.electricity.api.power;

import net.minecraft.resources.ResourceLocation;

/** One direct-current combiner box, described the way a combiner box datasheet describes one. */
public record CombinerSpec(
		ResourceLocation id,
		/** Model designation. */
		String displayName,
		/** Fused string inputs. */
		int fusedInputs,
		/** Insulation rating, volts DC. */
		double maxSystemVolts,
		/** Largest fuse the holders in this box will take, amps. */
		double fuseAmps,
		/** Rating of the output load-break switch, amps. */
		double outputAmps,
		/** A Type 2 surge protector across the output. */
		boolean surgeProtection,
		/** Per-string current monitoring. */
		boolean stringMonitoring,
		/** Ingress protection */
		String ingressProtection,
		/** What the monitoring and the auxiliaries draw, watts. */
		double selfConsumptionW
) {
	/** Fuse rating a string needs, as a multiple of its short-circuit current. */
	public static final double FUSE_MARGIN = 1.4;

	public CombinerSpec {
		if (fusedInputs < 1) throw new IllegalArgumentException(id + ": a combiner box combines something");
		if (maxSystemVolts <= 0.0) throw new IllegalArgumentException(id + ": insulation has a rating");
		if (fuseAmps <= 0.0) throw new IllegalArgumentException(id + ": a fuse has a rating");
		if (outputAmps < fuseAmps) throw new IllegalArgumentException(id + ": the output carries at least one string");
	}

	/** What turned a string away. */
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

	/** Whether one more array's strings will go in, and if not, which limit stopped them. */
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

	/** Self-consumption in kW, as a negative contribution the way an inverter's night draw is. */
	public double selfConsumptionKw() {
		return selfConsumptionW / 1000.0;
	}
}
