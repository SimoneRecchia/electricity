package com.dooji.electricity.main;

import net.minecraft.world.level.GameRules;

/** The mod's game rules. */
public final class ElectricityGameRules {
	/** @see RealTimeClock */
	public static GameRules.Key<GameRules.BooleanValue> REAL_TIME_CLOCK;

	private ElectricityGameRules() {
	}

	public static void register() {
		REAL_TIME_CLOCK = GameRules.register("electricityRealTimeClock", GameRules.Category.MISC, GameRules.BooleanValue.create(false));
	}
}
