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

/**
 * Draws the arrays, and turns the ones that track.
 *
 * <h2>Where the motion lives</h2>
 *
 * In the matrix, never in the vertices, and that is not a preference - the OBJ pipeline caches one
 * vertex buffer per block position and rebuilds it only when the light or the texture changes. A
 * rotation baked into the geometry would therefore be frozen at whatever angle the row happened to
 * be at when a player walked into range, and would stay there until a cloud went over.
 *
 * So every moving part is its own group in the model, authored lying flat, and this class works out a
 * matrix per group. The tracked groups are named {@code rotate_*} by the generator, which is the whole
 * of the contract between the two.
 *
 * <h2>The pivots are measured, not written down</h2>
 *
 * A single-axis row turns about its torque tube and a dual-axis frame about its pedestal and then its
 * elevation axis, and all three of those are the centre of a group the model already contains. Reading
 * them off the geometry rather than restating them here means the models can be regenerated at a
 * different height without anything in Java having to be told.
 *
 * <h2>Two axes, and why the flip is invisible</h2>
 *
 * The dual-axis frame's azimuth drive turns to face the sun's bearing, which in this world is due east
 * all morning and due west all afternoon - so it swings a half turn at noon. That happens to be exactly
 * the moment the elevation frame is lying flat, and a flat plate turned about its own vertical axis
 * looks identical, so the flip cannot be seen. Which is what a real azimuth-elevation machine does as
 * the sun crosses its zenith, for the same reason.
 */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Electricity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PvArrayRenderer extends ObjRendererBase {
	private static final double MAX_RENDER_DISTANCE_SQ = 96 * 96;
	private static final Map<BlockPos, Map<String, GroupBuffer>> BUFFER_CACHE = new HashMap<>();
	/** Where the azimuth drive has actually got to, per block, so the half turn at noon is a sweep rather than a jump. */
	private static final Map<BlockPos, Float> AZIMUTH_CACHE = new HashMap<>();
	/** Degrees the azimuth drive turns per frame. Slow, because a pedestal frame has a great deal of inertia. */
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
					PvArrayRenderer::drawnFacing, PvArrayRenderer::rotationForFacing,
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

	/**
	 * The groups that only exist once a set of leads has been worked into the array.
	 *
	 * The whole of the contract between this and the generator, the same way {@code rotate_} is: the model
	 * carries the junction box and the pair leaving it under one name, and this decides whether they are
	 * there. So plugging a reel of cable into an array is visible from across the field, which is the only
	 * way a player finds the one row they forgot.
	 */
	private static boolean isHarness(String groupName) {
		return groupName.startsWith("harness");
	}

	/**
	 * What is at each end of the row's own axis.
	 *
	 * {@code fed} is anything at all - the row alongside with its leads in, or copper laid up to that
	 * edge. {@code mid} is narrower: whatever is there brings its cable to the *middle* of the edge
	 * rather than to the corner, and so this row has to grow the turn out to the corner its own leads
	 * are on. The two want different things drawn, which is why they are counted apart.
	 *
	 * North and south are the model's own ends, not the world's. The model is authored with its input at
	 * the north end and the base rotation maps that onto the block's facing, so north here means the
	 * facing and south means the way the leads leave.
	 */
	private record Ends(boolean fedNorth, boolean fedSouth, boolean midNorth, boolean midSouth) {
	}

	private static Ends ends(PvArrayBlockEntity array) {
		if (array.getLevel() == null) return new Ends(false, false, false, false);

		Direction north = drawnFacing(array.getBlockState());
		Direction south = north.getOpposite();
		return new Ends(fedFrom(array, north), fedFrom(array, south),
				midRun(array, north), midRun(array, south));
	}

	private static boolean fedFrom(PvArrayBlockEntity array, Direction direction) {
		BlockPos beside = array.getBlockPos().relative(direction);
		if (PvArrayBlock.harnessed(array.getLevel().getBlockState(beside))) return true;

		return cableArrives(array.getLevel(), array.getBlockPos(), direction);
	}

	/**
	 * Whether whatever is at this edge meets it in the middle rather than at the corner.
	 *
	 * Laid copper does, because a run lies down the middle of its block. So does a tracked row, and for
	 * the same reason: a tracker's own harness goes the whole length of its block down the middle, and its
	 * plug sits there too, so it reaches this boundary half a block from the corner a fixed row's leads
	 * are on. Left as only the copper, a table or a rack next to a tracker showed a cable pointing at half
	 * a block of nothing - the same fault a laid run had before it grew the turn, and the same turn fixes
	 * it.
	 *
	 * Only on the two faces a tracker's tube actually runs to. Its tube is forced north-south, so its
	 * cable never reaches its east or west side, and a turn out to a face with nothing behind it would be
	 * this fault over again pointing the other way.
	 */
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
	 * Nothing of a row's own harness until a reel of cable has been worked in - so plugging an array in is
	 * visible from across the field.
	 *
	 * The socket is the exception, and deliberately: it belongs to whatever is *feeding* this row rather
	 * than to this row, so it appears as soon as the row behind has an output pointing at it whether or not
	 * this one has been cabled yet. Which is the right way round - a socket is where a cable arrives, and a
	 * cable arriving does not wait for the thing it is arriving at.
	 *
	 * A tracked row's plugs are the other way about: its run goes the whole length of the block, so a
	 * cabled row needs no plug at either end and an uncabled one shows the plug at whichever end has
	 * something at it. Which is a rule the fixed mountings do not need and a tracker cannot do without -
	 * placement forces a tracker's tube north-south whichever way the player was facing, so half the rows
	 * in a field are chained the other way round, and a plug only ever at the north end is at the wrong end
	 * of half of them.
	 *
	 * The entries are drawn for whatever meets this row in the middle of an edge - laid copper, or a
	 * tracked row, whose harness runs down the middle of its own block. A fixed row next door needs no
	 * turn: the two meet corner to corner already.
	 */
	private static boolean drawn(String groupName, boolean harnessed, Ends ends) {
		if (!isHarness(groupName)) return true;
		if (groupName.startsWith("harness_plug_north")) return !harnessed && ends.fedNorth();
		if (groupName.startsWith("harness_plug_south")) return !harnessed && ends.fedSouth();
		if (groupName.startsWith("harness_input")) return ends.fedNorth();
		if (groupName.startsWith("harness_entry_north")) return harnessed && ends.midNorth();
		if (groupName.startsWith("harness_entry_south")) return harnessed && ends.midSouth();

		return harnessed;
	}

	/**
	 * One roll about the torque tube, and everything bolted to it comes with.
	 *
	 * Positive rotation rolls the face towards the west, which is the sign convention
	 * {@link com.dooji.electricity.api.power.TrackerSpec} uses and the same one
	 * {@link PvArrayBlockEntity#planeAzimuthDeg()} reads to decide which way the plane is pointing. The
	 * two have to agree or the array would report facing one way and be drawn facing the other.
	 */
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

	/**
	 * The azimuth collar first, then the elevation frame inside it.
	 *
	 * The order matters: the elevation axis is carried on the yoke, so it turns with the collar. Nesting
	 * the two transforms is what makes that true, and it is why the elevation groups are named as a
	 * prefix of the azimuth ones rather than being a separate family.
	 */
	private static void poseDualAxis(ObjModel model, PoseStack poseStack, Map<String, Matrix4f> poses, PvArrayBlockEntity array, double rotation) {
		Vec3 collar = pivot(model, "azimuth", new Vec3(0.0, 0.60, 0.0));
		Vec3 elevation = pivot(model, "elevation", new Vec3(0.0, 0.7475, 0.0));
		// off the *target* rather than the current angle, because the target's sign comes from which
		// half of the sky the sun is in and flips once at noon, while the drive's own angle hovers
		// either side of zero as it crosses - and chasing that had the frame swinging back and forth
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

	/**
	 * Walks the azimuth drive towards its target rather than snapping it.
	 *
	 * Only ever asked for a half turn, and only at noon when the frame is flat and the turn cannot be
	 * seen. It is stepped anyway, because a matrix that jumps a hundred and eighty degrees in one frame
	 * would be visible on the pedestal collar even when the plate above it is not.
	 */
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

	/**
	 * Which way the model is turned before the tracker's own rotation is applied.
	 *
	 * North for anything tracked, whatever the block state says, because a tracker's tube runs
	 * north-south or it cannot follow a sun that travels east to west - and turning the model would turn
	 * the tube with it, leaving a row that sweeps beautifully and never points at anything. Placement
	 * already forces north, so this only matters for a block put down by a command or moved by
	 * something; the physics ignores the facing for a tracker in exactly the same way, and the two have
	 * to agree about that or the array would collect on a plane it is not drawn on.
	 */
	private static Direction drawnFacing(BlockState state) {
		if (state.getBlock() instanceof PvArrayBlock array && array.spec().tracked()) return Direction.NORTH;

		return state.getValue(PvArrayBlock.FACING);
	}

	/**
	 * How far to turn the model for a given facing.
	 *
	 * The models are authored tipping towards north, because that is the default facing and because
	 * {@link PvArrayBlock#planeAzimuthDeg} reads the plane's bearing off the same facing - so north
	 * needs no rotation and the rest follow round.
	 */
	private static float rotationForFacing(Direction facing) {
		return rotationFrom(Direction.NORTH, facing);
	}
}
