package com.dooji.electricity.client.hooks;

import com.dooji.electricity.block.MachineShell;
import com.dooji.electricity.block.MetStationBlockEntity;
import com.dooji.electricity.block.PlantControllerBlockEntity;
import com.dooji.electricity.block.PvArrayBlockEntity;
import com.dooji.electricity.block.PvCombinerBlockEntity;
import com.dooji.electricity.block.PvInverterBlockEntity;
import com.dooji.electricity.block.TurbineTowerBlock;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import com.dooji.electricity.client.render.obj.ObjRaycaster;
import com.dooji.electricity.client.screen.MetStationScreen;
import com.dooji.electricity.client.screen.PlantControllerScreen;
import com.dooji.electricity.client.screen.PowerInfoScreen;
import com.dooji.electricity.client.screen.PvArrayScreen;
import com.dooji.electricity.client.screen.PvCombinerScreen;
import com.dooji.electricity.client.screen.PvInverterScreen;
import com.dooji.electricity.client.screen.WindTurbineScreen;
import com.dooji.electricity.main.Electricity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraftforge.registries.ForgeRegistries;

@OnlyIn(Dist.CLIENT)
public final class PowerWrenchClientHooks {
	/**
	 * Which panel a machine gets.  A machine with none gets the plain readout, which is all there is to say
	 * about a pole or a junction box - so adding a panel is a line here rather than a branch in a chain.
	 */
	private static final Map<Class<?>, Function<BlockPos, Screen>> PANELS = panels();

	private static Map<Class<?>, Function<BlockPos, Screen>> panels() {
		Map<Class<?>, Function<BlockPos, Screen>> out = new LinkedHashMap<>();
		out.put(WindTurbineBlockEntity.class, WindTurbineScreen::new);
		out.put(PvInverterBlockEntity.class, PvInverterScreen::new);
		out.put(PvArrayBlockEntity.class, PvArrayScreen::new);
		out.put(PvCombinerBlockEntity.class, PvCombinerScreen::new);
		out.put(MetStationBlockEntity.class, MetStationScreen::new);
		out.put(PlantControllerBlockEntity.class, PlantControllerScreen::new);
		return out;
	}

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
		if (target == null) target = solidBlockUnderCursor(mc);
		if (target == null) return false;

		target = resolveTarget(mc, target);

		BlockEntity blockEntity = mc.level.getBlockEntity(target);
		for (var panel : PANELS.entrySet()) {
			if (panel.getKey().isInstance(blockEntity)) {
				mc.setScreen(panel.getValue().apply(target));
				return true;
			}
		}

		mc.setScreen(new PowerInfoScreen(target));
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

					if (!isMachine(mc.level, cursor)) continue;

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

	/** Whether the machine's own geometry is what the player can see */
	private static boolean hasLineOfSight(Minecraft mc, Player player, Vec3 start, Vec3 end, BlockPos targetPos) {
		if (mc.level == null) return false;
		ClipContext context = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
		BlockHitResult hitResult = mc.level.clip(context);
		if (hitResult.getType() == HitResult.Type.MISS) return true;
		return MachineShell.hostOr(mc.level, hitResult.getBlockPos()).equals(targetPos);
	}

	/** One of this mod's blocks under the crosshair, found by ordinary picking. */
	@Nullable
	private static BlockPos solidBlockUnderCursor(Minecraft mc) {
		if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit)) return null;

		BlockPos pos = MachineShell.hostOr(mc.level, hit.getBlockPos());
		if (mc.level.getBlockState(pos).getBlock() instanceof TurbineTowerBlock) return pos;
		if (isMachine(mc.level, pos)) return pos;

		return null;
	}

	/** The block a wrench click should act on. */
	private static BlockPos resolveTarget(Minecraft mc, BlockPos hit) {
		if (mc.level == null) return hit;
		if (!(mc.level.getBlockState(hit).getBlock() instanceof TurbineTowerBlock)) return hit;

		BlockPos turbine = TurbineTowerBlock.findTurbineAbove(mc.level, hit);
		return turbine != null ? turbine : hit;
	}

	/**
	 * One of this mod's machines: a block entity whose block is registered under this namespace.
	 *
	 * It used to be eight block entities named one by one, and the mod has thirteen - so a transformer, a
	 * switch, a lattice tower and a ground run could not be pointed at with the wrench at all, although each
	 * of them already answered {@code getCurrentPower()}.  Asking the registry cannot go stale.
	 */
	private static boolean isMachine(Level level, BlockPos pos) {
		if (level.getBlockEntity(pos) == null) return false;

		ResourceLocation id = ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock());
		return id != null && Electricity.MOD_ID.equals(id.getNamespace());
	}
}
