package com.dooji.electricity.block;

import com.dooji.electricity.api.power.RedstoneMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The redstone half of a machine's stop: a mode, the signal, and the answer.
 *
 * A turbine and an inverter both have one, and had a copy each - the same two fields, the same poll,
 * the same test, the same two tags in the same order. Neither copy was wrong, which is exactly why
 * they had drifted apart in a small way already: one carried a comment about why a change only matters
 * visually when the mode reacts, and the other had lost it.
 *
 * The mode belongs to the player and the signal to the world, and the machine only ever asks the one
 * question: {@link #stopping()}. Both fields are volatile because a computer reads the mode from its
 * own thread while the server thread is writing the signal.
 */
final class RedstoneStop {
	private volatile RedstoneMode mode = RedstoneMode.DISABLED;
	private volatile boolean powered = false;

	/**
	 * Reads the neighbourhood, and tells the machine only when the answer matters.
	 *
	 * Every tick, because there is no event for "a signal somewhere near you went away" that is cheaper
	 * than asking - and asking is one lookup of a value the chunk already holds.
	 *
	 * A machine set to ignore redstone still has to remember the signal, for when the mode changes; it
	 * just does not need a packet sent to every client watching it. So a change the mode is armed
	 * against runs the reaction, and a change it is ignoring only marks the block dirty.
	 */
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
