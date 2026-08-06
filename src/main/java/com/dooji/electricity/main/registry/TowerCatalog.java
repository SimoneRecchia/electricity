package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.TowerSpec;
import com.dooji.electricity.api.power.TowerSpec.Duty;
import static com.dooji.electricity.main.registry.Catalogue.id;
import java.util.List;

/**
 * The three lattice towers, which are three structural duties of one design.
 *
 * A Donaumast: two crossarm levels, three phases a side, so one tower carries two circuits at 400 kV.
 * The spans are what each duty will actually hold - a suspension tower is only holding weight, a tension
 * tower is holding the difference between two pulls, and a terminal tower is holding all of it.
 */
public final class TowerCatalog {
	private static final Catalogue<TowerSpec> TOWERS = new Catalogue<>(TowerSpec::id);

	public static final TowerSpec SUSPENSION = TOWERS.register(new TowerSpec(
			id("lattice_suspension"), "Donaumast S", Duty.SUSPENSION, 400_000.0, 2, 160.0));

	public static final TowerSpec TENSION = TOWERS.register(new TowerSpec(
			id("lattice_tension"), "Donaumast A", Duty.TENSION, 400_000.0, 2, 200.0));

	public static final TowerSpec TERMINAL = TOWERS.register(new TowerSpec(
			id("lattice_terminal"), "Donaumast E", Duty.TERMINAL, 400_000.0, 2, 200.0));

	private TowerCatalog() {
	}

	public static List<TowerSpec> all() {
		return TOWERS.all();
	}

}
