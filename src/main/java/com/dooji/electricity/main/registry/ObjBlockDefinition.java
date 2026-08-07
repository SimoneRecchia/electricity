package com.dooji.electricity.main.registry;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/**
 * One machine's model: which OBJ it is, which of its groups are fittings, and which way it was drawn.
 *
 * The authored facing is here because this is the only place that knows a block and its model together, and
 * anything that turns a part from the model's frame onto the world needs it - ObjRaycaster had been guessing,
 * with a hand-written table that was a quarter turn out at every facing.
 */
public record ObjBlockDefinition(Block block, ResourceLocation model, Direction authored,
		List<String> insulators) {
}
