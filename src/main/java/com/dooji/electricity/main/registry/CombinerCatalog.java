package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.CombinerSpec;
import static com.dooji.electricity.main.registry.Catalogue.id;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * The combiner boxes the mod ships, smallest first.
  *
 * The designation carries the number of ways, exactly as a {@code PVS-16} or a {@code CB-24} does.
 */
public final class CombinerCatalog {
	private static final Catalogue<CombinerSpec> COMBINERS = new Catalogue<>(CombinerSpec::id);

	/** Six ways, 1000 volts, twenty amp holders. */
	public static final CombinerSpec CB_6 = COMBINERS.register(new CombinerSpec(
			id("pv_combiner_6"), "CB-6",
			6, 1000.0, 20.0, 125.0,
			true, false, "IP65", 0.5));

	/** Sixteen ways, 1500 volts, thirty amp holders, four hundred amp output. */
	public static final CombinerSpec CB_16 = COMBINERS.register(new CombinerSpec(
			id("pv_combiner_16"), "CB-16",
			16, 1500.0, 30.0, 400.0,
			true, true, "IP65", 2.0));

	/** Thirty-two ways, 1500 volts, six hundred and thirty amp output. */
	public static final CombinerSpec CB_32 = COMBINERS.register(new CombinerSpec(
			id("pv_combiner_32"), "CB-32",
			32, 1500.0, 30.0, 630.0,
			true, true, "IP66", 3.0));

	private CombinerCatalog() {
	}

	public static List<CombinerSpec> all() {
		return COMBINERS.all();
	}

	public static CombinerSpec byId(ResourceLocation id) {
		return COMBINERS.byId(id);
	}

	/** Full designation as the label prints it. */
	public static String fullName(CombinerSpec spec) {
		return InverterCatalog.MANUFACTURER + " " + spec.displayName();
	}
}
