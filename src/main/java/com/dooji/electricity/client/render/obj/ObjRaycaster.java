package com.dooji.electricity.client.render.obj;

import com.dooji.electricity.block.ModelFacing;
import com.dooji.electricity.client.render.obj.ObjTransforms.Transform;
import com.dooji.electricity.main.registry.ObjBlockDefinition;
import com.dooji.electricity.main.registry.ObjDefinitions;

import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

// OBJ pipeline code will be migrated to Renderix
@OnlyIn(Dist.CLIENT)
public class ObjRaycaster {
	public static boolean isHoveringPart(Vec3 rayOrigin, Vec3 rayDirection, BlockPos blockPos, String partName) {
		return isHoveringPart(rayOrigin, rayDirection, blockPos, partName, null);
	}

	public static boolean isHoveringPart(Vec3 rayOrigin, Vec3 rayDirection, BlockPos blockPos, String partName, Direction facing) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return false;

		BlockState blockState = mc.level.getBlockState(blockPos);
		ResourceLocation modelLocation = ObjBlockRegistry.getModelLocation(blockState.getBlock());
		if (modelLocation == null) return false;

		ObjModel model = ObjLoader.getModel(modelLocation);
		if (model == null) return false;

		ensureBoundingBoxes(blockState.getBlock(), model);
		ObjModel.BoundingBox partBox = ObjBoundingBoxRegistry.getBoundingBox(blockState.getBlock(), partName);
		if (partBox == null) return false;

		Transform transform = ObjTransforms.resolve(mc.level.getBlockEntity(blockPos));
		Direction effectiveFacing = facing != null ? facing : getFacing(blockState);
		WorldBounds bounds = buildWorldBounds(partBox, blockPos, blockState, effectiveFacing, transform);
		return rayIntersectsBox(rayOrigin, rayDirection, bounds.min(), bounds.max());
	}

	public static String getHoveredPart(Vec3 rayOrigin, Vec3 rayDirection, BlockPos blockPos) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;

		BlockState blockState = mc.level.getBlockState(blockPos);
		ResourceLocation modelLocation = ObjBlockRegistry.getModelLocation(blockState.getBlock());
		if (modelLocation == null) return null;

		ObjModel model = ObjLoader.getModel(modelLocation);
		if (model == null) return null;

		Direction facing = getFacing(blockState);
		ensureBoundingBoxes(blockState.getBlock(), model);
		var interactiveParts = ObjInteractionRegistry.getInteractiveParts(blockState.getBlock());

		for (String partName : interactiveParts) {
			if (ObjBoundingBoxRegistry.getBoundingBox(blockState.getBlock(), partName) != null && isHoveringPart(rayOrigin, rayDirection, blockPos, partName, facing)) {
				return partName;
			}
		}

		var boundingBoxes = ObjBoundingBoxRegistry.getAllBoundingBoxes(blockState.getBlock());
		if (boundingBoxes != null && !boundingBoxes.isEmpty()) {
			for (String partName : boundingBoxes.keySet()) {
				if (isHoveringPart(rayOrigin, rayDirection, blockPos, partName, facing)) return partName;
			}
		}

		return null;
	}

	public static Vec3 getPartCenter(BlockPos blockPos, String partName) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;

		BlockState blockState = mc.level.getBlockState(blockPos);
		Direction facing = getFacing(blockState);
		return getPartCenter(blockPos, partName, facing);
	}

	public static Vec3 getPartCenter(BlockPos blockPos, String partName, Direction facing) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;

		BlockState blockState = mc.level.getBlockState(blockPos);
		ResourceLocation modelLocation = ObjBlockRegistry.getModelLocation(blockState.getBlock());
		if (modelLocation == null) return null;

		ObjModel model = ObjLoader.getModel(modelLocation);
		if (model == null) return null;

		ensureBoundingBoxes(blockState.getBlock(), model);
		ObjModel.BoundingBox partBox = ObjBoundingBoxRegistry.getBoundingBox(blockState.getBlock(), partName);
		if (partBox == null) return null;

		Transform transform = ObjTransforms.resolve(mc.level.getBlockEntity(blockPos));
		Direction effectiveFacing = facing != null ? facing : getFacing(blockState);
		return buildWorldCenter(partBox, blockPos, blockState, effectiveFacing, transform);
	}

	private static Vec3 applyYawPitchRotation(Vec3 point, Vec3 center, float yaw, float pitch) {
		Vec3 relative = point.subtract(center);

		if (pitch != 0) {
			double cosPitch = Math.cos(Math.toRadians(pitch));
			double sinPitch = Math.sin(Math.toRadians(pitch));
			double y = relative.y * cosPitch - relative.z * sinPitch;
			double z = relative.y * sinPitch + relative.z * cosPitch;
			relative = new Vec3(relative.x, y, z);
		}

		if (yaw != 0) {
			double cosYaw = Math.cos(Math.toRadians(yaw));
			double sinYaw = Math.sin(Math.toRadians(yaw));
			double x = relative.x * cosYaw + relative.z * sinYaw;
			double z = -relative.x * sinYaw + relative.z * cosYaw;
			relative = new Vec3(x, relative.y, z);
		}

		return center.add(relative);
	}

	public static boolean rayIntersectsBox(Vec3 rayOrigin, Vec3 rayDirection, Vec3 boxMin, Vec3 boxMax) {
		double tMin = Double.NEGATIVE_INFINITY;
		double tMax = Double.POSITIVE_INFINITY;

		if (rayDirection.x != 0.0) {
			double tx1 = (boxMin.x - rayOrigin.x) / rayDirection.x;
			double tx2 = (boxMax.x - rayOrigin.x) / rayDirection.x;
			tMin = Math.max(tMin, Math.min(tx1, tx2));
			tMax = Math.min(tMax, Math.max(tx1, tx2));
		} else if (rayOrigin.x < boxMin.x || rayOrigin.x > boxMax.x) return false;

		if (rayDirection.y != 0.0) {
			double ty1 = (boxMin.y - rayOrigin.y) / rayDirection.y;
			double ty2 = (boxMax.y - rayOrigin.y) / rayDirection.y;
			tMin = Math.max(tMin, Math.min(ty1, ty2));
			tMax = Math.min(tMax, Math.max(ty1, ty2));
		} else if (rayOrigin.y < boxMin.y || rayOrigin.y > boxMax.y) return false;

		if (rayDirection.z != 0.0) {
			double tz1 = (boxMin.z - rayOrigin.z) / rayDirection.z;
			double tz2 = (boxMax.z - rayOrigin.z) / rayDirection.z;
			tMin = Math.max(tMin, Math.min(tz1, tz2));
			tMax = Math.min(tMax, Math.max(tz1, tz2));
		} else if (rayOrigin.z < boxMin.z || rayOrigin.z > boxMax.z) return false;

		return tMax >= tMin && tMax >= 0;
	}

	public static Vec3 pickAnyGeometry(Vec3 rayOrigin, Vec3 rayDirection, BlockPos blockPos) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;

		BlockState blockState = mc.level.getBlockState(blockPos);
		ResourceLocation modelLocation = ObjBlockRegistry.getModelLocation(blockState.getBlock());
		if (modelLocation == null) return null;

		ObjModel model = ObjLoader.getModel(modelLocation);
		if (model == null) return null;

		Direction facing = getFacing(blockState);
		ensureBoundingBoxes(blockState.getBlock(), model);
		Transform transform = ObjTransforms.resolve(mc.level.getBlockEntity(blockPos));

		Vec3 bestCenter = null;
		double bestDistance = Double.MAX_VALUE;

		Map<String, ObjModel.BoundingBox> boxes = model.getAllBoundingBoxes();
		for (Map.Entry<String, ObjModel.BoundingBox> entry : boxes.entrySet()) {
			ObjModel.BoundingBox box = entry.getValue();
			if (box == null) continue;

			WorldBounds bounds = buildWorldBounds(box, blockPos, blockState, facing, transform);
			if (!rayIntersectsBox(rayOrigin, rayDirection, bounds.min(), bounds.max())) continue;

			Vec3 center = buildWorldCenter(box, blockPos, blockState, facing, transform);
			double distance = rayOrigin.distanceToSqr(center);
			if (distance < bestDistance) {
				bestDistance = distance;
				bestCenter = center;
			}
		}

		return bestCenter;
	}

	/**
	 * Which way a block is placed.
	 *
	 * One property, not a chain: every machine in this mod is placed with BlockStateProperties.HORIZONTAL_FACING,
	 * so the four-branch chain this replaced always took its first branch - and silently returned null for the
	 * machines added after it was written.
	 */
	static Direction getFacing(BlockState blockState) {
		return blockState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
				? blockState.getValue(BlockStateProperties.HORIZONTAL_FACING) : null;
	}

	/**
	 * Which way the model was authored, off the machine's own model definition.
	 *
	 * This has to be the turn ModelFacing does or a part's hover box is not where the part is - which is what
	 * rotatePointAroundCenter got wrong: both of its branches were a quarter turn out at every facing, so every
	 * insulator in the mod was hit-tested in the wrong quadrant of its own block.
	 */
	private static Direction authoredFacing(BlockState blockState) {
		ObjBlockDefinition definition = ObjDefinitions.get(blockState.getBlock());
		return definition == null ? Direction.NORTH : definition.authored();
	}

	private static void ensureBoundingBoxes(Block block, ObjModel model) {
		if (ObjBoundingBoxRegistry.getAllBoundingBoxes(block).isEmpty()) {
			Map<String, ObjModel.BoundingBox> boxes = model.getAllBoundingBoxes();
			if (!boxes.isEmpty()) {
				ObjBoundingBoxRegistry.registerBoundingBoxes(block, boxes);
			}
		}
	}

	private static WorldBounds buildWorldBounds(ObjModel.BoundingBox box, BlockPos blockPos, BlockState state, Direction facing, Transform transform) {
		Vec3 blockOffset = Vec3.atLowerCornerOf(blockPos).add(0.5, 0, 0.5);
		Vec3 min = toVec3(box.min).add(blockOffset);
		Vec3 max = toVec3(box.max).add(blockOffset);
		Vec3 center = new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);

		if (facing != null) {
			Direction authored = authoredFacing(state);
			// min and max turn independently, so a quarter turn swaps which is which - rayIntersectsBox takes
			// the two corners either way round
			min = center.add(ModelFacing.turned(min.subtract(center), authored, facing));
			max = center.add(ModelFacing.turned(max.subtract(center), authored, facing));
		}

		min = min.add(transform.offsetX(), transform.offsetY(), transform.offsetZ());
		max = max.add(transform.offsetX(), transform.offsetY(), transform.offsetZ());

		if (transform.yaw() != 0 || transform.pitch() != 0) {
			min = applyYawPitchRotation(min, center, transform.yaw(), transform.pitch());
			max = applyYawPitchRotation(max, center, transform.yaw(), transform.pitch());
		}

		return new WorldBounds(min, max);
	}

	private static Vec3 buildWorldCenter(ObjModel.BoundingBox box, BlockPos blockPos, BlockState state, Direction facing, Transform transform) {
		Vec3 center = toVec3(box.center).add(Vec3.atLowerCornerOf(blockPos)).add(0.5, 0, 0.5);
		Vec3 blockCenter = new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);

		if (facing != null) {
			center = blockCenter.add(ModelFacing.turned(center.subtract(blockCenter), authoredFacing(state), facing));
		}

		center = center.add(transform.offsetX(), transform.offsetY(), transform.offsetZ());

		if (transform.yaw() != 0 || transform.pitch() != 0) {
			center = applyYawPitchRotation(center, blockCenter, transform.yaw(), transform.pitch());
		}

		return center;
	}

	private static Vec3 toVec3(Vector3f vector) {
		return new Vec3(vector.x, vector.y, vector.z);
	}

	private record WorldBounds(Vec3 min, Vec3 max) {
	}
}
