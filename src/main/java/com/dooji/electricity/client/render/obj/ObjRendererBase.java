package com.dooji.electricity.client.render.obj;

import com.dooji.electricity.block.ModelFacing;
import com.dooji.electricity.block.DcCableBlock;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

// OBJ pipeline code will be migrated to Renderix
public abstract class ObjRendererBase {
	protected static void renderGrouped(ObjModel model, PoseStack poseStack, Matrix4f projectionMatrix, ResourceLocation texture, int packedLight, BlockPos pos, Map<BlockPos, Map<String, GroupBuffer>> cache) {
		renderGrouped(model, poseStack, projectionMatrix, texture, packedLight, pos, cache, groupName -> true);
	}

	/**
	 * The same, with a say in which groups are drawn.
	 *
	 * For the parts of a model that are there only sometimes: an array grows a junction box and a set of
	 * leads when a reel of cable is worked into it, and the alternative to leaving a group out is a second
	 * model that differs from the first by one box.
	 */
	protected static void renderGrouped(ObjModel model, PoseStack poseStack, Matrix4f projectionMatrix, ResourceLocation texture, int packedLight, BlockPos pos, Map<BlockPos, Map<String, GroupBuffer>> cache, Predicate<String> draw) {
		Map<ResourceLocation, List<GroupBuffer>> byTexture = new HashMap<>();
		for (Map.Entry<String, ObjModel.ObjGroup> groupEntry : model.groups.entrySet()) {
			String groupName = groupEntry.getKey();
			if (!draw.test(groupName)) continue;

			ObjModel.ObjGroup group = groupEntry.getValue();
			GroupBuffer buffer = bufferFor(pos, groupName, group, model, texture, packedLight, cache);
			if (buffer == null) continue;
			byTexture.computeIfAbsent(buffer.texture, t -> new ArrayList<>()).add(buffer.withPose(poseStack.last().pose()));
		}

		for (Map.Entry<ResourceLocation, List<GroupBuffer>> textureEntry : byTexture.entrySet()) {
			RenderType type = RenderType.entityCutoutNoCull(textureEntry.getKey());
			type.setupRenderState();

			RenderSystem.setShader(GameRenderer::getRendertypeEntityCutoutNoCullShader);
			ShaderInstance shader = GameRenderer.getRendertypeEntityCutoutNoCullShader();

			for (GroupBuffer buffer : textureEntry.getValue()) {
				buffer.buffer.bind();
				buffer.buffer.drawWithShader(buffer.modelMatrix, projectionMatrix, shader);
			}

			VertexBuffer.unbind();
			type.clearRenderState();
		}
	}

	protected static void renderGrouped(ObjModel model, Map<String, Matrix4f> poses, Matrix4f projectionMatrix, ResourceLocation texture, int packedLight, BlockPos pos, Map<BlockPos, Map<String, GroupBuffer>> cache) {
		Map<ResourceLocation, List<GroupBuffer>> byTexture = new HashMap<>();
		for (Map.Entry<String, ObjModel.ObjGroup> groupEntry : model.groups.entrySet()) {
			String groupName = groupEntry.getKey();
			ObjModel.ObjGroup group = groupEntry.getValue();
			Matrix4f pose = poses.get(groupName);
			if (pose == null) continue;
			GroupBuffer buffer = bufferFor(pos, groupName, group, model, texture, packedLight, cache);
			if (buffer == null) continue;
			byTexture.computeIfAbsent(buffer.texture, t -> new ArrayList<>()).add(buffer.withPose(pose));
		}

		for (Map.Entry<ResourceLocation, List<GroupBuffer>> textureEntry : byTexture.entrySet()) {
			RenderType type = RenderType.entityCutoutNoCull(textureEntry.getKey());
			type.setupRenderState();

			RenderSystem.setShader(GameRenderer::getRendertypeEntityCutoutNoCullShader);
			ShaderInstance shader = GameRenderer.getRendertypeEntityCutoutNoCullShader();

			for (GroupBuffer buffer : textureEntry.getValue()) {
				buffer.buffer.bind();
				buffer.buffer.drawWithShader(buffer.modelMatrix, projectionMatrix, shader);
			}

			VertexBuffer.unbind();
			type.clearRenderState();
		}
	}

	/**
	 * The cable entries a machine should draw: one per side a run has actually been laid against.
	 *
	 * Four groups in the model, one per side, and this decides which of them get a pose. That is the
	 * whole answer to a cable that stopped a pixel short of the machine over open ground: the machine
	 * grows the last stretch itself, from its own middle out to the edge the copper arrives at, at the
	 * cross-section a laid run has - so the two meet with no seam and nothing to see through.
	 *
	 * The names are model-space, because that is what the model is authored in and the block's facing has
	 * already turned it - so the world direction the copper arrives from is read back into the model's own
	 * frame to find the group that will end up pointing at it. Which way the model faces is the machine's
	 * to say, exactly as it is for the pose: this used to assume north, and the assumption was invisible.
	 */
	protected static Set<String> cableEntries(BlockGetter level, BlockPos pos, Direction authored, Direction facing, String prefix) {
		Set<String> live = new HashSet<>();
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (!cableArrives(level, pos, direction)) continue;

			live.add(prefix + "_" + ModelFacing.side(direction, authored, facing).getName());
		}

		return live;
	}

	/**
	 * Whether a run of cable reaches this machine from one direction.
	 *
	 * The three positions a run can reach from, which are the three dust reaches from: alongside, a step
	 * up, and a step down. The cable itself is asked, so this cannot drift from what the plant counts.
	 */
	protected static boolean cableArrives(BlockGetter level, BlockPos pos, Direction direction) {
		BlockPos beside = pos.relative(direction);
		for (BlockPos candidate : new BlockPos[]{beside, beside.above(), beside.below()}) {
			BlockState state = level.getBlockState(candidate);
			if (state.getBlock() instanceof DcCableBlock cable && cable.reaches(state, level, candidate, pos)) return true;
		}

		return false;
	}

	/**
	 * Whether one group should be drawn, given which entries are live.
	 *
	 * Anything that is not part of an entry is drawn as usual; an entry is drawn only for the side it
	 * belongs to.
	 */
	protected static boolean entryVisible(String groupName, String prefix, Set<String> live) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			String entry = prefix + "_" + direction.getName();
			if (groupName.startsWith(entry)) return live.contains(entry);
		}

		return true;
	}

	/**
	 * Where a moving part turns, from the marker the model carries for it.
	 *
	 * This used to take the centre of the rotating group's own bounding box, which is exact for anything
	 * symmetric about its axis - a torque tube, an elevation frame - and wrong for everything else. Three
	 * anemometer cups at 120 degrees have a box centre nowhere near the mast, so they turned about a
	 * point beside it and wobbled; a wind vane's box centre sits out by its tail.
	 *
	 * A pivot is a property of the design, so the generator emits it as a zero-size {@code pivot_*}
	 * object and this reads that. No pose is ever built for those groups, so they draw nothing.
	 *
	 * The fallback keeps a model with a renamed marker visibly wrong rather than invisible, which is the
	 * easier failure to notice.
	 */
	/**
	 * The turn a model authored facing one way takes to face another, as the function a pose needs.
	 *
	 * Every renderer here had its own copy of this as a four-case switch, and seven of the eight were the
	 * same three lines with the cases in a different order. What actually differs between them is one fact
	 * - which way the geometry was modelled - and each machine's block declares that as its own
	 * {@code AUTHORED}, so a renderer passes that and keeps no arithmetic at all.
	 *
	 * The arithmetic is {@link ModelFacing}, because the pose is not the only thing that needs it: the wire
	 * anchors and the collision cells turn by the same quarter turns, and they run on a dedicated server
	 * where nothing in this package exists. The one machine that cannot use this is the utility pole, whose
	 * model is mirrored rather than turned; it passes its own table instead.
	 */
	protected static FacingRotationFunction turnedFrom(Direction authored) {
		return facing -> ModelFacing.degrees(authored, facing);
	}

	protected static Vec3 pivot(ObjModel model, String name, Vec3 fallback) {
		return groupCentre(model, "pivot_" + name, fallback);
	}

	/**
	 * Centre of every group whose name starts with a prefix, taken together.
	 *
	 * Every match rather than the first, and that is not fussiness - the group map is a HashMap, so
	 * "the first" is whatever order the hash happened to produce, and a bound that moved between runs
	 * would be a genuinely nasty thing to debug.
	 */
	protected static Vec3 groupCentre(ObjModel model, String prefix, Vec3 fallback) {
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
		boolean found = false;

		for (String groupName : model.groups.keySet()) {
			if (!groupName.startsWith(prefix)) continue;

			ObjModel.BoundingBox box = model.getBoundingBox(groupName);
			if (box == null) continue;

			found = true;
			minX = Math.min(minX, box.min.x());
			minY = Math.min(minY, box.min.y());
			minZ = Math.min(minZ, box.min.z());
			maxX = Math.max(maxX, box.max.x());
			maxY = Math.max(maxY, box.max.y());
			maxZ = Math.max(maxZ, box.max.z());
		}

		return found ? new Vec3((minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0) : fallback;
	}

	/**
	 * Forgets the interpolated angle of anything no longer on screen.
	 *
	 * The angle caches carry a machine's drawn position between frames, which is what makes a rotor or a
	 * frame walk towards its target instead of snapping to it. They have to be emptied on the same terms
	 * as the buffers, or a client that walks past a field of trackers keeps every one of them for ever.
	 */
	protected static void cleanupAngles(Map<BlockPos, Float> cache, Set<BlockPos> seen) {
		if (cache.isEmpty() || seen.isEmpty()) return;

		cache.keySet().removeIf(pos -> !seen.contains(pos));
	}

	protected static void cleanupCache(Map<BlockPos, Map<String, GroupBuffer>> cache, Set<BlockPos> seen) {
		if (cache.isEmpty() || seen.isEmpty()) return;
		cache.keySet().removeIf(pos -> {
			if (!seen.contains(pos)) {
				var map = cache.get(pos);
				if (map != null) map.values().forEach(GroupBuffer::close);
				return true;
			}

			return false;
		});
	}

	private static GroupBuffer bufferFor(BlockPos pos, String groupName, ObjModel.ObjGroup group, ObjModel model, ResourceLocation fallbackTexture, int packedLight, Map<BlockPos, Map<String, GroupBuffer>> cache) {
		if (group.vertices.isEmpty()) return null;

		ResourceLocation texture = resolveTexture(model, group.materialName, fallbackTexture);
		Map<String, GroupBuffer> map = cache.computeIfAbsent(pos, p -> new HashMap<>());
		GroupBuffer buffer = map.get(groupName);

		if (buffer == null || buffer.packedLight != packedLight || !buffer.texture.equals(texture)) {
			if (buffer != null) buffer.close();
			buffer = buildBuffer(group, texture, packedLight);
			if (buffer == null) return null;
			map.put(groupName, buffer);
		}

		return buffer;
	}

	private static GroupBuffer buildBuffer(ObjModel.ObjGroup group, ResourceLocation texture, int packedLight) {
		RenderType type = RenderType.entityCutoutNoCull(texture);
		int capacity = Math.max(256, group.vertices.size() * type.format().getVertexSize());
		BufferBuilder builder = new BufferBuilder(capacity);
		builder.begin(type.mode(), type.format());

		for (int i = 0; i < group.vertices.size(); i++) {
			var pos = group.vertices.get(i);
			var normal = group.normals.get(i);
			float u = group.texCoords.get(i * 2);
			float v = group.texCoords.get(i * 2 + 1);
			builder.vertex(pos.x, pos.y, pos.z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal.x, normal.y, normal.z).endVertex();
		}

		BufferBuilder.RenderedBuffer rendered = builder.end();
		VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);

		vb.bind();
		vb.upload(rendered);
		VertexBuffer.unbind();
		return new GroupBuffer(vb, texture, packedLight, new Matrix4f());
	}

	private static ResourceLocation resolveTexture(ObjModel model, String materialName, ResourceLocation fallback) {
		if (materialName != null) {
			ObjModel.ObjMaterial mat = model.materials.get(materialName);
			if (mat != null && mat.texture != null) return mat.texture;
		}

		return fallback;
	}

	protected record GroupBuffer(VertexBuffer buffer, ResourceLocation texture, int packedLight, Matrix4f modelMatrix) {
		private GroupBuffer withPose(Matrix4f pose) {
			return new GroupBuffer(buffer, texture, packedLight, new Matrix4f(pose));
		}

		private void close() {
			buffer.close();
		}
	}
}
