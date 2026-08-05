package com.dooji.electricity.client.wire;

import com.dooji.electricity.block.ElectricCabinBlockEntity;
import com.dooji.electricity.block.PowerBoxBlockEntity;
import com.dooji.electricity.block.PvInverterBlockEntity;
import com.dooji.electricity.wire.InsulatorHost;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class InsulatorLookup {
	private static final Map<Integer, WeakReference<BlockEntity>> REGISTRY = new ConcurrentHashMap<>();

	private InsulatorLookup() {
	}

	public static void register(BlockEntity blockEntity) {
		if (blockEntity == null) return;
		int[] insulatorIds = extractIds(blockEntity);
		register(blockEntity, insulatorIds);
	}

	public static void register(BlockEntity blockEntity, int[] insulatorIds) {
		if (blockEntity == null || insulatorIds == null) return;

		for (int id : insulatorIds) {
			if (id >= 0) {
				REGISTRY.put(id, new WeakReference<>(blockEntity));
			}
		}
	}

	public static void unregister(int[] insulatorIds) {
		if (insulatorIds == null) return;

		for (int id : insulatorIds) {
			REGISTRY.remove(id);
		}
	}

	public static BlockEntity get(int insulatorId) {
		WeakReference<BlockEntity> reference = REGISTRY.get(insulatorId);
		if (reference == null) return null;

		BlockEntity blockEntity = reference.get();
		if (blockEntity == null || blockEntity.isRemoved()) {
			REGISTRY.remove(insulatorId);
			return null;
		}

		return blockEntity;
	}

	public static void clear() {
		REGISTRY.clear();
	}

	private static int[] extractIds(BlockEntity blockEntity) {
		return blockEntity instanceof InsulatorHost host ? host.getInsulatorIds() : null;
	}
}
