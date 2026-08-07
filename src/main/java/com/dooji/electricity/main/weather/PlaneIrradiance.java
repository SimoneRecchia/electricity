package com.dooji.electricity.main.weather;

/**
 * What actually falls on a module plane, broken into where it came from.
  *
 * Kept apart from {@link SkyConditions} because the two belong to different things.
 */
public record PlaneIrradiance(
		double incidenceAngleDeg,
		double beam,
		double skyDiffuse,
		double groundReflected,
		double rear
) {
	public static final PlaneIrradiance DARK = new PlaneIrradiance(90.0, 0.0, 0.0, 0.0, 0.0);

	/** Everything arriving on the front, W/m2. */
	public double front() {
		return beam + skyDiffuse + groundReflected;
	}

	/** Scales the beam and leaves everything else, which is what an obstruction between the sun and the plane does. */
	public PlaneIrradiance withBeamFraction(double fraction) {
		double kept = Math.max(0.0, Math.min(1.0, fraction));
		return new PlaneIrradiance(incidenceAngleDeg, beam * kept, skyDiffuse, groundReflected, rear);
	}

	/** Scales the sky and the ground terms, which is what having less of the dome in view does. */
	public PlaneIrradiance withSkyFraction(double fraction) {
		double kept = Math.max(0.0, Math.min(1.0, fraction));
		return new PlaneIrradiance(incidenceAngleDeg, beam, skyDiffuse * kept, groundReflected * kept, rear * kept);
	}

	/** Everything at once, for the case where a module is buried under snow or a solid block. */
	public PlaneIrradiance scaled(double fraction) {
		double kept = Math.max(0.0, Math.min(1.0, fraction));
		return new PlaneIrradiance(incidenceAngleDeg, beam * kept, skyDiffuse * kept, groundReflected * kept, rear * kept);
	}
}
