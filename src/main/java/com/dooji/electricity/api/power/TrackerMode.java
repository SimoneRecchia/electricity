package com.dooji.electricity.api.power;

import javax.annotation.Nullable;

/**
 * What a tracker controller is doing, in the vocabulary real tracker controllers use.
 *
 * Three modes rather than one flag, because the difference between them is the difference between a
 * plant and a liability. Automatic tracking is what a row does all day and includes deciding for
 * itself to stow; a hand position is what a technician sets to work on it; and a commanded stow is
 * what an operator asks for ahead of weather the controller cannot see coming.
 *
 * The order matters and it is the safe one: a commanded stow beats automatic tracking, and automatic
 * tracking still stows itself for wind. Nothing here can leave a row standing up in a storm.
 */
public enum TrackerMode {
	/** Following the sun, backtracking when it has to, and stowing itself for weather. The normal state. */
	AUTO,
	/** Held at an angle somebody chose. Maintenance, washing, or a player deciding they know better. */
	MANUAL,
	/** Commanded to its night stow and kept there. What an operator sets before a storm. */
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

	/**
	 * Why a row is where it is, which is what a panel has to be able to say.
	 *
	 * A tracker lying flat could be doing it for five different reasons and they call for five
	 * different responses: nothing at all overnight, nothing in a gale, a shovel in snow, patience
	 * under cloud, and a look at the controller if somebody left it in hand mode.
	 */
	public enum Stow {
		/** Not stowed. Tracking, or held somewhere on purpose. */
		NONE,
		/** The sun is down. */
		NIGHT,
		/** The wind reached the stow threshold, so the row went flat to give the gust no leverage. */
		WIND,
		/** Snow is lying on it, so it stood up to drop the lot. */
		SNOW,
		/**
		 * The sky is almost all diffuse, so there is no beam worth pointing at and the row went flat
		 * to see as much of the dome as it can. A real strategy on real controllers, and the one that
		 * makes an overcast day slightly less bad than it looks.
		 */
		DIFFUSE,
		/** Somebody commanded it. */
		COMMANDED
	}
}
