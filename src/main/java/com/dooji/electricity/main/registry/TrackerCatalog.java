package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.TrackerSpec;
import com.dooji.electricity.main.Electricity;
import net.minecraft.resources.ResourceLocation;

/** The trackers the mod ships. */
public final class TrackerCatalog {
	/** The fictional maker whose racking and trackers the arrays are built on. */
	public static final String MANUFACTURER = "Meridian";


	/** Horizontal single axis, one drive per row, self-powered. */
	public static final TrackerSpec HORIZON_R = new TrackerSpec(
			id("tracker_horizon_r"), "Horizon R", 1,
			60.0, 0.0,
			4.4, 2.0,
			0.0, 0.0, 60.0,
			20.0, 0.04,
			true, 120.0);

	/** Azimuth-elevation dual axis on a pedestal. */
	public static final TrackerSpec ZENITH_AE = new TrackerSpec(
			id("tracker_zenith_ae"), "Zenith AE", 2,
			80.0, 10.0,
			3.0, 1.0,
			0.0, 0.0, 70.0,
			18.0, 0.04,
			true, 250.0);

	private TrackerCatalog() {
	}

	private static ResourceLocation id(String path) {
		return new ResourceLocation(Electricity.MOD_ID, path);
	}

	/** Full name as a sell sheet prints it. */
	public static String fullName(TrackerSpec spec) {
		return MANUFACTURER + " " + spec.displayName();
	}
}
