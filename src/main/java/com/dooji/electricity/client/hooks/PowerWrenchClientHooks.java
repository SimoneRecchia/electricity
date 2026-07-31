package com.dooji.electricity.client.hooks;

import com.dooji.electricity.block.ElectricCabinBlockEntity;
import com.dooji.electricity.block.PowerBoxBlockEntity;
import com.dooji.electricity.block.UtilityPoleBlockEntity;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import com.dooji.electricity.client.render.obj.ObjRaycaster;
import com.dooji.electricity.client.screen.PowerInfoScreen;
import com.dooji.electricity.client.screen.WindTurbineScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PowerWrenchClientHooks {
	private PowerWrenchClientHooks() {
	}

	public static InteractionResultHolder<ItemStack> handleUse(Level level, Player player, InteractionHand hand, double reach) {
		ItemStack stack = player.getItemInHand(hand);
		if (tryOpenDiagnostics(player, reach)) return InteractionResultHolder.success(stack);
		return InteractionResultHolder.pass(stack);
	}

	public static InteractionResult handleUseOn(UseOnContext context, double reach) {
		Player player = context.getPlayer();
		if (player != null && tryOpenDiagnostics(player, reach)) return InteractionResult.SUCCESS;
		return InteractionResult.PASS;
	}

	private static boolean tryOpenDiagnostics(Player player, double reach) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return false;

		BlockPos target = findTargetedElectricBlock(mc, player, reach);
		if (target == null) return false;

		// a turbine has a control panel of its own; everything else still gets the plain
		// readout, which is all there is to say about a pole or a junction box
		if (mc.level.getBlockEntity(target) instanceof WindTurbineBlockEntity) {
			mc.setScreen(new WindTurbineScreen(target));
		} else {
			mc.setScreen(new PowerInfoScreen(target));
		}

		return true;
	}

	private static BlockPos findTargetedElectricBlock(Minecraft mc, Player player, double reach) {
		Vec3 eyePosition = player.getEyePosition(1.0f);
		Vec3 lookDirection = player.getLookAngle();
		BlockPos playerPos = player.blockPosition();
		int range = (int) Math.ceil(reach);
		double maxDistance = reach * reach;

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		BlockPos result = null;
		double bestDistance = Double.MAX_VALUE;

		for (int x = -range; x <= range; x++) {
			for (int y = -range; y <= range; y++) {
				for (int z = -range; z <= range; z++) {
					cursor.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);
					if (!mc.level.hasChunkAt(cursor)) continue;
					Vec3 centerEstimate = Vec3.atCenterOf(cursor);
					if (eyePosition.distanceToSqr(centerEstimate) > maxDistance) continue;

					BlockEntity blockEntity = mc.level.getBlockEntity(cursor);
					if (!isElectricBlock(blockEntity)) continue;

					Vec3 hitPoint = ObjRaycaster.pickAnyGeometry(eyePosition, lookDirection, cursor);
					if (hitPoint == null) continue;

					if (!hasLineOfSight(mc, player, eyePosition, hitPoint, cursor)) continue;

					double distance = eyePosition.distanceToSqr(hitPoint);
					if (distance < bestDistance) {
						bestDistance = distance;
						result = cursor.immutable();
					}
				}
			}
		}

		return result;
	}

	private static boolean hasLineOfSight(Minecraft mc, Player player, Vec3 start, Vec3 end, BlockPos targetPos) {
		if (mc.level == null) return false;
		ClipContext context = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
		BlockHitResult hitResult = mc.level.clip(context);
		if (hitResult.getType() == HitResult.Type.MISS) return true;
		return hitResult.getBlockPos().equals(targetPos);
	}

	private static boolean isElectricBlock(BlockEntity blockEntity) {
		return blockEntity instanceof WindTurbineBlockEntity || blockEntity instanceof ElectricCabinBlockEntity || blockEntity instanceof UtilityPoleBlockEntity
				|| blockEntity instanceof PowerBoxBlockEntity;
	}
}
