package com.dooji.electricity.client.render.block;

import com.dooji.electricity.block.ModelFacing;
import com.dooji.electricity.block.MetStationBlock;
import com.dooji.electricity.block.MetStationBlockEntity;
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

/** Draws the met mast, with the cups turning and the vane pointing downwind. */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Electricity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class MetStationRenderer extends ObjRendererBase {
	private static final double MAX_RENDER_DISTANCE_SQ = 96 * 96;
	private static final Map<BlockPos, Map<String, GroupBuffer>> BUFFER_CACHE = new HashMap<>();
	private static final Map<BlockPos, Float> CUP_ANGLE = new HashMap<>();
	private static final Map<BlockPos, Float> VANE_ANGLE = new HashMap<>();

	/** Fraction of the wind speed the cups themselves travel at. */
	private static final double CUP_SPEED_RATIO = 1.0 / 3.0;
	/** Radius the cups run at, in blocks, matching what the generator authors. */
	private static final double CUP_RADIUS_BLOCKS = 0.135;
	/** Degrees a frame the vane may turn. */
	private static final float VANE_STEP = 6.0f;

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		Set<BlockPos> seen = drawAll(event, MetStationBlockEntity.class, MAX_RENDER_DISTANCE_SQ,
				state -> state.getValue(MetStationBlock.FACING), MetStationBlock.AUTHORED, BUFFER_CACHE, MetStationRenderer::render);
		cleanupAngles(CUP_ANGLE, seen);
		cleanupAngles(VANE_ANGLE, seen);
	}

	private static void render(MetStationBlockEntity station, ObjRenderContext context, PoseStack poseStack, Matrix4f projection) {
		ObjModel model = context.model();
		float cups = advanceCups(station.getBlockPos(), station.windSpeed());
		float vane = smoothVane(station.getBlockPos(), (float) station.windDirection(), station.getBlockState().getValue(MetStationBlock.FACING));

		Vec3 cupHub = pivot(model, "cups", new Vec3(0.0, 1.0, 0.0));
		Vec3 vaneHub = pivot(model, "vane", new Vec3(0.0, 0.885, 0.0));

		Map<String, Matrix4f> poses = new HashMap<>();
		for (String groupName : model.groups.keySet()) {
			if (groupName.startsWith("pivot_")) continue;

			poseStack.pushPose();
			if (groupName.startsWith("rotate_cups")) {
				poseStack.translate(cupHub.x, cupHub.y, cupHub.z);
				poseStack.mulPose(Axis.YP.rotationDegrees(cups));
				poseStack.translate(-cupHub.x, -cupHub.y, -cupHub.z);
			} else if (groupName.startsWith("rotate_vane")) {
				poseStack.translate(vaneHub.x, vaneHub.y, vaneHub.z);
				poseStack.mulPose(Axis.YP.rotationDegrees(vane));
				poseStack.translate(-vaneHub.x, -vaneHub.y, -vaneHub.z);
			}

			poses.put(groupName, new Matrix4f(poseStack.last().pose()));
			poseStack.popPose();
		}

		renderGrouped(model, poses, projection, context.texture(), context.packedLight(), station.getBlockPos(), BUFFER_CACHE);
	}

	/** Advances the cups at the speed the wind they are reading implies. */
	private static float advanceCups(BlockPos pos, double windSpeed) {
		float current = CUP_ANGLE.getOrDefault(pos, 0.0f);
		if (Minecraft.getInstance().isPaused() || windSpeed <= 0.0) return current;

		double metresPerSecond = windSpeed * CUP_SPEED_RATIO;
		double radiusM = CUP_RADIUS_BLOCKS * com.dooji.electricity.api.WorldConditions.METRES_PER_BLOCK;
		double degreesPerSecond = Math.toDegrees(metresPerSecond / radiusM);
		float next = (float) ((current + degreesPerSecond / 20.0) % 360.0);
		CUP_ANGLE.put(pos, next);
		return next;
	}

	/** Points the vane downwind, taken the short way round the compass. */
	private static float smoothVane(BlockPos pos, float windDirection, Direction facing) {
		// the wind heading is where the air is going
		// is what makes it read as a weather vane rather than an arrow
		float target = Mth.wrapDegrees(-windDirection - ModelFacing.degrees(MetStationBlock.AUTHORED, facing) + 90.0f);
		float current = VANE_ANGLE.getOrDefault(pos, target);
		if (Minecraft.getInstance().isPaused()) return current;

		float delta = Mth.wrapDegrees(target - current);
		float next = Math.abs(delta) <= VANE_STEP ? target : Mth.wrapDegrees(current + Math.signum(delta) * VANE_STEP);
		VANE_ANGLE.put(pos, next);
		return next;
	}

	public static void init() {
		ObjBlockRegistry.register(Electricity.MET_STATION_BLOCK.get());
	}
}
