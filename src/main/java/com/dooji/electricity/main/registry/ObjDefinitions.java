package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.PvMounting;
import com.dooji.electricity.main.Electricity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public final class ObjDefinitions {
	private static final List<ObjBlockDefinition> ALL = new ArrayList<>();

	private ObjDefinitions() {
	}

	public static void bootstrap() {
		if (!ALL.isEmpty()) return;

		ALL.add(new ObjBlockDefinition(Electricity.UTILITY_POLE_BLOCK.get(), new ResourceLocation(Electricity.MOD_ID, "models/utility_pole/utility_pole.obj"), List.of(
				"insulator_1_Material.023",
				"insulator_2_Material.009",
				"insulator_3_Material.016",
				"insulator_4_Material.001",
				"insulator_5_Material.051",
				"insulator_6_Material.037",
				"insulator_7_Material.030",
				"insulator_8_Material.058"
		)));

		ALL.add(new ObjBlockDefinition(Electricity.POWER_BOX_BLOCK.get(), new ResourceLocation(Electricity.MOD_ID, "models/power_box/power_box.obj"), List.of(
				"insulator_Material"
		)));

		ALL.add(new ObjBlockDefinition(Electricity.ELECTRIC_CABIN_BLOCK.get(), new ResourceLocation(Electricity.MOD_ID, "models/electric_cab/cab.obj"), List.of(
				"insulator_input_Material.065",
				"insulatoroutput_Material.044"
		)));

		// Every machine in the catalogue draws the same model: the renderer scales it per
		// spec and stretches its tower to the built height, so the difference between a
		// C52 and a C130 on screen is a transform rather than a second asset.
		ResourceLocation turbineModel = new ResourceLocation(Electricity.MOD_ID, "models/wind_turbine/wind_turbine.obj");
		for (var spec : TurbineCatalog.all()) {
			Block block = Electricity.TURBINE_BLOCKS.get(spec.id()).get();
			ALL.add(new ObjBlockDefinition(block, turbineModel, List.of("insulator_Plastic")));
		}

		// One model per mounting rather than per product, because what a player sees is the
		// mounting: two products on the same racking differ by which modules are bolted to
		// it, and at a block's scale that is a texture rather than a shape. Arrays carry no
		// wire fitting - their direct current goes to an inverter by cable, not by insulator.
		for (var spec : PvCatalog.all()) {
			Block block = Electricity.PV_ARRAY_BLOCKS.get(spec.id()).get();
			ALL.add(new ObjBlockDefinition(block, arrayModel(spec.mounting()), List.of()));
		}

		// The inverters share one cabinet, scaled per nameplate the way the turbines share one
		// nacelle, and it is the only block here with a wire fitting on it: the inverter is
		// where a photovoltaic plant joins the grid.
		ResourceLocation inverterModel = new ResourceLocation(Electricity.MOD_ID, "models/pv_inverter/pv_inverter.obj");
		for (var spec : InverterCatalog.all()) {
			Block block = Electricity.PV_INVERTER_BLOCKS.get(spec.id()).get();
			ALL.add(new ObjBlockDefinition(block, inverterModel, List.of("insulator_instrument")));
		}

		// The combiner boxes share one model: the difference between a six-way box and a thirty-two way
		// one is the label on the door and the count on the panel, which is all it is in a catalogue
		// either. No wire fitting, because a combiner's output leaves on cable and not on a line.
		ResourceLocation combinerModel = new ResourceLocation(Electricity.MOD_ID, "models/pv_combiner/pv_combiner.obj");
		for (var spec : CombinerCatalog.all()) {
			Block block = Electricity.PV_COMBINER_BLOCKS.get(spec.id()).get();
			ALL.add(new ObjBlockDefinition(block, combinerModel, List.of()));
		}

		ALL.add(new ObjBlockDefinition(Electricity.MET_STATION_BLOCK.get(),
				new ResourceLocation(Electricity.MOD_ID, "models/met_mast/met_mast.obj"), List.of()));
	}

	private static ResourceLocation arrayModel(PvMounting mounting) {
		String name = switch (mounting) {
			case FLAT -> "pv_flat";
			case FIXED_TILT -> "pv_tilt";
			case SINGLE_AXIS -> "pv_track";
			case DUAL_AXIS -> "pv_dual";
		};

		return new ResourceLocation(Electricity.MOD_ID, "models/" + name + "/" + name + ".obj");
	}

	public static ObjBlockDefinition get(Block block) {
		if (ALL.isEmpty()) bootstrap();
		for (ObjBlockDefinition def : ALL) {
			if (def.block() == block) return def;
		}

		return null;
	}

	public static List<ObjBlockDefinition> all() {
		if (ALL.isEmpty()) bootstrap();
		return Collections.unmodifiableList(ALL);
	}
}
