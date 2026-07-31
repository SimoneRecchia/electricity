package com.dooji.electricity.api.power;

import javax.annotation.Nullable;

/**
 * How a turbine reacts to a redstone signal.
 *
 * The names match Mekanism's own RedstoneControl so a program that already speaks
 * to Mekanism machines uses the same strings here. Mekanism has a fourth mode,
 * PULSE, for machines that perform one operation per rising edge; a generator runs
 * continuously and has nothing to pulse, so it is deliberately absent rather than
 * accepted and quietly ignored.
 */
public enum RedstoneMode {
	/** Redstone is ignored. The default. */
	DISABLED,
	/** Runs only while receiving a redstone signal. */
	HIGH,
	/** Runs only while not receiving a redstone signal. */
	LOW;

	/**
	 * @return the mode named by {@code name}, case-insensitively, or null when there
	 *         is no such mode.
	 */
	@Nullable
	public static RedstoneMode byName(@Nullable String name) {
		if (name == null) return null;

		for (RedstoneMode mode : values()) {
			if (mode.name().equalsIgnoreCase(name)) return mode;
		}

		return null;
	}

	/**
	 * The next mode round, for a control panel with one button for all three.
	 *
	 * Wraps, so a player who overshoots comes back round rather than having to find a
	 * second control to go the other way.
	 */
	public RedstoneMode next() {
		RedstoneMode[] modes = values();
		return modes[(ordinal() + 1) % modes.length];
	}

	/**
	 * @param powered whether a redstone signal is present
	 * @return whether the turbine may run under this mode
	 */
	public boolean allowsRunning(boolean powered) {
		return switch (this) {
			case DISABLED -> true;
			case HIGH -> powered;
			case LOW -> !powered;
		};
	}
}
