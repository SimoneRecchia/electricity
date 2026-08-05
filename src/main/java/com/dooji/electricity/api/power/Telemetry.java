package com.dooji.electricity.api.power;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The machinery every machine's SCADA signals share: a snapshot, a builder, and what kind of number each signal is.
  *
 * Pulled out of {@link TurbineTelemetry} when the photovoltaic plant needed the same thing.
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

		/** Stores a reading, rounded to two decimals. */
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

	/** Starts a tag-to-kind map. */
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

		/** Everything declared so far, in declaration order */
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
