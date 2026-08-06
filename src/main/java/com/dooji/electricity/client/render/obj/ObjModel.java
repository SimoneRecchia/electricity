package com.dooji.electricity.client.render.obj;

import com.dooji.electricity.main.Electricity;
import com.dooji.renderix.RenderixFace;
import com.dooji.renderix.RenderixLoader;
import com.dooji.renderix.RenderixMaterial;
import com.dooji.renderix.RenderixMaterials;
import com.dooji.renderix.RenderixMesh;
import com.dooji.renderix.RenderixSplitter;
import com.dooji.renderix.RenderixTessellator;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// OBJ pipeline code will be migrated to Renderix
public class ObjModel {
	private static final Logger LOGGER = LoggerFactory.getLogger("electricity");
	public final Map<String, ObjGroup> groups = new HashMap<>();
	public final Map<String, ObjMaterial> materials = new HashMap<>();

	public static class ObjGroup {
		public final String name;
		public final List<Vector3f> vertices = new ArrayList<>();
		public final List<Vector3f> normals = new ArrayList<>();
		public final List<Float> texCoords = new ArrayList<>();
		public final String materialName;

		public ObjGroup(String name, String materialName) {
			this.name = name;
			this.materialName = materialName;
		}
	}

	public static class ObjMaterial {
		public final String name;
		public final ResourceLocation texture;

		public ObjMaterial(String name, ResourceLocation texture) {
			this.name = name;
			this.texture = texture;
		}
	}

	public static ObjModel loadFromResource(ResourceLocation resourceLocation) {
		try {
			InputStream stream = Minecraft.getInstance().getResourceManager().getResource(resourceLocation).orElseThrow().open();
			return loadFromStream(stream, resourceLocation);
		} catch (IOException e) {
			LOGGER.error("Failed to load OBJ model: {}", resourceLocation, e);
			return null;
		}
	}

	public static ObjModel loadFromStream(InputStream stream, ResourceLocation resourceLocation) {
		ObjModel model = new ObjModel();
		try {
			RenderixMesh mesh = RenderixLoader.readObj(stream);
			RenderixMesh renderableMesh = RenderixTessellator.toRenderable(mesh);

			model.loadMaterials(mesh, resourceLocation);

			Map<String, RenderixMesh> objectGroups = RenderixSplitter.splitByObject(renderableMesh);

			if (!objectGroups.isEmpty()) {
				for (Map.Entry<String, RenderixMesh> entry : objectGroups.entrySet()) {
					String objectName = entry.getKey();
					RenderixMesh objectMesh = entry.getValue();

					Map<String, RenderixMesh> materialGroups = RenderixSplitter.splitByMaterial(objectMesh);

					for (Map.Entry<String, RenderixMesh> materialEntry : materialGroups.entrySet()) {
						String materialName = materialEntry.getKey();
						RenderixMesh materialMesh = materialEntry.getValue();

						if (materialMesh.faceCount() == 0) continue;

						ObjGroup group = new ObjGroup(objectName, materialName);
						model.addDeindexedVertices(materialMesh, group);
						model.groups.put(objectName + "_" + materialName, group);
					}
				}
			} else {
				Map<String, RenderixMesh> materialGroups = RenderixSplitter.splitByMaterial(renderableMesh);

				for (Map.Entry<String, RenderixMesh> entry : materialGroups.entrySet()) {
					String materialName = entry.getKey();
					RenderixMesh materialMesh = entry.getValue();

					if (materialMesh.faceCount() == 0) continue;

					ObjGroup group = new ObjGroup("default", materialName);
					model.addDeindexedVertices(materialMesh, group);
					model.groups.put(materialName, group);
				}
			}

		} catch (IOException e) {
			LOGGER.error("Error parsing OBJ file", e);
			return null;
		}

		return model;
	}

	private void loadMaterials(RenderixMesh mesh, ResourceLocation resourceLocation) {
		String namespace = resourceLocation != null ? resourceLocation.getNamespace() : Electricity.MOD_ID;
		String resourcePath = resourceLocation != null ? resourceLocation.getPath() : "";
		String baseDirectory = "";
		int slashIndex = resourcePath.lastIndexOf('/');
		if (slashIndex >= 0) {
			baseDirectory = resourcePath.substring(0, slashIndex + 1);
		}

		for (String mtlFileName : mesh.materialLibraries()) {
			try {
				String relativePath = baseDirectory.isEmpty() ? mtlFileName : baseDirectory + mtlFileName;
				ResourceLocation mtlLocation = new ResourceLocation(namespace, relativePath);
				InputStream stream = Minecraft.getInstance().getResourceManager().getResource(mtlLocation).orElseThrow().open();

				List<RenderixMaterial> mtls = RenderixMaterials.read(stream);
				for (RenderixMaterial mtl : mtls) {
					ResourceLocation texture = null;
					if (mtl.diffuseMap() != null && !mtl.diffuseMap().isEmpty()) {
						texture = new ResourceLocation("electricity", "textures/block/" + mtl.diffuseMap());
					}
					materials.put(mtl.name(), new ObjMaterial(mtl.name(), texture));
				}

				stream.close();
			} catch (IOException e) {
				LOGGER.error("Failed to load MTL file: {}", mtlFileName, e);
			}
		}
	}

	private void addDeindexedVertices(RenderixMesh mesh, ObjGroup group) {
		for (int i = 0; i < mesh.faceCount(); i++) {
			RenderixFace face = mesh.face(i);
			int numVertices = face.vertexCount();
			if (numVertices < 3) continue;

			Vector3f fallbackNormal = face.hasNormals() ? null : calculateFaceNormal(mesh, face);

			for (int j = 0; j < numVertices; j++) {
				addDeindexedVertex(mesh, face, j, group, fallbackNormal);
			}

			if (numVertices == 3) {
				addDeindexedVertex(mesh, face, 2, group, fallbackNormal);
			}
		}
	}

	private void addDeindexedVertex(RenderixMesh mesh, RenderixFace face, int vertexIndexInFace, ObjGroup group, Vector3f fallbackNormal) {
		int posIndex = face.positionIndex(vertexIndexInFace);
		int normalIndex = face.hasNormals() ? face.normalIndex(vertexIndexInFace) : -1;
		int texCoordIndex = face.hasTexCoords() ? face.texCoordIndex(vertexIndexInFace) : -1;

		float[] vertex = mesh.position(posIndex);
		group.vertices.add(new Vector3f(getComponent(vertex, 0), getComponent(vertex, 1), getComponent(vertex, 2)));

		if (normalIndex != -1) {
			float[] normal = mesh.normal(normalIndex);
			group.normals.add(new Vector3f(getComponent(normal, 0), getComponent(normal, 1), getComponent(normal, 2)));
		} else {
			group.normals.add(fallbackNormal != null ? new Vector3f(fallbackNormal) : new Vector3f(0, 1, 0));
		}

		if (texCoordIndex != -1) {
			float[] texCoord = mesh.texCoord(texCoordIndex);
			group.texCoords.add(getComponent(texCoord, 0));
			group.texCoords.add(1.0f - getComponent(texCoord, 1));
		} else {
			group.texCoords.add(0.0f);
			group.texCoords.add(0.0f);
		}
	}

	private Vector3f calculateFaceNormal(RenderixMesh mesh, RenderixFace face) {
		if (face.vertexCount() < 3) return new Vector3f(0, 1, 0);

		float[] v0 = mesh.position(face.positionIndex(0));
		float[] v1 = mesh.position(face.positionIndex(1));
		float[] v2 = mesh.position(face.positionIndex(2));

		Vector3f edge1 = new Vector3f(getComponent(v1, 0) - getComponent(v0, 0), getComponent(v1, 1) - getComponent(v0, 1), getComponent(v1, 2) - getComponent(v0, 2));

		Vector3f edge2 = new Vector3f(getComponent(v2, 0) - getComponent(v0, 0), getComponent(v2, 1) - getComponent(v0, 1), getComponent(v2, 2) - getComponent(v0, 2));

		Vector3f normal = new Vector3f();
		edge1.cross(edge2, normal);
		normal.normalize();

		return normal;
	}

	private float getComponent(float[] values, int index) {
		if (values == null) return 0.0f;
		return index < values.length ? values[index] : 0.0f;
	}

	public static class BoundingBox {
		public final Vector3f min;
		public final Vector3f max;
		public final Vector3f center;

		public BoundingBox(Vector3f min, Vector3f max) {
			this.min = min;
			this.max = max;
			this.center = new Vector3f((min.x + max.x) / 2.0f, (min.y + max.y) / 2.0f, (min.z + max.z) / 2.0f);
		}
	}

	public BoundingBox getBoundingBox(String groupName) {
		ObjGroup group = groups.get(groupName);
		if (group == null || group.vertices.isEmpty()) return null;

		Vector3f min = new Vector3f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
		Vector3f max = new Vector3f(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);

		for (Vector3f vertex : group.vertices) {
			min.x = Math.min(min.x, vertex.x);
			min.y = Math.min(min.y, vertex.y);
			min.z = Math.min(min.z, vertex.z);
			max.x = Math.max(max.x, vertex.x);
			max.y = Math.max(max.y, vertex.y);
			max.z = Math.max(max.z, vertex.z);
		}

		return new BoundingBox(min, max);
	}

	public Map<String, BoundingBox> getAllBoundingBoxes() {
		Map<String, BoundingBox> boundingBoxes = new HashMap<>();
		for (String groupName : groups.keySet()) {
			BoundingBox bbox = getBoundingBox(groupName);
			if (bbox != null) {
				boundingBoxes.put(groupName, bbox);
			}
		}
		return boundingBoxes;
	}
}
