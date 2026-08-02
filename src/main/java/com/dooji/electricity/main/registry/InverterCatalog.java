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
 * The designation carries the AC kilowatts, exactly as an {@code SG110CX} or a
 * {@code SUN2000-100KTL} does, and the letter says what kind of machine it is: X for a string
 * inverter and C for a central one. The maker is {@link #MANUFACTURER}.
 *
 * <h2>String against central, which is the real argument</h2>
 *
 * A string inverter has one maximum power point tracker per few strings - nine on the 110, sixteen on
 * the 350 - so a shaded row, a soiled row or a row with a failed module only drags down its own
 * tracker. A central inverter has one tracker for the whole plant, so the weakest string in three
 * megawatts sets the operating point for all of them. In exchange it is cheaper per watt, it lives in
 * one place a technician can work in, and it can hold a power factor the grid asks for across the
 * whole site at once.
 *
 * That is the difference the MPPT count in this catalogue exists to express, and it is why the
 * central machine's window is narrow and high - 875 to 1325 volts - while a string machine will track
 * anything from 200 volts up: a central inverter is designed around one string length and the strings
 * are built to suit it. Which is a real constraint here too, and the panel says so: the only array in
 * the catalogue wired long enough to feed the central machine is the thin-film rack, because two hundred
 * and twenty-three volts a module fills a string in six.
 *
 * The string terminal count is declared beside the tracker count for the same reason. A string inverter
 * has two or three terminals per tracker; a central one has a single tracker and several hundred
 * terminals, because the strings arrive through combiner boxes aggregating sixteen to thirty-two each.
 * Deriving one from the other would have given the central machine two string inputs and made it
 * useless.
 *
 * <h2>Why the DC input is half again the AC nameplate</h2>
 *
 * Because that is what an economically sized array looks like, and the vendors say so on the front
 * page. A plant built at a DC-to-AC ratio of 1.3 to 1.55 throws away the top of the best few hours of
 * the best days - the clipping every one of these machines will do - and in exchange its inverter is
 * loaded properly for the rest of the year instead of idling at a third of nameplate all winter. The
 * optimum sits near 1.4, and the two or three percent of the year it costs is cheaper than the
 * inverter it saves.
 */
public final class InverterCatalog {
	/** The fictional maker whose initial the model numbers carry. */
	public static final String MANUFACTURER = "Volterra";

	private static final Map<ResourceLocation, InverterSpec> BY_ID = new LinkedHashMap<>();

	/**
	 * Ten kilowatts, two trackers, fan-less.
	 *
	 * A residential machine: it hangs on a wall, it cools through its own casing, and it draws a watt
	 * overnight. Two trackers is what a house needs, because a roof usually has two orientations.
	 */
	public static final InverterSpec VX_10 = register(new InverterSpec(
			id("inverter_10"), "VX-10K",
			10.0, 11.0, 15.0,
			2, 4, 140.0, 980.0, 1100.0, 200.0,
			0.986, 0.45,
			1.0,
			400.0, 50.0, 0.8,
			45.0, 60.0, InverterSpec.Cooling.NATURAL));

	/**
	 * A hundred and ten kilowatts, nine trackers, 1000 volt strings.
	 *
	 * The commercial workhorse, and the machine most of this catalogue's arrays are sized against: at
	 * a DC-to-AC ratio of 1.4 it wants about eight flat tables or fifteen tracker slices in front of
	 * it. Nine trackers is enough that one shaded corner of a roof cannot pull the rest down.
	 */
	public static final InverterSpec VX_110 = register(new InverterSpec(
			id("inverter_110"), "VX-110K",
			110.0, 121.0, 165.0,
			9, 18, 200.0, 1000.0, 1100.0, 200.0,
			0.987, 0.50,
			2.0,
			800.0, 50.0, 0.8,
			45.0, 60.0, InverterSpec.Cooling.FAN));

	/**
	 * Three hundred and fifty kilowatts, sixteen trackers, 1500 volt strings.
	 *
	 * What a utility plant is built out of now, and the 1500 volt input is the reason: longer strings
	 * mean fewer of them, so less copper and fewer terminations for the same array. Ninety-nine
	 * percent peak efficiency, which is about the limit of what silicon carbide can do.
	 */
	public static final InverterSpec VX_350 = register(new InverterSpec(
			id("inverter_350"), "VX-350K",
			352.0, 387.0, 528.0,
			16, 32, 500.0, 1500.0, 1500.0, 550.0,
			0.990, 0.50,
			3.0,
			800.0, 50.0, 0.8,
			50.0, 60.0, InverterSpec.Cooling.FAN));

	/**
	 * Two and a half megawatts in one cabinet, one tracker for the lot.
	 *
	 * The central machine, and everything about it follows from having one maximum power point tracker
	 * for three megawatts of glass: a narrow high input window that the strings are designed around, a
	 * hundred watts of overnight consumption because there is a great deal inside to keep alive, and
	 * an output at 690 volts because the transformer it feeds is standing next to it.
	 *
	 * It is not a better inverter, it is a different bet: cheaper per watt and utterly dependent on
	 * every string in the plant behaving alike.
	 */
	public static final InverterSpec VC_2500 = register(new InverterSpec(
			id("inverter_2500"), "VC-2500K",
			2500.0, 2750.0, 3125.0,
			1, 288, 875.0, 1325.0, 1500.0, 875.0,
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

	/** Every inverter, smallest first. */
	public static List<InverterSpec> all() {
		return List.copyOf(BY_ID.values());
	}

	/** Full designation as a nameplate prints it, e.g. {@code Volterra VX-110K}. */
	public static String fullName(InverterSpec spec) {
		return MANUFACTURER + " " + spec.displayName();
	}

	/** The machine an inverter falls back to when its saved id cannot be resolved. */
	public static InverterSpec fallback() {
		return VX_110;
	}
}
