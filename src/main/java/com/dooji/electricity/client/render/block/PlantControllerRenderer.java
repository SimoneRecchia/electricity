package com.dooji.electricity.client.render.block;

import com.dooji.electricity.block.PlantControllerBlock;
import com.dooji.electricity.block.PlantControllerBlockEntity;
import com.dooji.electricity.client.render.obj.ObjBlockRegistry;
import com.dooji.electricity.client.render.obj.ObjRendererBase;
import com.dooji.electricity.main.Electricity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Draws the plant control cabinet. Nothing on it moves: the door stays shut and the whip does not sway. */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Electricity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PlantControllerRenderer extends ObjRendererBase {
	private static final double MAX_RENDER_DISTANCE_SQ = 64 * 64;
	private static final Map<BlockPos, Map<String, GroupBuffer>> BUFFER_CACHE = new HashMap<>();

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		drawAll(event, PlantControllerBlockEntity.class, MAX_RENDER_DISTANCE_SQ,
				state -> state.getValue(PlantControllerBlock.FACING), PlantControllerBlock.AUTHORED, BUFFER_CACHE);
	}

	public static void init() {
		ObjBlockRegistry.register(Electricity.PLANT_CONTROLLER_BLOCK.get());
	}
}
