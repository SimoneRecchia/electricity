package com.dooji.electricity.main;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ElectricityServerConfig {
	private static final ForgeConfigSpec SERVER_SPEC_INTERNAL;
	private static final ForgeConfigSpec.IntValue POWER_BOX_RADIUS;
	private static final ForgeConfigSpec.BooleanValue EXTERNAL_ENERGY_ENABLED;
	private static final ForgeConfigSpec.DoubleValue TURBINE_EXPORT_FRACTION;
	private static final ForgeConfigSpec.DoubleValue TURBINE_MAX_JOULES_PER_TICK;

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
		builder.push("power");
		POWER_BOX_RADIUS = builder.comment("Radius (in blocks) of power field around a Power Box").defineInRange("powerBoxRadius", 5, 1, 16);
		builder.pop();
		builder.push("energy");
		EXTERNAL_ENERGY_ENABLED = builder.comment("Let generators feed the energy systems of other mods (Mekanism Joules and Forge Energy) directly").define("externalEnergyEnabled", true);
		TURBINE_EXPORT_FRACTION = builder.comment(
				"Share of its nameplate power a Wind Turbine will offer to another mod's cables, 0 to 1.",
				"This is the balance dial, and it is a fraction rather than a fixed number of Joules because",
				"the turbines now range from 10 kW to 4 MW: any absolute cap either strangles the large",
				"machines or hands the small ones more than they make.",
				"The default of 0.2 keeps the ratio the mod already shipped with - the original single turbine",
				"made 9844 J/t and was allowed to export 2000 of it - so an upgrade ladder still means",
				"something on the bridge without a 4 MW machine flattening a modpack's economy.",
				"Raise it to 1.0 to make turbines full-output exporters.",
				"Whatever is not taken stays on the wire network either way."
		).defineInRange("turbineExportFraction", 0.2, 0.0, 1.0);
		TURBINE_MAX_JOULES_PER_TICK = builder.comment(
				"Optional absolute ceiling, in Joules per tick, applied after turbineExportFraction.",
				"0 disables it, which is the default: the fraction above is the intended dial and this exists",
				"for packs that need a hard number regardless of which machines are installed.",
				"For reference, at 125 J per kW a 4 MW turbine makes 500000 J/t before either limit."
		).defineInRange("turbineMaxJoulesPerTick", 0.0, 0.0, 1.0e9);
		builder.pop();
		SERVER_SPEC_INTERNAL = builder.build();
	}

	private ElectricityServerConfig() {
	}

	public static ForgeConfigSpec spec() {
		return SERVER_SPEC_INTERNAL;
	}

	public static int powerBoxRadius() {
		return POWER_BOX_RADIUS.get();
	}

	public static boolean externalEnergyEnabled() {
		return EXTERNAL_ENERGY_ENABLED.get();
	}

	public static double turbineExportFraction() {
		return TURBINE_EXPORT_FRACTION.get();
	}

	/** The absolute ceiling, or {@link Double#MAX_VALUE} when it is switched off. */
	public static double turbineMaxJoulesPerTick() {
		double configured = TURBINE_MAX_JOULES_PER_TICK.get();
		return configured <= 0.0 ? Double.MAX_VALUE : configured;
	}
}
