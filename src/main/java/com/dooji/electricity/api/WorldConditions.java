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

	private WorldConditions() {
	}
}
