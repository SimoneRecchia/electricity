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

/**
 * The three overhead line conductors, which are three real products.
 *
 * Every figure here is off a datasheet, and the three sit at the three voltages a plant's power actually
 * passes through on its way to the grid:
 *
 * <ol>
 * <li><b>Aerial bundled cable</b> at 1 kV - what a distributor strings down a street. Four insulated
 *     cores laid up round a bare messenger, so it is one black bundle rather than four wires, and it may
 *     be run within touching distance of a building.</li>
 * <li><b>All-aluminium-alloy conductor</b> at 24 kV - bare, one per phase, a metre apart on pin
 *     insulators. This is the plant's collector: what leaves a transformer at a wind turbine's base or an
 *     inverter's skid and goes to the grid substation.</li>
 * <li><b>Aluminium conductor steel-reinforced</b> at 420 kV, in a <i>quad bundle</i> - what leaves the
 *     substation on lattice towers. Four sub-conductors 45 cm apart on spacers, and the reason is corona
 *     rather than capacity: one conductor thick enough to carry the current would have a surface field
 *     high enough to ionise the air round it and buzz, and four thinner ones spread over a wider circle
 *     do not.</li>
 * </ol>
 *
 * The names are the real ones. NFA2X is the French designation every European distributor's ABC is sold
 * under; Aster 228 is the AAAC RTE strings its 20 kV network with; and Curlew is the ACSR of the British
 * 400 kV grid, which is why a quad-Curlew bundle is what a lattice tower here carries.
 */
public final class ConductorCatalog {
	private static final Map<ResourceLocation, ConductorSpec> BY_ID = new LinkedHashMap<>();

	/**
	 * Aerial bundled cable, 4 by 70 mm2, to NF C 33-209.
	 *
	 * Cheap per block and short-spanned, because that is what it is: a street cable on poles forty metres
	 * apart, which sags a lot because it is hung slack on purpose - a bundle pulled tight snaps its
	 * messenger at the first frost.
	 */
	public static final ConductorSpec ABC_70 = register(new ConductorSpec(
			id("abc_conductor"), "NFA2X 4x70",
			VoltageClass.LOW,
			70.0, 1000.0, 185.0, 0.443,
			1, 0.0,
			0.055, 0x232428,
			0.055, 40.0, 4));

	/**
	 * All-aluminium-alloy conductor, 228 mm2, bare.
	 *
	 * One wire a phase and no insulation at all, which is what medium voltage is: at 20 kV a metre of air
	 * is cheaper, lighter and more reliable than any polymer, and the only thing between two phases is
	 * the crossarm holding them apart.
	 */
	public static final ConductorSpec AAAC_228 = register(new ConductorSpec(
			id("mv_conductor"), "Aster 228",
			VoltageClass.MEDIUM,
			228.0, 24000.0, 555.0, 0.146,
			1, 0.0,
			0.032, 0x9BA0A6,
			0.030, 90.0, 8));

	/**
	 * Aluminium conductor steel-reinforced, 4 by 592 mm2 - a quad Curlew bundle.
	 *
	 * The heaviest thing in the mod's inventory per block, and it should be: one span of quad Curlew is
	 * two and a half tonnes of aluminium. Sags least of the three despite the weight, because a
	 * transmission line is tensioned to about a fifth of its breaking load and a street cable is not.
	 */
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

	/**
	 * The spec a saved connection names, or null if it names something this build has not got.
	 *
	 * Null rather than a default, and the callers treat it as "draw the old plain wire": a world saved by
	 * a build before the conductors existed has every connection typed {@code "default"}, and turning
	 * those into high-voltage bundles would put a quad ACSR on every garden pole in it.
	 */
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
