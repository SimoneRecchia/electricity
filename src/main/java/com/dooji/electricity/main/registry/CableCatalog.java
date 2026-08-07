package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.DcCableSpec;
import static com.dooji.electricity.main.registry.Catalogue.id;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * The two direct-current cables the mod ships.
  *
 * Cable is sold by section, so the designation carries it, exactly as {@code H1Z2Z2-K 1x6} does.
 */
public final class CableCatalog {
	/** The fictional maker whose reels these come off. */
	public static final String MANUFACTURER = "Cabria";

	private static final Catalogue<DcCableSpec> CABLES = new Catalogue<>(DcCableSpec::id);

	/** Six square millimetres of tinned copper: the cable a string is wired with. */
	public static final DcCableSpec STRING_6 = CABLES.register(new DcCableSpec(
			id("dc_string_cable"), "SC-6",
			6.0, 1500.0,
			70.0, 0.86,
			4.32,
			false));

	/** Two hundred and forty square millimetres: the trunk out of a combiner box. */
	public static final DcCableSpec TRUNK_240 = CABLES.register(new DcCableSpec(
			id("dc_trunk_cable"), "DT-240",
			240.0, 1500.0,
			570.0, 0.82,
			0.0961,
			true));

	private CableCatalog() {
	}

	public static List<DcCableSpec> all() {
		return CABLES.all();
	}

	public static DcCableSpec byId(ResourceLocation id) {
		return CABLES.byId(id);
	}

	/** Full designation as a reel's label prints it. */
	public static String fullName(DcCableSpec spec) {
		return MANUFACTURER + " " + spec.displayName();
	}
}
