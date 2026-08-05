package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.TowerSpec;
import com.dooji.electricity.api.power.TowerSpec.Duty;
import com.dooji.electricity.main.Electricity;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * The three lattice towers, which are three structural duties of one design.
 *
 * A Donaumast: two crossarm levels, three phases a side, so one tower carries two circuits at 400 kV.
 * The spans are what each duty will actually hold - a suspension tower is only holding weight, a tension
 * tower is holding the difference between two pulls, and a terminal tower is holding all of it.
 */
public final class TowerCatalog {
	private static final Map<ResourceLocation, TowerSpec> BY_ID = new LinkedHashMap<>();

	public static final TowerSpec SUSPENSION = register(new TowerSpec(
			id("lattice_suspension"), "Donaumast S", Duty.SUSPENSION, 400_000.0, 2, 160.0));

	public static final TowerSpec TENSION = register(new TowerSpec(
			id("lattice_tension"), "Donaumast A", Duty.TENSION, 400_000.0, 2, 200.0));

	public static final TowerSpec TERMINAL = register(new TowerSpec(
			id("lattice_terminal"), "Donaumast E", Duty.TERMINAL, 400_000.0, 2, 200.0));

	private TowerCatalog() {
	}

	private static ResourceLocation id(String path) {
		return new ResourceLocation(Electricity.MOD_ID, path);
	}

	private static TowerSpec register(TowerSpec spec) {
		BY_ID.put(spec.id(), spec);
		return spec;
	}

	public static Collection<TowerSpec> all() {
		return List.copyOf(BY_ID.values());
	}

	@Nullable
	public static TowerSpec get(ResourceLocation id) {
		return BY_ID.get(id);
	}
}
