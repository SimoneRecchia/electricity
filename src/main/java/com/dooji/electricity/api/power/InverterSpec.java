package com.dooji.electricity.api.power;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** One inverter, described the way an inverter datasheet describes one. */
public record InverterSpec(
		ResourceLocation id,
		/** Model designation. */
		String displayName,
		/** Continuous active power at the terminals, kW. */
		double acPowerKw,
		/** Apparent power ceiling, kVA. */
		double apparentPowerKva,
		/** Direct-current input the machine will accept, kW. */
		double maxDcPowerKw,
		int mpptCount,
		/** String terminals the machine actually has. */
		int stringInputs,
		/** Most direct current one tracker's terminals will carry together, in amps. */
		double maxCurrentPerMppt,
		/** Whether the machine has fused string terminals of its own. */
		boolean stringTerminals,
		/** Whether it has busbars a combiner box's output can be lugged onto. */
		boolean trunkTerminals,
		double mpptMinVolts,
		double mpptMaxVolts,
		double maxDcVolts,
		/** Voltage at which the machine wakes up and starts looking for a maximum power point. */
		double startupVolts,
		double peakEfficiency,
		/** Where on the load curve the peak sits, as a fraction of nameplate. */
		double peakLoadFraction,
		/** What the machine draws overnight, in watts. */
		double nightWatts,
		/** Line-to-line volts at the terminals. */
		double nominalAcVolts,
		double frequencyHz,
		/** How far the power factor can be pushed either side of unity. */
		double minPowerFactor,
		/** Ambient temperature above which the machine starts backing off, in Celsius. */
		double derateOnsetC,
		double maxAmbientC,
		Cooling cooling
) {
	/**
	 * The load points and weights of the European efficiency, from the DIN/EN convention.
	  *
	 * A desert plant would be better judged by the CEC weighting, which puts more of it at full load.
	 */
	private static final double[] EURO_LOADS = {0.05, 0.10, 0.20, 0.30, 0.50, 1.00};
	private static final double[] EURO_WEIGHTS = {0.03, 0.06, 0.13, 0.10, 0.48, 0.20};

	public enum Cooling {
		/** No moving parts. */
		NATURAL("natural"),
		/** Temperature-controlled fans, which is what the cabinet temperature reading is for. */
		FAN("fan"),
		/** A duct and a blower, on a machine too big to cool through its own skin. */
		FORCED_AIR("forced_air");

		private final String key;

		Cooling(String key) {
			this.key = key;
		}

		public String key() {
			return key;
		}
	}

	public InverterSpec {
		if (acPowerKw <= 0.0) throw new IllegalArgumentException(id + ": rated power must be positive");
		if (apparentPowerKva < acPowerKw) throw new IllegalArgumentException(id + ": apparent power cannot be under the active nameplate");
		if (maxDcPowerKw < acPowerKw) throw new IllegalArgumentException(id + ": an inverter accepts at least its own nameplate in DC");
		if (mpptCount < 1) throw new IllegalArgumentException(id + ": an inverter has at least one maximum power point tracker");
		if (stringInputs < mpptCount) throw new IllegalArgumentException(id + ": every tracker needs at least one string terminal");
		if (maxCurrentPerMppt <= 0.0) throw new IllegalArgumentException(id + ": a tracker's terminals carry some current");
		if (!stringTerminals && !trunkTerminals) throw new IllegalArgumentException(id + ": direct current has to get in somehow");
		if (mpptMaxVolts <= mpptMinVolts) throw new IllegalArgumentException(id + ": invalid MPPT window");
		if (maxDcVolts < mpptMaxVolts) throw new IllegalArgumentException(id + ": the MPPT window has to fit inside the input rating");
		if (peakEfficiency <= 0.0 || peakEfficiency >= 1.0) throw new IllegalArgumentException(id + ": efficiency is a fraction under one");
		if (peakLoadFraction <= 0.0 || peakLoadFraction > 1.0) throw new IllegalArgumentException(id + ": the peak sits somewhere on the load curve");
		if (minPowerFactor <= 0.0 || minPowerFactor > 1.0) throw new IllegalArgumentException(id + ": power factor is a fraction");
		if (maxAmbientC <= derateOnsetC) throw new IllegalArgumentException(id + ": derating needs a band to happen over");
	}

	// ---- the efficiency curve ----

	/** Half the total loss at the peak, as a fraction of nameplate. */
	private double lossScale() {
		return (1.0 / peakEfficiency - 1.0) / 2.0;
	}

	/** Fixed loss, as a fraction of nameplate: everything that runs whether power is passing or not. */
	public double noLoadLossFraction() {
		return lossScale() * peakLoadFraction;
	}

	/** Resistive loss coefficient: this times the square of the load fraction is the conduction loss. */
	public double resistiveLossFraction() {
		return lossScale() / peakLoadFraction;
	}

	/** Efficiency at a fraction of nameplate output. */
	public double efficiencyAt(double loadFraction) {
		if (loadFraction <= 0.0) return 0.0;

		double load = Math.min(loadFraction, 1.0);
		double losses = noLoadLossFraction() + resistiveLossFraction() * load * load;
		return load / (load + losses);
	}

	/** European efficiency: the curve above, averaged at the six standard load points. */
	public double europeanEfficiency() {
		double total = 0.0;
		for (int i = 0; i < EURO_LOADS.length; i++) {
			total += EURO_WEIGHTS[i] * efficiencyAt(EURO_LOADS[i]);
		}

		return total;
	}

	// ---- what comes out ----

	/** What the machine would put out if nothing were holding it back, in kW. */
	public double unclippedAcKw(double dcKw) {
		if (dcKw <= 0.0) return 0.0;

		double estimate = dcKw * peakEfficiency;
		double refined = dcKw * efficiencyAt(estimate / acPowerKw);
		return dcKw * efficiencyAt(refined / acPowerKw);
	}

	/** Active power at the terminals, held under both the nameplate and whatever the heat allows. */
	public double acPowerFromDcKw(double dcKw, double thermalLimitKw) {
		return Math.min(unclippedAcKw(dcKw), Math.min(acPowerKw, Math.max(0.0, thermalLimitKw)));
	}

	/** Whether the array is offering more than the machine can pass. */
	public boolean clipping(double dcKw, double thermalLimitKw) {
		if (dcKw <= 0.0) return false;

		return unclippedAcKw(dcKw) > Math.min(acPowerKw, Math.max(0.0, thermalLimitKw)) + 1.0e-6;
	}

	/** Fraction of nameplate the machine will still pass at the top of its ambient range. */
	private static final double DERATED_FRACTION_AT_LIMIT = 0.5;

	/** The output ceiling at a given ambient temperature, in kW. */
	public double thermalLimitKw(double ambientC) {
		if (ambientC <= derateOnsetC) return acPowerKw;
		if (ambientC >= maxAmbientC) return acPowerKw * DERATED_FRACTION_AT_LIMIT;

		double through = (ambientC - derateOnsetC) / (maxAmbientC - derateOnsetC);
		return acPowerKw * (1.0 - through * (1.0 - DERATED_FRACTION_AT_LIMIT));
	}

	/** Whether the machine is holding itself back for heat rather than for anything the array did. */
	public boolean derating(double ambientC) {
		return ambientC > derateOnsetC;
	}

	/** Cabinet temperature at a given ambient and load. */
	public double cabinetTemperature(double ambientC, double loadFraction) {
		double load = Mth.clamp(loadFraction, 0.0, 1.0);
		double lossFraction = noLoadLossFraction() + resistiveLossFraction() * load * load;
		double riseAtFullLoad = switch (cooling) {
			case NATURAL -> 45.0;
			case FAN -> 30.0;
			case FORCED_AIR -> 25.0;
		};

		double fullLoadLoss = noLoadLossFraction() + resistiveLossFraction();
		return ambientC + riseAtFullLoad * lossFraction / Math.max(1.0e-9, fullLoadLoss);
	}

	/** Nominal DC-to-AC ratio the machine is sold at: what an array this inverter is meant for looks like. */
	public double nominalDcAcRatio() {
		return maxDcPowerKw / acPowerKw;
	}

	/** String terminals on one tracker: the terminal count shared out */
	public int inputsPerMppt() {
		return Math.max(1, stringInputs / mpptCount);
	}

	/** How many strings of a given current one tracker will actually take. */
	public int stringsPerMppt(double stringCurrentAmps) {
		if (stringCurrentAmps <= 0.0) return inputsPerMppt();

		return Math.max(1, Math.min(inputsPerMppt(), (int) Math.floor(maxCurrentPerMppt / stringCurrentAmps)));
	}

	/** Strings of a given current the whole machine will take, across all its trackers. */
	public int stringCapacity(double stringCurrentAmps) {
		return mpptCount * stringsPerMppt(stringCurrentAmps);
	}

	/** Whether a string at this voltage is one the machine can actually track. */
	public boolean withinMpptWindow(double volts) {
		return volts >= mpptMinVolts && volts <= mpptMaxVolts;
	}

	// ---- the AC side ----

	/** Apparent power for a given active output and power factor, kVA. */
	public double apparentKva(double activeKw, double powerFactor) {
		double pf = Mth.clamp(Math.abs(powerFactor), minPowerFactor, 1.0);
		return Math.min(apparentPowerKva, Math.max(0.0, activeKw) / pf);
	}

	/** Reactive power that a given active output at a given power factor implies, kvar. */
	public double reactiveKvar(double activeKw, double powerFactor) {
		double kva = apparentKva(activeKw, powerFactor);
		double squared = kva * kva - activeKw * activeKw;
		return squared <= 0.0 ? 0.0 : Math.sqrt(squared);
	}

	/** Phase current for a given apparent power, in amps. */
	public double phaseCurrentA(double kva) {
		if (nominalAcVolts <= 0.0) return 0.0;

		return kva * 1000.0 / (Math.sqrt(3.0) * nominalAcVolts);
	}

	/** Line-to-neutral volts, which is what a single-phase reading off a three-phase machine is. */
	public double phaseVolts() {
		return nominalAcVolts / Math.sqrt(3.0);
	}

	/** Night self-consumption in kW, as a negative output. */
	public double nightDrawKw() {
		return nightWatts / 1000.0;
	}
}
