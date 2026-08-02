package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.PvArraySpec;
import com.dooji.electricity.api.power.PvMounting;
import com.dooji.electricity.main.Electricity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * The array products the mod ships, in the order a player builds them.
 *
 * An array product is a module on a mounting with the wiring worked out, which is how the industry
 * actually sells one: nobody buys a tracker and then decides what to put on it. The designation is
 * the mounting code and the module's watts - FT for a flat table, TR for a tilted rack, HX for a
 * horizontal single axis, AE for azimuth-elevation - so a TR-580 is 580 W modules on a tilted rack.
 *
 * <h2>How the module count was arrived at</h2>
 *
 * Not chosen. A block is a hundred square metres of ground and a module has a real area, so the count
 * is however many of that module the mounting can cover the block with, and the string layout is
 * however they divide up under the system voltage limit at the coldest cell temperature. Both
 * constraints are checked rather than asserted: {@link PvArraySpec} refuses a count whose ground
 * cover is not what that mounting is built at, and {@link PvArraySpec#stringFitsSystemVoltage} is the
 * other half of the same check.
 *
 * The result is the trade the whole catalogue is about. A flat table carries eighteen kilowatts on a
 * block and gives a bell-shaped day. A tilted rack carries ten and sheds snow. A single-axis tracker
 * carries nine and gives a plateau instead of a bell. A dual-axis frame carries under six and
 * collects the first and last hour that everything else spills. Per block the flat table wins; per
 * kilowatt installed the tracker wins; which of those is the scarce thing is the player's problem,
 * exactly as it is a developer's.
 *
 * <h2>A block is a slice, not a row</h2>
 *
 * Worth stating because the geometry is otherwise puzzling: a real tracker row is seventy-eight to
 * ninety modules on one torque tube, which is a hundred metres long and would span ten blocks. So a
 * block of tracker is a slice of a row rather than a row, and the thirteen modules on it are the
 * thirteen that happen to stand on those hundred square metres. Placing a line of them is building
 * one row.
 */
public final class PvCatalog {
	private static final Map<ResourceLocation, PvArraySpec> BY_ID = new LinkedHashMap<>();

	/**
	 * The entry product: cheap PERC modules lying flat, forty-four to a block.
	 *
	 * This is the one the placeholder solar panel always was, and it keeps that block's registry name
	 * so existing worlds load unchanged. Its figures are near enough the placeholder's assertion -
	 * eighteen kilowatts against the twenty it claimed, at 215 W per square metre of module against
	 * the 200 it assumed - which is a good sign about the assertion rather than about this.
	 *
	 * Two strings of twenty-two, which is what fits under a 1000 V system limit once the modules are
	 * cold: at minus ten the open-circuit voltage of this module is up to 41 V from 37.6, and
	 * twenty-two of those is 905 V.
	 */
	public static final PvArraySpec FT_415 = register(new PvArraySpec(
			id("solar_panel"), "FT-415", PvModuleCatalog.HP_108_415, PvMounting.FLAT, null,
			22, 2));

	/**
	 * The same flat table with heterojunction modules.
	 *
	 * Identical land, identical layout, three quarters of a percent more nameplate - and a machine
	 * that behaves quite differently once it is hot, which is the entire reason to buy it. On a
	 * desert afternoon at seventy degrees of cell temperature the PERC table has given up 15% of its
	 * rating and this one 11%.
	 */
	public static final PvArraySpec FT_430 = register(new PvArraySpec(
			id("pv_flat_430"), "FT-430", PvModuleCatalog.HJ_132_430, PvMounting.FLAT, null,
			22, 2));

	/**
	 * Tilted rack, bifacial utility modules, one string of eighteen.
	 *
	 * Half the nameplate of a flat table on the same block, because tilted rows have to be spaced so
	 * they do not shade each other. What it buys is everything a tilt buys: rain runs off and washes
	 * the glass instead of pooling and drying dirty, snow slides off instead of sitting until spring,
	 * and the bifacial modules see appreciably more lit ground behind them than they would lying flat.
	 */
	public static final PvArraySpec TR_580 = register(new PvArraySpec(
			id("pv_tilt_580"), "TR-580", PvModuleCatalog.HN_144_580, PvMounting.FIXED_TILT, null,
			18, 1));

	/**
	 * Tilted rack, thin film, four strings of four.
	 *
	 * The lowest nameplate per block of any fixed array here, and the point is that nameplate is not
	 * what a plant is paid for. This module runs cooler than the silicon ones, holds its efficiency
	 * further down into weak light, and has a spectral response that is barely troubled by the
	 * overcast which costs a silicon array a third of its day. Four to a string rather than eighteen,
	 * because two hundred and twenty-three volts a module fills a string very quickly.
	 */
	public static final PvArraySpec TR_530 = register(new PvArraySpec(
			id("pv_tilt_530"), "TR-530", PvModuleCatalog.HT_268_530, PvMounting.FIXED_TILT, null,
			4, 4));

	/**
	 * Horizontal single-axis tracker, large-format bifacial modules, one string of thirteen.
	 *
	 * The utility standard, and the one machine here whose output curve is a different shape rather
	 * than a different size: a plateau from the moment it clears its rotation limit in the morning to
	 * the moment it goes back under in the evening. It also gets the most out of a bifacial module of
	 * anything in the catalogue, because an elevated tracker's back sees more lit ground than a
	 * fixed rack's and far more than a flat table's.
	 */
	public static final PvArraySpec HX_700 = register(new PvArraySpec(
			id("pv_track_700"), "HX-700", PvModuleCatalog.HN_132_700, PvMounting.SINGLE_AXIS, TrackerCatalog.HORIZON_R,
			13, 1));

	/**
	 * Azimuth-elevation dual axis, back-contact modules, one string of thirteen.
	 *
	 * The most expensive way to cover a block and the least nameplate on it, and it is in the
	 * catalogue because of the one thing it does that nothing else does: it faces a sun ten degrees
	 * off the horizon. Every other mounting here spills the first and last hour of the day, the flat
	 * ones to the cosine and the single-axis ones to their rotation limit.
	 *
	 * Its second axis is nearly idle in this world - the sun never leaves the east-west plane - so
	 * what a player is buying is the elevation travel and the stow positions, not the azimuth drive.
	 * That is said out loud in the documentation rather than left to be discovered.
	 */
	public static final PvArraySpec AE_440 = register(new PvArraySpec(
			id("pv_dual_440"), "AE-440", PvModuleCatalog.HB_066_440, PvMounting.DUAL_AXIS, TrackerCatalog.ZENITH_AE,
			13, 1));

	private PvCatalog() {
	}

	private static ResourceLocation id(String path) {
		return new ResourceLocation(Electricity.MOD_ID, path);
	}

	private static PvArraySpec register(PvArraySpec spec) {
		BY_ID.put(spec.id(), spec);
		return spec;
	}

	/** Every array product, cheapest first. */
	public static List<PvArraySpec> all() {
		return List.copyOf(BY_ID.values());
	}

	public static PvArraySpec byId(ResourceLocation id) {
		return BY_ID.get(id);
	}

	/** Full designation as a nameplate prints it, e.g. {@code Meridian TR-580}. */
	public static String fullName(PvArraySpec spec) {
		return TrackerCatalog.MANUFACTURER + " " + spec.displayName();
	}

	/**
	 * The product an array falls back to when its saved id cannot be resolved.
	 *
	 * The flat PERC table, for the same reason the C130 is the turbines' fallback: it is what the
	 * single original solar panel block already was, so a world older than this catalogue reads back
	 * as the thing it actually contained.
	 */
	public static PvArraySpec fallback() {
		return FT_415;
	}
}
