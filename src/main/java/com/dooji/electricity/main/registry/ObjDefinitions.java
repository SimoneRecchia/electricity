package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.PvMounting;
import com.dooji.electricity.block.ElectricCabinBlock;
import com.dooji.electricity.block.LatticeTowerBlock;
import com.dooji.electricity.block.MetStationBlock;
import com.dooji.electricity.block.PowerBoxBlock;
import com.dooji.electricity.block.PvArrayBlock;
import com.dooji.electricity.block.PvCombinerBlock;
import com.dooji.electricity.block.PvInverterBlock;
import com.dooji.electricity.block.SwitchgearBlock;
import com.dooji.electricity.block.TransformerBlock;
import com.dooji.electricity.block.UtilityPoleBlock;
import com.dooji.electricity.block.WindTurbineBlock;
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
		ALL.add(new ObjBlockDefinition(Electricity.UTILITY_POLE_BLOCK.get(), new ResourceLocation(Electricity.MOD_ID, "models/utility_pole/utility_pole.obj"), UtilityPoleBlock.AUTHORED, List.of(
				"insulator_1_porcelain",
				"insulator_2_porcelain",
				"insulator_3_porcelain",
				"insulator_4_porcelain",
				"insulator_5_porcelain",
				"insulator_6_porcelain",
				"insulator_7_porcelain",
				"insulator_8_porcelain"
		)));

		ALL.add(new ObjBlockDefinition(Electricity.POWER_BOX_BLOCK.get(), new ResourceLocation(Electricity.MOD_ID, "models/power_box/power_box.obj"), PowerBoxBlock.AUTHORED, List.of(
				"insulator_porcelain"
		)));

		ALL.add(new ObjBlockDefinition(Electricity.ELECTRIC_CABIN_BLOCK.get(), new ResourceLocation(Electricity.MOD_ID, "models/electric_cab/cab.obj"), ElectricCabinBlock.AUTHORED, List.of(
				"insulator_input_porcelain",
				"insulator_output_porcelain"
		)));

		// Every machine in the catalogue draws the same model: the renderer scales it per
		ResourceLocation turbineModel = new ResourceLocation(Electricity.MOD_ID, "models/wind_turbine/wind_turbine.obj");
		for (var spec : TurbineCatalog.all()) {
			Block block = Electricity.TURBINE_BLOCKS.get(spec.id()).get();
			ALL.add(new ObjBlockDefinition(block, turbineModel, WindTurbineBlock.AUTHORED, List.of("insulator_Plastic")));
		}

		// One model per mounting rather than per product
		for (var spec : PvCatalog.all()) {
			Block block = Electricity.PV_ARRAY_BLOCKS.get(spec.id()).get();
			ALL.add(new ObjBlockDefinition(block, arrayModel(spec.mounting()), PvArrayBlock.AUTHORED, List.of()));
		}

		// The inverters share one cabinet
		ResourceLocation inverterModel = new ResourceLocation(Electricity.MOD_ID, "models/pv_inverter/pv_inverter.obj");
		for (var spec : InverterCatalog.all()) {
			Block block = Electricity.PV_INVERTER_BLOCKS.get(spec.id()).get();
			ALL.add(new ObjBlockDefinition(block, inverterModel, PvInverterBlock.AUTHORED, List.of("insulator_instrument")));
		}

		// The combiner boxes share one model: the difference between a six-way box and a thirty-two way
		ResourceLocation combinerModel = new ResourceLocation(Electricity.MOD_ID, "models/pv_combiner/pv_combiner.obj");
		for (var spec : CombinerCatalog.all()) {
			Block block = Electricity.PV_COMBINER_BLOCKS.get(spec.id()).get();
			ALL.add(new ObjBlockDefinition(block, combinerModel, PvCombinerBlock.AUTHORED, List.of()));
		}

		// The bushings, low-voltage side first: three on a machine unit, six on a substation one.
		for (var spec : TransformerCatalog.all()) {
			Block block = Electricity.TRANSFORMER_BLOCKS.get(spec.id()).get();
			List<String> bushings = new ArrayList<>();
			for (int i = 1; i <= spec.bushings(); i++) bushings.add("bushing_" + i + "_porcelain");
			ALL.add(new ObjBlockDefinition(block,
					new ResourceLocation(Electricity.MOD_ID, "models/" + spec.modelName() + "/" + spec.modelName() + ".obj"),
					TransformerBlock.AUTHORED, List.copyOf(bushings)));
		}

		// The six phase fittings of a tower, in the order gen_tower_models writes PHASES: the lower arm
		// outward-in, then the upper arm. A wire is stored against the index, so this order is fixed.
		for (var spec : TowerCatalog.all()) {
			Block block = Electricity.LATTICE_TOWER_BLOCKS.get(spec.id()).get();
			ALL.add(new ObjBlockDefinition(block,
					new ResourceLocation(Electricity.MOD_ID, "models/" + spec.modelName() + "/" + spec.modelName() + ".obj"),
					LatticeTowerBlock.AUTHORED,
					List.of("insulator_1_porcelain", "insulator_2_porcelain", "insulator_3_porcelain",
							"insulator_4_porcelain", "insulator_5_porcelain", "insulator_6_porcelain")));
		}

		// The six fittings of a switch: the line side first, then the load side, which is the order
		// SwitchgearBlockEntity.busOf splits them on.
		for (var spec : SwitchgearCatalog.all()) {
			Block block = Electricity.SWITCHGEAR_BLOCKS.get(spec.id()).get();
			List<String> fittings = new ArrayList<>();
			for (int i = 1; i <= spec.fittings(); i++) fittings.add("insulator_" + i + "_porcelain");
			ALL.add(new ObjBlockDefinition(block,
					new ResourceLocation(Electricity.MOD_ID, "models/" + spec.modelName() + "/" + spec.modelName() + ".obj"),
					SwitchgearBlock.AUTHORED, List.copyOf(fittings)));
		}

		ALL.add(new ObjBlockDefinition(Electricity.MET_STATION_BLOCK.get(),
				new ResourceLocation(Electricity.MOD_ID, "models/met_mast/met_mast.obj"),
				MetStationBlock.AUTHORED, List.of()));
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
