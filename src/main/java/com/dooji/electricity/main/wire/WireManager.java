package com.dooji.electricity.main.wire;

import com.dooji.electricity.api.power.ConductorSpec;
import com.dooji.electricity.wire.InsulatorHost;
import com.dooji.electricity.main.network.ElectricityNetworking;
import com.dooji.electricity.main.network.payloads.CreateWireFromInsulatorsPayload;
import com.dooji.electricity.main.network.payloads.SyncWiresPayload;
import com.dooji.electricity.main.network.payloads.WireConnectionPayload;
import com.dooji.electricity.item.ConductorItem;
import com.dooji.electricity.main.registry.ConductorCatalog;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

public class WireManager {
	private static final Map<ServerLevel, WireSavedData> SAVED_DATA_CACHE = new ConcurrentHashMap<>();
	/** The longest span for a connection whose conductor this build does not know. */
	private static final double MAX_WIRE_DISTANCE = 64.0;

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

		String startPowerType = sanitizePowerType(startEntity, startInsulator.get().partName(), payload.startPowerType());
		String endPowerType = sanitizePowerType(endEntity, endInsulator.get().partName(), payload.endPowerType());

		// A span that is already there is not strung again.
		if (alreadyStrung(level, startInsulator.get().insulatorId(), endInsulator.get().insulatorId())) {
			notifyPlayer(player, Component.translatable("message.electricity.wire.already_connected"));
			return;
		}

		// Which conductor this is, read off the player's own hand rather than sent from the client: a
		// client that lies about it gets whatever it is actually holding.
		ConductorSpec spec = heldConductor(player);

		// And whether both fittings will take it at all. Five conductors that anything accepts are five
		// conductors that look interchangeable; a tower takes transmission, a pole takes a street bundle or a
		// medium-voltage line, and a kiosk takes the bundle. Each fitting says for itself.
		if (spec != null && !(accepts(startEntity, startInsulator.get().index(), spec)
				&& accepts(endEntity, endInsulator.get().index(), spec))) {
			notifyPlayer(player, Component.translatable("message.electricity.wire.wrong_class",
					Component.translatable("item.electricity." + spec.id().getPath())));
			return;
		}

		double span = Math.sqrt(spanDistanceSq);
		double limit = spec == null ? MAX_WIRE_DISTANCE : spec.maxSpan();
		if (span > limit) {
			notifyPlayer(player, Component.translatable("message.electricity.wire.too_far",
					String.format("%.1f", span), (int) limit));
			return;
		}

		// The charge, and it is the last thing before the connection is saved on purpose: everything that
		int charged = 0;
		if (spec != null && !player.isCreative()) {
			charged = spec.cost(span);
			if (!takeConductor(player, spec, charged)) {
				notifyPlayer(player, Component.translatable("message.electricity.wire.not_enough",
						charged, Component.translatable("item.electricity." + spec.id().getPath())));
				return;
			}
		}

		WireConnection connection = new WireConnection(startInsulator.get().insulatorId(), endInsulator.get().insulatorId(),
				spec == null ? "default" : spec.id().getPath(), payload.startBlockPos(), payload.endBlockPos(),
				startInsulator.get().blockType(), endInsulator.get().blockType(), startPowerType, endPowerType, charged);

		saveWireConnection(level, connection);
		broadcastWireCreation(level, connection);
		notifyPlayer(player, Component.translatable("message.electricity.wire.connected", startInsulator.get().insulatorId(), endInsulator.get().insulatorId()));
	}

	/** Whether these two fittings are already joined, in either order. */
	private boolean alreadyStrung(ServerLevel level, int first, int second) {
		for (WireConnection existing : getOrCreateSavedData(level).getAllWireConnections()) {
			if ((existing.startInsulatorId() == first && existing.endInsulatorId() == second)
					|| (existing.startInsulatorId() == second && existing.endInsulatorId() == first)) {
				return true;
			}
		}

		return false;
	}

	/** Whether one fitting will take this conductor: a class per fitting, not one rule for the mod. */
	private static boolean accepts(BlockEntity entity, int index, ConductorSpec spec) {
		return !(entity instanceof InsulatorHost host) || host.takesConductor(spec, index);
	}

	/** The conductor in the player's hand, or null if they are holding the old plain wire or nothing. */
	@javax.annotation.Nullable
	private static ConductorSpec heldConductor(ServerPlayer player) {
		for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
			if (stack.getItem() instanceof ConductorItem conductor) return conductor.spec();
		}

		return null;
	}

	/** Takes the conductor out of the player's inventory */
	private static boolean takeConductor(ServerPlayer player, ConductorSpec spec, int wanted) {
		int held = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.getItem() instanceof ConductorItem conductor && conductor.spec().id().equals(spec.id())) {
				held += stack.getCount();
			}
		}

		if (held < wanted) return false;

		int left = wanted;
		for (int slot = 0; slot < player.getInventory().getContainerSize() && left > 0; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!(stack.getItem() instanceof ConductorItem conductor)
					|| !conductor.spec().id().equals(spec.id())) continue;

			int take = Math.min(left, stack.getCount());
			stack.shrink(take);
			left -= take;
		}

		return true;
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
			refund(level, connection);
		}
	}

	/** Gives back exactly what a span was charged, as an item on the ground where it was strung. */
	private void refund(ServerLevel level, WireConnection connection) {
		int count = connection.chargedItems();
		if (count <= 0) return;

		ConductorSpec spec = ConductorCatalog.byPath(connection.wireType());
		if (spec == null) return;

		var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(spec.id());
		if (item == null) return;

		BlockPos at = connection.startBlockPos();
		while (count > 0) {
			int drop = Math.min(count, 64);
			net.minecraft.world.level.block.Block.popResource(level, at, new ItemStack(item, drop));
			count -= drop;
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
			String key = connection.startInsulatorId() + "_" + connection.endInsulatorId();
			wireConnections.put(key, connection);
		}

		public void removeWireConnection(WireConnection connection) {
			String key = connection.startInsulatorId() + "_" + connection.endInsulatorId();
			wireConnections.remove(key);
		}

		public List<WireConnection> removeConnectionsForInsulators(Set<Integer> insulatorIds) {
			List<WireConnection> removed = new ArrayList<>();
			Iterator<WireConnection> iterator = wireConnections.values().iterator();

			while (iterator.hasNext()) {
				WireConnection connection = iterator.next();
				if (insulatorIds.contains(connection.startInsulatorId()) || insulatorIds.contains(connection.endInsulatorId())) {
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
				wireTag.putInt("startInsulatorId", connection.startInsulatorId());
				wireTag.putInt("endInsulatorId", connection.endInsulatorId());
				wireTag.putString("wireType", connection.wireType());
				wireTag.putLong("startBlockPos", connection.startBlockPos().asLong());
				wireTag.putLong("endBlockPos", connection.endBlockPos().asLong());
				wireTag.putString("startBlockType", connection.startBlockType());
				wireTag.putString("endBlockType", connection.endBlockType());
				wireTag.putString("startPowerType", connection.startPowerType());
				wireTag.putString("endPowerType", connection.endPowerType());
				wireTag.putInt("chargedItems", connection.chargedItems());
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

				WireConnection connection = new WireConnection(startInsulatorId, endInsulatorId, wireType, startBlockPos, endBlockPos, startBlockType, endBlockType, startPowerType, endPowerType,
						wireTag.getInt("chargedItems"));
				String key = startInsulatorId + "_" + endInsulatorId;
				wireConnections.put(key, connection);
			}
		}
	}
}
