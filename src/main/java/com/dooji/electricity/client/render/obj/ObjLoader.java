package com.dooji.electricity.client.render.obj;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

// OBJ pipeline code will be migrated to Renderix
@OnlyIn(Dist.CLIENT)
public class ObjLoader {
	private static final Map<ResourceLocation, ObjModel> loadedModels = new HashMap<>();

	public static ObjModel getModel(ResourceLocation location) {
		ObjModel model = loadedModels.get(location);
		if (model == null) {
			model = ObjModel.loadFromResource(location);
			if (model != null) {
				loadedModels.put(location, model);
			}
		}
		return model;
	}
}
