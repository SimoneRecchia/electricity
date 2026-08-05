package com.dooji.electricity.api.power;

/** How an array is held up, which decides more about its output than the modules bolted to it. */
public enum PvMounting {
	/** Flat on the ground or a roof */
	FLAT("flat", false, 0.0, 0.85, 0.08),
	/** Fixed racking at a tilt, on driven piers or ground screws. */
	FIXED_TILT("fixed_tilt", false, 25.0, 0.45, 0.14),
	/** Horizontal single-axis: modules on a torque tube running north-south, rotating east to west. */
	SINGLE_AXIS("single_axis", true, 0.0, 0.40, 0.20),
	/** Azimuth-elevation dual axis on a pedestal. */
	DUAL_AXIS("dual_axis", true, 0.0, 0.25, 0.22);

	private final String key;
	private final boolean tracking;
	private final double fixedTiltDeg;
	private final double typicalGroundCover;
	/** Fraction of the ground-reflected light a bifacial module's back can see. */
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

	/** Tilt from horizontal for a mounting that does not move. */
	public double fixedTiltDeg() {
		return fixedTiltDeg;
	}

	public double typicalGroundCover() {
		return typicalGroundCover;
	}

	public double rearViewFactor() {
		return rearViewFactor;
	}

	/** Whether snow slides off a plane at this tilt on its own. */
	public static final double SNOW_SHED_TILT = 30.0;
}
