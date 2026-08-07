package com.dooji.electricity.api;

/** The handful of numbers describing the world that every machine and the weather both live in. */
public final class WorldConditions {
	/** Metres of real world one block of Minecraft stands for. */
	public static final double METRES_PER_BLOCK = 10.0;

	/** Air density at sea level and 15 C, kg/m3. */
	public static final double REFERENCE_AIR_DENSITY = 1.225;

	/** Standard mean sea level pressure, hPa. */
	public static final double SEA_LEVEL_PRESSURE = 1013.25;

	/** Seconds of real sky one tick of Minecraft's day clock stands for: 86400 over a 24000 tick day. */
	public static final double SECONDS_PER_DAY_TICK = 3.6;
	/** How far past a sustained wind limit a gust has to reach to trip a machine on its own. */
	public static final double GUST_TRIP_RATIO = 1.2;

	private WorldConditions() {
	}
}
