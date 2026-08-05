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
import net.minecraft.core.BlockPos;
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
	/** Six blocks from the tower, which is where a player stands to work a panel. */
	private static final double MAX_CONTROL_DISTANCE_SQ = 36.0;

	public static void init() {
		INSTANCE = NetworkRegistry.ChannelBuilder.named(NETWORK_CHANNEL).networkProtocolVersion(() -> PROTOCOL_VERSION).clientAcceptedVersions(v -> true).serverAcceptedVersions(v -> true)
				.simpleChannel();

		int id = 0;

		INSTANCE.messageBuilder(SyncWiresPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(SyncWiresPayload::write).decoder(SyncWiresPayload::read)
				.consumerNetworkThread((msg, contextSupplier) -> {
					var context = contextSupplier.get();

					if (context.getDirection().getReceptionSide().isClient()) {
						context.enqueueWork(() -> ElectricityClient.handleSyncPacket(msg));
					}

					context.setPacketHandled(true);
				}).add();

		INSTANCE.messageBuilder(UpdateUtilityPoleConfigPayload.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(UpdateUtilityPoleConfigPayload::write)
				.decoder(UpdateUtilityPoleConfigPayload::read).consumerNetworkThread((msg, contextSupplier) -> {
					var context = contextSupplier.get();

					if (context.getDirection().getReceptionSide().isServer()) {
						context.enqueueWork(() -> {
							var player = context.getSender();
							if (player == null) return;

							var world = player.serverLevel();
							var pos = msg.blockPos();

							if (!world.hasChunkAt(pos) || !world.getWorldBorder().isWithinBounds(pos)) return;
							if (!world.mayInteract(player, pos)) return;

							if (world.getBlockEntity(pos) instanceof UtilityPoleBlockEntity blockEntity) {
								blockEntity.setOffsetX(msg.offsetX());
								blockEntity.setOffsetY(msg.offsetY());
								blockEntity.setOffsetZ(msg.offsetZ());
								blockEntity.setYaw(msg.yaw());
								blockEntity.setPitch(msg.pitch());
							}
						});
					}

					context.setPacketHandled(true);
				}).add();

		INSTANCE.messageBuilder(CreateWireFromInsulatorsPayload.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(CreateWireFromInsulatorsPayload::write)
				.decoder(CreateWireFromInsulatorsPayload::read).consumerNetworkThread((msg, contextSupplier) -> {
					var context = contextSupplier.get();

					if (context.getDirection().getReceptionSide().isServer()) {
						context.enqueueWork(() -> {
							var player = context.getSender();
							if (player == null) return;

							Electricity.wireManager.createWireFromInsulators(player, msg);
						});
					}

					context.setPacketHandled(true);
				}).add();

		INSTANCE.messageBuilder(TurbineControlPayload.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(TurbineControlPayload::write).decoder(TurbineControlPayload::read)
				.consumerNetworkThread((msg, contextSupplier) -> {
					var context = contextSupplier.get();

					if (context.getDirection().getReceptionSide().isServer()) {
						context.enqueueWork(() -> {
							var player = context.getSender();
							if (player == null) return;

							var world = player.serverLevel();
							var pos = msg.blockPos();

							if (!world.hasChunkAt(pos) || !world.getWorldBorder().isWithinBounds(pos)) return;
							if (!world.mayInteract(player, pos)) return;

							if (world.getBlockEntity(pos) instanceof WindTurbineBlockEntity turbine) {
								if (!withinReach(player, turbine)) return;

								applyTurbineControl(turbine, msg);
							}
						});
					}

					context.setPacketHandled(true);
				}).add();

		INSTANCE.messageBuilder(SolarControlPayload.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(SolarControlPayload::write).decoder(SolarControlPayload::read)
				.consumerNetworkThread((msg, contextSupplier) -> {
					var context = contextSupplier.get();

					if (context.getDirection().getReceptionSide().isServer()) {
						context.enqueueWork(() -> {
							var player = context.getSender();
							if (player == null) return;

							var world = player.serverLevel();
							var pos = msg.blockPos();

							if (!world.hasChunkAt(pos) || !world.getWorldBorder().isWithinBounds(pos)) return;
							if (!world.mayInteract(player, pos)) return;
							// a plain distance check
							// block each, so there is no structure to stand anywhere along
							if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_CONTROL_DISTANCE_SQ) return;

							var blockEntity = world.getBlockEntity(pos);
							if (blockEntity instanceof PvInverterBlockEntity inverter) {
								applyInverterControl(inverter, msg);
							} else if (blockEntity instanceof PvArrayBlockEntity array) {
								applyArrayControl(array, msg);
							}
						});
					}

					context.setPacketHandled(true);
				}).add();

		INSTANCE.messageBuilder(WireConnectionPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(WireConnectionPayload::write).decoder(WireConnectionPayload::read)
				.consumerNetworkThread((msg, contextSupplier) -> {
					var context = contextSupplier.get();

					if (context.getDirection().getReceptionSide().isClient()) {
						context.enqueueWork(() -> ElectricityClient.handleWireConnectionPacket(msg));
					}

					context.setPacketHandled(true);
				}).add();

		INSTANCE.messageBuilder(PowerUpdatePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(PowerUpdatePayload::write).decoder(PowerUpdatePayload::read)
				.consumerNetworkThread((msg, contextSupplier) -> {
					var context = contextSupplier.get();

					if (context.getDirection().getReceptionSide().isClient()) {
						context.enqueueWork(() -> ElectricityClient.handlePowerUpdatePacket(msg));
					}

					context.setPacketHandled(true);
				}).add();
	}

	/** Whether the player is close enough to the machine to be working its panel. */
	private static boolean withinReach(ServerPlayer player, WindTurbineBlockEntity turbine) {
		BlockPos pos = turbine.getBlockPos();
		Vec3 axis = Vec3.atCenterOf(pos);
		double foot = pos.getY() - turbine.getTowerSegments();
		double nearestY = Mth.clamp(player.getY(), foot, pos.getY() + 1.0);

		return player.distanceToSqr(axis.x, nearestY, axis.z) <= MAX_CONTROL_DISTANCE_SQ;
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
