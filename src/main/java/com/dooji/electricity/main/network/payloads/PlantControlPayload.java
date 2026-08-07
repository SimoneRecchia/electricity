package com.dooji.electricity.main.network.payloads;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/** One command from the plant controller's panel. */
public record PlantControlPayload(BlockPos blockPos, Action action, double value) {
	public enum Action {
		/** Step the controller through off, limit and redstone. */
		CYCLE_MODE,
		/** Plant setpoint, in kW, in {@link #value}. */
		SET_SETPOINT;

		private static final Action[] VALUES = values();

		/** Reads an ordinal off the wire without trusting it to be in range. */
		static Action byOrdinal(int ordinal) {
			return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : CYCLE_MODE;
		}
	}

	public static void write(PlantControlPayload msg, FriendlyByteBuf buffer) {
		buffer.writeBlockPos(msg.blockPos);
		buffer.writeVarInt(msg.action.ordinal());
		buffer.writeDouble(msg.value);
	}

	public static PlantControlPayload read(FriendlyByteBuf buffer) {
		return new PlantControlPayload(buffer.readBlockPos(), Action.byOrdinal(buffer.readVarInt()), buffer.readDouble());
	}

	public static PlantControlPayload of(BlockPos pos, Action action) {
		return new PlantControlPayload(pos, action, 0.0);
	}
}
