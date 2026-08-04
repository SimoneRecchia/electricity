package com.dooji.electricity.client.hooks;

import com.dooji.electricity.block.MachineShell;
import com.dooji.electricity.client.render.obj.ObjRaycaster;
import com.dooji.electricity.client.wire.WireAnchorHelper;
import com.dooji.electricity.client.wire.WireManagerClient;
import com.dooji.electricity.main.network.ElectricityNetworking;
import com.dooji.electricity.main.network.payloads.CreateWireFromInsulatorsPayload;
import com.dooji.electricity.wire.InsulatorPartHelper;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class WireClientHooks {
	private WireClientHooks() {
	}

	public static InteractionResult handleUseOn(UseOnContext context) {
		var level = context.getLevel();
		// most of what a player can see of a cabin or a pole is a collision cell rather than the machine
		// itself, and an insulator sits on top of one, so the click has to be read as meaning the machine
		BlockPos clickedPos = MachineShell.hostOr(level, context.getClickedPos());
		BlockEntity blockEntity = level.getBlockEntity(clickedPos);
		if (blockEntity == null) return InteractionResult.FAIL;

		String hoveredPart = resolveHoveredPart(clickedPos);
		if (hoveredPart == null) return InteractionResult.FAIL;

		Vec3 connectionPoint = WireAnchorHelper.anchorOrFallback(blockEntity, hoveredPart, ObjRaycaster.getPartCenter(clickedPos, hoveredPart));
		if (connectionPoint == null) return InteractionResult.FAIL;

		Vec3 pendingPos = WireManagerClient.getPendingConnection();
		String pendingPart = WireManagerClient.getPendingPartName();

		if (pendingPos == null) {
			WireManagerClient.setPendingConnection(connectionPoint, clickedPos);
			WireManagerClient.setPendingPartName(hoveredPart);
			return InteractionResult.SUCCESS;
		}

		if (pendingPos.equals(connectionPoint)) {
			WireManagerClient.clearPendingConnection();
			return InteractionResult.SUCCESS;
		}

		BlockPos pendingBlockPos = WireManagerClient.getPendingBlockPos();
		WireManagerClient.clearPendingConnection();
		return createWireFromInsulators(context, pendingBlockPos, clickedPos, pendingPart, hoveredPart);
	}

	private static InteractionResult createWireFromInsulators(UseOnContext context, BlockPos startBlockPos, BlockPos endBlockPos, String startPartName, String endPartName) {
		var level = context.getLevel();
		BlockEntity startEntity = level.getBlockEntity(startBlockPos);
		BlockEntity endEntity = level.getBlockEntity(endBlockPos);
		if (startEntity == null || endEntity == null) return InteractionResult.FAIL;

		Optional<InsulatorPartHelper.Insulator> start = InsulatorPartHelper.resolve(startEntity, startPartName);
		Optional<InsulatorPartHelper.Insulator> end = InsulatorPartHelper.resolve(endEntity, endPartName);
		if (start.isEmpty() || end.isEmpty()) return InteractionResult.FAIL;

		String startPower = InsulatorPartHelper.determinePowerType(startEntity, startPartName);
		String endPower = InsulatorPartHelper.determinePowerType(endEntity, endPartName);

		CreateWireFromInsulatorsPayload payload = new CreateWireFromInsulatorsPayload(start.get().insulatorId(), end.get().insulatorId(), startBlockPos, endBlockPos, start.get().blockType(),
				end.get().blockType(), startPower, endPower);

		ElectricityNetworking.INSTANCE.sendToServer(payload);
		return InteractionResult.SUCCESS;
	}

	private static String resolveHoveredPart(BlockPos clickedPos) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return null;
		Vec3 cameraPos = mc.player.getEyePosition();
		Vec3 lookDirection = mc.player.getLookAngle();
		return ObjRaycaster.getHoveredPart(cameraPos, lookDirection, clickedPos);
	}
}
