package com.dooji.electricity.main;

import java.time.LocalDateTime;
import java.time.ZoneId;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;

/** Ties a world's clock to the wall clock, under a game rule. */
public final class RealTimeClock {
	/** Ticks of the day clock per real second. */
	private static final double TICKS_PER_REAL_SECOND = 24000.0 / 86400.0;
	/** Where in the day tick zero falls. */
	private static final double SUNRISE_HOUR = 6.0;

	private RealTimeClock() {
	}

	/** Puts the level's clock where the wall clock says */
	public static void apply(ServerLevel level) {
		if (!level.getGameRules().getBoolean(ElectricityGameRules.REAL_TIME_CLOCK)) return;

		level.setDayTime(dayTimeFor(LocalDateTime.now(ZoneId.systemDefault())));
	}

	/**
	 * Stops each client running a day cycle of its own, so the sky moves at the rate the server's clock actually moves at.
	 */
	public static void holdClientClocks(ServerLevel level) {
		if (!level.getGameRules().getBoolean(ElectricityGameRules.REAL_TIME_CLOCK)) return;
		// nothing to correct when the world already says the day does not advance: vanilla's own
		// broadcast is then carrying the same negated time this would
		if (!level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) return;

		ClientboundSetTimePacket held = new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(), false);
		for (ServerPlayer player : level.players()) {
			player.connection.send(held);
		}
	}

	/** The day-clock reading that matches a wall-clock moment. */
	static long dayTimeFor(LocalDateTime now) {
		double secondsIntoDay = now.toLocalTime().toSecondOfDay();
		double shifted = secondsIntoDay - SUNRISE_HOUR * 3600.0;
		// the epoch day of the moment sunrise last passed
		// day at the seam: before six in the morning both step back together
		long dayNumber = now.toLocalDate().toEpochDay() + (shifted < 0.0 ? -1 : 0);
		if (shifted < 0.0) shifted += 86400.0;

		return dayNumber * 24000L + Math.round(shifted * TICKS_PER_REAL_SECOND);
	}
}
