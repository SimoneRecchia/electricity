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
	 * Makes a block renderable, marks its fittings clickable and says where each one is.
	 *
	 * The box per fitting used to be a private method in each of eight renderers, all of them the same loop
	 * over the same list this one already walks - and a renderer that forgot it had fittings a wire could be
	 * clicked onto and never found.
	 */
	public static void register(ObjBlockDefinition definition) {
		register(definition.block(), definition.model(), null);
		ObjModel model = ObjLoader.getModel(definition.model());
		Map<String, ObjModel.BoundingBox> boxes = new HashMap<>();
		for (String insulator : definition.insulators()) {
			ObjInteractionRegistry.register(definition.block(), insulator);
			ObjModel.BoundingBox box = model == null ? null : model.getBoundingBox(insulator);
			if (box != null) boxes.put(insulator, box);
		}

		if (!boxes.isEmpty()) ObjBoundingBoxRegistry.registerBoundingBoxes(definition.block(), boxes);
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
