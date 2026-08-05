package com.dooji.electricity.block;

import com.dooji.electricity.main.Electricity;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

/** A tower block's presence, and nothing else. */
public class TurbineTowerBlockEntity extends BlockEntity {
	/** Last machine found above this block, so the walk up is not repeated on every query. */
	@Nullable
	private BlockPos machinePos;

	public TurbineTowerBlockEntity(BlockPos pos, BlockState state) {
		super(Electricity.TURBINE_TOWER_BLOCK_ENTITY.get(), pos, state);
	}

	/** The foot of a tower carries its machine's cables. */
	@Nonnull
	@Override
	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
		WindTurbineBlockEntity machine = machine();
		if (machine != null) return machine.getCapability(cap, side);

		return super.getCapability(cap, side);
	}

	/** The machine this block carries the cables for, or null if it carries none. */
	@Nullable
	private WindTurbineBlockEntity machine() {
		if (level == null || TurbineTowerBlock.countBelow(level, worldPosition) != 0) return null;

		if (machinePos == null || !(level.getBlockState(machinePos).getBlock() instanceof WindTurbineBlock)) {
			machinePos = TurbineTowerBlock.findTurbineAbove(level, worldPosition);
		}

		if (machinePos == null) return null;

		return level.getBlockEntity(machinePos) instanceof WindTurbineBlockEntity turbine ? turbine : null;
	}

	@Override
	public void onLoad() {
		super.onLoad();
		ClientTracking.track(this);
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		ClientTracking.untrack(this);
	}
}
