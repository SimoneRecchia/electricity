package com.dooji.electricity.client.events;

import com.dooji.electricity.block.ElectricCabinBlock;
import com.dooji.electricity.block.ElectricCabinBlockEntity;
import com.dooji.electricity.block.PowerBoxBlock;
import com.dooji.electricity.block.PowerBoxBlockEntity;
import com.dooji.electricity.block.UtilityPoleBlock;
import com.dooji.electricity.block.UtilityPoleBlockEntity;
import com.dooji.electricity.block.WindTurbineBlock;
import com.dooji.electricity.block.PvInverterBlockEntity;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import com.dooji.electricity.client.render.obj.ObjRaycaster;
import com.dooji.electricity.client.wire.WireAnchorHelper;
import com.dooji.electricity.client.wire.WireManagerClient;
import com.dooji.electricity.item.ConductorItem;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.network.ElectricityNetworking;
import com.dooji.electricity.main.network.payloads.CreateWireFromInsulatorsPayload;
import com.dooji.electricity.wire.InsulatorPartHelper;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Electricity.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WireInteractionEvents {
	@OnlyIn(Dist.CLIENT) @SubscribeEvent
	public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
		Player player = event.getEntity();
		if (player == null) return;

		InteractionHand hand = InteractionHand.MAIN_HAND;
		ItemStack heldItem = player.getItemInHand(hand);
		if (!(heldItem.getItem() instanceof ConductorItem)) {
			hand = InteractionHand.OFF_HAND;
			heldItem = player.getItemInHand(hand);
			if (!(heldItem.getItem() instanceof ConductorItem)) return;
		}

		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;

		Vec3 cameraPos = mc.player.getEyePosition();
		Vec3 lookDirection = mc.player.getLookAngle();

		int range = 16;
		BlockPos playerPos = mc.player.blockPosition();

		for (int x = -range; x <= range; x++) {
			for (int y = -range; y <= range; y++) {
				for (int z = -range; z <= range; z++) {
					BlockPos checkPos = playerPos.offset(x, y, z);

					BlockEntity blockEntity = mc.level.getBlockEntity(checkPos);
					if (blockEntity != null) {
						String hoveredPart = ObjRaycaster.getHoveredPart(cameraPos, lookDirection, checkPos);
						if (hoveredPart != null) {
							var blockState = mc.level.getBlockState(checkPos);

							Direction facing = null;

							if (blockState.hasProperty(UtilityPoleBlock.FACING)) {
								facing = blockState.getValue(UtilityPoleBlock.FACING);
							} else if (blockState.hasProperty(ElectricCabinBlock.FACING)) {
								facing = blockState.getValue(ElectricCabinBlock.FACING);
							} else if (blockState.hasProperty(PowerBoxBlock.FACING)) {
								facing = blockState.getValue(PowerBoxBlock.FACING);
							} else if (blockState.hasProperty(WindTurbineBlock.FACING)) {
								facing = blockState.getValue(WindTurbineBlock.FACING);
							}

							Vec3 fallback = ObjRaycaster.getPartCenter(checkPos, hoveredPart, facing);
							Vec3 partCenter = WireAnchorHelper.anchorOrFallback(blockEntity, hoveredPart, fallback);
							if (partCenter != null) {
								handleOBJPartClick(player, hand, partCenter, checkPos, hoveredPart);
								return;
							}
						}
					}
				}
			}
		}
	}

	@OnlyIn(Dist.CLIENT)
	private static void handleOBJPartClick(Player player, InteractionHand hand, Vec3 connectionPoint, BlockPos blockPos, String partName) {
		Vec3 pendingPos = WireManagerClient.getPendingConnection();
		String pendingPartName = WireManagerClient.getPendingPartName();
		boolean performedAction = false;

		if (pendingPos == null) {
			WireManagerClient.setPendingConnection(connectionPoint, blockPos);
			WireManagerClient.setPendingPartName(partName);
			performedAction = true;
		} else if (!pendingPos.equals(connectionPoint)) {
			BlockPos pendingBlockPos = WireManagerClient.getPendingBlockPos();
			WireManagerClient.clearPendingConnection();

			performedAction = createWireFromInsulators(pendingBlockPos, blockPos, pendingPartName, partName);
		} else {
			WireManagerClient.clearPendingConnection();
		}

		if (performedAction && player != null) {
			player.swing(hand);
		}
	}

	/** Whether a wire may be attached to this block at all. */
	@OnlyIn(Dist.CLIENT)
	private static boolean wireable(net.minecraft.world.level.block.entity.BlockEntity entity) {
		return entity instanceof UtilityPoleBlockEntity || entity instanceof ElectricCabinBlockEntity
				|| entity instanceof PowerBoxBlockEntity || entity instanceof WindTurbineBlockEntity
				|| entity instanceof PvInverterBlockEntity;
	}

	@OnlyIn(Dist.CLIENT)
	private static boolean createWireFromInsulators(BlockPos startBlockPos, BlockPos endBlockPos, String startPartName, String endPartName) {
		var level = Minecraft.getInstance().level;
		if (level == null) return false;

		var startEntity = level.getBlockEntity(startBlockPos);
		var endEntity = level.getBlockEntity(endBlockPos);

		if (!wireable(startEntity) || !wireable(endEntity)) {
			return false;
		}

		Optional<InsulatorPartHelper.Insulator> start = InsulatorPartHelper.resolve(startEntity, startPartName);
		Optional<InsulatorPartHelper.Insulator> end = InsulatorPartHelper.resolve(endEntity, endPartName);
		if (start.isEmpty() || end.isEmpty()) return false;

		String startBlockType = start.get().blockType();
		String endBlockType = end.get().blockType();

		String startPowerType = InsulatorPartHelper.determinePowerType(startEntity, startPartName);
		String endPowerType = InsulatorPartHelper.determinePowerType(endEntity, endPartName);

		CreateWireFromInsulatorsPayload payload = new CreateWireFromInsulatorsPayload(start.get().insulatorId(), end.get().insulatorId(), startBlockPos, endBlockPos, startBlockType, endBlockType,
				startPowerType, endPowerType);

		ElectricityNetworking.INSTANCE.sendToServer(payload);
		return true;
	}
}
