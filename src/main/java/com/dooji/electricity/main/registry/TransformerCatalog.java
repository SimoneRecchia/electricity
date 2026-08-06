package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.TransformerSpec;
import com.dooji.electricity.api.power.TransformerSpec.Duty;
import static com.dooji.electricity.main.registry.Catalogue.id;
import java.util.List;

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
	private static final Catalogue<TransformerSpec> TRANSFORMERS = new Catalogue<>(TransformerSpec::id);

	public static final TransformerSpec MACHINE = TRANSFORMERS.register(new TransformerSpec(
			id("tx_machine"), "VT-2500 Pad", Duty.MACHINE,
			2500.0, 800.0, 33_000.0, 0.011, 2.4, "tx_machine", 3));

	public static final TransformerSpec SUBSTATION = TRANSFORMERS.register(new TransformerSpec(
			id("tx_substation"), "VT-63000 Grid", Duty.SUBSTATION,
			63_000.0, 33_000.0, 400_000.0, 0.0055, 28.0, "tx_substation", 6));

	private TransformerCatalog() {
	}

	public static List<TransformerSpec> all() {
		return TRANSFORMERS.all();
	}

}
