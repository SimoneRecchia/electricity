package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.InverterSpec;
import com.dooji.electricity.main.Electricity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * The inverters the mod ships, smallest first.
  *
 * The maker is {@link #MANUFACTURER}.
 */
public final class InverterCatalog {
	/** The fictional maker whose initial the model numbers carry. */
	public static final String MANUFACTURER = "Volterra";

	private static final Map<ResourceLocation, InverterSpec> BY_ID = new LinkedHashMap<>();

	/** Ten kilowatts, two trackers, one regulated fan. */
	public static final InverterSpec VX_10 = register(new InverterSpec(
			id("inverter_10"), "VX-10K",
			10.0, 11.0, 15.0,
			2, 4, 26.0, true, false, 140.0, 980.0, 1100.0, 200.0,
			0.986, 0.45,
			1.0,
			400.0, 50.0, 0.8,
			45.0, 60.0, InverterSpec.Cooling.FAN));

	/** A hundred and ten kilowatts, nine trackers, 1000 volt strings. */
	public static final InverterSpec VX_110 = register(new InverterSpec(
			id("inverter_110"), "VX-110K",
			110.0, 121.0, 165.0,
			9, 18, 26.0, true, false, 200.0, 1000.0, 1100.0, 200.0,
			0.987, 0.50,
			2.0,
			800.0, 50.0, 0.8,
			45.0, 60.0, InverterSpec.Cooling.FAN));

	/** Three hundred and fifty kilowatts, sixteen trackers, 1500 volt strings. */
	public static final InverterSpec VX_350 = register(new InverterSpec(
			id("inverter_350"), "VX-350K",
			352.0, 387.0, 528.0,
			16, 32, 30.0, true, true, 500.0, 1500.0, 1500.0, 550.0,
			0.990, 0.50,
			3.0,
			800.0, 50.0, 0.8,
			50.0, 60.0, InverterSpec.Cooling.FAN));

	/** Two and a half megawatts in one cabinet, one tracker for the lot. */
	public static final InverterSpec VC_2500 = register(new InverterSpec(
			id("inverter_2500"), "VC-2500K",
			2500.0, 2750.0, 3125.0,
			1, 288, 2500.0, false, true, 875.0, 1325.0, 1500.0, 875.0,
			0.989, 0.50,
			100.0,
			690.0, 50.0, 0.8,
			50.0, 62.0, InverterSpec.Cooling.FORCED_AIR));

	private InverterCatalog() {
	}

	private static ResourceLocation id(String path) {
		return new ResourceLocation(Electricity.MOD_ID, path);
	}

	private static InverterSpec register(InverterSpec spec) {
		BY_ID.put(spec.id(), spec);
		return spec;
	}

	/** Every inverter */
	public static List<InverterSpec> all() {
		return List.copyOf(BY_ID.values());
	}

	/** Full designation as a nameplate prints it. */
	public static String fullName(InverterSpec spec) {
		return MANUFACTURER + " " + spec.displayName();
	}

	/** The machine an inverter falls back to when its saved id cannot be resolved. */
	public static InverterSpec fallback() {
		return VX_110;
	}
}
