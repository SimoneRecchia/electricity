package com.dooji.electricity.client.render.block;

import com.dooji.electricity.api.power.TurbineSpec;
import com.dooji.electricity.block.ModelFacing;
import com.dooji.electricity.block.TurbineTowerBlock;
import com.dooji.electricity.block.TurbineTowerBlockEntity;
import com.dooji.electricity.block.WindTurbineBlock;
import com.dooji.electricity.block.WindTurbineBlockEntity;
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
import com.dooji.electricity.main.registry.TurbineCatalog;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Electricity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class WindTurbineRenderer extends ObjRendererBase {
	private static final double MAX_RENDER_DISTANCE_SQ = 128 * 128;
	private static final Logger LOGGER = LoggerFactory.getLogger(Electricity.MOD_ID);
	private static final Map<BlockPos, Float> YAW_CACHE = new HashMap<>();
	private static final Map<BlockPos, Map<String, GroupBuffer>> BUFFER_CACHE = new HashMap<>();
	/** Kept apart from the machines' so a tower and a turbine at one position cannot share entries. */
	private static final Map<BlockPos, Map<String, GroupBuffer>> TOWER_BUFFER_CACHE = new HashMap<>();
	/** Narrowest a tower is drawn, matching the smallest nacelle in the catalogue. */
	private static final double SMALLEST_TOWER = 0.25;
	/** Group whose underside has to land on the tower top: the nacelle. */
	private static final String NACELLE_GROUP = "motor_Plastic";

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;

		Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
		HashSet<BlockPos> seen = new HashSet<>();

		for (WindTurbineBlockEntity turbine : TrackedBlockEntities.ofType(WindTurbineBlockEntity.class)) {
			seen.add(turbine.getBlockPos());
			ObjRenderUtil.withAlignedPose(turbine, event.getPoseStack(), mc.renderBuffers().bufferSource(), cameraPos, MAX_RENDER_DISTANCE_SQ, state -> state.getValue(WindTurbineBlock.FACING),
					turnedFrom(WindTurbineBlock.AUTHORED),
					(context, pose, buffers) -> renderWindTurbineWithAnimation(context.model(), pose, event.getProjectionMatrix(), context.texture(), context.packedLight(), turbine));
		}

		cleanupAngles(YAW_CACHE, seen);
		cleanupCache(BUFFER_CACHE, seen);
		renderBareTowers(mc, event, cameraPos);
	}

	/** Draws towers that have no machine on them yet. */
	private static void renderBareTowers(Minecraft mc, RenderLevelStageEvent event, Vec3 cameraPos) {
		HashSet<BlockPos> seen = new HashSet<>();

		for (TurbineTowerBlockEntity tower : TrackedBlockEntities.ofType(TurbineTowerBlockEntity.class)) {
			BlockPos pos = tower.getBlockPos();
			if (mc.level == null || !TurbineTowerBlock.isFoot(mc.level, pos)) continue;
			if (TurbineTowerBlock.findTurbineAbove(mc.level, pos) != null) continue;

			int height = 1 + TurbineTowerBlock.countAbove(mc.level, pos);
			seen.add(pos);
			ObjRenderUtil.withAlignedPose(tower, event.getPoseStack(), mc.renderBuffers().bufferSource(), cameraPos, MAX_RENDER_DISTANCE_SQ, null, null,
					(context, pose, buffers) -> {
						Map<String, Matrix4f> poses = new HashMap<>();
						for (Map.Entry<String, ObjModel.ObjGroup> entry : context.model().groups.entrySet()) {
							if (!entry.getKey().startsWith("pole")) continue;

							pose.pushPose();
							poseTower(pose, height, bareTowerScale(height));
							poses.put(entry.getKey(), new Matrix4f(pose.last().pose()));
							pose.popPose();
						}

						renderGrouped(context.model(), poses, event.getProjectionMatrix(), context.texture(), context.packedLight(), pos, TOWER_BUFFER_CACHE);
					});
		}

		cleanupCache(TOWER_BUFFER_CACHE, seen);
	}

	private static void renderWindTurbineWithAnimation(ObjModel model, PoseStack poseStack, Matrix4f projectionMatrix, ResourceLocation textureLocation, int packedLight,
			WindTurbineBlockEntity blockEntity) {
		float rotation1 = blockEntity.getRotation1();
		float rotation2 = blockEntity.getRotation2();
		float base = ModelFacing.degrees(WindTurbineBlock.AUTHORED, blockEntity.getBlockState().getValue(WindTurbineBlock.FACING));
		float renderYaw = smoothYaw(blockEntity.getBlockPos(), blockEntity.getYaw(), base);
		float yawOffset = Mth.wrapDegrees(renderYaw - base);

		Vec3 hubCenter = null;
		for (Map.Entry<String, ObjModel.ObjGroup> entry : model.groups.entrySet()) {
			if (entry.getKey().equals("rotate_2_Plastic")) {
				hubCenter = calculateGroupCenter(entry.getValue());
				break;
			}
		}

		if (hubCenter == null) {
			hubCenter = new Vec3(0.0, 12.65, 0.95);
		}

		TurbineSpec spec = blockEntity.spec();
		int towerSegments = blockEntity.getTowerSegments();

		// The nacelle is bolted to the top of the tower, so that is what it shrinks towards -
		double nacelleScale = spec.nacelleRenderScale();
		double nacelleBottom = nacelleBottom(model, hubCenter.y);
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

		renderGrouped(model, poses, projectionMatrix, textureLocation, packedLight, blockEntity.getBlockPos(), BUFFER_CACHE);
		poseStack.popPose();
	}

	/** Underside of the nacelle in the authored model, which is what has to meet the tower. */
	private static double nacelleBottom(ObjModel model, double fallback) {
		ObjModel.ObjGroup nacelle = model.groups.get(NACELLE_GROUP);
		if (nacelle == null || nacelle.vertices.isEmpty()) return fallback;

		double lowest = Double.MAX_VALUE;
		for (Vector3f vertex : nacelle.vertices) {
			lowest = Math.min(lowest, vertex.y);
		}

		return lowest;
	}

	/** Stretches the authored tube to a tower this many blocks tall, from its foot. */
	private static void poseTower(PoseStack poseStack, int height, double radial) {
		poseStack.scale((float) radial, (float) (height / TurbineSpec.MODEL_TOWER_HEIGHT_BLOCKS), (float) radial);
	}

	/** How wide to draw a tower with nothing on it yet, by its height. */
	private static double bareTowerScale(int height) {
		return Mth.clamp(SMALLEST_TOWER + (height - 2) / 11.0 * (1.0 - SMALLEST_TOWER), SMALLEST_TOWER, 1.0);
	}

	private static Vec3 calculateGroupCenter(ObjModel.ObjGroup group) {
		if (group.vertices.isEmpty()) return Vec3.ZERO;

		double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
		double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
		double minZ = Double.MAX_VALUE, maxZ = Double.MIN_VALUE;

		for (Vector3f vertex : group.vertices) {
			minX = Math.min(minX, vertex.x);
			maxX = Math.max(maxX, vertex.x);
			minY = Math.min(minY, vertex.y);
			maxY = Math.max(maxY, vertex.y);
			minZ = Math.min(minZ, vertex.z);
			maxZ = Math.max(maxZ, vertex.z);
		}

		return new Vec3((minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0);
	}

	public static void init() {
		// every machine in the catalogue
		for (TurbineSpec spec : TurbineCatalog.all()) {
			ObjBlockDefinition definition = ObjDefinitions.get(Electricity.TURBINE_BLOCKS.get(spec.id()).get());
			if (definition == null) continue;

			ObjBlockRegistry.register(definition);

			calculateAndRegisterBoundingBoxes(definition);
		}

		// the tower draws the same asset's pole group, so it needs the model registered against
		// its own block too - withAlignedPose looks the model up by block and refuses otherwise
		ObjBlockDefinition turbine = ObjDefinitions.get(Electricity.WIND_TURBINE_BLOCK.get());
		if (turbine != null) {
			ObjBlockRegistry.register(Electricity.TURBINE_TOWER_BLOCK.get(), turbine.model(), null);
		}
	}

	private static void calculateAndRegisterBoundingBoxes(ObjBlockDefinition definition) {
		var model = ObjLoader.getModel(definition.model());
		if (model == null) {
			LOGGER.error("Failed to load Wind Turbine model");
			return;
		}

		Map<String, ObjModel.BoundingBox> insulatorBoxes = new HashMap<>();

		for (String groupName : definition.insulators()) {
			ObjModel.BoundingBox bbox = model.getBoundingBox(groupName);
			if (bbox != null) {
				insulatorBoxes.put(groupName, bbox);
			} else {
				LOGGER.warn("Missing Wind Turbine insulator group: {}", groupName);
			}
		}

		ObjBoundingBoxRegistry.registerBoundingBoxes(definition.block(), insulatorBoxes);
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
