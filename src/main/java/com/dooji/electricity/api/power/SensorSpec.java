package com.dooji.electricity.api.power;

import com.dooji.electricity.api.WorldConditions;
import net.minecraft.resources.ResourceLocation;

/**
 * One meteorological instrument, described the way an instrument datasheet describes one.
 *
 * <h2>Why the instruments are modelled at all</h2>
 *
 * Because a plant's SCADA does not read the weather, it reads instruments, and the difference
 * shows. A thermopile pyranometer takes five seconds to answer a step change in light, so the
 * irradiance trend from a real plant is visibly smoother than the power trend beside it - the
 * modules respond in microseconds and the sensor watching them does not. A reference cell has the
 * same spectral response as the array and therefore disagrees with the pyranometer beside it,
 * consistently, by a few percent in the morning. And an ultrasonic snow gauge does not measure snow
 * at all: it measures the distance to whatever is under it, and the depth is arithmetic done
 * afterwards against a clear-ground reference - which is exactly why a real plant publishes both
 * numbers and why a frozen sensor reads a plausible distance and an absurd depth.
 *
 * None of that is noise for its own sake. It is the difference between a mod that tells a player
 * what the weather is and a mod that hands them the instrument readings a real operator has to work
 * out the weather from.
 *
 * <h2>Where each one lives</h2>
 *
 * {@link Instrument#onArray} is the answer to the question a plant designer actually asks. Module
 * temperature is a resistance thermometer taped to the back of a representative module, so it
 * belongs to the array and there is one per array. Everything else is a separate instrument on a
 * mast, and there are one or two of those for a whole plant.
 */
public record SensorSpec(
		ResourceLocation id,
		/** Instrument designation, e.g. {@code SP-11}. A proper noun: never translated. */
		String displayName,
		Instrument instrument,
		/**
		 * Classification the instrument is sold under.
		 *
		 * ISO 9060 for the radiometers, and it is the figure that decides whether a plant's
		 * performance test is contractually valid: a Class A pyranometer is what a utility power
		 * purchase agreement demands, and a Class C one is what a rooftop gets.
		 */
		String instrumentClass,
		double spectralLowNm,
		double spectralHighNm,
		/**
		 * Time to reach 95% of a step change, in seconds.
		 *
		 * The number that makes an instrument reading different from the thing it is measuring. A
		 * thermopile is a black disc warming up, so it cannot answer faster than it can change
		 * temperature; five seconds is what a Class A pyranometer takes and it is why a cloud edge
		 * crossing an array arrives on the power trend before it arrives on the irradiance one.
		 */
		double responseTimeS,
		/** Daily-total uncertainty as a percentage, which is what an instrument is actually judged on. */
		double uncertaintyPercent,
		double rangeLow,
		double rangeHigh,
		String unit
) {
	/**
	 * The instruments a photovoltaic plant carries, and what each is for.
	 *
	 * The order is the order they sit on the bus, which is how the seven on a mast are addressed.
	 */
	public enum Instrument {
		/**
		 * Global irradiance on a horizontal surface, or on the plane of the array when tilted with it.
		 *
		 * The instrument the whole plant is judged against: performance ratio is production over
		 * what this says was available, so an error here is an error in the contract.
		 */
		PYRANOMETER("pyranometer", false),
		/**
		 * Two pyranometers back to back, one up and one down. The ratio is the ground albedo.
		 *
		 * Only worth having on a bifacial plant, where it is worth a great deal: the rear-side gain
		 * is proportional to it, so an albedometer is how you find out whether the grass under the
		 * array should have been gravel.
		 */
		ALBEDOMETER("albedometer", false),
		/**
		 * A pyranometer under a shadow ring, so it sees the sky and not the sun. The diffuse component.
		 *
		 * The one instrument that needs looking after: the ring has to be adjusted as the sun's
		 * declination moves, and on a real plant it is the sensor most often quietly wrong.
		 */
		DIFFUSE_PYRANOMETER("diffuse_pyranometer", false),
		/**
		 * A single crystalline cell, short-circuited across a shunt.
		 *
		 * Not a better pyranometer, a different measurement: it responds only to the wavelengths the
		 * array responds to, so it tracks the array's current far more closely and reads lower than
		 * the pyranometer beside it whenever the light is red - first thing, last thing, and under
		 * heavy cloud.
		 */
		REFERENCE_CELL("reference_cell", true),
		/**
		 * A platinum resistance thermometer on the back sheet of a representative module.
		 *
		 * Belongs to the array rather than the mast, because there is nothing representative about
		 * the temperature of a module a hundred metres away on a different mounting.
		 */
		MODULE_TEMPERATURE("module_temperature", true),
		/** Air temperature in a radiation shield, which is the only way to measure air rather than sunshine. */
		AMBIENT_TEMPERATURE("ambient_temperature", false),
		/**
		 * Ultrasonic distance to the surface below, from which snow depth is worked out.
		 *
		 * Publishes both numbers, and that is not redundancy: the distance is what it measures and
		 * the height is a subtraction from a surveyed clear-ground reference, so the two disagreeing
		 * is how you find out the sensor has iced up or something has been parked under it.
		 */
		SNOW_DEPTH("snow_depth", false),
		/** Cup anemometer. Wind at the mast, which is what cools the modules and stows the trackers. */
		ANEMOMETER("anemometer", false),
		/** Wind vane. Direction, which decides which way a stowed tracker is loaded. */
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

	/**
	 * What this instrument reads, given what is actually there and what it read last tick.
	 *
	 * A first-order lag on the instrument's own time constant. A datasheet quotes the time to 95% of a
	 * step, which is three time constants, so the constant is a third of it.
	 */
	public double reading(double previous, double actual, int ticks) {
		return clampToRange(previous + (actual - previous) * response(ticks));
	}

	/**
	 * How far a reading moves towards the truth in this many ticks, 0 to 1.
	 *
	 * Against the day clock rather than against real seconds, and that is the whole of the argument
	 * about this class. Five seconds of thermal mass is five seconds however fast the sun is moving - so
	 * real seconds look like the honest choice, and it is what this did at first. But a tick of the day
	 * clock stands for 3.6 seconds of weather, so against real seconds a five-second pyranometer answers
	 * a cloud edge in a fourteenth of a tick: the lag is real, correct, and invisible, and the model
	 * does nothing it was written to do. The same conversion is already applied to a tracker drive quoted
	 * in degrees a minute, for the same reason and in the same words - the sky is what everything here is
	 * being timed against.
	 *
	 * An instrument with no response time - a resistance thermometer read through an ADC, near enough -
	 * answers immediately, and this returns one rather than smoothing the truth by a hair.
	 */
	public double response(int ticks) {
		if (responseTimeS <= 0.0 || ticks <= 0) return 1.0;

		double timeConstant = responseTimeS / 3.0;
		return 1.0 - Math.exp(-(ticks * WorldConditions.SECONDS_PER_DAY_TICK) / timeConstant);
	}

	/**
	 * The reading held inside what the instrument can actually report.
	 *
	 * Real instruments saturate, and saying so is more useful than pretending they do not: a
	 * pyranometer that tops out at 4000 W/m2 will never see it, but a snow gauge whose range starts
	 * at half a metre genuinely cannot tell you about the first centimetre.
	 */
	private double clampToRange(double value) {
		return Math.max(rangeLow, Math.min(rangeHigh, value));
	}
}
