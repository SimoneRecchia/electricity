package com.dooji.electricity.main.network;

import com.dooji.electricity.block.PvArrayBlockEntity;
import com.dooji.electricity.block.PvInverterBlockEntity;
import com.dooji.electricity.block.UtilityPoleBlockEntity;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import com.dooji.electricity.client.ElectricityClient;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.network.payloads.CreateWireFromInsulatorsPayload;
import com.dooji.electricity.main.network.payloads.PowerUpdatePayload;
import com.dooji.electricity.main.network.payloads.SolarControlPayload;
import com.dooji.electricity.main.network.payloads.SyncWiresPayload;
import com.dooji.electricity.main.network.payloads.TurbineControlPayload;
import com.dooji.electricity.main.network.payloads.UpdateUtilityPoleConfigPayload;
import com.dooji.electricity.main.network.payloads.WireConnectionPayload;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ElectricityNetworking {
	private static final String PROTOCOL_VERSION = "1";
	public static final ResourceLocation NETWORK_CHANNEL = ResourceLocation.tryBuild(Electricity.MOD_ID, "main");
	public static SimpleChannel INSTANCE;
	/** Six blocks from the machine, which is where a player stands to work a panel. */
	private static final double MAX_CONTROL_DISTANCE_SQ = 36.0;

	/**
	 * The channel, and every message on it.
	 *
	 * The order is the wire protocol: each message's id is its position here, so a message may be added at
	 * the end but never moved.
	 */
	public static void init() {
		INSTANCE = NetworkRegistry.ChannelBuilder.named(NETWORK_CHANNEL).networkProtocolVersion(() -> PROTOCOL_VERSION).clientAcceptedVersions(v -> true).serverAcceptedVersions(v -> true)
				.simpleChannel();

		int id = 0;

		toClient(id++, SyncWiresPayload.class, SyncWiresPayload::write, SyncWiresPayload::read,
				ElectricityClient::handleSyncPacket);

		panelCommand(id++, UpdateUtilityPoleConfigPayload.class, UpdateUtilityPoleConfigPayload::write,
				UpdateUtilityPoleConfigPayload::read, UpdateUtilityPoleConfigPayload::blockPos, (player, msg) -> {
					if (player.serverLevel().getBlockEntity(msg.blockPos()) instanceof UtilityPoleBlockEntity pole) {
						pole.setOffsetX(msg.offsetX());
						pole.setOffsetY(msg.offsetY());
						pole.setOffsetZ(msg.offsetZ());
						pole.setYaw(msg.yaw());
						pole.setPitch(msg.pitch());
					}
				});

		clientRequest(id++, CreateWireFromInsulatorsPayload.class, CreateWireFromInsulatorsPayload::write,
				CreateWireFromInsulatorsPayload::read, Electricity.wireManager::createWireFromInsulators);

		panelCommand(id++, TurbineControlPayload.class, TurbineControlPayload::write, TurbineControlPayload::read,
				TurbineControlPayload::blockPos, (player, msg) -> {
					if (player.serverLevel().getBlockEntity(msg.blockPos()) instanceof WindTurbineBlockEntity turbine) {
						applyTurbineControl(turbine, msg);
					}
				});

		panelCommand(id++, SolarControlPayload.class, SolarControlPayload::write, SolarControlPayload::read,
				SolarControlPayload::blockPos, (player, msg) -> {
					var machine = player.serverLevel().getBlockEntity(msg.blockPos());
					if (machine instanceof PvInverterBlockEntity inverter) {
						applyInverterControl(inverter, msg);
					} else if (machine instanceof PvArrayBlockEntity array) {
						applyArrayControl(array, msg);
					}
				});

		toClient(id++, WireConnectionPayload.class, WireConnectionPayload::write, WireConnectionPayload::read,
				ElectricityClient::handleWireConnectionPacket);

		toClient(id++, PowerUpdatePayload.class, PowerUpdatePayload::write, PowerUpdatePayload::read,
				ElectricityClient::handlePowerUpdatePacket);
	}

	/** A reading the server sends out and the client draws: nothing to check but the side it arrived on. */
	private static <T> void toClient(int id, Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder,
			Function<FriendlyByteBuf, T> decoder, Consumer<T> handler) {
		INSTANCE.messageBuilder(type, id, NetworkDirection.PLAY_TO_CLIENT).encoder(encoder).decoder(decoder)
				.consumerNetworkThread((msg, contextSupplier) -> {
					var context = contextSupplier.get();
					if (context.getDirection().getReceptionSide().isClient()) {
						context.enqueueWork(() -> handler.accept(msg));
					}

					context.setPacketHandled(true);
				}).add();
	}

	/**
	 * A command from a machine's panel, taken only if the player could be standing at that machine.
	 *
	 * One guard for all of them, because they were three with three different subsets of it: the pole's
	 * offsets had no distance bound at all, so a client that asked could re-pose any pole in a loaded chunk.
	 */
	private static <T> void panelCommand(int id, Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder,
			Function<FriendlyByteBuf, T> decoder, Function<T, BlockPos> target, BiConsumer<ServerPlayer, T> handler) {
		clientRequest(id, type, encoder, decoder, (player, msg) -> {
			if (atThePanel(player, target.apply(msg))) handler.accept(player, msg);
		});
	}

	/**
	 * A request that checks its own targets, so only the sender is established here.
	 *
	 * The one of these is stringing a span, and it has no reach bound on purpose: its two ends are a
	 * conductor's length apart by design, and the far one may be a fitting eleven blocks up a tower reached
	 * through a shell cell. {@link com.dooji.electricity.main.wire.WireManager} validates both ends itself,
	 * reads the conductor off the player's hand rather than the packet, and asks each fitting whether it
	 * takes that class.
	 */
	private static <T> void clientRequest(int id, Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder,
			Function<FriendlyByteBuf, T> decoder, BiConsumer<ServerPlayer, T> handler) {
		INSTANCE.messageBuilder(type, id, NetworkDirection.PLAY_TO_SERVER).encoder(encoder).decoder(decoder)
				.consumerNetworkThread((msg, contextSupplier) -> {
					var context = contextSupplier.get();
					if (context.getDirection().getReceptionSide().isServer()) {
						context.enqueueWork(() -> {
							ServerPlayer player = context.getSender();
							if (player != null) handler.accept(player, msg);
						});
					}

					context.setPacketHandled(true);
				}).add();
	}

	/** Whether the player is where they would have to be to work the machine at this position. */
	private static boolean atThePanel(ServerPlayer player, BlockPos pos) {
		ServerLevel world = player.serverLevel();
		if (!world.hasChunkAt(pos) || !world.getWorldBorder().isWithinBounds(pos)) return false;
		if (!world.mayInteract(player, pos)) return false;

		// A machine taller than its own block is worked from anywhere along it: a turbine's panel is at the
		// foot of a tower that stands up to eleven blocks under the nacelle's own block.
		double foot = world.getBlockEntity(pos) instanceof WindTurbineBlockEntity turbine
				? pos.getY() - turbine.getTowerSegments() : pos.getY();
		Vec3 axis = Vec3.atCenterOf(pos);
		return player.distanceToSqr(axis.x, Mth.clamp(player.getY(), foot, pos.getY() + 1.0), axis.z) <= MAX_CONTROL_DISTANCE_SQ;
	}

	/** Applies one panel command. */
	private static void applyTurbineControl(WindTurbineBlockEntity turbine, TurbineControlPayload msg) {
		switch (msg.action()) {
			case TOGGLE_RUNNING -> turbine.setStoppedByPlayer(!turbine.isStoppedByPlayer());
			case CYCLE_REDSTONE_MODE -> turbine.setRedstoneMode(turbine.getRedstoneMode().next());
			case SET_POWER_LIMIT -> turbine.setActivePowerLimit(msg.value());
		}
	}

	/** Applies one command from an inverter's panel. */
	private static void applyInverterControl(PvInverterBlockEntity inverter, SolarControlPayload msg) {
		switch (msg.action()) {
			case INVERTER_TOGGLE_RUNNING -> inverter.setStoppedByPlayer(!inverter.isStoppedByPlayer());
			case INVERTER_CYCLE_REDSTONE_MODE -> inverter.setRedstoneMode(inverter.getRedstoneMode().next());
			case INVERTER_SET_POWER_LIMIT -> inverter.setActivePowerLimit(msg.value());
			case INVERTER_SET_POWER_FACTOR -> inverter.setPowerFactor(msg.value());
			default -> {
			}
		}
	}

	/** Applies one command from an array's panel. */
	private static void applyArrayControl(PvArrayBlockEntity array, SolarControlPayload msg) {
		switch (msg.action()) {
			case ARRAY_CYCLE_TRACKER_MODE -> array.setTrackerMode(array.trackerMode().next());
			case ARRAY_SET_TRACKER_ANGLE -> {
				// a hand angle only means anything in hand mode, so asking for one asks for the mode too:
				// setting the angle and leaving the controller tracking would have the next tick undo it
				array.setTrackerMode(com.dooji.electricity.api.power.TrackerMode.MANUAL);
				array.setManualRotation(msg.value());
			}
			default -> {
			}
		}
	}

	public static void broadcastToAllClients(ServerLevel world, WireConnectionPayload payload) {
		INSTANCE.send(PacketDistributor.ALL.noArg(), payload);
	}

	public static void sendToClient(ServerPlayer player, WireConnectionPayload payload) {
		INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), payload);
	}

	public static void sendToClient(ServerPlayer player, SyncWiresPayload payload) {
		INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), payload);
	}

	public static void sendPowerUpdate(ServerLevel world, BlockPos pos, double power) {
		INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> world.getChunkAt(pos)), new PowerUpdatePayload(pos, power));
	}
}
