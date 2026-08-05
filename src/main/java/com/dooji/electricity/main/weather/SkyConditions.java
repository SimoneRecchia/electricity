package com.dooji.electricity.main.weather;

/** The sunlight at one place and one moment, split three ways. */
public record SkyConditions(
		SunPosition sun,
		double airMass,
		double cloudCover,
		double extraterrestrialNormal,
		double directNormal,
		double diffuseHorizontal,
		double globalHorizontal,
		double albedo,
		double ambientTempC,
		double windSpeed
) {
	/** Night, at a place with this ground and this air. */
	public static SkyConditions night(SunPosition sun, double cloudCover, double albedo, double ambientTempC, double windSpeed) {
		return new SkyConditions(sun, Double.POSITIVE_INFINITY, cloudCover, 0.0, 0.0, 0.0, 0.0, albedo, ambientTempC, windSpeed);
	}

	/** Beam on a horizontal surface, W/m2. */
	public double beamHorizontal() {
		return directNormal * sun().sinElevation();
	}

	/** Diffuse over global: 0.1 or so under a clear sky, near 1 under a heavy overcast. */
	public double diffuseFraction() {
		return globalHorizontal <= 0.0 ? 1.0 : Math.min(1.0, diffuseHorizontal / globalHorizontal);
	}

	/** Clearness index: global over what would arrive on a horizontal surface with no atmosphere at all. */
	public double clearnessIndex() {
		double horizontal = extraterrestrialNormal * sun().sinElevation();
		return horizontal <= 0.0 ? 0.0 : Math.min(1.0, globalHorizontal / horizontal);
	}
}
