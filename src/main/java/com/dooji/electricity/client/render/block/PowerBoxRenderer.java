package com.dooji.electricity.client.render.block;

import com.dooji.electricity.block.PowerBoxBlock;
import com.dooji.electricity.block.PowerBoxBlockEntity;
import com.dooji.electricity.client.render.obj.ObjBlockRegistry;
import com.dooji.electricity.client.render.obj.ObjRenderContext;
import com.dooji.electricity.client.render.obj.ObjRendererBase;
import com.dooji.electricity.main.Electricity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Draws the pad-mount kiosk, on its plinth or back against a wall. */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Electricity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PowerBoxRenderer extends ObjRendererBase {
	private static final double MAX_RENDER_DISTANCE_SQ = 64 * 64;
	private static final Map<BlockPos, Map<String, GroupBuffer>> BUFFER_CACHE = new HashMap<>();

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		drawAll(event, PowerBoxBlockEntity.class, MAX_RENDER_DISTANCE_SQ, state -> state.getValue(PowerBoxBlock.FACING),
				PowerBoxBlock.AUTHORED, BUFFER_CACHE, PowerBoxRenderer::render);
	}

	/**
	 * Hung on a wall the kiosk loses its plinth and sits back against it.
	 *
	 * PowerBoxBlock.WALL_CELLS moves the collision by the same figure.
	 */
	private static void render(PowerBoxBlockEntity powerBox, ObjRenderContext context, PoseStack poseStack, Matrix4f projection) {
		BlockPos pos = powerBox.getBlockPos();
		if (!powerBox.getBlockState().getValue(PowerBoxBlock.MOUNTED)) {
			renderGrouped(context.model(), poseStack, projection, context.texture(), context.packedLight(), pos, BUFFER_CACHE);
			return;
		}

		Map<String, Matrix4f> poses = new HashMap<>();
		for (String groupName : context.model().groups.keySet()) {
			if (groupName.startsWith("plinth") || groupName.startsWith("conduit")) continue;

			poseStack.pushPose();
			poseStack.translate(0.0, 0.0, PowerBoxBlock.BACKSET);
			poses.put(groupName, new Matrix4f(poseStack.last().pose()));
			poseStack.popPose();
		}

		renderGrouped(context.model(), poses, projection, context.texture(), context.packedLight(), pos, BUFFER_CACHE);
	}

	public static void init() {
		ObjBlockRegistry.register(Electricity.POWER_BOX_BLOCK.get());
	}
}
