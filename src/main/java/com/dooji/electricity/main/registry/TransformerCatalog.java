package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.TransformerSpec;
import com.dooji.electricity.api.power.TransformerSpec.Duty;
import com.dooji.electricity.main.Electricity;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * The two transformers, which are the two steps a plant's output really takes.
 *
 * A wind generator makes 690 V and a solar inverter 800 V - both low voltage, which is the thing that
 * surprises people: there is no such thing as a machine that generates at medium voltage. So the chain is
 * the same for both technologies and it is two transformers, not one:
 *
 * <ol>
 * <li>a machine unit at each machine's foot, 0.8 kV to 33 kV, which is what makes a collector network
 *     possible at all - at 800 V the cable to carry two megawatts a kilometre would be absurd;</li>
 * <li>a substation unit at the grid connection, 33 kV to 400 kV, and only from there do the towers
 *     start.</li>
 * </ol>
 *
 * The figures are off real nameplates. A 2500 kVA pad-mount unit loses about 1.1 % at full load and
 * burns 2.4 kW doing nothing; a 63 MVA grid transformer loses 0.55 % and burns 28 kW. Which is why a
 * plant that is switched off still costs something to keep energised.
 */
public final class TransformerCatalog {
	private static final Map<ResourceLocation, TransformerSpec> BY_ID = new LinkedHashMap<>();

	public static final TransformerSpec MACHINE = register(new TransformerSpec(
			id("tx_machine"), "VT-2500 Pad", Duty.MACHINE,
			2500.0, 800.0, 33_000.0, 0.011, 2.4, "tx_machine", 3));

	public static final TransformerSpec SUBSTATION = register(new TransformerSpec(
			id("tx_substation"), "VT-63000 Grid", Duty.SUBSTATION,
			63_000.0, 33_000.0, 400_000.0, 0.0055, 28.0, "tx_substation", 6));

	private TransformerCatalog() {
	}

	private static ResourceLocation id(String path) {
		return new ResourceLocation(Electricity.MOD_ID, path);
	}

	private static TransformerSpec register(TransformerSpec spec) {
		BY_ID.put(spec.id(), spec);
		return spec;
	}

	public static Collection<TransformerSpec> all() {
		return List.copyOf(BY_ID.values());
	}

	@Nullable
	public static TransformerSpec get(ResourceLocation id) {
		return BY_ID.get(id);
	}
}
