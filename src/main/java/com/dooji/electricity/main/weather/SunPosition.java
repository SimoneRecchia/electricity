package com.dooji.electricity.main.weather;

/** Where the sun is, as a direction rather than a height. */
public record SunPosition(double elevationDeg, double azimuthDeg) {
	/** Due east, on the mod's compass: where the sun is all morning. */
	public static final double EAST = 0.0;
	/** Due west: where it is all afternoon. */
	public static final double WEST = 180.0;

	public boolean up() {
		return elevationDeg > 0.0;
	}

	/** Sine of the elevation, or zero at night. */
	public double sinElevation() {
		return up() ? Math.sin(Math.toRadians(elevationDeg)) : 0.0;
	}

	public double cosElevation() {
		return Math.cos(Math.toRadians(Math.max(0.0, elevationDeg)));
	}

	public boolean afternoon() {
		return azimuthDeg == WEST;
	}

	/** Cosine of the angle between this sun and a plane's normal. */
	public double cosIncidence(double planeTiltDeg, double planeAzimuthDeg) {
		double tilt = Math.toRadians(planeTiltDeg);
		double bearing = Math.toRadians(planeAzimuthDeg - azimuthDeg);
		return Math.cos(tilt) * sinElevation() + Math.sin(tilt) * cosElevation() * Math.cos(bearing);
	}
}
