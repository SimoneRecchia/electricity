package com.dooji.electricity.client.render.block;

import com.dooji.electricity.block.PowerBoxBlock;
import com.dooji.electricity.block.PowerBoxBlockEntity;
import com.dooji.electricity.client.TrackedBlockEntities;
import com.dooji.electricity.client.render.obj.ObjBlockRegistry;
import com.dooji.electricity.client.render.obj.ObjBoundingBoxRegistry;
import com.dooji.electricity.client.render.obj.ObjLoader;
import com.dooji.electricity.client.render.obj.ObjModel;
import com.dooji.electricity.client.render.obj.ObjRenderUtil;
import com.dooji.electricity.client.render.obj.ObjRendererBase;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.registry.ObjBlockDefinition;
import com.dooji.electricity.main.registry.ObjDefinitions;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Electricity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PowerBoxRenderer extends ObjRendererBase {
	private static final double MAX_RENDER_DISTANCE_SQ = 64 * 64;
	private static final Logger LOGGER = LoggerFactory.getLogger(Electricity.MOD_ID);
	private static final Map<BlockPos, Map<String, GroupBuffer>> BUFFER_CACHE = new HashMap<>();

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;

		Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
		HashSet<BlockPos> seen = new HashSet<>();

		for (PowerBoxBlockEntity powerBox : TrackedBlockEntities.ofType(PowerBoxBlockEntity.class)) {
			seen.add(powerBox.getBlockPos());
			ObjRenderUtil.withAlignedPose(powerBox, event.getPoseStack(), mc.renderBuffers().bufferSource(), cameraPos, MAX_RENDER_DISTANCE_SQ, state -> state.getValue(PowerBoxBlock.FACING),
					turnedFrom(PowerBoxBlock.AUTHORED),
					(context, pose, buffers) -> renderBaked(context.model(), pose, event.getProjectionMatrix(), context.texture(), context.packedLight(), powerBox.getBlockPos()));
		}

		cleanupCache(BUFFER_CACHE, seen);
	}

	public static void init() {
		ObjBlockDefinition definition = ObjDefinitions.get(Electricity.POWER_BOX_BLOCK.get());
		if (definition == null) return;
		ObjBlockRegistry.register(definition);

		calculateAndRegisterBoundingBoxes(definition);
	}

	private static void calculateAndRegisterBoundingBoxes(ObjBlockDefinition definition) {
		var model = ObjLoader.getModel(definition.model());
		if (model == null) {
			LOGGER.error("Failed to load Power Box model");
			return;
		}

		Map<String, ObjModel.BoundingBox> insulatorBoxes = new HashMap<>();

		for (String groupName : definition.insulators()) {
			ObjModel.BoundingBox bbox = model.getBoundingBox(groupName);
			if (bbox != null) {
				insulatorBoxes.put(groupName, bbox);
			} else {
				LOGGER.warn("Missing Power Box insulator group: {}", groupName);
			}
		}

		ObjBoundingBoxRegistry.registerBoundingBoxes(definition.block(), insulatorBoxes);
	}


	private static void renderBaked(ObjModel model, PoseStack poseStack, Matrix4f projectionMatrix, ResourceLocation texture, int packedLight, BlockPos pos) {
		renderGrouped(model, poseStack, projectionMatrix, texture, packedLight, pos, BUFFER_CACHE);
	}
}
