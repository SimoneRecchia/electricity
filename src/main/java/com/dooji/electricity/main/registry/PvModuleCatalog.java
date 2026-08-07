package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.PvModuleSpec;
import com.dooji.electricity.main.Electricity;
import net.minecraft.resources.ResourceLocation;

/** The photovoltaic modules the mod ships */
public final class PvModuleCatalog {
	/** The fictional maker whose initial the model numbers carry. */
	public static final String MANUFACTURER = "Helion";


	/** The p-type workhorse: 108 half-cut PERC cells, 415 W. */
	public static final PvModuleSpec HP_108_415 = new PvModuleSpec(
			id("hp_108_415"), "HP-108-415", PvModuleSpec.Cell.PERC, 108,
			1.722, 1.134, 0.030, 21.5,
			415.0, 31.40, 13.22, 37.60, 13.93,
			-0.0034, -0.0027, 0.00048,
			45.0, 0.70, 1000.0, 20.0);

	/** Heterojunction, 132 half-cut cells, 430 W. */
	public static final PvModuleSpec HJ_132_430 = new PvModuleSpec(
			id("hj_132_430"), "HJ-132-430", PvModuleSpec.Cell.HJT, 132,
			1.730, 1.118, 0.030, 21.5,
			430.0, 33.00, 13.03, 39.50, 13.85,
			-0.0024, -0.0024, 0.00040,
			44.0, 0.0, 1000.0, 20.0);

	/** Back contact, 66 whole cells, 440 W. */
	public static final PvModuleSpec HB_066_440 = new PvModuleSpec(
			id("hb_066_440"), "HB-066-440", PvModuleSpec.Cell.IBC, 66,
			1.872, 1.032, 0.040, 22.5,
			440.0, 67.00, 6.57, 80.50, 6.98,
			-0.0027, -0.00235, 0.00050,
			43.0, 0.0, 1500.0, 20.0);

	/** Thin film cadmium telluride, 268 cells, 530 W. */
	public static final PvModuleSpec HT_268_530 = new PvModuleSpec(
			id("ht_268_530"), "HT-268-530", PvModuleSpec.Cell.CDTE, 268,
			2.300, 1.216, 0.032, 39.7,
			530.0, 183.00, 2.90, 223.00, 3.19,
			-0.0032, -0.0028, 0.00040,
			45.0, 0.0, 1500.0, 5.0);

	/** The utility standard: 144 half-cut TOPCon cells, 580 W, bifacial and dual glass. */
	public static final PvModuleSpec HN_144_580 = new PvModuleSpec(
			id("hn_144_580"), "HN-144-580", PvModuleSpec.Cell.TOPCON, 144,
			2.278, 1.134, 0.035, 32.0,
			580.0, 43.52, 13.33, 51.19, 14.06,
			-0.0029, -0.0025, 0.00045,
			45.0, 0.80, 1500.0, 25.0);

	/** The large format: 132 half-cut cells off 210 mm wafers, 700 W. */
	public static final PvModuleSpec HN_132_700 = new PvModuleSpec(
			id("hn_132_700"), "HN-132-700", PvModuleSpec.Cell.TOPCON, 132,
			2.384, 1.303, 0.033, 38.3,
			700.0, 39.60, 17.68, 46.70, 18.75,
			-0.0029, -0.0025, 0.00045,
			43.0, 0.80, 1500.0, 30.0);

	private PvModuleCatalog() {
	}

	private static ResourceLocation id(String path) {
		return new ResourceLocation(Electricity.MOD_ID, path);
	}

	/** Full designation as a nameplate prints it. */
	public static String fullName(PvModuleSpec spec) {
		return MANUFACTURER + " " + spec.displayName();
	}
}
