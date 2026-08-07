package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.PvArraySpec;
import com.dooji.electricity.api.power.PvMounting;
import static com.dooji.electricity.main.registry.Catalogue.id;
import java.util.List;

/** The array products the mod ships, in the order a player builds them. */
public final class PvCatalog {
	private static final Catalogue<PvArraySpec> ARRAYS = new Catalogue<>(PvArraySpec::id);

	/** The entry product: cheap PERC modules lying flat, forty-four to a block. */
	public static final PvArraySpec FT_415 = ARRAYS.register(new PvArraySpec(
			id("solar_panel"), "FT-415", PvModuleCatalog.HP_108_415, PvMounting.FLAT, null,
			22, 2));

	/** The same flat table with heterojunction modules. */
	public static final PvArraySpec FT_430 = ARRAYS.register(new PvArraySpec(
			id("pv_flat_430"), "FT-430", PvModuleCatalog.HJ_132_430, PvMounting.FLAT, null,
			22, 2));

	/** Tilted rack, bifacial utility modules, one string of eighteen. */
	public static final PvArraySpec TR_580 = ARRAYS.register(new PvArraySpec(
			id("pv_tilt_580"), "TR-580", PvModuleCatalog.HN_144_580, PvMounting.FIXED_TILT, null,
			18, 1));

	/** Tilted rack, thin film, three strings of six. */
	public static final PvArraySpec TR_530 = ARRAYS.register(new PvArraySpec(
			id("pv_tilt_530"), "TR-530", PvModuleCatalog.HT_268_530, PvMounting.FIXED_TILT, null,
			6, 3));

	/** Horizontal single-axis tracker, large-format bifacial modules, one string of thirteen. */
	public static final PvArraySpec HX_700 = ARRAYS.register(new PvArraySpec(
			id("pv_track_700"), "HX-700", PvModuleCatalog.HN_132_700, PvMounting.SINGLE_AXIS, TrackerCatalog.HORIZON_R,
			13, 1));

	/** Azimuth-elevation dual axis, back-contact modules, one string of thirteen. */
	public static final PvArraySpec AE_440 = ARRAYS.register(new PvArraySpec(
			id("pv_dual_440"), "AE-440", PvModuleCatalog.HB_066_440, PvMounting.DUAL_AXIS, TrackerCatalog.ZENITH_AE,
			13, 1));

	private PvCatalog() {
	}

	/** Every array product */
	public static List<PvArraySpec> all() {
		return ARRAYS.all();
	}

	/** Full designation as a nameplate prints it. */
	public static String fullName(PvArraySpec spec) {
		return TrackerCatalog.MANUFACTURER + " " + spec.displayName();
	}

	/** The product an array falls back to when its saved id cannot be resolved. */
	public static PvArraySpec fallback() {
		return FT_415;
	}
}
