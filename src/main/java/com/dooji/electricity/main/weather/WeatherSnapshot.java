package com.dooji.electricity.main.weather;

/** The weather at one place, one height and one moment. */
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
		SkyConditions sky
) {
}
