package com.dooji.electricity.wire;

import com.dooji.electricity.main.registry.ObjBlockDefinition;
import com.dooji.electricity.main.registry.ObjDefinitions;
import javax.annotation.Nullable;
import java.util.List;
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
	/** A lattice transmission tower, in any of its three duties. */
	public static final String TYPE_LATTICE_TOWER = "lattice_tower";
	/** A transformer, of either duty. */
	public static final String TYPE_TRANSFORMER = "transformer";
	/** A length of conductor laid on the ground, which offers one fitting. */
	public static final String TYPE_GROUND_CONDUCTOR = "ground_conductor";
	/** A switch in a line, of either kind: a disconnector or a circuit breaker. */
	public static final String TYPE_SWITCHGEAR = "switchgear";

	/** The part names a device's insulators are drawn as, read off the model's own definition. */
	private static List<String> parts(BlockEntity entity) {
		ObjBlockDefinition definition = ObjDefinitions.get(entity.getBlockState().getBlock());
		if (definition != null && !definition.insulators().isEmpty()) return definition.insulators();

		// A machine with no OBJ definition can still have fittings: a ground-laid conductor is drawn by a
		// block model rather than by the mod's renderer, so it has no definition to name a group in, and its
		// one fitting is named for it here. Without this its insulator resolves to nothing and no span can
		// be anchored to it.
		if (entity instanceof InsulatorHost host) {
			List<String> named = new java.util.ArrayList<>();
			for (int i = 0; i < host.getInsulatorIds().length; i++) named.add(host.fittingType() + "_" + (i + 1));
			return List.copyOf(named);
		}

		return List.of();
	}

	private InsulatorPartHelper() {
	}

	/** Which group of a model an insulator is drawn as, by index. */
	@Nullable
	public static String insulatorName(@Nullable ObjBlockDefinition definition, int index) {
		if (definition == null || index < 0 || index >= definition.insulators().size()) return null;

		return definition.insulators().get(index);
	}

	public static Optional<Insulator> resolve(BlockEntity entity, String partName) {
		if (!(entity instanceof InsulatorHost host)) return Optional.empty();

		return mapFromArray(host.fittingType(), partName, parts(entity), host.getInsulatorIds(), host::getWirePosition);
	}

	public static Optional<Insulator> resolve(BlockEntity entity, int insulatorId) {
		if (!(entity instanceof InsulatorHost host)) return Optional.empty();

		return mapFromId(host.fittingType(), insulatorId, parts(entity), host.getInsulatorIds(), host::getWirePosition);
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
		return entity instanceof InsulatorHost host ? host.powerType(partName) : "bidirectional";
	}

	public static String getBlockType(BlockEntity entity) {
		return entity instanceof InsulatorHost host ? host.fittingType() : "unknown";
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
