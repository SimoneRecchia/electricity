package com.dooji.electricity.api;

/**
 * The handful of numbers describing the world that every machine and the weather both live in.
 *
 * They are here rather than on any one class because more than one subsystem needs each of
 * them, and a physical constant written down twice is a constant free to be changed once. The
 * scale used to sit on the turbine datasheet, which had the atmosphere importing a machine's
 * specification to find out how tall a block is; the air density sat in two places at once.
 */
public final class WorldConditions {
	/**
	 * Metres of real world one block of Minecraft stands for.
	 *
	 * A 130 m rotor drawn a metre to the block would not fit under the build height with a
	 * tower beneath it, so the mod draws ten metres to the block and reports the metres.
	 * Everything needing a height in metres - a hub, a wind profile, the elevation of a hill,
	 * the size of a cloud - goes through this and nothing else.
	 */
	public static final double METRES_PER_BLOCK = 10.0;

	/**
	 * Air density at sea level and 15 C, kg/m3.
	 *
	 * Two subsystems need it and mean slightly different things by it, which is exactly why it
	 * is written once: it is the condition every power curve is rated at, and it is the
	 * representative density the geostrophic balance is worked out with.
	 */
	public static final double REFERENCE_AIR_DENSITY = 1.225;

	/** Standard mean sea level pressure, hPa. */
	public static final double SEA_LEVEL_PRESSURE = 1013.25;

	/**
	 * Seconds of real sky one tick of Minecraft's day clock stands for: 86400 over a 24000 tick day.
	 *
	 * Time is the one scale the mod does not choose - Minecraft already runs a day in twenty
	 * minutes, so this is a conversion rather than a decision. It is here because two subsystems
	 * need it and would otherwise each write their own: the weather quotes every timescale in
	 * real terms and converts through this, and a tracker drive quoted in degrees a minute has to
	 * come through it as well or a row would chase the sun seventy times faster than the machine
	 * it is drawn from.
	 */
	public static final double SECONDS_PER_DAY_TICK = 3.6;
	/**
	 * How far past a sustained wind limit a gust has to reach to trip a machine on its own.
	 *
	 * A fifth, and it is here because every machine that supervises wind needs it and they must agree.
	 * Datasheets quote wind limits against a ten-minute mean - 25 m/s to take a turbine off load, 20 to
	 * put a tracker flat - and pair each with a gust figure about a fifth higher. Supervising the gust
	 * against the mean's own number instead is the mistake: a three-second gust runs about 1.4 times the
	 * mean, so a row would go flat whenever the mean passed fourteen, which in this world is often enough
	 * to leave a tracker lying down for minutes at a time with nothing visibly wrong.
	 */
	public static final double GUST_TRIP_RATIO = 1.2;

	private WorldConditions() {
	}
}
