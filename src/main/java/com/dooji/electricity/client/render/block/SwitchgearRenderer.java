package com.dooji.electricity.client.render.block;

import com.dooji.electricity.api.power.SwitchgearSpec;
import com.dooji.electricity.block.SwitchgearBlock;
import com.dooji.electricity.block.SwitchgearBlockEntity;
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
import com.dooji.electricity.main.registry.SwitchgearCatalog;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
import org.joml.Matrix4f;

/**
 * Draws the two switches, and swings a disconnector's blades when it is open.
 *
 * The blade turns about the hinge its own model marks with {@code pivot_blade}, which runs along x - so one
 * marker serves all three poles, because a rotation about x does not care where along x a part is.  A
 * breaker has nothing that moves: its contacts are inside a sealed bottle, and the two flag plates are what
 * it can show instead.
 */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Electricity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class SwitchgearRenderer extends ObjRendererBase {
	private static final double MAX_RENDER_DISTANCE_SQ = 96 * 96;
	private static final Map<BlockPos, Map<String, GroupBuffer>> BUFFER_CACHE = new HashMap<>();

	/** How far a blade swings, in degrees: far enough that the gap reads as a gap from anywhere. */
	private static final float OPEN_DEGREES = 84.0f;

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;

		Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
		HashSet<BlockPos> seen = new HashSet<>();

		for (SwitchgearBlockEntity gear : TrackedBlockEntities.ofType(SwitchgearBlockEntity.class)) {
			seen.add(gear.getBlockPos());
			boolean open = SwitchgearBlock.open(gear.getBlockState());
			ObjRenderUtil.withAlignedPose(gear, event.getPoseStack(), mc.renderBuffers().bufferSource(), cameraPos,
					MAX_RENDER_DISTANCE_SQ, state -> state.getValue(SwitchgearBlock.FACING),
					turnedFrom(SwitchgearBlock.AUTHORED),
					(context, pose, buffers) -> render(context.model(), pose, event.getProjectionMatrix(),
							context.texture(), context.packedLight(), gear.getBlockPos(), open));
		}

		cleanupCache(BUFFER_CACHE, seen);
	}

	private static void render(ObjModel model, PoseStack poseStack, Matrix4f projectionMatrix,
			net.minecraft.resources.ResourceLocation texture, int packedLight, BlockPos pos, boolean open) {
		Vec3 hinge = pivot(model, "blade", new Vec3(0.0, 0.336, 0.155));
		Map<String, Matrix4f> poses = new HashMap<>();

		for (String groupName : model.groups.keySet()) {
			if (groupName.startsWith("pivot_") || !drawn(groupName, open)) continue;

			poseStack.pushPose();
			if (open && groupName.startsWith("rotate_")) {
				poseStack.translate(0.0, hinge.y, hinge.z);
				poseStack.mulPose(Axis.XP.rotationDegrees(OPEN_DEGREES));
				poseStack.translate(0.0, -hinge.y, -hinge.z);
			}

			poses.put(groupName, new Matrix4f(poseStack.last().pose()));
			poseStack.popPose();
		}

		renderGrouped(model, poses, projectionMatrix, texture, packedLight, pos, BUFFER_CACHE);
	}

	/** The two flag plates are one plate: whichever the position calls for. */
	private static boolean drawn(String groupName, boolean open) {
		if (groupName.startsWith("flag_open")) return open;
		if (groupName.startsWith("flag_shut")) return !open;

		return true;
	}

	public static void init() {
		for (SwitchgearSpec spec : SwitchgearCatalog.all()) {
			ObjBlockDefinition definition = ObjDefinitions.get(Electricity.SWITCHGEAR_BLOCKS.get(spec.id()).get());
			if (definition == null) continue;

			ObjBlockRegistry.register(definition);
			registerInsulators(definition);
		}
	}

	/** The six palms a wire can be bolted to, so a click on one finds it. */
	private static void registerInsulators(ObjBlockDefinition definition) {
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
