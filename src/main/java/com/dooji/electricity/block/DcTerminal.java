package com.dooji.electricity.block;

import com.dooji.electricity.api.power.DcCableSpec;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** A block a direct-current cable can be landed on. */
public interface DcTerminal {
	/** Whether a cable of this kind connects to this block as it stands. */
	boolean acceptsCable(BlockState state, DcCableSpec cable, Direction side);

	/** The state this block takes when a length of that cable is worked into it */
	@Nullable
	default BlockState withCableFitted(BlockState state, DcCableSpec cable) {
		return null;
	}
}
