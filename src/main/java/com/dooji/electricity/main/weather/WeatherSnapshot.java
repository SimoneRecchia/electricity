package com.dooji.electricity.main.weather;

/**
 * The weather at one place, one height and one moment.
 *
 * Three wind speeds rather than one, because a real machine is told all three and does
 * different things with each: it makes power from what is passing the rotor now, it
 * decides whether to shut down on a ten-minute mean, and it trips on a gust. Collapsing
 * them into one figure is most of what made a turbine's output read as a straight line.
 *
 * @param meanWind      ten-minute mean at the sampled height, m/s
 * @param instantWind   what the rotor is in right now: the mean plus its turbulence
 * @param gustWind      three-second gust the mean and its turbulence imply
 * @param turbulence    turbulence intensity, the standard deviation of the wind over its mean
 * @param direction     heading the air travels towards, 0..360, friction turning included
 * @param temperatureC  air temperature at the site
 * @param pressureHpa   air pressure at the site, reduced from the map's sea level value
 * @param airDensity    kg/m3, from that temperature and that pressure
 * @param shearExponent exponent of the wind profile here and now, which is how much another
 *                      ten metres of tower would be worth
 * @param cloudCover    fraction of the sky covered, 0 to 1
 * @param irradiance    global horizontal irradiance reaching the ground, W/m2
 */
public record WeatherSnapshot(
		double meanWind,
		double instantWind,
		double gustWind,
		double turbulence,
		float direction,
		double temperatureC,
		double pressureHpa,
		double airDensity,
		double shearExponent,
		double cloudCover,
		double irradiance
) {
}
