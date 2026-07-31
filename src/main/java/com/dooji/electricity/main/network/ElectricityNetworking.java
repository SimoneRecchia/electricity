package com.dooji.electricity.main.network;

import com.dooji.electricity.block.UtilityPoleBlockEntity;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import com.dooji.electricity.client.ElectricityClient;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.network.payloads.CreateWireFromInsulatorsPayload;
import com.dooji.electricity.main.network.payloads.PowerUpdatePayload;
import com.dooji.electricity.main.network.payloads.SyncWiresPayload;
import com.dooji.electricity.main.network.payloads.TurbineControlPayload;
import com.dooji.electricity.main.network.payloads.UpdateUtilityPoleConfigPayload;
import com.dooji.electricity.main.network.payloads.WireConnectionPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ElectricityNetworking {
	private static final String PROTOCOL_VERSION = "1";
	public static final ResourceLocation NETWORK_CHANNEL = ResourceLocation.tryBuild(Electricity.MOD_ID, "main");
	public static SimpleChannel INSTANCE;
	/** Six blocks: a panel is reached from the foot of the tower, not from across the world. */
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
							// a control panel is worked from in front of the machine. Without this a
							// crafted packet could stop every turbine on the server from spawn.
							if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_CONTROL_DISTANCE_SQ) return;

							if (world.getBlockEntity(pos) instanceof WindTurbineBlockEntity turbine) {
								applyTurbineControl(player, turbine, msg);
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

	/**
	 * Applies one panel command.
	 *
	 * Nothing here trusts the payload's number: the block entity's own setters clamp the
	 * curtailment setpoint to the machine's nameplate and the tower to the range its model
	 * is sold on, so the worst a crafted packet achieves is a setting the player could
	 * have dialled in by hand anyway.
	 */
	private static void applyTurbineControl(ServerPlayer player, WindTurbineBlockEntity turbine, TurbineControlPayload msg) {
		switch (msg.action()) {
			case TOGGLE_RUNNING -> turbine.setStoppedByPlayer(!turbine.isStoppedByPlayer());
			case CYCLE_REDSTONE_MODE -> turbine.setRedstoneMode(turbine.getRedstoneMode().next());
			case SET_POWER_LIMIT -> turbine.setActivePowerLimit(msg.value());
			case ADD_TOWER_SEGMENT -> raiseTower(player, turbine);
			case REMOVE_TOWER_SEGMENT -> lowerTower(player, turbine);
		}
	}

	/**
	 * Spends a tower segment to stand the nacelle one block higher.
	 *
	 * The segment comes out of the inventory before the tower goes up, and only if the
	 * tower can actually go up: taking the item first and then finding the machine already
	 * at its tallest would charge the player for nothing.
	 */
	private static void raiseTower(ServerPlayer player, WindTurbineBlockEntity turbine) {
		if (turbine.getTowerSegments() >= turbine.spec().maxTowerSegments()) return;

		boolean free = player.isCreative();
		int slot = free ? -1 : findTowerSegment(player);
		if (!free && slot < 0) return;

		if (!turbine.setTowerSegments(turbine.getTowerSegments() + 1)) return;
		if (!free) player.getInventory().removeItem(slot, 1);
	}

	/** Takes a segment back down and hands the item back, so height stays a reversible choice. */
	private static void lowerTower(ServerPlayer player, WindTurbineBlockEntity turbine) {
		if (turbine.getTowerSegments() <= turbine.spec().minTowerSegments()) return;
		if (!turbine.setTowerSegments(turbine.getTowerSegments() - 1)) return;

		if (!player.isCreative()) {
			ItemStack returned = new ItemStack(Electricity.TOWER_SEGMENT_ITEM.get());
			if (!player.getInventory().add(returned)) player.drop(returned, false);
		}
	}

	private static int findTowerSegment(ServerPlayer player) {
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (inventory.getItem(slot).is(Electricity.TOWER_SEGMENT_ITEM.get())) return slot;
		}

		return -1;
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
