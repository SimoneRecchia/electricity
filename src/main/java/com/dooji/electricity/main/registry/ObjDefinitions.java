package com.dooji.electricity.main.registry;

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
