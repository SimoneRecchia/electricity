package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.PvModuleSpec;
import com.dooji.electricity.main.Electricity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * The photovoltaic modules the mod ships, cheapest first.
 *
 * The naming follows the industry convention rather than inventing one: a designation carries the
 * technology, the cell count and the watts, exactly as a {@code JKM580N-72HL4} or a
 * {@code TSM-DE21} does. Here the maker is {@link #MANUFACTURER} and the letter after it is the
 * cell type - P for the p-type workhorse, N for n-type, J for heterojunction, B for back contact,
 * T for thin film - so an HN-144-580 is an n-type module of a hundred and forty-four cells making
 * five hundred and eighty watts.
 *
 * Every figure below was checked against the module it is drawn from: dimensions to the millimetre,
 * the four points of the I-V curve, the three temperature coefficients, the NOCT and the
 * bifaciality. Efficiency is not in the list because it is not a figure a datasheet is free to
 * choose - it is the nameplate over the area, and it comes out at 22.5% for the two utility
 * modules, 22.8% for the back-contact one and 18.9% for the thin film, which are the published
 * numbers.
 *
 * <h2>Why six, and why these six</h2>
 *
 * Because the technologies genuinely behave differently and the catalogue is where that difference
 * gets to matter. The PERC module is cheap and loses a third of a percent of its output for every
 * degree; the heterojunction one loses a quarter and wins every hot afternoon. The back-contact
 * module packs the most watts into the least glass and does it at eighty volts, so it goes thirteen
 * to a string where others go twenty. And the thin film is two thirds the efficiency of any of them
 * and still the right answer somewhere: it runs cooler, it holds up better in weak light, and its
 * response is shifted blue, so under the sort of overcast that ruins a silicon plant it is barely
 * troubled.
 */
public final class PvModuleCatalog {
	/** The fictional maker whose initial the model numbers carry. */
	public static final String MANUFACTURER = "Helion";

	private static final Map<ResourceLocation, PvModuleSpec> BY_ID = new LinkedHashMap<>();

	/**
	 * The p-type workhorse: 108 half-cut PERC cells, 415 W.
	 *
	 * What the industry ran on for a decade and what a cheap array is still built from. Everything
	 * about it is a little worse than the n-type modules above it - the temperature coefficient, the
	 * low-light behaviour, the bifaciality - and it covers a block for less.
	 */
	public static final PvModuleSpec HP_108_415 = register(new PvModuleSpec(
			id("hp_108_415"), "HP-108-415", PvModuleSpec.Cell.PERC, 108,
			1.722, 1.134, 0.030, 21.5,
			415.0, 31.40, 13.22, 37.60, 13.93,
			-0.0034, -0.0027, 0.00048,
			45.0, 0.70, 1000.0, 20.0));

	/**
	 * Heterojunction, 132 half-cut cells, 430 W.
	 *
	 * The same size and nearly the same nameplate as the PERC module, and a different machine in
	 * heat: -0.24 %/K against -0.34 means it keeps three percent more of its rating at seventy
	 * degrees, every afternoon, for as long as it stands there. Monofacial, because the premium
	 * residential module it is drawn from is sold with a solid back sheet.
	 */
	public static final PvModuleSpec HJ_132_430 = register(new PvModuleSpec(
			id("hj_132_430"), "HJ-132-430", PvModuleSpec.Cell.HJT, 132,
			1.730, 1.118, 0.030, 21.5,
			430.0, 33.00, 13.03, 39.50, 13.85,
			-0.0024, -0.0024, 0.00040,
			44.0, 0.0, 1000.0, 20.0));

	/**
	 * Back contact, 66 whole cells, 440 W.
	 *
	 * The most watts of any module here in the least glass, because nothing on the front of the cell
	 * shades it. The consequence is electrical rather than optical: whole cells rather than halves
	 * means twice the voltage at half the current, so this module runs at eighty volts open circuit
	 * and seven amps, and a string of them is short and high.
	 */
	public static final PvModuleSpec HB_066_440 = register(new PvModuleSpec(
			id("hb_066_440"), "HB-066-440", PvModuleSpec.Cell.IBC, 66,
			1.872, 1.032, 0.040, 22.5,
			440.0, 67.00, 6.57, 80.50, 6.98,
			-0.0027, -0.00235, 0.00050,
			43.0, 0.0, 1500.0, 20.0));

	/**
	 * Thin film cadmium telluride, 268 cells, 530 W.
	 *
	 * The outlier, and the one worth reading the datasheet of twice. Two thirds the efficiency of
	 * the silicon modules, so it needs half again the glass for the same watts. Two hundred and
	 * twenty-three volts open circuit at three amps, so six of them fill a 1500 V string where a
	 * silicon module takes twenty-seven. The best temperature coefficient outside heterojunction,
	 * the flattest low-light curve of anything here, and a spectral response shifted towards the
	 * blue - which is why it does relatively *better* under the humid or overcast sky that costs the
	 * silicon modules most.
	 */
	public static final PvModuleSpec HT_268_530 = register(new PvModuleSpec(
			id("ht_268_530"), "HT-268-530", PvModuleSpec.Cell.CDTE, 268,
			2.300, 1.216, 0.032, 39.7,
			530.0, 183.00, 2.90, 223.00, 3.19,
			-0.0032, -0.0028, 0.00040,
			45.0, 0.0, 1500.0, 5.0));

	/**
	 * The utility standard: 144 half-cut TOPCon cells, 580 W, bifacial and dual glass.
	 *
	 * What most of the world's new ground-mounted capacity is made of. Eighty percent bifaciality,
	 * so what it is worth depends on what it is standing over - a couple of percent over grass and a
	 * tenth over snow.
	 */
	public static final PvModuleSpec HN_144_580 = register(new PvModuleSpec(
			id("hn_144_580"), "HN-144-580", PvModuleSpec.Cell.TOPCON, 144,
			2.278, 1.134, 0.035, 32.0,
			580.0, 43.52, 13.33, 51.19, 14.06,
			-0.0029, -0.0025, 0.00045,
			45.0, 0.80, 1500.0, 25.0));

	/**
	 * The large format: 132 half-cut cells off 210 mm wafers, 700 W.
	 *
	 * Three square metres and thirty-eight kilos, which is why it belongs on a tracker rather than a
	 * roof - two people can only just handle one. Seventeen and a half amps at the maximum power
	 * point, nearly a third more than the 580, so the same string count needs a heavier combiner and
	 * a machine that can swallow it.
	 */
	public static final PvModuleSpec HN_132_700 = register(new PvModuleSpec(
			id("hn_132_700"), "HN-132-700", PvModuleSpec.Cell.TOPCON, 132,
			2.384, 1.303, 0.033, 38.3,
			700.0, 39.60, 17.68, 46.70, 18.75,
			-0.0029, -0.0025, 0.00045,
			43.0, 0.80, 1500.0, 30.0));

	private PvModuleCatalog() {
	}

	private static ResourceLocation id(String path) {
		return new ResourceLocation(Electricity.MOD_ID, path);
	}

	private static PvModuleSpec register(PvModuleSpec spec) {
		BY_ID.put(spec.id(), spec);
		return spec;
	}

	public static List<PvModuleSpec> all() {
		return List.copyOf(BY_ID.values());
	}

	/** Full designation as a nameplate prints it, e.g. {@code Helion HN-144-580}. */
	public static String fullName(PvModuleSpec spec) {
		return MANUFACTURER + " " + spec.displayName();
	}
}
