package com.dooji.electricity.block;

import com.dooji.electricity.client.TrackedBlockEntities;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** Tells the renderers which machines exist, and does it without mentioning them on a server. */
final class ClientTracking {
	private ClientTracking() {
	}

	static void track(BlockEntity machine) {
		if (onClient(machine)) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TrackedBlockEntities.track(machine));
		}
	}

	static void untrack(BlockEntity machine) {
		if (onClient(machine)) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TrackedBlockEntities.untrack(machine));
		}
	}

	private static boolean onClient(BlockEntity machine) {
		Level level = machine.getLevel();
		return level != null && level.isClientSide();
	}
}
