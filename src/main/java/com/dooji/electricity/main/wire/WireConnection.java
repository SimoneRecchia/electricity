package com.dooji.electricity.main.wire;

import net.minecraft.core.BlockPos;

/**
 * One span: which two fittings it joins, what it is made of, and how many reels it was charged for.
 *
 * The block and power types are the strings a saved world holds - see {@link com.dooji.electricity.wire.InsulatorHost#fittingType()}
 * - so they travel with the span rather than being looked up from the world, which may not have the chunk loaded.
 */
public record WireConnection(int startInsulatorId, int endInsulatorId, String wireType, BlockPos startBlockPos,
		BlockPos endBlockPos, String startBlockType, String endBlockType, String startPowerType,
		String endPowerType, int chargedItems) {
	public WireConnection {
		chargedItems = Math.max(0, chargedItems);
	}

	/** A span that has not been charged for yet: what arrives over the wire, before the reel is debited. */
	public WireConnection(int startInsulatorId, int endInsulatorId, String wireType, BlockPos startBlockPos,
			BlockPos endBlockPos, String startBlockType, String endBlockType, String startPowerType,
			String endPowerType) {
		this(startInsulatorId, endInsulatorId, wireType, startBlockPos, endBlockPos, startBlockType, endBlockType,
				startPowerType, endPowerType, 0);
	}
}
