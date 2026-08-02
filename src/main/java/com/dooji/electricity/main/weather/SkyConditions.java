package com.dooji.electricity.main.weather;

/**
 * The sunlight at one place and one moment, split three ways.
 *
 * Three quantities rather than one, and the split is what everything downstream depends on. The mod
 * used to carry a single global figure with the sky's own contribution folded in as a flat ten
 * percent, and on top of that no shadow could behave correctly: a panel with something over it loses
 * almost everything under a clear sky, where nine tenths of the light arrives from one direction, and
 * almost nothing under an overcast one, where it arrives from everywhere. One lumped number cannot
 * express the difference, so it got the common case wrong in both directions.
 *
 * The three are the standard decomposition and they are what a real plant's met station measures with
 * three separate instruments:
 *
 * <ul>
 * <li><b>Direct normal</b> - the beam, measured on a surface held square to the sun. What a shadow
 *     takes away entirely and what a tracker exists to collect.</li>
 * <li><b>Diffuse horizontal</b> - the sky itself, measured on a horizontal surface under a shadow
 *     ring. What is left on an overcast day, and what a tilted plane sees less of than a flat one
 *     because it is looking at less sky.</li>
 * <li><b>Global horizontal</b> - both together on a horizontal surface. The figure a single
 *     pyranometer reads and the one a plant's performance ratio is written against.</li>
 * </ul>
 *
 * They are not independent: {@code GHI = DNI x sin(elevation) + DHI} always, and that identity is
 * what keeps the model honest when cloud takes the beam away and hands part of it back as diffuse.
 *
 * @param sun                   where the sun is
 * @param airMass               atmospheric path length in zenith paths, station pressure included
 * @param cloudCover            fraction of the sky covered, 0 to 1
 * @param extraterrestrialNormal solar irradiance above the atmosphere, W/m2
 * @param directNormal          beam irradiance on a surface square to the sun, W/m2
 * @param diffuseHorizontal     sky irradiance on a horizontal surface, W/m2
 * @param globalHorizontal      both, on a horizontal surface, W/m2
 * @param albedo                fraction of the light the ground here throws back up
 * @param ambientTempC          air temperature, which the cell temperature is built on
 * @param windSpeed             wind at instrument height, m/s: what actually cools a module
 */
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
	/** Night, at a place with this ground and this air. Everything radiative is zero and stays consistent. */
	public static SkyConditions night(SunPosition sun, double cloudCover, double albedo, double ambientTempC, double windSpeed) {
		return new SkyConditions(sun, Double.POSITIVE_INFINITY, cloudCover, 0.0, 0.0, 0.0, 0.0, albedo, ambientTempC, windSpeed);
	}

	/**
	 * Beam on a horizontal surface, W/m2. The part of the global that a shadow can take away.
	 */
	public double beamHorizontal() {
		return directNormal * sun().sinElevation();
	}

	/**
	 * Diffuse over global: 0.1 or so under a clear sky, near 1 under a heavy overcast.
	 *
	 * The single most useful number for reasoning about shading, and the reason it is worth carrying
	 * the three components separately at all.
	 */
	public double diffuseFraction() {
		return globalHorizontal <= 0.0 ? 1.0 : Math.min(1.0, diffuseHorizontal / globalHorizontal);
	}

	/**
	 * Clearness index: global over what would arrive on a horizontal surface with no atmosphere at all.
	 *
	 * The standard measure of how much the sky is taking, and the thing every diffuse-fraction
	 * correlation in the literature is written against. Around 0.75 under a clear zenith sun, 0.2
	 * under a heavy overcast, and it is published here because a real plant's analysis reports it.
	 */
	public double clearnessIndex() {
		double horizontal = extraterrestrialNormal * sun().sinElevation();
		return horizontal <= 0.0 ? 0.0 : Math.min(1.0, globalHorizontal / horizontal);
	}
}
