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
 * Cable is sold by section, so the designation carries it, exactly as {@code H1Z2Z2-K 1x6} does. The
 * maker is {@link #MANUFACTURER} - a different one from the machines, because nobody buys their cable
 * from their inverter vendor.
 *
 * <h2>Where each one belongs</h2>
 *
 * The string cable is what leaves an array: clipped along the racking in stainless or polyvinylidene
 * fluoride hangers, in free air under the modules, out to whatever collects it. Plastic cable ties are
 * what fails on a real plant - they go brittle under ultraviolet and drop the run into the dirt - which
 * is why the fittings are metal and why this cable is drawn lying in a clip rather than hanging.
 *
 * The trunk is what leaves a combiner box, carrying everything in it: sixteen strings at eighteen amps
 * is nearly three hundred, and no plug takes that. It is lugged onto a busbar at both ends.
 *
 * <h2>The figures</h2>
 *
 * Resistance is IEC 60228 at 20 °C multiplied by 1.275 for a conductor at 90 °C, which is the
 * temperature the current rating is defined at. Current is reference method E - a single cable in free
 * air - at 60 °C ambient, because that is what a cable clipped under a module in the sun sits in and
 * it is the ambient every solar cable datasheet quotes.
 */
public final class CableCatalog {
	/** The fictional maker whose reels these come off. */
	public static final String MANUFACTURER = "Cabria";

	private static final Map<ResourceLocation, DcCableSpec> BY_ID = new LinkedHashMap<>();

	/**
	 * Six square millimetres of tinned copper: the cable a string is wired with.
	 *
	 * Seventy amps against a string's eighteen, which sounds like four times more cable than anyone
	 * needs and is not: the section is chosen for volt drop over a long run rather than for the
	 * current, and 4 mm² is what a plant that skimped on it uses. It loses a seventh of its rating in a
	 * trench, which is less than the trunk does, because a thin cable has less heat to get rid of per
	 * millimetre of surface.
	 */
	public static final DcCableSpec STRING_6 = register(new DcCableSpec(
			id("dc_string_cable"), "SC-6",
			6.0, 1500.0,
			70.0, 0.86,
			4.32,
			false));

	/**
	 * Two hundred and forty square millimetres: the trunk out of a combiner box.
	 *
	 * Five hundred and seventy amps in free air and four hundred and seventy in the ground, which is
	 * the constraint that decides how many strings one trunk can actually carry - a thirty-two way
	 * combiner fills it, and a buried one over-fills it. Forty-five times the section of the string
	 * cable for forty-five times less resistance per metre, which is the entire reason a plant
	 * aggregates before it travels.
	 */
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

	/** Full designation as a reel's label prints it, e.g. {@code Cabria SC-6}. */
	public static String fullName(DcCableSpec spec) {
		return MANUFACTURER + " " + spec.displayName();
	}
}
