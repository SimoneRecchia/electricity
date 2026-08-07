package com.dooji.electricity.api;

import java.util.Locale;

/**
 * How a figure is written where a player reads it: on an item's label, or on a machine's own panel.
 *
 * One file because the same rating is printed in both places, and the plain formatter was copied into five -
 * three item classes and two screens. The power figure comes in two precisions on purpose: a label carries a
 * rating that never moves, a panel carries a reading that moves every tick and is watched.
 */
public final class Nameplate {
	private Nameplate() {
	}

	/** String.format at the root locale, so a decimal point is a point wherever the game is played. */
	public static String fmt(String pattern, Object... values) {
		return String.format(Locale.ROOT, pattern, values);
	}

	/**
	 * A rating, as it is printed on a label.
	 *
	 * kW below a megawatt and MW above it, because a catalogue spanning 10 kW to 4 MW reads badly in either
	 * unit alone: "4000 kW" and "0.01 MW" are both harder to place at a glance.
	 */
	public static String rating(double kw) {
		if (kw >= 1000.0) return fmt("%.1f MW", kw / 1000.0);

		return fmt("%.0f kW", kw);
	}

	/** The same, for a live reading: a decimal further, because a panel is watched while it changes. */
	public static String reading(double kw) {
		if (Math.abs(kw) >= 1000.0) return fmt("%.2f MW", kw / 1000.0);

		return fmt("%.1f kW", kw);
	}

	/** Energy the same way: kWh below a megawatt hour and MWh above. */
	public static String energy(double kwh) {
		if (kwh >= 1000.0) return fmt("%.2f MWh", kwh / 1000.0);

		return fmt("%.1f kWh", kwh);
	}
}
