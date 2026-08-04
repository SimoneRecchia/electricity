package com.dooji.electricity.api.power;

/**
 * How an array is held up, which decides more about its output than the modules bolted to it.
 *
 * Four things follow from the mounting and nothing else: how much of the ground the modules
 * cover, how much of the sky and the ground each module can see, whether snow slides off, and
 * whether the plane follows the sun. The first is why a tracker carries a third less nameplate
 * per hectare than a flat roof does, and the last is why it earns it back.
 *
 * <h2>Ground cover, and why the numbers differ so much</h2>
 *
 * A flat array can be packed almost solid, because a plane lying flat casts no shadow on its
 * neighbour. Tilt it and it does, so the rows have to be spaced - and a tracker, which spends
 * the morning and evening tilted hard over, needs more space still. These are the published
 * ranges: 0.40 to 0.60 fixed tilt, 0.28 to 0.50 for a horizontal single axis, 0.20 to 0.30
 * dual axis, and up to 0.95 on a flat ballasted roof.
 */
public enum PvMounting {
	/**
	 * Flat on the ground or a roof, ballasted rather than bolted down.
	 *
	 * Nearly the optimum in this world and nowhere else, which is worth stating because it is
	 * the one place the mod's sky is unlike ours: Minecraft's sun passes through the zenith
	 * every day, so a horizontal plane is square to it at noon and there is no tilt that would
	 * do better on the year. On Earth a flat array gives up a tenth of the year's energy to a
	 * tilted one and never dries out or sheds snow.
	 */
	FLAT("flat", false, 0.0, 0.85, 0.08),
	/**
	 * Fixed racking at a tilt, on driven piers or ground screws.
	 *
	 * Twenty-five degrees, which is not an optimisation - a zenith sun would want zero - but a
	 * maintenance decision, and a real one: below about thirty degrees snow can sit on a module
	 * for a whole season, and rain that runs off a tilted module washes it while rain on a flat
	 * one pools and dries dirty. So the tilt costs a few percent at noon and buys back the
	 * soiling and the snow, which is the trade every rooftop installer makes.
	 */
	FIXED_TILT("fixed_tilt", false, 25.0, 0.45, 0.14),
	/**
	 * Horizontal single-axis: modules on a torque tube running north-south, rotating east to west.
	 *
	 * The utility standard, and in this world it is the whole of tracking: with the sun in one
	 * vertical plane all day, a north-south axis can hold the module square to it from the
	 * moment it clears the rotation limit until it goes back under. What the plant sees is not a
	 * higher peak - the peak is the same - but a plateau instead of a bell.
	 */
	SINGLE_AXIS("single_axis", true, 0.0, 0.40, 0.20),
	/**
	 * Azimuth-elevation dual axis on a pedestal.
	 *
	 * Honest about what it is worth here: the second axis has almost nothing to do, because the
	 * sun never leaves the east-west plane, so there is no azimuth travel to sell. What it does
	 * buy is reach - a frame that tilts to eighty degrees faces a sun ten degrees off the
	 * horizon, where a row limited to sixty degrees of rotation has already given up - and that
	 * is the first and last hour of every day. It pays for it in ground cover, which is why it
	 * carries barely a third of the flat array's nameplate on the same block.
	 */
	DUAL_AXIS("dual_axis", true, 0.0, 0.25, 0.22);

	private final String key;
	private final boolean tracking;
	private final double fixedTiltDeg;
	private final double typicalGroundCover;
	/**
	 * Fraction of the ground-reflected light a bifacial module's back can see.
	 *
	 * The view factor, and it is mostly about height: a module lying flat on the ground sees
	 * almost nothing behind it, while an elevated tracker several feet up sees a wide patch of
	 * lit ground either side. Published figures run 0.10 to 0.20 for fixed tilt and higher for
	 * trackers, which is a large part of why bifacial and tracking are sold together.
	 */
	private final double rearViewFactor;

	PvMounting(String key, boolean tracking, double fixedTiltDeg, double typicalGroundCover, double rearViewFactor) {
		this.key = key;
		this.tracking = tracking;
		this.fixedTiltDeg = fixedTiltDeg;
		this.typicalGroundCover = typicalGroundCover;
		this.rearViewFactor = rearViewFactor;
	}

	public String key() {
		return key;
	}

	public boolean tracking() {
		return tracking;
	}

	/** Tilt from horizontal for a mounting that does not move. Zero for the tracked ones, whose tilt is a reading. */
	public double fixedTiltDeg() {
		return fixedTiltDeg;
	}

	public double typicalGroundCover() {
		return typicalGroundCover;
	}

	public double rearViewFactor() {
		return rearViewFactor;
	}

	/**
	 * Whether snow slides off a plane at this tilt on its own.
	 *
	 * Thirty degrees is the threshold the field data puts it at: elevated modules at 30 and 45
	 * degrees shed within a day or two and lose 5 to 12 percent of the year, while below thirty
	 * a module that gets covered early in the winter can stay covered until spring. A tracker
	 * beats both, because it can be commanded to a snow stow far steeper than any fixed racking
	 * would be built at.
	 */
	public static final double SNOW_SHED_TILT = 30.0;
}
