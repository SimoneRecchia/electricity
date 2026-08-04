package com.dooji.electricity.api.power;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The machinery every machine's SCADA signals share: a snapshot, a builder, and what kind of
 * number each signal is.
 *
 * Pulled out of {@link TurbineTelemetry} when the photovoltaic plant needed the same thing. The
 * bookkeeping is easy to get subtly wrong twice - the rounding, the boolean coercion, the
 * unmodifiable wrapper, the empty instance that must never be null - and a second copy of it would
 * have been a second place for those to drift.
 *
 * What is left in the per-machine classes is what genuinely differs: the tag names and which kind
 * each one is.
 *
 * <h2>Why a snapshot at all</h2>
 *
 * Because readers arrive off the server thread. A ComputerCraft program calls into a peripheral from
 * the computer thread, so reading a block entity's live fields would be a data race and could hand
 * out a mix of values from two different ticks. A machine instead publishes one finished snapshot per
 * tick to a volatile field; a reader gets a single self-consistent view with no locking and without
 * having to wait for a tick boundary.
 *
 * <h2>Why the kinds</h2>
 *
 * Three kinds of value live in these maps, and the difference matters a great deal to anyone
 * building control logic on top:
 *
 * <ul>
 * <li>{@link Kind#MEASURED} - read straight from mod state or the world. Wind, irradiance, power,
 *     temperature, whether the machine is running.</li>
 * <li>{@link Kind#DERIVED} - computed from measured values by a relation that would hold on the real
 *     machine. Phase currents from apparent power, generator speed through a gearbox ratio, string
 *     voltage from a module's temperature coefficient.</li>
 * <li>{@link Kind#SIMULATED} - plausible instrumentation the mod does not model. Bearing
 *     temperatures, hydraulic pressures, insulation resistance. These react correctly to load and
 *     ambient conditions through a first-order lag, and they are not a physical simulation. A
 *     program may treat them as realistic and must not treat them as ground truth.</li>
 * </ul>
 *
 * Publishing which is which, rather than leaving a program to guess, is the whole reason the
 * distinction is in the API instead of only in a comment.
 */
public final class Telemetry {
	public enum Kind {
		MEASURED,
		DERIVED,
		SIMULATED
	}

	/** A finished set of readings, safe to hand to any thread. */
	public static final class Snapshot {
		public static final Snapshot EMPTY = new Snapshot(new LinkedHashMap<>());

		private final Map<String, Object> values;

		private Snapshot(Map<String, Object> values) {
			this.values = Collections.unmodifiableMap(values);
		}

		public Map<String, Object> values() {
			return values;
		}

		public Object get(String tag) {
			return values.get(tag);
		}

		/** A reading as a number, with a flag counting as one and nothing counting as zero. */
		public double number(String tag) {
			Object value = values.get(tag);
			if (value instanceof Number number) return number.doubleValue();
			if (value instanceof Boolean flag) return flag ? 1.0 : 0.0;

			return 0.0;
		}

	}

	private Telemetry() {
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private final Map<String, Object> values = new LinkedHashMap<>();

		private Builder() {
		}

		/**
		 * Stores a reading, rounded to two decimals.
		 *
		 * Instruments do not report fifteen significant digits, and an unrounded double turns into
		 * noise the moment a Lua program prints it.
		 */
		public Builder put(String tag, double value) {
			values.put(tag, Math.round(value * 100.0) / 100.0);
			return this;
		}

		public Builder put(String tag, boolean value) {
			values.put(tag, value);
			return this;
		}

		/** A tag whose value is a name rather than a number: a mode, a state, a reason. */
		public Builder put(String tag, String value) {
			values.put(tag, value);
			return this;
		}

		public Snapshot build() {
			return new Snapshot(values);
		}
	}

	/** Starts a tag-to-kind map. Reads as a list of tags under each heading, which is what it is. */
	public static Kinds tags() {
		return new Kinds();
	}

	public static final class Kinds {
		private final Map<String, Kind> map = new LinkedHashMap<>();

		private Kinds() {
		}

		public Kinds measured(String... tags) {
			return put(Kind.MEASURED, tags);
		}

		public Kinds derived(String... tags) {
			return put(Kind.DERIVED, tags);
		}

		public Kinds simulated(String... tags) {
			return put(Kind.SIMULATED, tags);
		}

		private Kinds put(Kind kind, String... tags) {
			for (String tag : tags) {
				map.put(tag, kind);
			}

			return this;
		}

		/** Everything declared so far, in declaration order, which is the order a peripheral lists it. */
		public Map<String, Kind> build() {
			return Collections.unmodifiableMap(map);
		}
	}

	/** Two tag sets read as one, for a peripheral that publishes both. */
	public static Map<String, Kind> merge(Map<String, Kind> first, Map<String, Kind> second) {
		Map<String, Kind> merged = new LinkedHashMap<>(first);
		merged.putAll(second);
		return Collections.unmodifiableMap(merged);
	}
}
