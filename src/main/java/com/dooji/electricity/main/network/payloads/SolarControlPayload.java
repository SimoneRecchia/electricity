package com.dooji.electricity.main.network.payloads;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * One command from a photovoltaic plant's control panels.
 *
 * The same shape as {@link TurbineControlPayload} and for the same reason: every command is pick a
 * block, change one setting, and the server validates them all identically. The value is only read by
 * the actions that need one and the rest ignore whatever arrives in it.
 *
 * Nothing here is trusted. The block entity's own setters clamp the curtailment setpoint to the
 * machine's nameplate, the power factor to what the machine can hold, and the tracker angle to what the
 * drive can reach - so the worst a crafted packet achieves is a setting the player could have dialled in
 * by hand.
 *
 * One payload for both node kinds rather than two, because the actions are disjoint: an inverter action
 * arriving at an array is a no-op rather than a confusion, and keeping them in one enum means the
 * distance check and the permission check are written once.
 */
public record SolarControlPayload(BlockPos blockPos, Action action, double value) {
	public enum Action {
		/** Release or apply the inverter's stop by hand. */
		INVERTER_TOGGLE_RUNNING,
		/** Step the inverter through disabled, high and low. */
		INVERTER_CYCLE_REDSTONE_MODE,
		/** Curtailment setpoint, in kW, in {@link #value}. */
		INVERTER_SET_POWER_LIMIT,
		/** Power factor setpoint, 0 to 1, in {@link #value}. */
		INVERTER_SET_POWER_FACTOR,
		/** Step the tracker through automatic, hand and stow. */
		ARRAY_CYCLE_TRACKER_MODE,
		/** Hand position for the tracker, in degrees from horizontal, in {@link #value}. */
		ARRAY_SET_TRACKER_ANGLE;

		private static final Action[] VALUES = values();

		/** Reads an ordinal off the wire without trusting it to be in range. */
		static Action byOrdinal(int ordinal) {
			return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : INVERTER_TOGGLE_RUNNING;
		}
	}

	public static void write(SolarControlPayload msg, FriendlyByteBuf buffer) {
		buffer.writeBlockPos(msg.blockPos);
		buffer.writeVarInt(msg.action.ordinal());
		buffer.writeDouble(msg.value);
	}

	public static SolarControlPayload read(FriendlyByteBuf buffer) {
		return new SolarControlPayload(buffer.readBlockPos(), Action.byOrdinal(buffer.readVarInt()), buffer.readDouble());
	}

	public static SolarControlPayload of(BlockPos pos, Action action) {
		return new SolarControlPayload(pos, action, 0.0);
	}
}
