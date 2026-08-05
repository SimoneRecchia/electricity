package com.dooji.electricity.client.render.block;

import com.dooji.electricity.api.power.TransformerSpec;
import com.dooji.electricity.block.TransformerBlock;
import com.dooji.electricity.block.TransformerBlockEntity;
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
import com.dooji.electricity.main.registry.TransformerCatalog;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
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
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;

		Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
		HashSet<BlockPos> seen = new HashSet<>();

		for (TransformerBlockEntity transformer : TrackedBlockEntities.ofType(TransformerBlockEntity.class)) {
			seen.add(transformer.getBlockPos());
			ObjRenderUtil.withAlignedPose(transformer, event.getPoseStack(), mc.renderBuffers().bufferSource(), cameraPos, MAX_RENDER_DISTANCE_SQ,
					state -> state.getValue(TransformerBlock.FACING), turnedFrom(TransformerBlock.AUTHORED),
					(context, pose, buffers) -> renderGrouped(context.model(), pose, event.getProjectionMatrix(), context.texture(),
							context.packedLight(), transformer.getBlockPos(), BUFFER_CACHE));
		}

		cleanupCache(BUFFER_CACHE, seen);
	}

	public static void init() {
		for (TransformerSpec spec : TransformerCatalog.all()) {
			ObjBlockDefinition definition = ObjDefinitions.get(Electricity.TRANSFORMER_BLOCKS.get(spec.id()).get());
			if (definition == null) continue;

			ObjBlockRegistry.register(definition);
			registerBushings(definition);
		}
	}

	/** The bushings, so a wire hung on one is drawn where it is hung. */
	private static void registerBushings(ObjBlockDefinition definition) {
		ObjModel model = ObjLoader.getModel(definition.model());
		if (model == null) return;

		Map<String, ObjModel.BoundingBox> boxes = new HashMap<>();
		for (String groupName : definition.insulators()) {
			ObjModel.BoundingBox box = model.getBoundingBox(groupName);
			if (box != null) boxes.put(groupName, box);
		}

		ObjBoundingBoxRegistry.registerBoundingBoxes(definition.block(), boxes);
	}
}
