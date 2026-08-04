package com.dooji.electricity.api.power;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * One inverter, described the way an inverter datasheet describes one.
 *
 * The inverter is where a photovoltaic plant becomes an electrical machine rather than a field of
 * glass: it is the thing with a nameplate, the thing that clips, the thing that derates when its
 * cabinet gets hot, the thing the grid negotiates a power factor with, and the thing a plant's
 * SCADA actually talks to. The arrays report through it.
 *
 * <h2>The efficiency curve</h2>
 *
 * Two losses and nothing else, which is all a real curve has in it: a fixed one that does not
 * care how much power is passing - gate drivers, control electronics, fans, the magnetising
 * current in the filter - and a resistive one that grows with the square of the current.
 * {@link #efficiencyAt} is those two written down, and everything else about the curve follows
 * from them: the steep climb out of nothing at first light, the flat top, and the slight fall at
 * full load that makes the peak sit near half power rather than at the end.
 *
 * That is also why the European efficiency is {@link #europeanEfficiency() derived} rather than
 * declared beside the peak. It is not an independent fact - it is the same curve averaged with
 * the standard weights - so declaring it would be a second number free to drift away from the
 * first. It comes out a tenth or two under the published figure, because a modern multi-level
 * bridge is flatter at high load than two losses can express, and that is a better error than a
 * curve which agrees with a printed average while having the wrong shape.
 */
public record InverterSpec(
		ResourceLocation id,
		/** Model designation, e.g. {@code HX-110}. A proper noun: never translated. */
		String displayName,
		/** Continuous active power at the terminals, kW. The nameplate, and where clipping happens. */
		double acPowerKw,
		/**
		 * Apparent power ceiling, kVA.
		 *
		 * Above the active nameplate on every real machine, by ten percent or so, because the grid
		 * asks for reactive power as well and an inverter that could only make its rated kW would
		 * have to give up real output to supply any.
		 */
		double apparentPowerKva,
		/**
		 * Direct-current input the machine will accept, kW.
		 *
		 * Half again the AC nameplate on a real string inverter, which is not generosity - it is
		 * the vendor stating what an economically sized array looks like. Utility plant is built at
		 * a DC-to-AC ratio of 1.3 to 1.55 on purpose, accepting that the best few hours of the best
		 * days get clipped, because the alternative is an inverter that idles at a third load all
		 * winter.
		 */
		double maxDcPowerKw,
		int mpptCount,
		/**
		 * String terminals the machine actually has.
		 *
		 * Declared rather than derived from the tracker count, because the relation between the two is
		 * not one relation. A string inverter has two or three terminals on each tracker, so a
		 * nine-tracker machine takes eighteen strings. A central inverter has *one* tracker and several
		 * hundred strings, because they arrive through combiner boxes aggregating sixteen to
		 * thirty-two each - which is the whole architectural difference between the two and exactly the
		 * thing a derived figure got wrong.
		 */
		int stringInputs,
		/**
		 * Most direct current one tracker's terminals will carry together, in amps.
		 *
		 * The figure that actually decides how many strings an inverter can take, and the one a
		 * datasheet prints beside the terminal count rather than instead of it. Twenty-six amps is the
		 * common number on a string machine, thirty on the big ones, and a central inverter's input is
		 * measured in thousands because its strings arrive already paralleled through combiner boxes.
		 *
		 * It is a separate constraint from the terminal count and it can be the binding one: two strings
		 * of a 210 mm cell module carry about thirty-four amps together, which is more than a
		 * twenty-six amp tracker will take however many holes there are to plug them into. Real
		 * designers hit this before they run out of terminals, and it is why a high-current module ends
		 * up on fewer strings per tracker than a narrow one.
		 */
		double maxCurrentPerMppt,
		/**
		 * Whether the machine has fused string terminals of its own.
		 *
		 * True of every string inverter, and that is what a string inverter *is*: its terminals are the
		 * fusing and the disconnect, which is why nobody puts a combiner box in front of one. A central
		 * inverter has bare busbars instead, so it cannot take a string at all until it is given a set of
		 * fuses - which is the real reason combiner boxes exist and not a rule invented for this.
		 */
		boolean stringTerminals,
		/**
		 * Whether it has busbars a combiner box's output can be lugged onto.
		 *
		 * A residential or commercial machine does not: its inputs are plug connectors, and three hundred
		 * amps of 240 mm² will not go into one. A utility string machine has both, because it is sold into
		 * plants laid out either way, and a central machine has only these.
		 */
		boolean trunkTerminals,
		double mpptMinVolts,
		double mpptMaxVolts,
		double maxDcVolts,
		/**
		 * Voltage at which the machine wakes up and starts looking for a maximum power point.
		 *
		 * Why a plant produces nothing at all for the first minutes after sunrise rather than a
		 * trickle: the strings have to come up past this before there is anything to track.
		 */
		double startupVolts,
		double peakEfficiency,
		/**
		 * Where on the load curve the peak sits, as a fraction of nameplate.
		 *
		 * A datasheet does not print this as a number but every datasheet plots it, and it is the
		 * one figure that fixes the two losses against each other: the peak lands at the load
		 * where the resistive loss has grown to equal the fixed one, so declaring where it is
		 * declares their ratio.
		 */
		double peakLoadFraction,
		/**
		 * What the machine draws overnight, in watts.
		 *
		 * Small and never zero, which is why a real plant's meter reads slightly negative between
		 * sunset and sunrise. Two orders of magnitude apart across the catalogue - fifty
		 * milliwatts for a microinverter against a hundred watts for a central one - because the
		 * cabinet has to keep its controller, its communications and its insulation monitoring
		 * alive whatever the array is doing.
		 */
		double nightWatts,
		/** Line-to-line volts at the terminals. */
		double nominalAcVolts,
		double frequencyHz,
		/**
		 * How far the power factor can be pushed either side of unity.
		 *
		 * 0.8 on a modern machine, which is what lets a plant be asked to hold voltage on a weak
		 * feeder instead of just pushing power at it.
		 */
		double minPowerFactor,
		/**
		 * Ambient temperature above which the machine starts backing off, in Celsius.
		 *
		 * Full output to 45 or 50 C and derating above, because the semiconductors have a junction
		 * temperature limit and the only way to respect it is to pass less current. It is why a
		 * plant in a hot climate can be clipping at eleven and derating at three.
		 */
		double derateOnsetC,
		double maxAmbientC,
		Cooling cooling
) {
	/**
	 * The load points and weights of the European efficiency, from the DIN/EN convention.
	 *
	 * Weighted for a temperate climate, which is what makes it a fairer number than the peak: half
	 * the weight sits at half load, because that is where a northern European inverter spends most
	 * of its year. A desert plant would be better judged by the CEC weighting, which puts more of
	 * it at full load.
	 */
	private static final double[] EURO_LOADS = {0.05, 0.10, 0.20, 0.30, 0.50, 1.00};
	private static final double[] EURO_WEIGHTS = {0.03, 0.06, 0.13, 0.10, 0.48, 0.20};

	public enum Cooling {
		/** No moving parts. Everything up to about 20 kW. */
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

	/**
	 * Half the total loss at the peak, as a fraction of nameplate.
	 *
	 * At the peak the fixed loss and the resistive loss are equal, so the peak efficiency alone
	 * fixes their geometric mean - and the load the peak sits at splits that mean into the two.
	 * Two published figures, two losses, no free parameters.
	 */
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

	/**
	 * Efficiency at a fraction of nameplate output.
	 *
	 * Zero below nothing, and it has to be: an inverter at first light is consuming more than the
	 * array is making, which is why a real plant sits flat at zero for a few minutes after sunrise
	 * rather than producing a trickle.
	 */
	public double efficiencyAt(double loadFraction) {
		if (loadFraction <= 0.0) return 0.0;

		double load = Math.min(loadFraction, 1.0);
		double losses = noLoadLossFraction() + resistiveLossFraction() * load * load;
		return load / (load + losses);
	}

	/**
	 * European efficiency: the curve above, averaged at the six standard load points.
	 *
	 * Derived rather than declared, so it cannot disagree with the curve it describes.
	 */
	public double europeanEfficiency() {
		double total = 0.0;
		for (int i = 0; i < EURO_LOADS.length; i++) {
			total += EURO_WEIGHTS[i] * efficiencyAt(EURO_LOADS[i]);
		}

		return total;
	}

	// ---- what comes out ----

	/**
	 * What the machine would put out if nothing were holding it back, in kW.
	 *
	 * The efficiency is published against *output* load, which makes this mildly circular and is
	 * also correct. Two refinement passes off the peak efficiency converge to well inside the two
	 * decimals anything here reports, and above full load the curve clamps itself, so an
	 * over-subscribed array comes out at the full-load efficiency rather than off the end.
	 */
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

	/**
	 * Whether the array is offering more than the machine can pass.
	 *
	 * Not a fault and not a design error: an oversized array is deliberate, and the few hours a year
	 * it costs are cheaper than the inverter it saves. It is worth reporting though, because a plant
	 * that clips for four hours in the middle of every clear day is a plant whose owner should know -
	 * and because clipping and derating look identical from outside and have completely different
	 * answers.
	 */
	public boolean clipping(double dcKw, double thermalLimitKw) {
		if (dcKw <= 0.0) return false;

		return unclippedAcKw(dcKw) > Math.min(acPowerKw, Math.max(0.0, thermalLimitKw)) + 1.0e-6;
	}

	/**
	 * Fraction of nameplate the machine will still pass at the top of its ambient range.
	 *
	 * Half, and not zero, because a real machine does not switch off at 60 C - it backs the current
	 * down to keep its junctions inside their limit and carries on. Ramping to zero instead would
	 * have a plant in a hot desert produce nothing on the afternoons it was built for, which is the
	 * opposite of what the derating curve on the datasheet says.
	 */
	private static final double DERATED_FRACTION_AT_LIMIT = 0.5;

	/**
	 * The output ceiling at a given ambient temperature, in kW.
	 *
	 * Against ambient rather than the cabinet, because that is the axis every datasheet's derating
	 * curve is drawn on: the vendor cannot know how a particular cabinet will sit in the sun, so the
	 * guarantee is written against the air outside it. The cabinet temperature is a separate
	 * reading - ambient plus a rise that follows the load - and it belongs in the telemetry rather
	 * than in this decision.
	 *
	 * Flat to the onset and falling linearly to {@link #DERATED_FRACTION_AT_LIMIT} at the top, which
	 * is the shape the published curves have.
	 */
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

	/**
	 * Cabinet temperature at a given ambient and load.
	 *
	 * The reading a plant's SCADA publishes, and it is a consequence rather than an input: the
	 * losses the efficiency curve accounts for have to go somewhere, and where they go is into the
	 * heatsink and then into the air. So the rise is proportional to the loss rather than to the
	 * output, which is why a cabinet is hottest at full load and still appreciably warm at first
	 * light - the fixed loss is there whatever the array is doing.
	 *
	 * Fan-cooled machines run cooler than convection-cooled ones at the same load, which is what the
	 * fan is for and why the reading is worth watching: a cabinet climbing at constant load is a
	 * cabinet whose fan has stopped.
	 */
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

	/** String terminals on one tracker: the terminal count shared out, at least one each. */
	public int inputsPerMppt() {
		return Math.max(1, stringInputs / mpptCount);
	}

	/**
	 * How many strings of a given current one tracker will actually take.
	 *
	 * The lower of two limits, which is the whole point of carrying both: the holes in the machine and
	 * the copper behind them. A twenty-six amp tracker with two terminals takes two strings of a
	 * thirteen amp module and only one of a seventeen amp one, and no amount of terminal counting says
	 * so.
	 */
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

	/**
	 * Apparent power for a given active output and power factor, kVA.
	 *
	 * Held under the machine's own ceiling, because that ceiling is the real constraint: an
	 * inverter asked for a deep power factor at full output cannot supply both and gives up active
	 * power first, which is what a grid operator asking for reactive support is actually buying.
	 */
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

	/** Phase current for a given apparent power, in amps. Three-phase, so the root of three is in it. */
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
