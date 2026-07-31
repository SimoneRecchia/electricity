package com.dooji.renderix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RenderixMesh {
	private final List<float[]> positions;
	private final List<float[]> normals;
	private final List<float[]> texCoords;
	private final List<RenderixFace> faces;
	private final List<String> materialLibraries;

	public RenderixMesh(List<float[]> positions, List<float[]> normals, List<float[]> texCoords, List<RenderixFace> faces, List<String> materialLibraries) {
		this(positions, normals, texCoords, faces, materialLibraries, false);
	}

	private RenderixMesh(List<float[]> positions, List<float[]> normals, List<float[]> texCoords, List<RenderixFace> faces, List<String> materialLibraries, boolean reuse) {
		if (reuse) {
			this.positions = positions;
			this.normals = normals;
			this.texCoords = texCoords;
			this.materialLibraries = materialLibraries;
		} else {
			this.positions = Collections.unmodifiableList(new ArrayList<>(positions));
			this.normals = Collections.unmodifiableList(new ArrayList<>(normals));
			this.texCoords = Collections.unmodifiableList(new ArrayList<>(texCoords));
			this.materialLibraries = Collections.unmodifiableList(new ArrayList<>(materialLibraries));
		}

		this.faces = Collections.unmodifiableList(new ArrayList<>(faces));
	}

	public List<String> materialLibraries() {
		return materialLibraries;
	}

	public int faceCount() {
		return faces.size();
	}

	public RenderixFace face(int index) {
		return faces.get(index);
	}

	public float[] position(int index) {
		return positions.get(index);
	}

	public float[] normal(int index) {
		return normals.get(index);
	}

	public float[] texCoord(int index) {
		return texCoords.get(index);
	}

	List<RenderixFace> facesView() {
		return faces;
	}
	RenderixMesh withFaces(List<RenderixFace> subset) {
		return new RenderixMesh(positions, normals, texCoords, subset, materialLibraries, true);
	}
}
