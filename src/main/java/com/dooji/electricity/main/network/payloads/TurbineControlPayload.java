package com.dooji.electricity.main.network.payloads;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/** One command from a turbine's control panel. */
public record TurbineControlPayload(BlockPos blockPos, Action action, double value) {
	public enum Action {
		/** Release or apply the brake by hand. */
		TOGGLE_RUNNING,
		/** Step through disabled, high and low. */
		CYCLE_REDSTONE_MODE,
		/** Curtailment setpoint, in kW, in {@link #value}. */
		SET_POWER_LIMIT;

		private static final Action[] VALUES = values();

		/** Reads an ordinal off the wire without trusting it to be in range. */
		static Action byOrdinal(int ordinal) {
			return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : TOGGLE_RUNNING;
		}
	}

	public static void write(TurbineControlPayload msg, FriendlyByteBuf buffer) {
		buffer.writeBlockPos(msg.blockPos);
		buffer.writeVarInt(msg.action.ordinal());
		buffer.writeDouble(msg.value);
	}

	public static TurbineControlPayload read(FriendlyByteBuf buffer) {
		return new TurbineControlPayload(buffer.readBlockPos(), Action.byOrdinal(buffer.readVarInt()), buffer.readDouble());
	}

	public static TurbineControlPayload of(BlockPos pos, Action action) {
		return new TurbineControlPayload(pos, action, 0.0);
	}
}
