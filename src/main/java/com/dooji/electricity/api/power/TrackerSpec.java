package com.dooji.electricity.api.power;

import com.dooji.electricity.api.WorldConditions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * One tracker, described the way a tracker vendor's sell sheet describes one.
  *
 * See {@link #backtrackRotation}.
 */
public record TrackerSpec(
		ResourceLocation id,
		/** Product name. */
		String displayName,
		int axes,
		/** Rotation the drive can reach either side of horizontal, in degrees. */
		double rotationLimitDeg,
		/** Lowest sun the plane can be aimed straight at, in degrees above the horizon. */
		double minSunElevationDeg,
		/** Slew rate, degrees a minute. */
		double slewDegPerMinute,
		/** How far the row is allowed to drift off the sun before the drive runs, in degrees. */
		double trackingDeadbandDeg,
		/** Where the row parks overnight. */
		double nightStowDeg,
		/** Where the row goes in high wind. */
		double windStowDeg,
		/** Where the row goes to shed snow. */
		double snowStowDeg,
		/** Wind speed at which the controller stows, m/s. */
		double windStowSpeed,
		/** Snow depth at which the controller goes to the snow stow, in metres. */
		double snowStowDepth,
		boolean backtracking,
		/** Drive power while slewing, in watts. */
		double motorW
) {

	public TrackerSpec {
		if (axes < 1 || axes > 2) throw new IllegalArgumentException(id + ": a tracker has one axis or two");
		if (rotationLimitDeg <= 0.0 || rotationLimitDeg > 90.0) throw new IllegalArgumentException(id + ": rotation limit is between 0 and 90 degrees");
		if (slewDegPerMinute <= 0.0) throw new IllegalArgumentException(id + ": a tracker that cannot slew is a fixed mounting");
		if (windStowSpeed <= 0.0) throw new IllegalArgumentException(id + ": a tracker stows in some wind");
	}

	public boolean dualAxis() {
		return axes >= 2;
	}

	/** Lowest sun this tracker can point straight at, in degrees. */
	public double lowestTrackableSunDeg() {
		return dualAxis() ? minSunElevationDeg : 90.0 - rotationLimitDeg;
	}

	/** Rotation that puts the plane square to the sun, before any limit or backtracking. */
	public double trueRotation(double sunElevationDeg, boolean afternoon) {
		double zenith = 90.0 - Mth.clamp(sunElevationDeg, 0.0, 90.0);
		return afternoon ? zenith : -zenith;
	}

	/**
	 * The rotation actually commanded, once the row has to stop shading the row behind it.
	  *
	 * At {@code sin(a) = GCR} the correction is zero, so the row leaves true tracking without a step in it.
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

	/** Degrees the drive moves in one tick of the day clock. */
	public double slewPerTick() {
		return slewDegPerMinute / 60.0 * WorldConditions.SECONDS_PER_DAY_TICK;
	}

	/** Whether an error of this many degrees is worth starting the motor for. */
	/** Gust at which the controller stows even though the mean is still under the limit, m/s. */
	public double gustStowSpeed() {
		return windStowSpeed * WorldConditions.GUST_TRIP_RATIO;
	}

	public boolean worthMoving(double errorDeg) {
		return Math.abs(errorDeg) >= trackingDeadbandDeg;
	}

}
