package com.dooji.electricity.main.network.payloads;

import com.dooji.electricity.main.wire.WireConnection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record WireConnectionPayload(WireConnection connection, boolean isCreation) {
public static final ResourceLocation ID = new ResourceLocation("electricity", "wire_connection");

	public WireConnectionPayload(FriendlyByteBuf buf) {
		this(readConnection(buf), buf.readBoolean());
	}

	public void write(FriendlyByteBuf buf) {
		writeConnection(buf, connection);
		buf.writeBoolean(isCreation);
	}

	public static WireConnectionPayload read(FriendlyByteBuf buf) {
		return new WireConnectionPayload(buf);
	}

	public static WireConnection readConnection(FriendlyByteBuf buf) {
		return new WireConnection(buf.readInt(), buf.readInt(), buf.readUtf(), buf.readBlockPos(), buf.readBlockPos(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf());
	}

	public static void writeConnection(FriendlyByteBuf buf, WireConnection connection) {
		buf.writeInt(connection.startInsulatorId());
		buf.writeInt(connection.endInsulatorId());
		buf.writeUtf(connection.wireType());
		buf.writeBlockPos(connection.startBlockPos());
		buf.writeBlockPos(connection.endBlockPos());
		buf.writeUtf(connection.startBlockType());
		buf.writeUtf(connection.endBlockType());
		buf.writeUtf(connection.startPowerType());
		buf.writeUtf(connection.endPowerType());
	}
}
