package com.dooji.electricity.client.events;

import com.dooji.electricity.client.render.obj.ObjRaycaster;
import com.dooji.electricity.client.wire.WireAnchorHelper;
import com.dooji.electricity.client.wire.WireManagerClient;
import com.dooji.electricity.item.ConductorItem;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.network.ElectricityNetworking;
import com.dooji.electricity.main.network.payloads.CreateWireFromInsulatorsPayload;
import com.dooji.electricity.wire.InsulatorHost;
import com.dooji.electricity.wire.InsulatorPartHelper;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
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

		// The nearest fitting on the ray, not the first cell of the search cube: scanning x then y then z
		// returned whichever machine happened to be lowest and most westward, so aiming down a line of
		// towers picked the wrong one.
		BlockPos foundPos = null;
		String foundPart = null;
		Vec3 foundAnchor = null;
		double nearest = Double.MAX_VALUE;

		for (int x = -range; x <= range; x++) {
			for (int y = -range; y <= range; y++) {
				for (int z = -range; z <= range; z++) {
					BlockPos checkPos = playerPos.offset(x, y, z);

					BlockEntity blockEntity = mc.level.getBlockEntity(checkPos);
					if (!wireable(blockEntity)) continue;

					String hoveredPart = ObjRaycaster.getHoveredPart(cameraPos, lookDirection, checkPos);
					if (hoveredPart == null) continue;

					Vec3 fallback = ObjRaycaster.getPartCenter(checkPos, hoveredPart);
					Vec3 partCenter = WireAnchorHelper.anchorOrFallback(blockEntity, hoveredPart, fallback);
					if (partCenter == null) continue;

					double distance = cameraPos.distanceToSqr(partCenter);
					if (distance >= nearest) continue;

					nearest = distance;
					foundPos = checkPos;
					foundPart = hoveredPart;
					foundAnchor = partCenter;
				}
			}
		}

		if (foundAnchor != null) {
			handleOBJPartClick(player, hand, foundAnchor, foundPos, foundPart);
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

	/**
	 * Whether a wire may be attached to this block at all.
	  *
	 * {@link InsulatorHost} is the one definition of it. Listed by type instead, this chain named five
	 * machines and left out the lattice tower, the transformers and the ground conductor - so a player
	 * pointing at a tower's insulator, which is the one place a 400 kV line can land, got nothing.
	 */
	@OnlyIn(Dist.CLIENT)
	private static boolean wireable(net.minecraft.world.level.block.entity.BlockEntity entity) {
		return entity instanceof InsulatorHost;
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
