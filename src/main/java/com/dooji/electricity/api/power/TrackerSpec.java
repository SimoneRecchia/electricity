package com.dooji.electricity.api.power;

import com.dooji.electricity.api.WorldConditions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * One tracker, described the way a tracker vendor's sell sheet describes one.
 *
 * Everything here is geometry and mechanism: the rotation the drive can reach, how fast it gets
 * there, and where it goes when the weather says stop. Nothing here decides *whether* to stow -
 * that is the controller's judgement and lives with the array, which is the thing that knows
 * what the weather is doing.
 *
 * <h2>Sign convention</h2>
 *
 * Rotation is degrees about the primary axis, measured from horizontal, positive when the plane
 * has rolled so its face looks west. So a morning tracker sits negative, noon is zero, and the
 * afternoon is positive - which matches the sun, and matches the direction the renderer turns
 * the torque tube.
 *
 * <h2>Backtracking</h2>
 *
 * The one piece of geometry here that had to be derived rather than copied, because the
 * published forms disagree about what their angles are measured from. See
 * {@link #backtrackRotation}.
 */
public record TrackerSpec(
		ResourceLocation id,
		/** Product name, e.g. {@code Meridian HR}. A proper noun: never translated. */
		String displayName,
		int axes,
		/**
		 * Rotation the drive can reach either side of horizontal, in degrees.
		 *
		 * The number that decides how much of the first and last hour a row collects, and the
		 * real ones are 60 and 52 degrees. A row limited to 60 is square to the sun only once
		 * the sun is 30 degrees up; below that it is looking past it and taking the beam at an
		 * angle, which is exactly the loss a dual-axis frame exists to avoid.
		 */
		double rotationLimitDeg,
		/**
		 * Lowest sun the plane can be aimed straight at, in degrees above the horizon.
		 *
		 * On a single axis this is not free - it follows from the rotation limit - so it is
		 * declared only for the dual-axis frame, whose elevation drive has a travel of its own.
		 * Ten degrees is what a pedestal machine reaches.
		 */
		double minSunElevationDeg,
		/**
		 * Slew rate, degrees a minute.
		 *
		 * Slow, and deliberately so: real trackers step every few minutes rather than following
		 * the sun continuously, because a drive that never stops is a drive that wears out. 4.4
		 * degrees a minute is a published figure, which is about a quarter of the fifteen
		 * degrees an hour the sun actually moves - so the row is always a little behind, and
		 * catches up in steps.
		 */
		double slewDegPerMinute,
		/** Where the row parks overnight. Flat on the real machines, which is also where the dew runs off. */
		double nightStowDeg,
		/**
		 * Where the row goes in high wind.
		 *
		 * Flat, and that is not obvious: a flat plane presents no lever arm to a horizontal
		 * gust, so the torque on the drive and the tube goes to almost nothing. Trackers are
		 * tested to 200 km/h stowed and their self-powered controllers keep a battery precisely
		 * so a grid outage cannot leave a row standing up in a storm.
		 */
		double windStowDeg,
		/**
		 * Where the row goes to shed snow.
		 *
		 * Steeper than any fixed racking would ever be built at, which is the tracker's one
		 * unanswerable advantage in a cold climate: a fixed array below thirty degrees can stay
		 * buried for a season, and a tracker simply stands up and drops the lot.
		 */
		double snowStowDeg,
		/** Wind speed at which the controller stows, m/s. Around 20 on the real machines. */
		double windStowSpeed,
		/** Snow depth at which the controller goes to the snow stow, in metres. */
		double snowStowDepth,
		boolean backtracking,
		/** Drive power while slewing, in watts. A real row's drive is a 24 V motor and a gearbox. */
		double motorW
) {
	/** Degrees the sun moves in an hour. */
	private static final double SUN_DEGREES_PER_HOUR = 15.0;

	public TrackerSpec {
		if (axes < 1 || axes > 2) throw new IllegalArgumentException(id + ": a tracker has one axis or two");
		if (rotationLimitDeg <= 0.0 || rotationLimitDeg > 90.0) throw new IllegalArgumentException(id + ": rotation limit is between 0 and 90 degrees");
		if (slewDegPerMinute <= 0.0) throw new IllegalArgumentException(id + ": a tracker that cannot slew is a fixed mounting");
		if (windStowSpeed <= 0.0) throw new IllegalArgumentException(id + ": a tracker stows in some wind");
	}

	public boolean dualAxis() {
		return axes >= 2;
	}

	/**
	 * Lowest sun this tracker can point straight at, in degrees.
	 *
	 * A single axis is limited by its rotation and nothing else, so the answer is derived rather
	 * than declared - 30 degrees for a machine that rolls to 60. A dual-axis frame has an
	 * elevation drive with its own travel, so it declares one, and reaches lower.
	 */
	public double lowestTrackableSunDeg() {
		return dualAxis() ? minSunElevationDeg : 90.0 - rotationLimitDeg;
	}

	/**
	 * Rotation that puts the plane square to the sun, before any limit or backtracking.
	 *
	 * In a world whose sun stays in one vertical plane this is the whole of true tracking: the
	 * plane's normal has to be at the sun's elevation, so the roll from horizontal is the sun's
	 * zenith angle, signed towards whichever half of the sky the sun is in.
	 */
	public double trueRotation(double sunElevationDeg, boolean afternoon) {
		double zenith = 90.0 - Mth.clamp(sunElevationDeg, 0.0, 90.0);
		return afternoon ? zenith : -zenith;
	}

	/**
	 * The rotation actually commanded, once the row has to stop shading the row behind it.
	 *
	 * <h2>Where this comes from</h2>
	 *
	 * A plane held square to the sun intercepts a beam exactly as wide as the plane, and that
	 * beam meets the ground at the sun's elevation - so the shadow on the ground is
	 * {@code L / sin(a)} across. The row behind is {@code P} away, so shading begins the moment
	 * {@code L / sin(a) > P}, which is to say the moment {@code sin(a) < GCR}.
	 *
	 * Below that the drive backs off until the shadow just reaches the next row and no further.
	 * Rolling back by an angle puts the beam on the plane at that angle of incidence, narrowing
	 * the intercepted beam to {@code L cos(theta)} and the shadow to {@code L cos(theta)/sin(a)},
	 * so holding the shadow at exactly {@code P} needs {@code cos(theta) = sin(a) / GCR}. The
	 * commanded rotation is the true one pulled back by that much.
	 *
	 * Two properties are worth checking against, because they are what make it right rather than
	 * merely plausible. At {@code sin(a) = GCR} the correction is zero, so the row leaves true
	 * tracking without a step in it. And as the sun touches the horizon the correction reaches a
	 * right angle, so the row ends up flat - which is where a real tracker is at sunrise, and
	 * why an array of them lies down together in the first light rather than standing up into
	 * the sun.
	 *
	 * The cost is real and it is meant to be: backtracking throws away beam on purpose so that
	 * no module in the string is shaded, because a shaded cell in a series string drags the
	 * whole string down through its bypass diode and loses far more than the cosine does.
	 */
	public double backtrackRotation(double trueRotation, double sunElevationDeg, double groundCoverRatio) {
		if (!backtracking || groundCoverRatio <= 0.0) return trueRotation;

		double sinElevation = Math.sin(Math.toRadians(Mth.clamp(sunElevationDeg, 0.0, 90.0)));
		if (sinElevation >= groundCoverRatio) return trueRotation;

		double correction = Math.toDegrees(Math.acos(Mth.clamp(sinElevation / groundCoverRatio, -1.0, 1.0)));
		return trueRotation >= 0.0 ? trueRotation - correction : trueRotation + correction;
	}

	/** The rotation held within what the drive can reach. */
	public double clampRotation(double rotation) {
		return Mth.clamp(rotation, -rotationLimitDeg, rotationLimitDeg);
	}

	/**
	 * Degrees the drive moves in one tick of the day clock.
	 *
	 * Against the day clock rather than the tick counter, and that is the whole reason this is a
	 * method rather than the field it looks like. A tracker's speed only means anything relative
	 * to the sun it is chasing, and one tick of Minecraft's day clock stands for
	 * {@link WorldConditions#SECONDS_PER_DAY_TICK} seconds of real sky - so a drive quoted at 4.4
	 * degrees a minute has to be converted through that, or a row would be seventy times faster
	 * than the machine it is drawn from and tracking would look effortless.
	 *
	 * Converted, it comes out about seventeen times the fifteen degrees an hour the sun moves,
	 * which is the same ratio the real pair have: fast enough to catch up in steps, slow enough
	 * that a stow takes a couple of hundred ticks and a controller has to see weather coming
	 * rather than react to it.
	 */
	public double slewPerTick() {
		return slewDegPerMinute / 60.0 * WorldConditions.SECONDS_PER_DAY_TICK;
	}

}
