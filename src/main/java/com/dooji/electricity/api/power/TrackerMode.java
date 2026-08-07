package com.dooji.electricity.api.power;

import javax.annotation.Nullable;

/** What a tracker controller is doing, in the vocabulary real tracker controllers use. */
public enum TrackerMode {
	/** Following the sun, backtracking when it has to, and stowing itself for weather. */
	AUTO,
	/** Held at an angle somebody chose. */
	MANUAL,
	/** Commanded to its night stow and kept there. */
	STOW;

	@Nullable
	public static TrackerMode byName(@Nullable String name) {
		if (name == null) return null;

		for (TrackerMode mode : values()) {
			if (mode.name().equalsIgnoreCase(name)) return mode;
		}

		return null;
	}

	public TrackerMode next() {
		TrackerMode[] modes = values();
		return modes[(ordinal() + 1) % modes.length];
	}

	/** Why a row is where it is, which is what a panel has to be able to say. */
	public enum Stow {
		/** Not stowed. */
		NONE,
		/** The sun is down. */
		NIGHT,
		/** The wind reached the stow threshold, so the row went flat to give the gust no leverage. */
		WIND,
		/** Snow is lying on it, so it stood up to drop the lot. */
		SNOW,
		/**
		 * The sky is almost all diffuse, so there is no beam worth pointing at and the row went flat to see as much of the dome as it can.
		 */
		DIFFUSE,
		/** Somebody commanded it. */
		COMMANDED
	}
}
