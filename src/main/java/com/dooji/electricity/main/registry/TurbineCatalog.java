package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.TurbineSpec;
import com.dooji.electricity.main.Electricity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * The machines the mod ships, in the order a player builds them.
 *
 * The naming follows the industry convention rather than inventing one: the letter
 * is the manufacturer and the number is the rotor diameter in metres, exactly as a
 * Vestas V90, a Nordex N90 or an Enercon E-82. Here the maker is
 * {@link #MANUFACTURER}, so a C90 has a 90 m rotor. The suffix is the nameplate in
 * MW, which is why two machines can share a rotor size or a generator — the C90-3.0
 * and the C112-3.0 are the same generator behind different rotors, and that is the
 * whole lesson of the IEC class printed beside them: the wider rotor reaches its
 * plateau in less wind and belongs on a quiet site, while the narrower one needs a
 * windy one to be worth its generator.
 *
 * Every figure below was checked against the machine it is drawn from. The rotor speed
 * ranges are the published ones - a V52 runs 14.0 to 31.4 rpm, a V80 9 to 19, a V90 9.9 to
 * 18.4, a V112 6.2 to 17.7 - which is why they are declared rather than derived from one
 * shared tip speed limit: 80 m/s is right for a V80 and thirty percent short of what a
 * V112's longer blades are allowed. Cut-in speeds are theirs too, and the C130 cuts out at
 * 22.5 m/s like the rest of its platform rather than at 25.
 *
 * Rated wind speed stays derived, from the curve rather than declared beside it, and now
 * lands on the published figures: 14.6 to 15.5 m/s across the C line against Vestas figures
 * of 15 and 16. The catalogue is a default rather than the whole story - these are ordinary
 * data, and a datapack can add machines beside them.
 */
public final class TurbineCatalog {
	/** The fictional maker whose initial the model numbers carry. */
	public static final String MANUFACTURER = "Cube";

	private static final Map<ResourceLocation, TurbineSpec> BY_ID = new LinkedHashMap<>();

	/**
	 * The 10 kW machine, and the only one outside the C line.
	 *
	 * Small wind is a different industry from utility scale, with its own naming and
	 * its own engineering: a tail vane instead of yaw motors, a rotor turning far
	 * faster, and an efficiency no utility machine would accept. It is also the one
	 * entry whose rotor is drawn larger than the scale says, because seven metres
	 * divided by ten is smaller than its own nacelle.
	 */
	public static final TurbineSpec SW_10 = register(new TurbineSpec(
			id("sw_10"), "SW-10", "",
			10.0, 7.0, 0.35,
			3.0, 25.0, 25.0,
			60.0, 250.0,
			2, 4,
			3.3, TurbineSpec.Nacelle.SMALL_WIND));

	/** High-wind class: a small rotor worked hard, for an exposed site. */
	public static final TurbineSpec C52_085 = register(new TurbineSpec(
			id("c52_085"), "C52-0.85", "IA",
			850.0, 52.0, 0.44,
			4.0, 25.0, 22.0,
			14.0, 31.4,
			4, 7,
			1.0, TurbineSpec.Nacelle.UTILITY));

	public static final TurbineSpec C80_20 = register(new TurbineSpec(
			id("c80_20"), "C80-2.0", "IIA",
			2000.0, 80.0, 0.46,
			4.0, 25.0, 22.0,
			9.0, 19.0,
			6, 10,
			1.0, TurbineSpec.Nacelle.UTILITY));

	public static final TurbineSpec C90_30 = register(new TurbineSpec(
			id("c90_30"), "C90-3.0", "IIA",
			3000.0, 90.0, 0.46,
			3.5, 25.0, 22.0,
			9.9, 18.4,
			8, 11,
			1.0, TurbineSpec.Nacelle.UTILITY));

	/** The C90's generator behind a rotor a fifth wider: same nameplate, reached in less wind. */
	public static final TurbineSpec C112_30 = register(new TurbineSpec(
			id("c112_30"), "C112-3.0", "IIIA",
			3000.0, 112.0, 0.47,
			3.0, 25.0, 22.0,
			6.2, 17.7,
			8, 12,
			1.0, TurbineSpec.Nacelle.UTILITY));

	/**
	 * The top of the line, and the machine the authored model already was: its rotor
	 * measures 13.044 blocks from the hub, so this is the one that draws at 1:1 and
	 * every other model is it scaled down.
	 */
	public static final TurbineSpec C130_40 = register(new TurbineSpec(
			id("c130_40"), "C130-4.0", "IIIA",
			4000.0, 130.0, 0.47,
			3.0, 25.0, 22.5,
			5.5, 14.0,
			9, 13,
			1.0, TurbineSpec.Nacelle.UTILITY));

	private TurbineCatalog() {
	}

	private static ResourceLocation id(String path) {
		return new ResourceLocation(Electricity.MOD_ID, path);
	}

	private static TurbineSpec register(TurbineSpec spec) {
		BY_ID.put(spec.id(), spec);
		return spec;
	}

	/** Every known machine, cheapest first. */
	public static List<TurbineSpec> all() {
		return List.copyOf(BY_ID.values());
	}

	/** Full designation as a nameplate prints it, e.g. {@code Cube C130-4.0}. */
	public static String fullName(TurbineSpec spec) {
		return MANUFACTURER + " " + spec.displayName();
	}

	/**
	 * The machine a turbine falls back to when its saved model cannot be resolved — an
	 * id from a datapack that is no longer loaded, or a world older than the catalogue.
	 * The C130 is the right answer for the second case: it is the machine the single
	 * original turbine block already was, geometrically.
	 */
	public static TurbineSpec fallback() {
		return C130_40;
	}
}
