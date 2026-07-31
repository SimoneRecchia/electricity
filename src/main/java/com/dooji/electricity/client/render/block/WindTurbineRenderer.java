package com.dooji.electricity.client.render.block;

import com.dooji.electricity.api.power.TurbineSpec;
import com.dooji.electricity.block.WindTurbineBlock;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import com.dooji.electricity.client.TrackedBlockEntities;
import com.dooji.electricity.client.render.obj.ObjBlockRegistry;
import com.dooji.electricity.client.render.obj.ObjBoundingBoxRegistry;
import com.dooji.electricity.client.render.obj.ObjInteractionRegistry;
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
import net.minecraft.core.Direction;
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
					WindTurbineRenderer::rotationForTurbine,
					(context, pose, buffers) -> renderWindTurbineWithAnimation(context.model(), pose, event.getProjectionMatrix(), context.texture(), context.packedLight(), turbine));
		}

		if (!YAW_CACHE.isEmpty() && !seen.isEmpty()) {
			YAW_CACHE.keySet().removeIf(pos -> !seen.contains(pos));
		}

		cleanupCache(BUFFER_CACHE, seen);
	}

	private static void renderWindTurbineWithAnimation(ObjModel model, PoseStack poseStack, Matrix4f projectionMatrix, ResourceLocation textureLocation, int packedLight,
			WindTurbineBlockEntity blockEntity) {
		float rotation1 = blockEntity.getRotation1();
		float rotation2 = blockEntity.getRotation2();
		float base = rotationForTurbine(blockEntity.getBlockState().getValue(WindTurbineBlock.FACING));
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
		double nacelleScale = spec.nacelleRenderScale();
		// the authored tower is 12.43 blocks; a tower of N segments is that stretched to N,
		// which keeps the taper continuous and needs no second asset. Stretching rather than
		// tiling is what makes any height possible: the pole carries vertices at two heights
		// only, so a segment cut out of it would restart at the wide radius on every copy
		// and the tower would read as a stack of cones.
		double towerHeightScale = blockEntity.getTowerSegments() / TurbineSpec.MODEL_TOWER_HEIGHT_BLOCKS;
		double towerOffset = blockEntity.getTowerSegments() - TurbineSpec.MODEL_TOWER_HEIGHT_BLOCKS;

		poseStack.pushPose();
		if (yawOffset != 0.0f) {
			poseStack.mulPose(Axis.YP.rotationDegrees(yawOffset));
		}

		Map<String, Matrix4f> poses = new HashMap<>();
		for (Map.Entry<String, ObjModel.ObjGroup> entry : model.groups.entrySet()) {
			String groupName = entry.getKey();

			poseStack.pushPose();

			if (groupName.startsWith("pole")) {
				// radius follows the machine's size, height follows the tower the player built
				poseStack.scale((float) nacelleScale, (float) towerHeightScale, (float) nacelleScale);
			} else if (!groupName.startsWith("insulator")) {
				// the nacelle, the hub cone and the blades ride up with the tower and scale
				// about where they sit on it, so the assembly always meets the tower top
				poseStack.translate(0.0, towerOffset, 0.0);
				poseStack.translate(0.0, TurbineSpec.MODEL_TOWER_HEIGHT_BLOCKS, 0.0);
				poseStack.scale((float) nacelleScale, (float) nacelleScale, (float) nacelleScale);
				poseStack.translate(0.0, -TurbineSpec.MODEL_TOWER_HEIGHT_BLOCKS, 0.0);

				// the blades are scaled further, to the rotor's own diameter. On the C line
				// this is the same figure and nothing extra happens; on the small-wind machine
				// the rotor is small next to a body that has to stay big enough to texture,
				// which is the proportion a real small turbine has.
				double rotorScale = spec.renderScale() / nacelleScale;
				if (groupName.startsWith("rotate_1") && rotorScale != 1.0) {
					poseStack.translate(hubCenter.x, hubCenter.y, hubCenter.z);
					poseStack.scale((float) rotorScale, (float) rotorScale, (float) rotorScale);
					poseStack.translate(-hubCenter.x, -hubCenter.y, -hubCenter.z);
				}

				if (groupName.startsWith("rotate_1") || groupName.startsWith("rotate_2")) {
					// one angle for both: the hub cone is bolted to the blade roots
					float rotation = groupName.startsWith("rotate_1") ? rotation1 : rotation2;
					poseStack.translate(hubCenter.x, hubCenter.y, hubCenter.z);
					poseStack.mulPose(Axis.ZP.rotationDegrees(rotation));
					poseStack.translate(-hubCenter.x, -hubCenter.y, -hubCenter.z);
				}
			}

			poses.put(groupName, new Matrix4f(poseStack.last().pose()));

			poseStack.popPose();
		}

		renderGrouped(model, poses, projectionMatrix, textureLocation, packedLight, blockEntity.getBlockPos(), BUFFER_CACHE);
		poseStack.popPose();
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
		// every machine in the catalogue, not just the original block: each is its own
		// block and needs its own model registration, interaction groups and insulator
		// bounding boxes, or a C52 would place fine and then render nothing
		for (TurbineSpec spec : TurbineCatalog.all()) {
			ObjBlockDefinition definition = ObjDefinitions.get(Electricity.TURBINE_BLOCKS.get(spec.id()).get());
			if (definition == null) continue;

			ObjBlockRegistry.register(definition.block(), definition.model(), null);
			for (String insulator : definition.insulators()) {
				ObjInteractionRegistry.register(definition.block(), insulator, null);
			}

			calculateAndRegisterBoundingBoxes(definition);
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

	private static float rotationForTurbine(Direction facing) {
		return switch (facing) {
			case EAST -> 90.0f;
			case SOUTH -> 0.0f;
			case WEST -> 270.0f;
			default -> 180.0f;
		};
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
