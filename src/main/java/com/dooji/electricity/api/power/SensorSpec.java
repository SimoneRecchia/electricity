package com.dooji.electricity.api.power;

import com.dooji.electricity.api.WorldConditions;
import net.minecraft.resources.ResourceLocation;

/** One meteorological instrument, described the way an instrument datasheet describes one. */
public record SensorSpec(
		ResourceLocation id,
		/** Instrument designation. */
		String displayName,
		Instrument instrument,
		/** Classification the instrument is sold under. */
		String instrumentClass,
		double spectralLowNm,
		double spectralHighNm,
		/** Time to reach 95% of a step change, in seconds. */
		double responseTimeS,
		/** Daily-total uncertainty as a percentage */
		double uncertaintyPercent,
		double rangeLow,
		double rangeHigh,
		String unit
) {
	/** The instruments a photovoltaic plant carries */
	public enum Instrument {
		/** Global irradiance on a horizontal surface */
		PYRANOMETER("pyranometer", false),
		/** Two pyranometers back to back */
		ALBEDOMETER("albedometer", false),
		/** A pyranometer under a shadow ring, so it sees the sky and not the sun. */
		DIFFUSE_PYRANOMETER("diffuse_pyranometer", false),
		/** A single crystalline cell, short-circuited across a shunt. */
		REFERENCE_CELL("reference_cell", true),
		/** A platinum resistance thermometer on the back sheet of a representative module. */
		MODULE_TEMPERATURE("module_temperature", true),
		/** Air temperature in a radiation shield, which is the only way to measure air rather than sunshine. */
		AMBIENT_TEMPERATURE("ambient_temperature", false),
		/** Ultrasonic distance to the surface below */
		SNOW_DEPTH("snow_depth", false),
		/** Cup anemometer. */
		ANEMOMETER("anemometer", false),
		/** Wind vane. */
		WIND_VANE("wind_vane", false);

		private final String key;
		/** Whether this instrument is fitted to an array rather than standing on a mast of its own. */
		private final boolean onArray;

		Instrument(String key, boolean onArray) {
			this.key = key;
			this.onArray = onArray;
		}

		public String key() {
			return key;
		}

		public boolean onArray() {
			return onArray;
		}
	}

	public SensorSpec {
		if (responseTimeS < 0.0) throw new IllegalArgumentException(id + ": an instrument cannot answer before it is asked");
		if (rangeHigh <= rangeLow) throw new IllegalArgumentException(id + ": invalid measurement range");
	}

	/** What this instrument reads, given what is actually there and what it read last tick. */
	public double reading(double previous, double actual, int ticks) {
		return clampToRange(previous + (actual - previous) * response(ticks));
	}

	/** How far a reading moves towards the truth in this many ticks */
	public double response(int ticks) {
		if (responseTimeS <= 0.0 || ticks <= 0) return 1.0;

		double timeConstant = responseTimeS / 3.0;
		return 1.0 - Math.exp(-(ticks * WorldConditions.SECONDS_PER_DAY_TICK) / timeConstant);
	}

	/** The reading held inside what the instrument can actually report. */
	private double clampToRange(double value) {
		return Math.max(rangeLow, Math.min(rangeHigh, value));
	}
}
