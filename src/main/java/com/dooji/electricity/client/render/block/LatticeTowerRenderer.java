package com.dooji.electricity.client.render.block;

import com.dooji.electricity.block.LatticeTowerBlock;
import com.dooji.electricity.block.LatticeTowerBlockEntity;
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

/**
 * Draws the three lattice towers.
 *
 * Nothing on a tower moves. Further than the machines because a tower is eleven blocks tall and is meant to
 * be seen from the next one along.
 */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Electricity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class LatticeTowerRenderer extends ObjRendererBase {
	private static final double MAX_RENDER_DISTANCE_SQ = 192 * 192;
	private static final Map<BlockPos, Map<String, GroupBuffer>> BUFFER_CACHE = new HashMap<>();

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		drawAll(event, LatticeTowerBlockEntity.class, MAX_RENDER_DISTANCE_SQ,
				state -> state.getValue(LatticeTowerBlock.FACING), LatticeTowerBlock.AUTHORED, BUFFER_CACHE);
	}

	public static void init() {
		ObjBlockRegistry.registerAll(Electricity.LATTICE_TOWER_BLOCKS);
	}
}
