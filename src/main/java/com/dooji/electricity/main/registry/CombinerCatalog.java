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
 * The designation carries the number of ways, exactly as a {@code PVS-16} or a {@code CB-24} does.
 */
public final class CombinerCatalog {
	private static final Map<ResourceLocation, CombinerSpec> BY_ID = new LinkedHashMap<>();

	/** Six ways, 1000 volts, twenty amp holders. */
	public static final CombinerSpec CB_6 = register(new CombinerSpec(
			id("pv_combiner_6"), "CB-6",
			6, 1000.0, 20.0, 125.0,
			true, false, "IP65", 0.5));

	/** Sixteen ways, 1500 volts, thirty amp holders, four hundred amp output. */
	public static final CombinerSpec CB_16 = register(new CombinerSpec(
			id("pv_combiner_16"), "CB-16",
			16, 1500.0, 30.0, 400.0,
			true, true, "IP65", 2.0));

	/** Thirty-two ways, 1500 volts, six hundred and thirty amp output. */
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

	/** Full designation as the label prints it. */
	public static String fullName(CombinerSpec spec) {
		return InverterCatalog.MANUFACTURER + " " + spec.displayName();
	}
}
