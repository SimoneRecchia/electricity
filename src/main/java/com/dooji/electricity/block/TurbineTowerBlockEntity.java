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

/**
 * A tower block's presence, and nothing else.
 *
 * It saves nothing and never ticks. It exists because this mod draws through
 * {@code RenderLevelStageEvent} over tracked block entities rather than through the
 * chunk mesh: without something to be found by, a tower would be invisible until a
 * machine was mounted on it, which is no way to stack thirteen of them.
 *
 * The block at the foot of a stack does two jobs the rest do not. It draws the whole
 * tube in one pass - the same geometry the machine draws once one is mounted - and it
 * answers energy capability queries for the machine above, because the foot is where a
 * turbine's cables belong. Every other block in a tower is pure bookkeeping.
 */
public class TurbineTowerBlockEntity extends BlockEntity {
	/** Last machine found above this block, so the walk up is not repeated on every query. */
	@Nullable
	private BlockPos machinePos;

	public TurbineTowerBlockEntity(BlockPos pos, BlockState state) {
		super(Electricity.TURBINE_TOWER_BLOCK_ENTITY.get(), pos, state);
	}

	/**
	 * The foot of a tower carries its machine's cables.
	 *
	 * Mekanism's Wind Generator is a block on the ground with a decorative tower above it,
	 * and its energy leaves through the bottom and front faces of that block. Here the tower
	 * is real and the machine sits on top of it, so the block standing where Mekanism's does
	 * is this one - and it has to answer for the machine, or a cable at the tower base finds
	 * nothing and the turbine is unreachable from any height a player would build at.
	 *
	 * Delegated rather than reimplemented, so the faces stay the machine's own: {@code side}
	 * goes through untouched and the turbine applies its own front-and-bottom rule to it.
	 */
	@Nonnull
	@Override
	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
		WindTurbineBlockEntity machine = machine();
		if (machine != null) return machine.getCapability(cap, side);

		return super.getCapability(cap, side);
	}

	/**
	 * The machine this block carries the cables for, or null if it carries none.
	 *
	 * Only the foot answers. Ruling the rest out costs a single lookup, because a block with
	 * a tower beneath it is not the foot and {@code countBelow} says so on its first step;
	 * the walk up to the machine is the part worth remembering, so it is cached and only
	 * repeated once what was cached is no longer a turbine.
	 */
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
