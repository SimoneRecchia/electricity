package com.dooji.electricity.api.power;

import com.dooji.electricity.api.WorldConditions;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** One block's worth of photovoltaic array: a module, a mounting, and the wiring between them. */
public record PvArraySpec(
		ResourceLocation id,
		/** Product designation. */
		String displayName,
		PvModuleSpec module,
		PvMounting mounting,
		/** The tracker under it, or null for a mounting that does not move. */
		@Nullable TrackerSpec tracker,
		/** Modules in series in one string. */
		int modulesPerString,
		int strings
) {
	/** Ground one block covers, in square metres. */
	public static final double LAND_AREA_M2 = WorldConditions.METRES_PER_BLOCK * WorldConditions.METRES_PER_BLOCK;
	/** How far the derived ground cover may sit outside the band its mounting is normally built at. */
	private static final double GROUND_COVER_TOLERANCE = 0.25;
	/** Coldest cell temperature a string layout has to survive, in Celsius. */
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

	/** Module row width over row pitch, which is the same thing as module area over land area. */
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

	/** Tilt of the module plane from horizontal, in degrees. */
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

	/** Current in one string, in amps. */
	public double stringCurrent(double poaIrradiance, double cellTemperatureC) {
		return module.impAt(poaIrradiance, cellTemperatureC);
	}

	/** Total array current at the combiner, in amps. */
	public double arrayCurrent(double poaIrradiance, double cellTemperatureC) {
		return strings * stringCurrent(poaIrradiance, cellTemperatureC);
	}

	/** Effective irradiance on the modules, front and back together, W/m2. */
	public double effectiveIrradiance(double frontPoa, double rearPoa) {
		return Math.max(0.0, frontPoa) + module.bifaciality() * Math.max(0.0, rearPoa);
	}

	/** Direct-current output of the whole block, in kW. */
	public double dcPowerKw(double effectiveIrradiance, double cellTemperatureC, double spectralFactor, double availability) {
		if (effectiveIrradiance <= 0.0 || availability <= 0.0) return 0.0;

		return moduleCount() * module.powerW(effectiveIrradiance, cellTemperatureC, spectralFactor) * availability / 1000.0;
	}

	// ---- snow ----

	/** Whether snow slides off the plane as it stands. */
	public boolean shedsSnow(double trackerRotation, double moduleTempC) {
		return tiltDeg(trackerRotation) >= PvMounting.SNOW_SHED_TILT && moduleTempC > 0.0;
	}
}
