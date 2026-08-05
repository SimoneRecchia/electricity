package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.ConductorSpec;
import com.dooji.electricity.api.power.ConductorSpec.VoltageClass;
import com.dooji.electricity.main.Electricity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** The three overhead line conductors, which are three real products. */
public final class ConductorCatalog {
	private static final Map<ResourceLocation, ConductorSpec> BY_ID = new LinkedHashMap<>();

	/** Aerial bundled cable, 4 by 70 mm2, to NF C 33-209. */
	public static final ConductorSpec ABC_70 = register(new ConductorSpec(
			id("abc_conductor"), "NFA2X 4x70",
			VoltageClass.LOW,
			70.0, 1000.0, 185.0, 0.443,
			1, 0.0,
			0.055, 0x232428,
			0.055, 40.0, 4));

	/** All-aluminium-alloy conductor, 228 mm2, bare. */
	public static final ConductorSpec AAAC_228 = register(new ConductorSpec(
			id("mv_conductor"), "Aster 228",
			VoltageClass.MEDIUM,
			228.0, 24000.0, 555.0, 0.146,
			1, 0.0,
			0.032, 0x9BA0A6,
			0.030, 90.0, 8));

	/** Aluminium conductor steel-reinforced, 4 by 592 mm2 - a quad Curlew bundle. */
	public static final ConductorSpec ACSR_592_QUAD = register(new ConductorSpec(
			id("hv_conductor"), "Curlew ACSR, quad",
			VoltageClass.HIGH,
			592.0, 420000.0, 1150.0, 0.0136,
			4, 0.045,
			0.026, 0xA8ADB4,
			0.022, 160.0, 16));

	private ConductorCatalog() {
	}

	private static ResourceLocation id(String path) {
		return new ResourceLocation(Electricity.MOD_ID, path);
	}

	private static ConductorSpec register(ConductorSpec spec) {
		BY_ID.put(spec.id(), spec);
		return spec;
	}

	public static Collection<ConductorSpec> all() {
		return List.copyOf(BY_ID.values());
	}

	@Nullable
	public static ConductorSpec get(ResourceLocation id) {
		return BY_ID.get(id);
	}

	/** The spec a saved connection names, or null if it names something this build has not got. */
	@Nullable
	public static ConductorSpec byPath(String path) {
		if (path == null || path.isEmpty() || path.equals("default")) return null;

		return BY_ID.get(path.indexOf(':') >= 0 ? new ResourceLocation(path) : id(path));
	}

	/** Every spec of one voltage class, in the order they are declared. */
	public static List<ConductorSpec> ofClass(VoltageClass voltageClass) {
		List<ConductorSpec> out = new ArrayList<>();
		for (ConductorSpec spec : BY_ID.values()) {
			if (spec.voltageClass() == voltageClass) out.add(spec);
		}

		return List.copyOf(out);
	}
}
