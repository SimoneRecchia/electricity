package com.dooji.electricity.main;

import java.time.LocalDateTime;
import java.time.ZoneId;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

/**
 * Ties a world's clock to the wall clock, under a game rule.
 *
 * With {@code electricityRealTimeClock} on, the sun stands where the real sun stands: six
 * in the evening outside is six in the evening in the world, a day takes a day, and one
 * second of play is one second. It exists because this mod's whole subject is a power
 * system, and a power system is a thing people watch over hours - a solar plant whose
 * whole generating day fits in ten minutes cannot be watched at all, and a wind farm's
 * output does not read as a trend when a front passes every quarter of an hour.
 *
 * Sleeping still works and still does everything else it does - it heals, it sets a spawn,
 * it clears the weather, it dismisses phantoms - it simply cannot move the sun any more.
 * That is not a special case written for it: with the clock following the wall, there is
 * nowhere for a night to be skipped to.
 *
 * <h2>What follows from it</h2>
 *
 * The mod's weather runs off the day clock rather than the tick counter for exactly this
 * reason, so it slows down with the sun and keeps its own timescales honest: a front that
 * takes two days to pass takes two real days here, having taken forty real minutes before.
 * Nothing else in the mod needs to know this rule exists.
 */
public final class RealTimeClock {
	/**
	 * Ticks of the day clock per real second. A Minecraft day is 24000 ticks and a real one
	 * 86400 seconds, so the world's clock runs at a little over a third of a tick a second -
	 * against the twenty a second it normally does.
	 */
	private static final double TICKS_PER_REAL_SECOND = 24000.0 / 86400.0;
	/**
	 * Where in the day tick zero falls.
	 *
	 * Minecraft starts its day at sunrise and calls that time zero, while the world outside
	 * starts at midnight, so the two are a quarter of a day out of step. Six in the morning
	 * has to land on tick zero for noon to land at 6000 and sunset at 12000.
	 */
	private static final double SUNRISE_HOUR = 6.0;

	private RealTimeClock() {
	}

	/**
	 * Puts the level's clock where the wall clock says, if the rule is on.
	 *
	 * Written every tick rather than only when it drifts, because vanilla advances the day
	 * clock on its own and a sleeping night jumps it: overwriting unconditionally is both
	 * shorter than watching for those and immune to whatever else moves it.
	 *
	 * The date is carried, not just the time, so the count of days keeps rising across
	 * midnight. Anything reading the day clock as a monotonic quantity - the weather, above
	 * all - would otherwise see it fall back by a whole day every night.
	 */
	public static void apply(ServerLevel level) {
		if (!level.getGameRules().getBoolean(ElectricityGameRules.REAL_TIME_CLOCK)) return;

		level.setDayTime(dayTimeFor(LocalDateTime.now(ZoneId.systemDefault())));
	}

	/** The day-clock reading that matches a wall-clock moment. Separate so it can be reasoned about. */
	static long dayTimeFor(LocalDateTime now) {
		double secondsIntoDay = now.toLocalTime().toSecondOfDay();
		double shifted = secondsIntoDay - SUNRISE_HOUR * 3600.0;
		// the epoch day of the moment sunrise last passed, so the two halves cannot disagree by a
		// day at the seam: before six in the morning both step back together
		long dayNumber = now.toLocalDate().toEpochDay() + (shifted < 0.0 ? -1 : 0);
		if (shifted < 0.0) shifted += 86400.0;

		return dayNumber * 24000L + Math.round(shifted * TICKS_PER_REAL_SECOND);
	}
}
