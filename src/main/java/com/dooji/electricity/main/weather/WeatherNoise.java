package com.dooji.electricity.main.weather;

import net.minecraft.util.Mth;

/**
 * Smooth, seeded, three-dimensional noise: the one source of randomness the whole
 * atmosphere is built from.
 *
 * A pure function of its coordinates, which is the property everything else here
 * depends on. The weather model asks for the pressure at a place and a time and gets
 * the same answer whoever asks, whenever they ask, and whether or not the chunks
 * around that place happen to be loaded. That is what lets the model hold no state at
 * all: nothing to save, nothing to migrate, nothing to drift out of step between two
 * players standing in different corners of the same world.
 *
 * It is value noise rather than gradient noise. Gradient noise has the nicer
 * derivatives, but the fields built on this one are differentiated numerically
 * anyway - see {@link Atmosphere#geostrophicWind} - and value noise is the shorter
 * thing to state, so its arithmetic can be reproduced exactly outside the game when
 * the model needs checking against real wind statistics.
 */
public final class WeatherNoise {
	private static final long X_PRIME = 0x9E3779B97F4A7C15L;
	private static final long Y_PRIME = 0xC2B2AE3D27D4EB4FL;
	private static final long Z_PRIME = 0x165667B19E3779F9L;

	private WeatherNoise() {
	}

	/**
	 * One octave, in roughly -1..1. Its standard deviation is 0.40 rather than
	 * anything tidier, which is why every caller that cares about the spread of its
	 * output divides by a measured constant instead of a guessed one.
	 */
	public static double sample(long seed, double x, double y, double z) {
		int x0 = Mth.floor(x);
		int y0 = Mth.floor(y);
		int z0 = Mth.floor(z);

		double tx = fade(x - x0);
		double ty = fade(y - y0);
		double tz = fade(z - z0);

		double y00 = Mth.lerp(tx, lattice(seed, x0, y0, z0), lattice(seed, x0 + 1, y0, z0));
		double y10 = Mth.lerp(tx, lattice(seed, x0, y0 + 1, z0), lattice(seed, x0 + 1, y0 + 1, z0));
		double y01 = Mth.lerp(tx, lattice(seed, x0, y0, z0 + 1), lattice(seed, x0 + 1, y0, z0 + 1));
		double y11 = Mth.lerp(tx, lattice(seed, x0, y0 + 1, z0 + 1), lattice(seed, x0 + 1, y0 + 1, z0 + 1));

		return Mth.lerp(tz, Mth.lerp(ty, y00, y10), Mth.lerp(ty, y01, y11));
	}

	/** Quintic, so the second derivative is continuous and pressure fields have no creases. */
	private static double fade(double t) {
		return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
	}

	private static double lattice(long seed, int x, int y, int z) {
		long hash = mix(seed ^ (x * X_PRIME) ^ (y * Y_PRIME) ^ (z * Z_PRIME));
		return (double) (hash >>> 11) / (double) (1L << 52) - 1.0;
	}

	/** SplitMix64's finalizer: enough avalanche that neighbouring lattice points are unrelated. */
	private static long mix(long value) {
		value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
		value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
		return value ^ (value >>> 31);
	}

	/** A stable number in 0..1 from any integers: per-position quantities that must never drift. */
	public static double hashUnit(long seed, int a, int b, int c) {
		long hash = mix(seed ^ (a * X_PRIME) ^ (b * Y_PRIME) ^ (c * Z_PRIME));
		return (double) (hash >>> 11) / (double) (1L << 53);
	}
}
