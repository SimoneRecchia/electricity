package com.dooji.electricity.client.render.block;

import com.dooji.electricity.api.power.PvArraySpec;
import com.dooji.electricity.block.PvArrayBlock;
import com.dooji.electricity.block.PvArrayBlockEntity;
import com.dooji.electricity.client.TrackedBlockEntities;
import com.dooji.electricity.client.render.obj.ObjBlockRegistry;
import com.dooji.electricity.client.render.obj.ObjModel;
import com.dooji.electricity.client.render.obj.ObjRenderUtil;
import com.dooji.electricity.client.render.obj.ObjRendererBase;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.registry.ObjBlockDefinition;
import com.dooji.electricity.main.registry.ObjDefinitions;
import com.dooji.electricity.main.registry.PvCatalog;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Draws the arrays, and turns the ones that track. */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Electricity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PvArrayRenderer extends ObjRendererBase {
	private static final double MAX_RENDER_DISTANCE_SQ = 96 * 96;
	private static final Map<BlockPos, Map<String, GroupBuffer>> BUFFER_CACHE = new HashMap<>();
	/**
	 * Where the azimuth drive has actually got to, per block, so the half turn at noon is a sweep rather than a jump.
	 */
	private static final Map<BlockPos, Float> AZIMUTH_CACHE = new HashMap<>();
	/** Degrees the azimuth drive turns per frame. */
	private static final float AZIMUTH_STEP = 2.0f;

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;

		Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
		HashSet<BlockPos> seen = new HashSet<>();

		for (PvArrayBlockEntity array : TrackedBlockEntities.ofType(PvArrayBlockEntity.class)) {
			seen.add(array.getBlockPos());
			ObjRenderUtil.withAlignedPose(array, event.getPoseStack(), mc.renderBuffers().bufferSource(), cameraPos, MAX_RENDER_DISTANCE_SQ,
					PvArrayRenderer::drawnFacing, turnedFrom(PvArrayBlock.AUTHORED),
					(context, pose, buffers) -> render(context.model(), pose, event.getProjectionMatrix(), context.texture(), context.packedLight(), array));
		}

		cleanupCache(BUFFER_CACHE, seen);
		cleanupAngles(AZIMUTH_CACHE, seen);
	}

	private static void render(ObjModel model, PoseStack poseStack, Matrix4f projectionMatrix, net.minecraft.resources.ResourceLocation texture,
			int packedLight, PvArrayBlockEntity array) {
		boolean harnessed = array.harnessed();
		Ends ends = ends(array);

		if (!array.tracked()) {
			// nothing moves on a fixed mounting, so the whole model shares one matrix and the cheap
			// overload does the work
			renderGrouped(model, poseStack, projectionMatrix, texture, packedLight, array.getBlockPos(), BUFFER_CACHE,
					groupName -> drawn(groupName, harnessed, ends));
			return;
		}

		double rotation = array.rotationDeg();
		Map<String, Matrix4f> poses = new HashMap<>();

		if (array.dualAxis()) {
			poseDualAxis(model, poseStack, poses, array, rotation);
		} else {
			poseSingleAxis(model, poseStack, poses, rotation);
		}

		poses.keySet().removeIf(groupName -> !drawn(groupName, harnessed, ends));

		renderGrouped(model, poses, projectionMatrix, texture, packedLight, array.getBlockPos(), BUFFER_CACHE);
	}

	/** The groups that only exist once a set of leads has been worked into the array. */
	private static boolean isHarness(String groupName) {
		return groupName.startsWith("harness");
	}

	/** What is at each end of the row's own axis. */
	private record Ends(boolean fedNorth, boolean fedSouth, boolean midNorth, boolean midSouth) {
	}

	private static Ends ends(PvArrayBlockEntity array) {
		if (array.getLevel() == null) return new Ends(false, false, false, false);

		Direction north = drawnFacing(array.getBlockState());
		Direction south = north.getOpposite();
		return new Ends(fedFrom(array, north), fedFrom(array, south),
				midRun(array, north), midRun(array, south));
	}

	/** Whether something at this end has its own cable up against this row. */
	private static boolean fedFrom(PvArrayBlockEntity array, Direction direction) {
		BlockPos beside = array.getBlockPos().relative(direction);
		if (PvArrayBlock.gives(array.getLevel().getBlockState(beside), direction.getOpposite())) return true;

		return cableArrives(array.getLevel(), array.getBlockPos(), direction);
	}

	/** Whether whatever is at this edge meets it in the middle rather than at the corner. */
	private static boolean midRun(PvArrayBlockEntity array, Direction direction) {
		if (cableArrives(array.getLevel(), array.getBlockPos(), direction)) return true;

		BlockState beside = array.getLevel().getBlockState(array.getBlockPos().relative(direction));
		return tracked(beside) && drawnFacing(beside).getAxis() == direction.getAxis();
	}

	/** Whether a block is an array whose harness runs down the middle of it rather than along an edge. */
	private static boolean tracked(BlockState state) {
		return state.getBlock() instanceof PvArrayBlock array && array.spec().tracked();
	}

	/**
	 * Whether one group of an array's model is drawn.
	  *
	 * It is also exactly what makes the row wired: see {@link PvArrayBlock#takes}.
	 */
	private static boolean drawn(String groupName, boolean harnessed, Ends ends) {
		if (!isHarness(groupName)) return true;
		if (groupName.startsWith("harness_plug_north")) return !harnessed && ends.fedNorth();
		if (groupName.startsWith("harness_plug_south")) return !harnessed && ends.fedSouth();
		if (groupName.startsWith("harness_input")) return ends.fedNorth();
		if (groupName.startsWith("harness_entry_north")) return ends.midNorth();
		if (groupName.startsWith("harness_entry_south")) return harnessed && ends.midSouth();

		return harnessed;
	}

	/** One roll about the torque tube */
	private static void poseSingleAxis(ObjModel model, PoseStack poseStack, Map<String, Matrix4f> poses, double rotation) {
		Vec3 axis = pivot(model, "tube", new Vec3(0.0, 0.62, 0.0));

		for (String groupName : model.groups.keySet()) {
			if (groupName.startsWith("pivot_")) continue;

			poseStack.pushPose();
			if (groupName.startsWith("rotate_")) {
				poseStack.translate(0.0, axis.y, 0.0);
				poseStack.mulPose(Axis.ZP.rotationDegrees((float) rotation));
				poseStack.translate(0.0, -axis.y, 0.0);
			}

			poses.put(groupName, new Matrix4f(poseStack.last().pose()));
			poseStack.popPose();
		}
	}

	/** The azimuth collar first */
	private static void poseDualAxis(ObjModel model, PoseStack poseStack, Map<String, Matrix4f> poses, PvArrayBlockEntity array, double rotation) {
		Vec3 collar = pivot(model, "azimuth", new Vec3(0.0, 0.60, 0.0));
		Vec3 elevation = pivot(model, "elevation", new Vec3(0.0, 0.7475, 0.0));
		// off the *target* rather than the current angle
		float azimuth = smoothAzimuth(array.getBlockPos(), array.targetRotationDeg() >= 0.0 ? 0.0f : 180.0f);

		for (String groupName : model.groups.keySet()) {
			if (groupName.startsWith("pivot_")) continue;

			poseStack.pushPose();
			if (groupName.startsWith("rotate_")) {
				poseStack.translate(0.0, collar.y, 0.0);
				poseStack.mulPose(Axis.YP.rotationDegrees(azimuth));
				poseStack.translate(0.0, -collar.y, 0.0);
			}

			if (groupName.startsWith("rotate_elevation")) {
				poseStack.translate(0.0, elevation.y, 0.0);
				poseStack.mulPose(Axis.ZP.rotationDegrees((float) Math.abs(rotation)));
				poseStack.translate(0.0, -elevation.y, 0.0);
			}

			poses.put(groupName, new Matrix4f(poseStack.last().pose()));
			poseStack.popPose();
		}
	}

	/** Walks the azimuth drive towards its target rather than snapping it. */
	private static float smoothAzimuth(BlockPos pos, float target) {
		float current = AZIMUTH_CACHE.getOrDefault(pos, target);
		if (Minecraft.getInstance().isPaused()) return current;

		float delta = Mth.wrapDegrees(target - current);
		float next = Math.abs(delta) <= AZIMUTH_STEP ? target : Mth.wrapDegrees(current + Math.signum(delta) * AZIMUTH_STEP);
		AZIMUTH_CACHE.put(pos, next);
		return next;
	}

	public static void init() {
		for (PvArraySpec spec : PvCatalog.all()) {
			ObjBlockDefinition definition = ObjDefinitions.get(Electricity.PV_ARRAY_BLOCKS.get(spec.id()).get());
			if (definition == null) continue;

			ObjBlockRegistry.register(definition);
		}
	}

	/** Which way the model is turned before the tracker's own rotation is applied. */
	private static Direction drawnFacing(BlockState state) {
		if (state.getBlock() instanceof PvArrayBlock array && array.spec().tracked()) return Direction.NORTH;

		return state.getValue(PvArrayBlock.FACING);
	}

	/** How far to turn the model for a given facing. */
}
