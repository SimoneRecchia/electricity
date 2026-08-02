package com.dooji.electricity.client.render.obj;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

// OBJ pipeline code will be migrated to Renderix
public abstract class ObjRendererBase {
	protected static void renderGrouped(ObjModel model, PoseStack poseStack, Matrix4f projectionMatrix, ResourceLocation texture, int packedLight, BlockPos pos, Map<BlockPos, Map<String, GroupBuffer>> cache) {
		Map<ResourceLocation, List<GroupBuffer>> byTexture = new HashMap<>();
		for (Map.Entry<String, ObjModel.ObjGroup> groupEntry : model.groups.entrySet()) {
			String groupName = groupEntry.getKey();
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
