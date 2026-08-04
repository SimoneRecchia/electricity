package com.dooji.electricity.wire;

import com.dooji.electricity.main.registry.ObjBlockDefinition;
import com.dooji.electricity.main.registry.ObjDefinitions;
import javax.annotation.Nullable;
import com.dooji.electricity.block.ElectricCabinBlockEntity;
import com.dooji.electricity.block.PowerBoxBlockEntity;
import com.dooji.electricity.block.PvInverterBlockEntity;
import com.dooji.electricity.block.UtilityPoleBlockEntity;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import java.util.List;
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

	/**
	 * The part names a device's insulators are drawn as, read off the model's own definition.
	 *
	 * There used to be a table per device here, restating the group names {@link ObjDefinitions} already
	 * declares - and when the pole and the power box were remodelled, those tables still named the
	 * groups of the models that had gone, so a wire clicked onto either of them resolved to nothing at
	 * all. One list, in one place, and the index into it is what a wire is stored against.
	 */
	private static List<String> parts(BlockEntity entity) {
		ObjBlockDefinition definition = ObjDefinitions.get(entity.getBlockState().getBlock());
		return definition == null ? List.of() : definition.insulators();
	}

	private InsulatorPartHelper() {
	}

	/**
	 * Which group of a model an insulator is drawn as, by index.
	 *
	 * The model's own list, in the order it declares them, which is why nothing here restates a part
	 * name: a device with four insulators has four entries and the fourth is index three. Null for an
	 * index the model does not have, which happens while a device is being built and its arrays have
	 * been sized before its definition has loaded.
	 */
	@Nullable
	public static String insulatorName(@Nullable ObjBlockDefinition definition, int index) {
		if (definition == null || index < 0 || index >= definition.insulators().size()) return null;

		return definition.insulators().get(index);
	}

	public static Optional<Insulator> resolve(BlockEntity entity, String partName) {
		if (entity instanceof UtilityPoleBlockEntity pole) {
			return mapFromArray(TYPE_UTILITY_POLE, partName, parts(pole), pole.getInsulatorIds(), pole::getWirePosition);
		} else if (entity instanceof ElectricCabinBlockEntity cabin) {
			return mapFromArray(TYPE_ELECTRIC_CABIN, partName, parts(cabin), cabin.getInsulatorIds(), cabin::getWirePosition);
		} else if (entity instanceof PowerBoxBlockEntity powerBox) {
			return mapFromArray(TYPE_POWER_BOX, partName, parts(powerBox), powerBox.getInsulatorIds(), powerBox::getWirePosition);
		} else if (entity instanceof WindTurbineBlockEntity turbine) {
			return mapFromArray(TYPE_WIND_TURBINE, partName, parts(turbine), turbine.getInsulatorIds(), turbine::getWirePosition);
		} else if (entity instanceof PvInverterBlockEntity inverter) {
			return mapFromArray(TYPE_PV_INVERTER, partName, parts(inverter), inverter.getInsulatorIds(), inverter::getWirePosition);
		}

		return Optional.empty();
	}

	public static Optional<Insulator> resolve(BlockEntity entity, int insulatorId) {
		if (entity instanceof UtilityPoleBlockEntity pole) {
			return mapFromId(TYPE_UTILITY_POLE, insulatorId, parts(pole), pole.getInsulatorIds(), pole::getWirePosition);
		} else if (entity instanceof ElectricCabinBlockEntity cabin) {
			return mapFromId(TYPE_ELECTRIC_CABIN, insulatorId, parts(cabin), cabin.getInsulatorIds(), cabin::getWirePosition);
		} else if (entity instanceof PowerBoxBlockEntity powerBox) {
			return mapFromId(TYPE_POWER_BOX, insulatorId, parts(powerBox), powerBox.getInsulatorIds(), powerBox::getWirePosition);
		} else if (entity instanceof WindTurbineBlockEntity turbine) {
			return mapFromId(TYPE_WIND_TURBINE, insulatorId, parts(turbine), turbine.getInsulatorIds(), turbine::getWirePosition);
		} else if (entity instanceof PvInverterBlockEntity inverter) {
			return mapFromId(TYPE_PV_INVERTER, insulatorId, parts(inverter), inverter.getInsulatorIds(), inverter::getWirePosition);
		}

		return Optional.empty();
	}

	private static Optional<Insulator> mapFromArray(String blockType, String partName, List<String> parts, int[] insulatorIds, PositionResolver resolver) {
		if (partName == null) return Optional.empty();

		int index = parts.indexOf(partName);
		if (index < 0 || index >= insulatorIds.length) return Optional.empty();

		int insulatorId = insulatorIds[index];
		return Optional.of(new Insulator(blockType, insulatorId, index, parts.get(index), resolver.resolve(index)));
	}

	private static Optional<Insulator> mapFromId(String blockType, int targetId, List<String> partNames, int[] insulatorIds, PositionResolver resolver) {
		for (int i = 0; i < insulatorIds.length; i++) {
			if (insulatorIds[i] == targetId && targetId >= 0) {
				Vec3 anchor = resolver.resolve(i);
				String partName = i < partNames.size() ? partNames.get(i) : null;
				return Optional.of(new Insulator(blockType, targetId, i, partName, anchor));
			}
		}

		return Optional.empty();
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
