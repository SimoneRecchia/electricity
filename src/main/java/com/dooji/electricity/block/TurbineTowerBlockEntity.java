package com.dooji.electricity.block;

import com.dooji.electricity.client.TrackedBlockEntities;
import com.dooji.electricity.main.Electricity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * A tower block's presence, and nothing else.
 *
 * It holds no state, saves nothing and never ticks. It exists because this mod draws
 * through {@code RenderLevelStageEvent} over tracked block entities rather than
 * through the chunk mesh: without something to be found by, a tower would be
 * invisible until a machine was mounted on it, which is no way to stack thirteen of
 * them.
 *
 * Only the block at the foot of a stack actually draws anything - it renders the whole
 * tube in one pass, the same geometry the machine draws once it is mounted - so the
 * rest of these are pure bookkeeping.
 */
public class TurbineTowerBlockEntity extends BlockEntity {
	public TurbineTowerBlockEntity(BlockPos pos, BlockState state) {
		super(Electricity.TURBINE_TOWER_BLOCK_ENTITY.get(), pos, state);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if (level != null && level.isClientSide()) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TrackedBlockEntities.track(this));
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if (level != null && level.isClientSide()) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TrackedBlockEntities.untrack(this));
		}
	}
}
