package com.dooji.electricity.main.weather;

/**
 * Where the sun is, as a direction rather than a height.
 *
 * The mod used to keep only the sine of the elevation, which is all a flat panel needs and nothing
 * like enough for a tracker: a machine whose whole purpose is to point at the sun has to be told
 * which way that is.
 *
 * <h2>What this world's sky is like</h2>
 *
 * Minecraft has no axial tilt and no latitude. The sun rises due east, climbs through the zenith and
 * sets due west, and it does it identically every day of the year - so every day here is an equinox
 * at the equator. Three things follow, and all three shape the design of everything that reads this:
 *
 * The azimuth is two-valued rather than continuous. The sun is due east all morning and due west all
 * afternoon, and it swings between them by passing overhead rather than by sweeping round the south.
 * A tracker's azimuth drive therefore has nothing to do, and its elevation drive does everything.
 *
 * A horizontal plane is nearly the optimum. On Earth a flat array gives up about a tenth of the
 * year's energy to a tilted one; here the sun is square to horizontal at noon every day, so a tilt
 * is a maintenance decision - shedding snow, washing dirt off - rather than an optimisation.
 *
 * And the yields are equatorial ones. A well-sited array here makes something near a fifth of its
 * nameplate over the year, against the eleventh a temperate country manages.
 *
 * @param elevationDeg degrees above the horizon, negative at night
 * @param azimuthDeg   direction the sun lies in, on the mod's compass: 0 is east, 90 is south, 180 is
 *                     west. The same convention {@link Atmosphere.Flow#direction()} uses, so a wind
 *                     heading and a sun bearing can be compared without conversion.
 */
public record SunPosition(double elevationDeg, double azimuthDeg) {
	/** Due east, on the mod's compass: where the sun is all morning. */
	public static final double EAST = 0.0;
	/** Due west: where it is all afternoon. */
	public static final double WEST = 180.0;

	public boolean up() {
		return elevationDeg > 0.0;
	}

	/** Sine of the elevation, or zero at night. What every irradiance figure is projected onto. */
	public double sinElevation() {
		return up() ? Math.sin(Math.toRadians(elevationDeg)) : 0.0;
	}

	public double cosElevation() {
		return Math.cos(Math.toRadians(Math.max(0.0, elevationDeg)));
	}

	/** Degrees from straight up. The angle an air mass is measured along. */
	public double zenithDeg() {
		return 90.0 - elevationDeg;
	}

	public boolean afternoon() {
		return azimuthDeg == WEST;
	}

	/**
	 * Cosine of the angle between this sun and a plane's normal.
	 *
	 * The one piece of spherical geometry the whole solar model rests on, and it collapses to
	 * something short because both directions are expressed as an elevation and a bearing: the
	 * component of the sun along the plane's normal is the product of the two elevations' cosines
	 * times the cosine of the bearing between them, plus the product of their sines.
	 *
	 * Negative when the sun is behind the plane, which callers must respect rather than take the
	 * absolute value of - a module lit from behind is a module in shade, not a module producing.
	 */
	public double cosIncidence(double planeTiltDeg, double planeAzimuthDeg) {
		double tilt = Math.toRadians(planeTiltDeg);
		double bearing = Math.toRadians(planeAzimuthDeg - azimuthDeg);
		return Math.cos(tilt) * sinElevation() + Math.sin(tilt) * cosElevation() * Math.cos(bearing);
	}

	/** Angle of incidence on a plane, in degrees. 90 or more means the sun is not on that face at all. */
	public double incidenceDeg(double planeTiltDeg, double planeAzimuthDeg) {
		double cos = cosIncidence(planeTiltDeg, planeAzimuthDeg);
		return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cos))));
	}
}
