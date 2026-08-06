package com.dooji.electricity.client.wire;

import com.dooji.electricity.main.wire.WireConnection;
import com.dooji.electricity.wire.InsulatorHost;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WireManagerClient {
	private static final Map<String, WireConnection> WIRE_CONNECTIONS = new ConcurrentHashMap<>();
	private static final Map<Integer, Vec3> POSITION_CACHE = new ConcurrentHashMap<>();
	private static Vec3 pendingConnection = null;
	private static BlockPos pendingBlockPos = null;
	private static String pendingPartName = null;

	public static void sync(Collection<WireConnection> connections) {
		WIRE_CONNECTIONS.clear();
		POSITION_CACHE.clear();
		clearPendingConnection();
		if (connections == null) return;
		for (WireConnection connection : connections) {
			String key = connection.getStartInsulatorId() + "_" + connection.getEndInsulatorId();
			WIRE_CONNECTIONS.put(key, connection);
		}
	}

	public static void addWireConnection(WireConnection connection) {
		String key = connection.getStartInsulatorId() + "_" + connection.getEndInsulatorId();
		WIRE_CONNECTIONS.put(key, connection);
	}

	public static void removeWireConnection(WireConnection connection) {
		String key = connection.getStartInsulatorId() + "_" + connection.getEndInsulatorId();
		WIRE_CONNECTIONS.remove(key);
	}

	public static Collection<WireConnection> getAllWireConnections() {
		return new ArrayList<>(WIRE_CONNECTIONS.values());
	}

	public static void removeAll() {
		WIRE_CONNECTIONS.clear();
		POSITION_CACHE.clear();
		clearPendingConnection();
	}

	public static void setPendingConnection(Vec3 pos) {
		pendingConnection = pos;
	}

	public static void setPendingConnection(Vec3 pos, BlockPos blockPos) {
		pendingConnection = pos;
		pendingBlockPos = blockPos;
	}

	public static void clearPendingConnection() {
		pendingConnection = null;
		pendingBlockPos = null;
		pendingPartName = null;
	}

	public static Vec3 getPendingConnection() {
		return pendingConnection;
	}

	public static BlockPos getPendingBlockPos() {
		return pendingBlockPos;
	}

	public static void setPendingPartName(String partName) {
		pendingPartName = partName;
	}

	public static String getPendingPartName() {
		return pendingPartName;
	}

	public static Vec3 getWirePosition(Level level, int insulatorId, BlockPos blockPos) {
		if (insulatorId < 0) return null;

		Vec3 cached = POSITION_CACHE.get(insulatorId);
		if (cached != null) return cached;

		BlockEntity blockEntity = InsulatorLookup.get(insulatorId);
		if (blockEntity == null && level != null && blockPos != null && level.hasChunkAt(blockPos)) {
			blockEntity = level.getBlockEntity(blockPos);
			if (blockEntity != null) {
				InsulatorLookup.register(blockEntity);
			}
		}

		Vec3 resolved = resolveInsulatorPosition(blockEntity, insulatorId);
		if (resolved != null) {
			POSITION_CACHE.put(insulatorId, resolved);
		}

		return resolved;
	}

	public static void invalidateInsulatorCache(int... insulatorIds) {
		if (insulatorIds == null || insulatorIds.length == 0) return;
		for (int id : insulatorIds) {
			POSITION_CACHE.remove(id);
		}
	}

	/**
	 * Where one end of a span is, asked of the machine that carries the fitting.
	 *
	 * This was an {@code instanceof} chain over the pole, the cabin, the kiosk and the turbine, with the same
	 * loop written out four times - so a span to a lattice tower, a transformer, a switch, an inverter or a
	 * ground-laid run resolved to null and <b>was never drawn</b>. The server had saved it and the wire simply
	 * did not appear. {@link InsulatorHost} is the one definition of "a wire can hang here", and it is the
	 * same lesson as WireInteractionEvents.wireable.
	 */
	private static Vec3 resolveInsulatorPosition(BlockEntity blockEntity, int insulatorId) {
		if (!(blockEntity instanceof InsulatorHost host)) return null;

		int[] ids = host.getInsulatorIds();
		for (int index = 0; index < ids.length; index++) {
			if (ids[index] == insulatorId) return host.getWirePosition(index);
		}

		return null;
	}
}
