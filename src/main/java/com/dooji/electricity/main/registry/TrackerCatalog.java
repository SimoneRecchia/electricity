package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.TrackerSpec;
import com.dooji.electricity.main.Electricity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * The trackers the mod ships.
 *
 * Trackers are sold under names rather than numbers - a Horizon, a DuraTrack, a Voyager - because
 * what a buyer is choosing between is a mechanism rather than a rating. So these have names, and the
 * maker is {@link #MANUFACTURER}, the same one whose racking the fixed arrays sit on.
 *
 * <h2>The two mechanisms, and why the difference matters</h2>
 *
 * A row can have its own drive or share one. An independent row carries a small motor, a controller
 * and a battery, so it tracks alone, stows alone and keeps stowing through a grid outage - which is
 * the whole reason the self-powered design won: a linked block of rows whose one drive has failed is
 * a block of rows standing up in a storm. A linked row is cheaper per watt and has one drive to
 * maintain instead of forty, and that is a real trade rather than a wrong answer.
 *
 * <h2>What the second axis is worth here, honestly</h2>
 *
 * Almost nothing, and it is better to say so than to quietly inflate it. Minecraft's sun rises due
 * east, passes through the zenith and sets due west, so it never leaves one vertical plane: a
 * north-south horizontal axis can hold a plane square to it all day on its own, and an azimuth drive
 * has nothing left to chase.
 *
 * The dual-axis frame earns its keep on the one thing a single axis cannot do, and it is a real
 * thing that real controllers exploit: reach. A row that can only roll sixty degrees is square to
 * the sun only once the sun is thirty degrees up, and takes the first and last hour of every day at
 * an angle. A pedestal frame that tilts to eighty degrees is square to a sun ten degrees off the
 * horizon. That is worth a few percent of the day, and it costs a great deal of ground - which is
 * why the real world builds single-axis trackers by the gigawatt and dual-axis ones for
 * concentrators and car parks.
 */
public final class TrackerCatalog {
	/** The fictional maker whose racking and trackers the arrays are built on. */
	public static final String MANUFACTURER = "Meridian";

	private static final Map<ResourceLocation, TrackerSpec> BY_ID = new LinkedHashMap<>();

	/**
	 * Horizontal single axis, one drive per row, self-powered.
	 *
	 * Sixty degrees of rotation, backtracking, and the four stow positions a real controller has:
	 * flat overnight so the dew runs off, flat in wind because a flat plane gives a gust no lever to
	 * pull on, and steep in snow because standing up and dropping the lot is the one thing no fixed
	 * rack can do.
	 */
	public static final TrackerSpec HORIZON_R = register(new TrackerSpec(
			id("tracker_horizon_r"), "Horizon R", 1,
			60.0, 0.0,
			4.4,
			0.0, 0.0, 60.0,
			20.0, 0.04,
			true, 120.0));

	/**
	 * Horizontal single axis, rows mechanically linked to one drive.
	 *
	 * Fifty-two degrees rather than sixty, because a driveline that has to turn a long block of rows
	 * together is built stiffer and shorter of travel. Cheaper, and one motor's worth of power spread
	 * across a great deal of array - which is why the figure here is a fraction of the independent
	 * row's rather than a larger number.
	 */
	public static final TrackerSpec HORIZON_L = register(new TrackerSpec(
			id("tracker_horizon_l"), "Horizon L", 1,
			52.0, 0.0,
			4.0,
			0.0, 0.0, 52.0,
			22.0, 0.06,
			true, 55.0));

	/**
	 * Azimuth-elevation dual axis on a pedestal.
	 *
	 * Eighty degrees of tilt and an elevation drive that reaches a sun ten degrees off the horizon,
	 * which is the whole of its advantage in this world. Slower than the single-axis rows, because a
	 * pedestal frame carrying its modules on a mast has far more inertia to turn than a torque tube
	 * with the load balanced along it.
	 */
	public static final TrackerSpec ZENITH_AE = register(new TrackerSpec(
			id("tracker_zenith_ae"), "Zenith AE", 2,
			80.0, 10.0,
			3.0,
			0.0, 0.0, 70.0,
			18.0, 0.04,
			true, 250.0));

	private TrackerCatalog() {
	}

	private static ResourceLocation id(String path) {
		return new ResourceLocation(Electricity.MOD_ID, path);
	}

	private static TrackerSpec register(TrackerSpec spec) {
		BY_ID.put(spec.id(), spec);
		return spec;
	}

	public static List<TrackerSpec> all() {
		return List.copyOf(BY_ID.values());
	}

	/** Full name as a sell sheet prints it, e.g. {@code Meridian Horizon R}. */
	public static String fullName(TrackerSpec spec) {
		return MANUFACTURER + " " + spec.displayName();
	}
}
