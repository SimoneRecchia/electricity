package com.dooji.electricity.client.render.obj;

import java.util.HashMap;
import java.util.Map;
import com.dooji.electricity.main.registry.ObjBlockDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

// OBJ pipeline code will be migrated to Renderix
@OnlyIn(Dist.CLIENT)
public class ObjBlockRegistry {
	private static final Map<Block, Entry> ENTRIES = new HashMap<>();

	private record Entry(ResourceLocation model, ResourceLocation texture) {
	}

	public static void register(Block block, ResourceLocation modelLocation, ResourceLocation textureLocation) {
		ENTRIES.put(block, new Entry(modelLocation, textureLocation));
	}

	/**
	 * Makes a block renderable and marks its insulators clickable.
	 *
	 * The two registries always go together, and every renderer opened with the same four lines
	 * to fill them. Saying it once means a machine cannot be given a model and then quietly left
	 * unclickable.
	 */
	public static void register(ObjBlockDefinition definition) {
		register(definition.block(), definition.model(), null);
		for (String insulator : definition.insulators()) {
			ObjInteractionRegistry.register(definition.block(), insulator);
		}
	}

	public static ResourceLocation getModelLocation(Block block) {
		Entry entry = ENTRIES.get(block);
		return entry != null ? entry.model() : null;
	}

	public static ResourceLocation getTextureLocation(Block block) {
		Entry entry = ENTRIES.get(block);
		return entry != null ? entry.texture() : null;
	}

	public static boolean hasModel(Block block) {
		return ENTRIES.containsKey(block);
	}
}
