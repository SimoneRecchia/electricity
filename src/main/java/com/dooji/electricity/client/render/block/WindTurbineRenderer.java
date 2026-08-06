package com.dooji.electricity.client.render.block;

import com.dooji.electricity.api.power.TurbineSpec;
import com.dooji.electricity.block.ModelFacing;
import com.dooji.electricity.block.TurbineTowerBlock;
import com.dooji.electricity.block.TurbineTowerBlockEntity;
import com.dooji.electricity.block.WindTurbineBlock;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import com.dooji.electricity.client.render.obj.ObjBlockRegistry;
import com.dooji.electricity.client.render.obj.ObjModel;
import com.dooji.electricity.client.render.obj.ObjRenderContext;
import com.dooji.electricity.client.render.obj.ObjRendererBase;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.registry.ObjBlockDefinition;
import com.dooji.electricity.main.registry.ObjDefinitions;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Draws the turbines - spinning, yawing into the wind, each on a tower of its own height - and bare towers. */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Electricity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class WindTurbineRenderer extends ObjRendererBase {
	private static final double MAX_RENDER_DISTANCE_SQ = 128 * 128;
	private static final Map<BlockPos, Float> YAW_CACHE = new HashMap<>();
	private static final Map<BlockPos, Map<String, GroupBuffer>> BUFFER_CACHE = new HashMap<>();
	/** Kept apart from the machines' so a tower and a turbine at one position cannot share entries. */
	private static final Map<BlockPos, Map<String, GroupBuffer>> TOWER_BUFFER_CACHE = new HashMap<>();
	/** Narrowest a tower is drawn, matching the smallest nacelle in the catalogue. */
	private static final double SMALLEST_TOWER = 0.25;
	/** Group whose underside has to land on the tower top: the nacelle. */
	private static final String NACELLE_GROUP = "motor_Plastic";
	/** The hub cone, which is what both rotating groups turn about. */
	private static final String HUB_GROUP = "rotate_2_Plastic";

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		cleanupAngles(YAW_CACHE, drawAll(event, WindTurbineBlockEntity.class, MAX_RENDER_DISTANCE_SQ,
				state -> state.getValue(WindTurbineBlock.FACING), WindTurbineBlock.AUTHORED, BUFFER_CACHE, WindTurbineRenderer::render));
		drawAll(event, TurbineTowerBlockEntity.class, MAX_RENDER_DISTANCE_SQ, null, null, TOWER_BUFFER_CACHE,
				WindTurbineRenderer::bare, WindTurbineRenderer::renderBareTower);
	}

	/** A tower draws itself only while it is one on its own: the foot of a stack with no machine over it. */
	private static boolean bare(TurbineTowerBlockEntity tower) {
		Level level = tower.getLevel();
		return level != null && TurbineTowerBlock.isFoot(level, tower.getBlockPos())
				&& TurbineTowerBlock.findTurbineAbove(level, tower.getBlockPos()) == null;
	}

	private static void renderBareTower(TurbineTowerBlockEntity tower, ObjRenderContext context, PoseStack poseStack, Matrix4f projection) {
		int height = 1 + TurbineTowerBlock.countAbove(tower.getLevel(), tower.getBlockPos());
		Map<String, Matrix4f> poses = new HashMap<>();

		for (String groupName : context.model().groups.keySet()) {
			if (!groupName.startsWith("pole")) continue;

			poseStack.pushPose();
			poseTower(poseStack, height, bareTowerScale(height));
			poses.put(groupName, new Matrix4f(poseStack.last().pose()));
			poseStack.popPose();
		}

		renderGrouped(context.model(), poses, projection, context.texture(), context.packedLight(), tower.getBlockPos(), TOWER_BUFFER_CACHE);
	}

	private static void render(WindTurbineBlockEntity blockEntity, ObjRenderContext context, PoseStack poseStack, Matrix4f projection) {
		ObjModel model = context.model();
		float rotation1 = blockEntity.getRotation1();
		float rotation2 = blockEntity.getRotation2();
		float base = ModelFacing.degrees(WindTurbineBlock.AUTHORED, blockEntity.getBlockState().getValue(WindTurbineBlock.FACING));
		float renderYaw = smoothYaw(blockEntity.getBlockPos(), blockEntity.getYaw(), base);
		float yawOffset = Mth.wrapDegrees(renderYaw - base);

		Vec3 hubCenter = groupCentre(model, HUB_GROUP, new Vec3(0.0, 12.65, 0.95));
		TurbineSpec spec = blockEntity.spec();
		int towerSegments = blockEntity.getTowerSegments();

		// The nacelle is bolted to the top of the tower, so that is what it shrinks towards - and its
		// underside is what has to land there.
		double nacelleScale = spec.nacelleRenderScale();
		ObjModel.BoundingBox nacelle = model.getBoundingBox(NACELLE_GROUP);
		double nacelleBottom = nacelle != null ? nacelle.min.y() : hubCenter.y;
		Vec3 hub = new Vec3(hubCenter.x * nacelleScale, towerSegments + nacelleScale * (hubCenter.y - nacelleBottom), hubCenter.z * nacelleScale);

		poseStack.pushPose();
		if (yawOffset != 0.0f) {
			poseStack.mulPose(Axis.YP.rotationDegrees(yawOffset));
		}

		Map<String, Matrix4f> poses = new HashMap<>();
		for (Map.Entry<String, ObjModel.ObjGroup> entry : model.groups.entrySet()) {
			String groupName = entry.getKey();

			poseStack.pushPose();

			// ObjTransforms has already put this frame at the foot of the tower
			if (groupName.startsWith("pole")) {
				// the tower is this machine's tower
				poseTower(poseStack, towerSegments, nacelleScale);
			} else if (groupName.startsWith("insulator")) {
				// nothing: authored at the foot
				// size fitting whichever turbine it came from
			} else if (groupName.startsWith("rotate_1") || groupName.startsWith("rotate_2")) {
				// Both spin about the hub, which has already moved with the nacelle. The blades
				double scale = groupName.startsWith("rotate_1") ? spec.renderScale() : nacelleScale;

				poseStack.translate(hub.x, hub.y, hub.z);
				poseStack.scale((float) scale, (float) scale, (float) scale);
				// one angle for both: the hub cone is bolted to the blade roots
				poseStack.mulPose(Axis.ZP.rotationDegrees(groupName.startsWith("rotate_1") ? rotation1 : rotation2));
				poseStack.translate(-hubCenter.x, -hubCenter.y, -hubCenter.z);
			} else {
				// the nacelle, scaled about the tower axis at the tower top
				poseStack.translate(0.0, towerSegments, 0.0);
				poseStack.scale((float) nacelleScale, (float) nacelleScale, (float) nacelleScale);
				poseStack.translate(0.0, -nacelleBottom, 0.0);
			}

			poses.put(groupName, new Matrix4f(poseStack.last().pose()));

			poseStack.popPose();
		}

		renderGrouped(model, poses, projection, context.texture(), context.packedLight(), blockEntity.getBlockPos(), BUFFER_CACHE);
		poseStack.popPose();
	}

	/** Stretches the authored tube to a tower this many blocks tall, from its foot. */
	private static void poseTower(PoseStack poseStack, int height, double radial) {
		poseStack.scale((float) radial, (float) (height / TurbineSpec.MODEL_TOWER_HEIGHT_BLOCKS), (float) radial);
	}

	/** How wide to draw a tower with nothing on it yet, by its height. */
	private static double bareTowerScale(int height) {
		return Mth.clamp(SMALLEST_TOWER + (height - 2) / 11.0 * (1.0 - SMALLEST_TOWER), SMALLEST_TOWER, 1.0);
	}

	public static void init() {
		ObjBlockRegistry.registerAll(Electricity.TURBINE_BLOCKS);

		// the tower draws the same asset's pole group, so it needs the model registered against
		// its own block too - withAlignedPose looks the model up by block and refuses otherwise
		ObjBlockDefinition turbine = ObjDefinitions.get(Electricity.WIND_TURBINE_BLOCK.get());
		if (turbine != null) {
			ObjBlockRegistry.register(Electricity.TURBINE_TOWER_BLOCK.get(), turbine.model(), null);
		}
	}

	private static float smoothYaw(BlockPos pos, float target, float base) {
		float current = YAW_CACHE.containsKey(pos) ? YAW_CACHE.get(pos) : target;
		if (Minecraft.getInstance().isPaused()) return current;
		float delta = Mth.wrapDegrees(target - current);
		float step = Mth.clamp(delta * 0.1f, -1.5f, 1.5f);
		if (Math.abs(delta) <= 0.25f) {
			current = target;
		} else {
			current = Mth.wrapDegrees(current + step);
		}

		YAW_CACHE.put(pos, current);
		return current;
	}
}
