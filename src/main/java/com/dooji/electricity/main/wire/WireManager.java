package com.dooji.electricity.main.wire;

import com.dooji.electricity.api.power.ConductorSpec;
import com.dooji.electricity.block.ElectricCabinBlockEntity;
import com.dooji.electricity.block.MachineShell;
import com.dooji.electricity.block.PowerBoxBlockEntity;
import com.dooji.electricity.block.UtilityPoleBlockEntity;
import com.dooji.electricity.block.PvInverterBlockEntity;
import com.dooji.electricity.block.WindTurbineBlockEntity;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

public class WireManager {
	private static final Map<ServerLevel, WireSavedData> SAVED_DATA_CACHE = new ConcurrentHashMap<>();
	/**
	 * The longest span for a connection whose conductor this build does not know.
	 *
	 * Which is every connection in a world saved before the conductors existed: those are typed
	 * {@code "default"} and there is nothing to ask, so the old global figure still governs them. A span
	 * strung with a real conductor is limited by that conductor instead - forty blocks for a street
	 * bundle, a hundred and sixty for a transmission one - because how far a line can go between
	 * structures is a property of what is strung, not of the game.
	 */
	private static final double MAX_WIRE_DISTANCE = 64.0;

	public InteractionResult handleWireUse(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null) return InteractionResult.FAIL;

		Level level = context.getLevel();
		// the same reading of the click the client made: a collision cell means the machine it belongs to
		BlockPos clickedPos = MachineShell.hostOr(level, context.getClickedPos());

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

		String startPowerType = sanitizePowerType(startEntity, startInsulator.get().partName(), payload.startPowerType());
		String endPowerType = sanitizePowerType(endEntity, endInsulator.get().partName(), payload.endPowerType());

		// A span that is already there is not strung again.
		//
		// Without this a client that sends the payload twice - by lag, by a macro, or on purpose - pays
		// twice and gets one span, because the saved data keys a connection by its two insulators and the
		// second save overwrites the first. Both orders are checked, since the two ends are stored in the
		// order they were clicked.
		if (alreadyStrung(level, startInsulator.get().insulatorId(), endInsulator.get().insulatorId())) {
			notifyPlayer(player, Component.translatable("message.electricity.wire.already_connected"));
			return;
		}

		// Which conductor this is, read off the player's own hand rather than sent from the client: a
		// client that lies about it gets whatever it is actually holding.
		ConductorSpec spec = heldConductor(player);
		double span = Math.sqrt(spanDistanceSq);
		double limit = spec == null ? MAX_WIRE_DISTANCE : spec.maxSpan();
		if (span > limit) {
			notifyPlayer(player, Component.translatable("message.electricity.wire.too_far",
					String.format("%.1f", span), (int) limit));
			return;
		}

		// The charge, and it is the last thing before the connection is saved on purpose: everything that
		// can refuse the span has refused it by now, so there is no path that takes the conductor and then
		// fails to string it.
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
			if ((existing.getStartInsulatorId() == first && existing.getEndInsulatorId() == second)
					|| (existing.getStartInsulatorId() == second && existing.getEndInsulatorId() == first)) {
				return true;
			}
		}

		return false;
	}

	/** The conductor in the player's hand, or null if they are holding the old plain wire or nothing. */
	@javax.annotation.Nullable
	private static ConductorSpec heldConductor(ServerPlayer player) {
		for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
			if (stack.getItem() instanceof ConductorItem conductor) return conductor.spec();
		}

		return null;
	}

	/**
	 * Takes the conductor out of the player's inventory, all of it or none of it.
	 *
	 * Counted first and taken second, which matters: taking as it goes and giving up half way through
	 * leaves the player short of conductor and without a span, and there is no transaction to roll back
	 * inside a Minecraft inventory.
	 */
	private static boolean takeConductor(ServerPlayer player, ConductorSpec spec, int wanted) {
		int held = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.getItem() instanceof ConductorItem conductor && conductor.spec() == spec) {
				held += stack.getCount();
			}
		}

		if (held < wanted) return false;

		int left = wanted;
		for (int slot = 0; slot < player.getInventory().getContainerSize() && left > 0; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!(stack.getItem() instanceof ConductorItem conductor) || conductor.spec() != spec) continue;

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

	/**
	 * Gives back exactly what a span was charged, as an item on the ground where it was strung.
	 *
	 * Exactly what was charged, off the connection, and not what the same span would cost now - see
	 * {@link WireConnection#getChargedItems()}. Dropped rather than handed to a player because nothing
	 * here knows which player took the structure down, and often nobody did: a line comes down when the
	 * pole under it is blown up, decays, or is removed by another mod.
	 */
	private void refund(ServerLevel level, WireConnection connection) {
		int count = connection.getChargedItems();
		if (count <= 0) return;

		ConductorSpec spec = ConductorCatalog.byPath(connection.getWireType());
		if (spec == null) return;

		var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(spec.id());
		if (item == null) return;

		BlockPos at = connection.getStartBlockPos();
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
				wireTag.putInt("chargedItems", connection.getChargedItems());
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
