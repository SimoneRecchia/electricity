package com.dooji.electricity.client.wire;

import com.dooji.electricity.block.ElectricCabinBlock;
import com.dooji.electricity.block.LatticeTowerBlock;
import com.dooji.electricity.block.PowerBoxBlock;
import com.dooji.electricity.block.UtilityPoleBlock;
import com.dooji.electricity.block.WindTurbineBlock;
import com.dooji.electricity.client.ElectricityClientConfig;
import com.dooji.electricity.client.render.obj.ObjRaycaster;
import com.dooji.electricity.wire.InsulatorPartHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class WireAnchorHelper {
	private WireAnchorHelper() {
	}

	public static Vec3 anchorOrFallback(BlockPos pos, String partName) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return Vec3.atCenterOf(pos);
		BlockEntity entity = mc.level.getBlockEntity(pos);
		Vec3 fallback = computeFallback(mc.level.getBlockState(pos), pos, partName);
		return anchorOrFallback(entity, partName, fallback);
	}

	public static Vec3 anchorOrFallback(BlockEntity entity, String partName, Vec3 fallback) {
		if (entity != null && ElectricityClientConfig.previewSnappingEnabled()) {
			return InsulatorPartHelper.resolve(entity, partName).map(InsulatorPartHelper.Insulator::anchor).orElse(fallback != null ? fallback : Vec3.atCenterOf(entity.getBlockPos()));
		}
		return fallback;
	}

	private static Vec3 computeFallback(BlockState state, BlockPos pos, String partName) {
		Direction facing = extractFacing(state);
		Vec3 partCenter = ObjRaycaster.getPartCenter(pos, partName, facing);
		if (partCenter != null) return partCenter;
		return Vec3.atCenterOf(pos);
	}

	private static Direction extractFacing(BlockState state) {
		if (state == null) return null;
		if (state.hasProperty(UtilityPoleBlock.FACING)) return state.getValue(UtilityPoleBlock.FACING);
		if (state.hasProperty(ElectricCabinBlock.FACING)) return state.getValue(ElectricCabinBlock.FACING);
		if (state.hasProperty(PowerBoxBlock.FACING)) return state.getValue(PowerBoxBlock.FACING);
		if (state.hasProperty(WindTurbineBlock.FACING)) return state.getValue(WindTurbineBlock.FACING);
		if (state.hasProperty(LatticeTowerBlock.FACING)) return state.getValue(LatticeTowerBlock.FACING);
		return null;
	}
}
