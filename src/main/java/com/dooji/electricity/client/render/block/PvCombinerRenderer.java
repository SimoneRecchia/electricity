package com.dooji.electricity.client.render.block;

import com.dooji.electricity.block.PvCombinerBlock;
import com.dooji.electricity.block.PvCombinerBlockEntity;
import com.dooji.electricity.client.render.obj.ObjBlockRegistry;
import com.dooji.electricity.client.render.obj.ObjModel;
import com.dooji.electricity.client.render.obj.ObjRenderContext;
import com.dooji.electricity.client.render.obj.ObjRendererBase;
import com.dooji.electricity.main.Electricity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Draws the combiner boxes, and throws their switches. */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Electricity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PvCombinerRenderer extends ObjRendererBase {
	private static final double MAX_RENDER_DISTANCE_SQ = 96 * 96;
	private static final Map<BlockPos, Map<String, GroupBuffer>> BUFFER_CACHE = new HashMap<>();
	/** Where each handle has got to, so throwing the switch is a swing rather than a jump. */
	private static final Map<BlockPos, Float> HANDLE_ANGLE = new HashMap<>();

	/** Degrees the handle turns to open the switch, and how fast it gets there per frame. */
	private static final float OPEN_DEGREES = 90.0f;
	private static final float HANDLE_STEP = 9.0f;

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		cleanupAngles(HANDLE_ANGLE, drawAll(event, PvCombinerBlockEntity.class, MAX_RENDER_DISTANCE_SQ,
				state -> state.getValue(PvCombinerBlock.FACING), PvCombinerBlock.AUTHORED, BUFFER_CACHE, PvCombinerRenderer::render));
	}

	private static void render(PvCombinerBlockEntity combiner, ObjRenderContext context, PoseStack poseStack, Matrix4f projection) {
		ObjModel model = context.model();
		Vec3 spindle = pivot(model, "handle", new Vec3(0.15, 0.50, -0.115));
		float angle = advanceHandle(combiner.getBlockPos(), combiner.isolated());
		Direction facing = combiner.getBlockState().getValue(PvCombinerBlock.FACING);
		Set<String> entries = combiner.getLevel() == null ? Set.of()
				: cableEntries(combiner.getLevel(), combiner.getBlockPos(), PvCombinerBlock.AUTHORED, facing, "entry");
		Map<String, Matrix4f> poses = new HashMap<>();

		for (String groupName : model.groups.keySet()) {
			if (groupName.startsWith("pivot_")) continue;
			if (!entryVisible(groupName, "entry", entries)) continue;

			poseStack.pushPose();
			if (groupName.startsWith("rotate_handle")) {
				// about the axis the spindle runs on, which is straight out of the door
				poseStack.translate(spindle.x, spindle.y, spindle.z);
				poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
				poseStack.translate(-spindle.x, -spindle.y, -spindle.z);
			}

			poses.put(groupName, new Matrix4f(poseStack.last().pose()));
			poseStack.popPose();
		}

		renderGrouped(model, poses, projection, context.texture(), context.packedLight(), combiner.getBlockPos(), BUFFER_CACHE);
	}

	/** Walks the handle towards where the switch is, so it swings instead of teleporting. */
	private static float advanceHandle(BlockPos pos, boolean isolated) {
		float target = isolated ? OPEN_DEGREES : 0.0f;
		float current = HANDLE_ANGLE.getOrDefault(pos, target);
		if (Minecraft.getInstance().isPaused()) return current;

		float next = Math.abs(target - current) <= HANDLE_STEP ? target
				: current + Math.signum(target - current) * HANDLE_STEP;
		next = Mth.clamp(next, 0.0f, OPEN_DEGREES);
		HANDLE_ANGLE.put(pos, next);
		return next;
	}

	public static void init() {
		ObjBlockRegistry.registerAll(Electricity.PV_COMBINER_BLOCKS);
	}
}
