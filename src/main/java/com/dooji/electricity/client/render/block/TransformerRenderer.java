package com.dooji.electricity.client.render.block;

import com.dooji.electricity.block.TransformerBlock;
import com.dooji.electricity.block.TransformerBlockEntity;
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

/** Draws the two transformers. Nothing on either moves: an oil-filled machine has no moving part outside it. */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Electricity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class TransformerRenderer extends ObjRendererBase {
	private static final double MAX_RENDER_DISTANCE_SQ = 128 * 128;
	private static final Map<BlockPos, Map<String, GroupBuffer>> BUFFER_CACHE = new HashMap<>();

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		drawAll(event, TransformerBlockEntity.class, MAX_RENDER_DISTANCE_SQ,
				state -> state.getValue(TransformerBlock.FACING), TransformerBlock.AUTHORED, BUFFER_CACHE);
	}

	public static void init() {
		ObjBlockRegistry.registerAll(Electricity.TRANSFORMER_BLOCKS);
	}
}
