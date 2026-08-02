package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.CombinerSpec;
import com.dooji.electricity.main.Electricity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * The combiner boxes the mod ships, smallest first.
 *
 * The designation carries the number of ways, exactly as a {@code PVS-16} or a {@code CB-24} does. The
 * maker is {@link InverterCatalog#MANUFACTURER}, because an inverter vendor selling the switchgear that
 * feeds its own machines is not a shortcut here - it is what every one of them does.
 *
 * <h2>Why the small one is nearly useless, and why it is here anyway</h2>
 *
 * The six-way box is a 1000 volt product whose fuse holders stop at twenty amps, which was a perfectly
 * ordinary specification when a module made nine. A modern 210 mm cell module makes eighteen and three
 * quarters, and IEC 62548 wants the fuse at 1.4 times that - so no fuse that would protect such a string
 * will fit in this box, and it says so rather than quietly passing it. That is a real thing that happens
 * to real plants: the cheap box is the wrong box, and the reason is on both datasheets.
 */
public final class CombinerCatalog {
	private static final Map<ResourceLocation, CombinerSpec> BY_ID = new LinkedHashMap<>();

	/**
	 * Six ways, 1000 volts, twenty amp holders.
	 *
	 * A commercial rooftop box: no monitoring, one switch, and a rating that belongs to the decade before
	 * 1500 volt strings. It takes the two flat tables, whose strings need a nineteen and a half amp fuse
	 * and whose cold voltage stays under a thousand, and it refuses the 210 mm cell tracker outright: that
	 * string wants a twenty-six amp fuse and no holder here will take one.
	 */
	public static final CombinerSpec CB_6 = register(new CombinerSpec(
			id("pv_combiner_6"), "CB-6",
			6, 1000.0, 20.0, 125.0,
			true, false, "IP65", 0.5));

	/**
	 * Sixteen ways, 1500 volts, thirty amp holders, four hundred amp output.
	 *
	 * The utility workhorse, and the box the trunk cable is sized around: sixteen strings of a
	 * high-current module is two hundred and eighty-three amps, which is what a 240 mm² pair carries
	 * comfortably in free air. Thirty amp holders because that is what a 210 mm cell module needs - 1.4
	 * times its eighteen and three quarter amps of short-circuit current, which is twenty-six and a
	 * quarter, and the next standard size up is thirty.
	 */
	public static final CombinerSpec CB_16 = register(new CombinerSpec(
			id("pv_combiner_16"), "CB-16",
			16, 1500.0, 30.0, 400.0,
			true, true, "IP65", 2.0));

	/**
	 * Thirty-two ways, 1500 volts, six hundred and thirty amp output.
	 *
	 * Where the copper stops being the box's problem and starts being the trench's: thirty-two
	 * high-current strings is five hundred and sixty-six amps, which one 240 mm² pair carries in free
	 * air and does *not* carry buried. Filling this box and then digging its trunk in is the one design
	 * mistake in the whole plant that the cable notices before anything else does.
	 */
	public static final CombinerSpec CB_32 = register(new CombinerSpec(
			id("pv_combiner_32"), "CB-32",
			32, 1500.0, 30.0, 630.0,
			true, true, "IP66", 3.0));

	private CombinerCatalog() {
	}

	private static ResourceLocation id(String path) {
		return new ResourceLocation(Electricity.MOD_ID, path);
	}

	private static CombinerSpec register(CombinerSpec spec) {
		BY_ID.put(spec.id(), spec);
		return spec;
	}

	public static List<CombinerSpec> all() {
		return List.copyOf(BY_ID.values());
	}

	public static CombinerSpec byId(ResourceLocation id) {
		return BY_ID.get(id);
	}

	/** Full designation as the label prints it, e.g. {@code Volterra CB-16}. */
	public static String fullName(CombinerSpec spec) {
		return InverterCatalog.MANUFACTURER + " " + spec.displayName();
	}
}
