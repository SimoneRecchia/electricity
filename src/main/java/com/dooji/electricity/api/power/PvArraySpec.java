package com.dooji.electricity.api.power;

import com.dooji.electricity.api.WorldConditions;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * One block's worth of photovoltaic array: a module, a mounting, and the wiring between them.
 *
 * This is the product a player places, and it is a *product* rather than a free configuration for
 * the same reason the turbines are: a tracker built for 700 W bifacial modules is a different
 * thing to buy from a fixed rack built for thin film, and pretending they are one thing with knobs
 * on would lose the decision worth making.
 *
 * <h2>Why the module count is not a free number</h2>
 *
 * A block is a hundred square metres of ground at the mod's ten metres to the block, and modules
 * have a real size in real square metres. So the count is decided by how much of that hundred the
 * mounting can cover: nearly all of it lying flat, under half at a fixed tilt, and a quarter under
 * a dual-axis frame that spends the morning tilted hard over. Everything else follows - the DC
 * nameplate, the string voltage, the ground cover ratio - and {@link #groundCoverRatio()} is
 * derived rather than declared so it cannot disagree with the modules it is counting.
 *
 * That is also the whole trade between the mountings, and it is a real one. The flat array carries
 * nearly twenty kilowatts on a block and gives a bell-shaped day. The single-axis tracker carries
 * half that and gives a plateau, so it makes more energy per kilowatt installed and less per block.
 * Which is better depends on whether land or hardware is the thing in short supply, which is the
 * argument the industry has been having for twenty years.
 */
public record PvArraySpec(
		ResourceLocation id,
		/** Product designation, e.g. {@code Meridian HR-580}. A proper noun: never translated. */
		String displayName,
		PvModuleSpec module,
		PvMounting mounting,
		/** The tracker under it, or null for a mounting that does not move. */
		@Nullable TrackerSpec tracker,
		/**
		 * Modules in series in one string.
		 *
		 * Not a round number chosen for tidiness: it is however many fit under the system voltage
		 * limit at the coldest cell temperature the site sees, which is why a thin-film module at
		 * 223 V open circuit goes six to a string where a 51 V silicon one goes twenty-seven.
		 */
		int modulesPerString,
		int strings
) {
	/** Ground one block covers, in square metres. A hundred, at ten metres to the block. */
	public static final double LAND_AREA_M2 = WorldConditions.METRES_PER_BLOCK * WorldConditions.METRES_PER_BLOCK;
	/**
	 * How far the derived ground cover may sit outside the band its mounting is normally built at.
	 *
	 * A quarter, which is loose enough that a module's real dimensions decide the count rather than
	 * a target, and tight enough that a fixed rack cannot quietly end up packed like a flat roof.
	 */
	private static final double GROUND_COVER_TOLERANCE = 0.25;
	/**
	 * Coldest cell temperature a string layout has to survive, in Celsius.
	 *
	 * String length is decided at the coldest temperature a site sees rather than at 25 C, because a
	 * module's open-circuit voltage *rises* as it cools - which is the wrong direction for safety. Minus
	 * ten is a cold clear morning in a temperate biome, and it is the figure the catalogue's layouts were
	 * worked out against.
	 */
	public static final double COLDEST_DESIGN_CELL_C = -10.0;

	public PvArraySpec {
		if (modulesPerString < 1 || strings < 1) throw new IllegalArgumentException(id + ": an array has at least one module");
		if (mounting.tracking() != (tracker != null)) {
			throw new IllegalArgumentException(id + ": " + mounting + " and the tracker have to agree about whether this moves");
		}

		if (tracker != null && (mounting == PvMounting.DUAL_AXIS) != tracker.dualAxis()) {
			throw new IllegalArgumentException(id + ": the mounting and the tracker disagree about how many axes it has");
		}

		// the layout has to survive a cold morning, or the array would exceed its own system voltage
		// limit exactly when it was producing best. Checked here rather than left as a method nothing
		// called, because an unchecked invariant is not an invariant
		int maxSeries = module.maxSeriesModules(COLDEST_DESIGN_CELL_C);
		if (modulesPerString > maxSeries) {
			throw new IllegalArgumentException(id + ": " + modulesPerString + " modules in series exceeds " + module.maxSystemVolts()
					+ " V at " + COLDEST_DESIGN_CELL_C + " C, where only " + maxSeries + " fit");
		}

		double cover = modulesPerString * strings * module.areaM2() / LAND_AREA_M2;
		double typical = mounting.typicalGroundCover();
		if (cover < typical * (1.0 - GROUND_COVER_TOLERANCE) || cover > typical * (1.0 + GROUND_COVER_TOLERANCE)) {
			throw new IllegalArgumentException(id + ": " + modulesPerString * strings + " modules cover " + cover + " of the block, which is not how a " + mounting + " array is built");
		}
	}

	// ---- what is on the block ----

	public int moduleCount() {
		return modulesPerString * strings;
	}

	public double moduleAreaM2() {
		return moduleCount() * module.areaM2();
	}

	/**
	 * Module row width over row pitch, which is the same thing as module area over land area.
	 *
	 * Derived, and it is the number that everything about self-shading depends on: backtracking
	 * geometry, the sun elevation below which the rows start shading each other, and how much of
	 * the ground behind a bifacial module is lit.
	 */
	public double groundCoverRatio() {
		return moduleAreaM2() / LAND_AREA_M2;
	}

	/** Nameplate at standard test conditions, kW. */
	public double dcPowerKw() {
		return moduleCount() * module.ratedPowerW() / 1000.0;
	}

	public boolean tracked() {
		return tracker != null;
	}

	/**
	 * Tilt of the module plane from horizontal, in degrees.
	 *
	 * A property of the mounting on a fixed rack and a reading on a tracked one, which is why the
	 * tracked rotation has to be passed in rather than asked for: only the block knows where its
	 * drive has actually got to.
	 */
	public double tiltDeg(double trackerRotation) {
		return tracked() ? Math.abs(trackerRotation) : mounting.fixedTiltDeg();
	}

	// ---- the electrical side ----

	/** String voltage at the maximum power point, at a given cell temperature. */
	public double stringVoltage(double cellTemperatureC) {
		return modulesPerString * module.vmpAt(cellTemperatureC);
	}

	/** Open-circuit string voltage, which is what the system voltage limit is written against. */
	public double stringOpenCircuitVoltage(double cellTemperatureC) {
		return modulesPerString * module.vocAt(cellTemperatureC);
	}

	/** Current in one string, in amps. The same current whether there is one string or forty. */
	public double stringCurrent(double poaIrradiance, double cellTemperatureC) {
		return module.impAt(poaIrradiance, cellTemperatureC);
	}

	/** Total array current at the combiner, in amps. */
	public double arrayCurrent(double poaIrradiance, double cellTemperatureC) {
		return strings * stringCurrent(poaIrradiance, cellTemperatureC);
	}

	/**
	 * Effective irradiance on the modules, front and back together, W/m2.
	 *
	 * A bifacial module does not have two outputs, it has one - so the standard way to account for
	 * the back is to add what it collects to the front as an equivalent irradiance, weighted by how
	 * much worse the back is than the front. The rear figure arriving here has already been through
	 * the mounting's view factor, which is where most of the difference between a flat array and an
	 * elevated tracker lives.
	 */
	public double effectiveIrradiance(double frontPoa, double rearPoa) {
		return Math.max(0.0, frontPoa) + module.bifaciality() * Math.max(0.0, rearPoa);
	}

	/**
	 * Direct-current output of the whole block, in kW.
	 *
	 * Everything before the inverter: the light on the plane, the cell temperature, the low-light
	 * knee, the spectrum, and whatever fraction of the array is still working.
	 */
	public double dcPowerKw(double effectiveIrradiance, double cellTemperatureC, double spectralFactor, double availability) {
		if (effectiveIrradiance <= 0.0 || availability <= 0.0) return 0.0;

		return moduleCount() * module.powerW(effectiveIrradiance, cellTemperatureC, spectralFactor) * availability / 1000.0;
	}

	// ---- snow ----

	/**
	 * Whether snow slides off the plane as it stands.
	 *
	 * Needs both: a steep enough plane, and modules warm enough to melt the film of ice holding the
	 * snow to the glass. The second is why a snowed-over array can stay snowed over on a bright
	 * cold day - it is producing nothing, so it is generating no waste heat, so it stays at air
	 * temperature, so the snow stays. A module that can get a little light through the snow warms
	 * up and frees itself; one that is completely buried does not.
	 */
	public boolean shedsSnow(double trackerRotation, double moduleTempC) {
		return tiltDeg(trackerRotation) >= PvMounting.SNOW_SHED_TILT && moduleTempC > 0.0;
	}
}
