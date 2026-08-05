package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.DcCableSpec;
import com.dooji.electricity.main.Electricity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * The two direct-current cables the mod ships.
  *
 * Cable is sold by section, so the designation carries it, exactly as {@code H1Z2Z2-K 1x6} does.
 */
public final class CableCatalog {
	/** The fictional maker whose reels these come off. */
	public static final String MANUFACTURER = "Cabria";

	private static final Map<ResourceLocation, DcCableSpec> BY_ID = new LinkedHashMap<>();

	/** Six square millimetres of tinned copper: the cable a string is wired with. */
	public static final DcCableSpec STRING_6 = register(new DcCableSpec(
			id("dc_string_cable"), "SC-6",
			6.0, 1500.0,
			70.0, 0.86,
			4.32,
			false));

	/** Two hundred and forty square millimetres: the trunk out of a combiner box. */
	public static final DcCableSpec TRUNK_240 = register(new DcCableSpec(
			id("dc_trunk_cable"), "DT-240",
			240.0, 1500.0,
			570.0, 0.82,
			0.0961,
			true));

	private CableCatalog() {
	}

	private static ResourceLocation id(String path) {
		return new ResourceLocation(Electricity.MOD_ID, path);
	}

	private static DcCableSpec register(DcCableSpec spec) {
		BY_ID.put(spec.id(), spec);
		return spec;
	}

	public static List<DcCableSpec> all() {
		return List.copyOf(BY_ID.values());
	}

	public static DcCableSpec byId(ResourceLocation id) {
		return BY_ID.get(id);
	}

	/** Full designation as a reel's label prints it. */
	public static String fullName(DcCableSpec spec) {
		return MANUFACTURER + " " + spec.displayName();
	}
}
