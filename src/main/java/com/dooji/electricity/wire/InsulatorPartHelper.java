package com.dooji.electricity.wire;

import com.dooji.electricity.block.ElectricCabinBlockEntity;
import com.dooji.electricity.block.PowerBoxBlockEntity;
import com.dooji.electricity.block.PvInverterBlockEntity;
import com.dooji.electricity.block.UtilityPoleBlockEntity;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public final class InsulatorPartHelper {
	public static final String TYPE_WIND_TURBINE = "wind_turbine";
	public static final String TYPE_ELECTRIC_CABIN = "electric_cabin";
	public static final String TYPE_UTILITY_POLE = "utility_pole";
	public static final String TYPE_POWER_BOX = "power_box";
	/** The photovoltaic plant's one connection to the grid. */
	public static final String TYPE_PV_INVERTER = "pv_inverter";

	private static final String[] UTILITY_POLE_PARTS = {"insulator_1_Material.023", "insulator_2_Material.009", "insulator_3_Material.016", "insulator_4_Material.001", "insulator_5_Material.051",
			"insulator_6_Material.037", "insulator_7_Material.030", "insulator_8_Material.058"};

	private static final String[] ELECTRIC_CABIN_PARTS = {"insulator_input_Material.065", "insulatoroutput_Material.044"};

	private static final String[] POWER_BOX_PARTS = {"insulator_Material"};

	private static final String[] WIND_TURBINE_PARTS = {"insulator_Plastic"};

	private static final String[] PV_INVERTER_PARTS = {"insulator_instrument"};

	private InsulatorPartHelper() {
	}

	public static Optional<Insulator> resolve(BlockEntity entity, String partName) {
		if (entity instanceof UtilityPoleBlockEntity pole) {
			return mapFromArray(TYPE_UTILITY_POLE, partName, UTILITY_POLE_PARTS, pole.getInsulatorIds(), pole::getWirePosition);
		} else if (entity instanceof ElectricCabinBlockEntity cabin) {
			return mapFromArray(TYPE_ELECTRIC_CABIN, partName, ELECTRIC_CABIN_PARTS, cabin.getInsulatorIds(), cabin::getWirePosition);
		} else if (entity instanceof PowerBoxBlockEntity powerBox) {
			return mapFromArray(TYPE_POWER_BOX, partName, POWER_BOX_PARTS, powerBox.getInsulatorIds(), powerBox::getWirePosition);
		} else if (entity instanceof WindTurbineBlockEntity turbine) {
			return mapFromArray(TYPE_WIND_TURBINE, partName, WIND_TURBINE_PARTS, turbine.getInsulatorIds(), turbine::getWirePosition);
		} else if (entity instanceof PvInverterBlockEntity inverter) {
			return mapFromArray(TYPE_PV_INVERTER, partName, PV_INVERTER_PARTS, inverter.getInsulatorIds(), inverter::getWirePosition);
		}

		return Optional.empty();
	}

	public static Optional<Insulator> resolve(BlockEntity entity, int insulatorId) {
		if (entity instanceof UtilityPoleBlockEntity pole) {
			return mapFromId(TYPE_UTILITY_POLE, insulatorId, UTILITY_POLE_PARTS, pole.getInsulatorIds(), pole::getWirePosition);
		} else if (entity instanceof ElectricCabinBlockEntity cabin) {
			return mapFromId(TYPE_ELECTRIC_CABIN, insulatorId, ELECTRIC_CABIN_PARTS, cabin.getInsulatorIds(), cabin::getWirePosition);
		} else if (entity instanceof PowerBoxBlockEntity powerBox) {
			return mapFromId(TYPE_POWER_BOX, insulatorId, POWER_BOX_PARTS, powerBox.getInsulatorIds(), powerBox::getWirePosition);
		} else if (entity instanceof WindTurbineBlockEntity turbine) {
			return mapFromId(TYPE_WIND_TURBINE, insulatorId, WIND_TURBINE_PARTS, turbine.getInsulatorIds(), turbine::getWirePosition);
		} else if (entity instanceof PvInverterBlockEntity inverter) {
			return mapFromId(TYPE_PV_INVERTER, insulatorId, PV_INVERTER_PARTS, inverter.getInsulatorIds(), inverter::getWirePosition);
		}

		return Optional.empty();
	}

	private static Optional<Insulator> mapFromArray(String blockType, String partName, String[] parts, int[] insulatorIds, PositionResolver resolver) {
		if (partName == null) return Optional.empty();

		int index = indexOf(parts, partName);
		if (index < 0 || index >= insulatorIds.length) return Optional.empty();

		int insulatorId = insulatorIds[index];
		return Optional.of(new Insulator(blockType, insulatorId, index, parts[index], resolver.resolve(index)));
	}

	private static Optional<Insulator> mapFromId(String blockType, int targetId, String[] partNames, int[] insulatorIds, PositionResolver resolver) {
		for (int i = 0; i < insulatorIds.length; i++) {
			if (insulatorIds[i] == targetId && targetId >= 0) {
				Vec3 anchor = resolver.resolve(i);
				String partName = partNames != null && i < partNames.length ? partNames[i] : null;
				return Optional.of(new Insulator(blockType, targetId, i, partName, anchor));
			}
		}

		return Optional.empty();
	}

	private static int indexOf(String[] parts, String partName) {
		for (int i = 0; i < parts.length; i++) {
			if (Objects.equals(parts[i], partName)) return i;
		}

		return -1;
	}

	public static String determinePowerType(BlockEntity entity, String partName) {
		// a generator's one fitting is an output, whichever kind of generator it is
		if (entity instanceof WindTurbineBlockEntity || entity instanceof PvInverterBlockEntity) return "output";
		if (entity instanceof ElectricCabinBlockEntity) {
			if (partName != null && partName.toLowerCase(Locale.ROOT).contains("output")) return "output";
			if (partName != null && partName.toLowerCase(Locale.ROOT).contains("input")) return "input";
		}

		return "bidirectional";
	}

	public static String getBlockType(BlockEntity entity) {
		if (entity instanceof WindTurbineBlockEntity) {
			return TYPE_WIND_TURBINE;
		} else if (entity instanceof ElectricCabinBlockEntity) {
			return TYPE_ELECTRIC_CABIN;
		} else if (entity instanceof UtilityPoleBlockEntity) {
			return TYPE_UTILITY_POLE;
		} else if (entity instanceof PowerBoxBlockEntity) {
			return TYPE_POWER_BOX;
		} else if (entity instanceof PvInverterBlockEntity) {
			return TYPE_PV_INVERTER;
		}

		return "unknown";
	}

	public static boolean matchesReportedType(BlockEntity entity, String reportedType) {
		return Objects.equals(getBlockType(entity), reportedType);
	}

	public static record Insulator(String blockType, int insulatorId, int index, String partName, Vec3 anchor) {
	}

	@FunctionalInterface
	private interface PositionResolver {
		Vec3 resolve(int index);
	}
}
