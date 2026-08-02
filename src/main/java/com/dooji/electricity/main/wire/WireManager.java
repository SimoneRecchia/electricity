package com.dooji.electricity.main.wire;

import com.dooji.electricity.block.ElectricCabinBlockEntity;
import com.dooji.electricity.block.PowerBoxBlockEntity;
import com.dooji.electricity.block.UtilityPoleBlockEntity;
import com.dooji.electricity.block.PvInverterBlockEntity;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import com.dooji.electricity.main.network.ElectricityNetworking;
import com.dooji.electricity.main.network.payloads.CreateWireFromInsulatorsPayload;
import com.dooji.electricity.main.network.payloads.SyncWiresPayload;
import com.dooji.electricity.main.network.payloads.WireConnectionPayload;
import com.dooji.electricity.wire.InsulatorPartHelper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

public class WireManager {
	private static final Map<ServerLevel, WireSavedData> SAVED_DATA_CACHE = new ConcurrentHashMap<>();
	private static final double MAX_WIRE_DISTANCE = 64.0;
	private static final double MAX_WIRE_DISTANCE_SQ = MAX_WIRE_DISTANCE * MAX_WIRE_DISTANCE;

	public InteractionResult handleWireUse(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null) return InteractionResult.FAIL;

		Level level = context.getLevel();
		BlockPos clickedPos = context.getClickedPos();

		if (level.isClientSide) return InteractionResult.SUCCESS;

		BlockEntity blockEntity = level.getBlockEntity(clickedPos);
		if (!(blockEntity instanceof UtilityPoleBlockEntity || blockEntity instanceof ElectricCabinBlockEntity || blockEntity instanceof PowerBoxBlockEntity
				|| blockEntity instanceof WindTurbineBlockEntity || blockEntity instanceof PvInverterBlockEntity)) {
			return InteractionResult.FAIL;
		}

		return InteractionResult.SUCCESS;
	}

	public void createWireFromInsulators(ServerPlayer player, CreateWireFromInsulatorsPayload payload) {
		if (player == null) return;
		ServerLevel level = player.serverLevel();
		if (!validateEndpoint(player, level, payload.startBlockPos()) || !validateEndpoint(player, level, payload.endBlockPos())) return;

		BlockEntity startEntity = level.getBlockEntity(payload.startBlockPos());
		BlockEntity endEntity = level.getBlockEntity(payload.endBlockPos());
		if (startEntity == null || endEntity == null) return;

		var startInsulator = InsulatorPartHelper.resolve(startEntity, payload.startInsulatorId());
		var endInsulator = InsulatorPartHelper.resolve(endEntity, payload.endInsulatorId());
		if (startInsulator.isEmpty() || endInsulator.isEmpty()) {
			notifyPlayer(player, Component.translatable("message.electricity.wire.failed_insulator"));
			return;
		}

		if (!InsulatorPartHelper.matchesReportedType(startEntity, payload.startBlockType()) || !InsulatorPartHelper.matchesReportedType(endEntity, payload.endBlockType())) {
			return;
		}

		Vec3 startAnchor = startInsulator.get().anchor();
		Vec3 endAnchor = endInsulator.get().anchor();
		if (startAnchor == null || endAnchor == null) {
			notifyPlayer(player, Component.translatable("message.electricity.wire.anchor_unavailable"));
			return;
		}

		double spanDistanceSq = startAnchor.distanceToSqr(endAnchor);
		if (spanDistanceSq > MAX_WIRE_DISTANCE_SQ) {
			notifyPlayer(player, Component.translatable("message.electricity.wire.too_far", String.format("%.1f", Math.sqrt(spanDistanceSq)), MAX_WIRE_DISTANCE));
			return;
		}

		String startPowerType = sanitizePowerType(startEntity, startInsulator.get().partName(), payload.startPowerType());
		String endPowerType = sanitizePowerType(endEntity, endInsulator.get().partName(), payload.endPowerType());

		WireConnection connection = new WireConnection(startInsulator.get().insulatorId(), endInsulator.get().insulatorId(), "default", payload.startBlockPos(), payload.endBlockPos(),
				startInsulator.get().blockType(), endInsulator.get().blockType(), startPowerType, endPowerType);

		saveWireConnection(level, connection);
		broadcastWireCreation(level, connection);
		notifyPlayer(player, Component.translatable("message.electricity.wire.connected", startInsulator.get().insulatorId(), endInsulator.get().insulatorId()));
	}

	private void saveWireConnection(ServerLevel level, WireConnection connection) {
		WireSavedData savedData = getOrCreateSavedData(level);
		savedData.addWireConnection(connection);
		savedData.setDirty();
	}

	private void broadcastWireCreation(ServerLevel level, WireConnection connection) {
		WireConnectionPayload payload = new WireConnectionPayload(connection, true);
		ElectricityNetworking.broadcastToAllClients(level, payload);
	}

	private boolean validateEndpoint(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (!level.hasChunkAt(pos)) {
			notifyPlayer(player, Component.translatable("message.electricity.wire.chunk_unloaded", pos.getX(), pos.getY(), pos.getZ()));
			return false;
		}

		if (!level.getWorldBorder().isWithinBounds(pos)) return false;

		if (!level.mayInteract(player, pos)) {
			notifyPlayer(player, Component.translatable("message.electricity.wire.no_permission", pos.getX(), pos.getY(), pos.getZ()));
			return false;
		}

		return true;
	}

	private void notifyPlayer(ServerPlayer player, Component message) {
		if (player != null && message != null) {
			player.displayClientMessage(message, true);
		}
	}

	private String sanitizePowerType(BlockEntity entity, String partName, String reported) {
		String expected = InsulatorPartHelper.determinePowerType(entity, partName);
		if ("bidirectional".equals(expected)) return "bidirectional";
		if ("output".equals(expected) || "input".equals(expected)) return expected;
		return reported != null ? reported : "bidirectional";
	}

	private void broadcastWireRemoval(ServerLevel level, WireConnection connection) {
		WireConnectionPayload payload = new WireConnectionPayload(connection, false);
		ElectricityNetworking.broadcastToAllClients(level, payload);
	}

	public void sendAllWiresToPlayer(ServerPlayer player) {
		if (player.level() instanceof ServerLevel serverLevel) {
			WireSavedData savedData = getOrCreateSavedData(serverLevel);
			SyncWiresPayload payload = new SyncWiresPayload(new ArrayList<>(savedData.getAllWireConnections()));
			ElectricityNetworking.sendToClient(player, payload);
		}
	}

	public void loadFromWorld(ServerLevel level) {
		WireSavedData savedData = getOrCreateSavedData(level);
	}

	public void forceSave(ServerLevel level) {
		WireSavedData savedData = getOrCreateSavedData(level);
		savedData.setDirty();
	}

	public WireSavedData getSavedData(ServerLevel level) {
		return getOrCreateSavedData(level);
	}

	public void removeConnectionsForInsulators(ServerLevel level, int[] insulatorIds) {
		if (insulatorIds == null || insulatorIds.length == 0) return;

		Set<Integer> ids = new HashSet<>();
		for (int id : insulatorIds) {
			if (id >= 0) {
				ids.add(id);
			}
		}

		if (ids.isEmpty()) return;

		WireSavedData savedData = getOrCreateSavedData(level);
		List<WireConnection> removedConnections = savedData.removeConnectionsForInsulators(ids);
		if (removedConnections.isEmpty()) return;

		savedData.setDirty();
		for (WireConnection connection : removedConnections) {
			broadcastWireRemoval(level, connection);
		}
	}

	private WireSavedData getOrCreateSavedData(ServerLevel level) {
		return SAVED_DATA_CACHE.computeIfAbsent(level, l -> l.getDataStorage().computeIfAbsent(WireSavedData::new, WireSavedData::new, "electricity_wires"));
	}

	public static class WireSavedData extends SavedData {
		private final Map<String, WireConnection> wireConnections = new ConcurrentHashMap<>();

		public WireSavedData() {
		}

		public WireSavedData(CompoundTag tag) {
			load(tag);
		}

		public void addWireConnection(WireConnection connection) {
			String key = connection.getStartInsulatorId() + "_" + connection.getEndInsulatorId();
			wireConnections.put(key, connection);
		}

		public void removeWireConnection(WireConnection connection) {
			String key = connection.getStartInsulatorId() + "_" + connection.getEndInsulatorId();
			wireConnections.remove(key);
		}

		public List<WireConnection> removeConnectionsForInsulators(Set<Integer> insulatorIds) {
			List<WireConnection> removed = new ArrayList<>();
			Iterator<WireConnection> iterator = wireConnections.values().iterator();

			while (iterator.hasNext()) {
				WireConnection connection = iterator.next();
				if (insulatorIds.contains(connection.getStartInsulatorId()) || insulatorIds.contains(connection.getEndInsulatorId())) {
					iterator.remove();
					removed.add(connection);
				}
			}

			return removed;
		}

		public Collection<WireConnection> getAllWireConnections() {
			return wireConnections.values();
		}

		@Override
		public CompoundTag save(@Nonnull CompoundTag tag) {
			ListTag wiresList = new ListTag();
			for (WireConnection connection : wireConnections.values()) {
				CompoundTag wireTag = new CompoundTag();
				wireTag.putInt("startInsulatorId", connection.getStartInsulatorId());
				wireTag.putInt("endInsulatorId", connection.getEndInsulatorId());
				wireTag.putString("wireType", connection.getWireType());
				wireTag.putLong("startBlockPos", connection.getStartBlockPos().asLong());
				wireTag.putLong("endBlockPos", connection.getEndBlockPos().asLong());
				wireTag.putString("startBlockType", connection.getStartBlockType());
				wireTag.putString("endBlockType", connection.getEndBlockType());
				wireTag.putString("startPowerType", connection.getStartPowerType());
				wireTag.putString("endPowerType", connection.getEndPowerType());
				wiresList.add(wireTag);
			}
			tag.put("wires", wiresList);
			return tag;
		}

		private void load(CompoundTag tag) {
			wireConnections.clear();
			ListTag wiresList = tag.getList("wires", Tag.TAG_COMPOUND);

			for (int i = 0; i < wiresList.size(); i++) {
				CompoundTag wireTag = wiresList.getCompound(i);
				int startInsulatorId = wireTag.getInt("startInsulatorId");
				int endInsulatorId = wireTag.getInt("endInsulatorId");
				String wireType = wireTag.getString("wireType");

				BlockPos startBlockPos = BlockPos.of(wireTag.getLong("startBlockPos"));
				BlockPos endBlockPos = BlockPos.of(wireTag.getLong("endBlockPos"));
				String startBlockType = wireTag.getString("startBlockType");
				String endBlockType = wireTag.getString("endBlockType");
				String startPowerType = wireTag.getString("startPowerType");
				String endPowerType = wireTag.getString("endPowerType");

				WireConnection connection = new WireConnection(startInsulatorId, endInsulatorId, wireType, startBlockPos, endBlockPos, startBlockType, endBlockType, startPowerType, endPowerType);
				String key = startInsulatorId + "_" + endInsulatorId;
				wireConnections.put(key, connection);
			}
		}
	}
}
