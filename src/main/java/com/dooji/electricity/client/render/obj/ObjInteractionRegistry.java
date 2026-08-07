package com.dooji.electricity.client.render.obj;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Which named parts of a block's OBJ model a player can click on.
  *
 * {@link ObjRaycaster} reads it to decide what a ray is allowed to hit.
 */
// OBJ pipeline code will be migrated to Renderix
@OnlyIn(Dist.CLIENT)
public class ObjInteractionRegistry {
	private static final Map<Block, Set<String>> PARTS = new HashMap<>();

	public static void register(Block block, String partName) {
		PARTS.computeIfAbsent(block, key -> new HashSet<>()).add(partName);
	}

	public static Set<String> getInteractiveParts(Block block) {
		return Collections.unmodifiableSet(PARTS.getOrDefault(block, Collections.emptySet()));
	}
}
