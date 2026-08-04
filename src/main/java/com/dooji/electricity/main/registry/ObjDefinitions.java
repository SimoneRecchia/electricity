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

		// The eight pin insulators, in the order tools/gen_grid_models.py writes them: the lower arm's
		// four and then the upper arm's, inner pair before outer, negative side first. The order is the
		// index a wire is stored against, so it is the one thing about the pole that cannot be
		// rearranged without moving every wire in every world that has one.
		ALL.add(new ObjBlockDefinition(Electricity.UTILITY_POLE_BLOCK.get(), new ResourceLocation(Electricity.MOD_ID, "models/utility_pole/utility_pole.obj"), List.of(
				"insulator_1_porcelain",
				"insulator_2_porcelain",
				"insulator_3_porcelain",
				"insulator_4_porcelain",
				"insulator_5_porcelain",
				"insulator_6_porcelain",
				"insulator_7_porcelain",
				"insulator_8_porcelain"
		)));

		ALL.add(new ObjBlockDefinition(Electricity.POWER_BOX_BLOCK.get(), new ResourceLocation(Electricity.MOD_ID, "models/power_box/power_box.obj"), List.of(
				"insulator_porcelain"
		)));

		// The lamp draws itself from OBJ like the machines rather than from a JSON cube, because its
		// column is round and the vanilla format cannot express a cylinder at all. No wire fitting: a
		// lamp is fed by the power field in a radius, not by a line.
		ALL.add(new ObjBlockDefinition(Electricity.ELECTRIC_LAMP_BLOCK.get(),
				new ResourceLocation(Electricity.MOD_ID, "models/electric_lamp/electric_lamp.obj"), List.of()));

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
