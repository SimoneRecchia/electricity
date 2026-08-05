package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.PvArraySpec;
import com.dooji.electricity.api.power.PvMounting;
import com.dooji.electricity.main.Electricity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** The array products the mod ships, in the order a player builds them. */
public final class PvCatalog {
	private static final Map<ResourceLocation, PvArraySpec> BY_ID = new LinkedHashMap<>();

	/** The entry product: cheap PERC modules lying flat, forty-four to a block. */
	public static final PvArraySpec FT_415 = register(new PvArraySpec(
			id("solar_panel"), "FT-415", PvModuleCatalog.HP_108_415, PvMounting.FLAT, null,
			22, 2));

	/** The same flat table with heterojunction modules. */
	public static final PvArraySpec FT_430 = register(new PvArraySpec(
			id("pv_flat_430"), "FT-430", PvModuleCatalog.HJ_132_430, PvMounting.FLAT, null,
			22, 2));

	/** Tilted rack, bifacial utility modules, one string of eighteen. */
	public static final PvArraySpec TR_580 = register(new PvArraySpec(
			id("pv_tilt_580"), "TR-580", PvModuleCatalog.HN_144_580, PvMounting.FIXED_TILT, null,
			18, 1));

	/** Tilted rack, thin film, three strings of six. */
	public static final PvArraySpec TR_530 = register(new PvArraySpec(
			id("pv_tilt_530"), "TR-530", PvModuleCatalog.HT_268_530, PvMounting.FIXED_TILT, null,
			6, 3));

	/** Horizontal single-axis tracker, large-format bifacial modules, one string of thirteen. */
	public static final PvArraySpec HX_700 = register(new PvArraySpec(
			id("pv_track_700"), "HX-700", PvModuleCatalog.HN_132_700, PvMounting.SINGLE_AXIS, TrackerCatalog.HORIZON_R,
			13, 1));

	/** Azimuth-elevation dual axis, back-contact modules, one string of thirteen. */
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

	/** Every array product */
	public static List<PvArraySpec> all() {
		return List.copyOf(BY_ID.values());
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
