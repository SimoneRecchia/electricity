package com.dooji.electricity.block;

import com.dooji.electricity.client.TrackedBlockEntities;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Tells the renderers which machines exist, and does it without mentioning them on a server.
 *
 * Anything drawn by a global renderer rather than by a block entity renderer has to be in a list the
 * renderer can walk, so it registers itself on load and takes itself out on removal. The awkward part
 * is that the list is a client class: naming it from a method that also runs on a dedicated server
 * would load it there and crash. The nested lambda is what stops that - the reference lives in a
 * synthetic method that a server never reaches.
 *
 * Six block entities had that dance written out. One copy of a guard that fails by crashing a server is
 * worth more than six that look right.
 */
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
