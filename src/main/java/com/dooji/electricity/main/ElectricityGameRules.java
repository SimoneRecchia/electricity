package com.dooji.electricity.main;

import net.minecraft.world.level.GameRules;

/**
 * The mod's game rules.
 *
 * A game rule rather than a config entry because this one belongs to a world rather than to
 * a server: it is saved with the world, it survives being copied elsewhere, and an operator
 * can turn it on from inside the game with {@code /gamerule} instead of restarting to edit
 * a file. Forge has no API for adding one, so {@link GameRules#register} is opened by the
 * one line in {@code accesstransformer.cfg}.
 *
 * Registration has to happen during mod construction, before any world is loaded, because
 * the rule set is read when a level's data is deserialised - register later and a world
 * saved with the rule on would come back with it off.
 */
public final class ElectricityGameRules {
	/** @see RealTimeClock */
	public static GameRules.Key<GameRules.BooleanValue> REAL_TIME_CLOCK;

	private ElectricityGameRules() {
	}

	public static void register() {
		REAL_TIME_CLOCK = GameRules.register("electricityRealTimeClock", GameRules.Category.MISC, GameRules.BooleanValue.create(false));
	}
}
