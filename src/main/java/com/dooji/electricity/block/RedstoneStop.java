package com.dooji.electricity.block;

import com.dooji.electricity.api.power.RedstoneMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** The redstone half of a machine's stop: a mode, the signal, and the answer. */
final class RedstoneStop {
	private volatile RedstoneMode mode = RedstoneMode.DISABLED;
	private volatile boolean powered = false;

	/** Reads the neighbourhood, and tells the machine only when the answer matters. */
	void poll(BlockEntity machine, Runnable reaction) {
		Level level = machine.getLevel();
		if (level == null || level.isClientSide()) return;

		boolean signal = level.hasNeighborSignal(machine.getBlockPos());
		if (signal == powered) return;

		powered = signal;
		if (mode != RedstoneMode.DISABLED) {
			reaction.run();
		} else {
			machine.setChanged();
		}
	}

	/** Whether redstone is holding the machine stopped right now. */
	boolean stopping() {
		return !mode.allowsRunning(powered);
	}

	RedstoneMode mode() {
		return mode;
	}

	/** Sets the mode, answering whether it actually changed - so only a real change costs a sync. */
	boolean mode(RedstoneMode wanted) {
		if (wanted == null || mode == wanted) return false;

		mode = wanted;
		return true;
	}

	void save(CompoundTag tag) {
		tag.putBoolean("redstonePowered", powered);
		tag.putString("redstoneMode", mode.name());
	}

	void load(CompoundTag tag) {
		powered = tag.getBoolean("redstonePowered");
		RedstoneMode saved = RedstoneMode.byName(tag.getString("redstoneMode"));
		mode = saved != null ? saved : RedstoneMode.DISABLED;
	}
}
