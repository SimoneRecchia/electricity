package com.dooji.electricity.main.registry;

import com.dooji.electricity.main.Electricity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * A family of machine specifications, keyed by id and kept in the order they were written.
 *
 * Nine catalogues each had this map, its register method and the same id helper. What differs between them
 * is the specs and how a name is printed, which is what each one keeps for itself.
 */
public final class Catalogue<T> {
	private final Map<ResourceLocation, T> byId = new LinkedHashMap<>();
	private final Function<T, ResourceLocation> identity;

	public Catalogue(Function<T, ResourceLocation> identity) {
		this.identity = identity;
	}

	/** The mod's own namespace, which is where every spec in every catalogue lives. */
	public static ResourceLocation id(String path) {
		return new ResourceLocation(Electricity.MOD_ID, path);
	}

	/** Adds a spec and hands it straight back, so a catalogue's constants are its registrations. */
	public T register(T spec) {
		byId.put(identity.apply(spec), spec);
		return spec;
	}

	/** In the order they were written, which is the order a creative tab and a sheet of renders show them. */
	public List<T> all() {
		return List.copyOf(byId.values());
	}

	/** The spec a saved world named, or null if this build no longer has it. */
	@Nullable
	public T byId(ResourceLocation id) {
		return byId.get(id);
	}
}
