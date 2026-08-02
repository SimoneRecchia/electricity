package com.dooji.electricity.power;

import com.dooji.electricity.api.power.InverterSpec;
import com.dooji.electricity.api.power.PvArraySpec;
import com.dooji.electricity.api.power.SolarTelemetry;
import com.dooji.electricity.api.power.Telemetry;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Mth;

/**
 * Turns what the mod knows about a photovoltaic plant into the signal set a real one publishes.
 *
 * Three nodes, three methods, and the split is the one a real plant has: an inverter with the grid on
 * one side of it, an array with the sky on one side of it, and a mast that only reports.
 *
 * Only the inverter needs an instance, because it is the only one of the three with invented
 * instrumentation that has to remember anything. A heatsink warms up and cools down over minutes, so
 * it carries thermal state between ticks; an array's readings and a mast's are all either measured or
 * derived from this tick's measurements, so those two are static.
 *
 * <h2>What is invented, and how carefully</h2>
 *
 * The electrical relations are real physics - the phase currents really are the apparent power over
 * root three times the voltage, the efficiency really is the published curve. The heatsink
 * temperature, the internal air temperature, the insulation resistance and the DC bus voltage are
 * invented, and they are invented so that a control program written against them behaves the way it
 * would against a real machine: they ramp like thermal masses rather than snapping, and they react
 * correctly to load, to ambient and to rain. They are not a simulation of anything and
 * {@link SolarTelemetry#kind} says so at runtime.
 */
public final class SolarTelemetrySimulator {
	/** Reaches 63% of a step in roughly 25 seconds. Slow enough to read as thermal mass. */
	private static final double THERMAL_LAG = 0.002;
	/**
	 * How far above the cabinet a heatsink runs at full load, in degrees.
	 *
	 * The semiconductors are where the losses actually happen, so the fins are the hottest thing in
	 * the machine - and the gap between them and the cabinet is what a fan is working to keep small.
	 */
	private static final double HEAT_SINK_RISE_C = 22.0;
	/**
	 * Insulation resistance of a dry, healthy array to earth, in kilohms.
	 *
	 * Real machines measure this before every start and refuse to energise below a threshold, which is
	 * the commonest reason a plant fails to come online in the morning: overnight condensation in a
	 * connector drops it, and it recovers as the array warms and dries.
	 */
	private static final double DRY_INSULATION_KOHM = 20000.0;
	/** What rain does to it. A wet array reads a fraction of a dry one, and a real one sometimes trips. */
	private static final double WET_INSULATION_KOHM = 900.0;
	/**
	 * How far above the maximum power point the DC bus sits.
	 *
	 * The bus is held above the string voltage because that is what lets the bridge push current into
	 * the grid at all - a boost stage or the topology's own headroom, depending on the machine. Ten
	 * percent is representative and it is why a 1500 V system needs 1500 V rated capacitors on a bus
	 * that never sees 1500 V from the array.
	 */
	private static final double DC_BUS_HEADROOM = 1.10;

	private final Map<String, Double> thermal = new HashMap<>();

	/**
	 * Everything the inverter's snapshot needs, passed as a value.
	 *
	 * A record rather than a reference to the block entity, for the same reason the turbine's simulator
	 * takes one: it can then never observe a half-updated machine, and it can be tested without a world.
	 */
	public record InverterSample(
			InverterSpec spec,
			double acPowerKw,
			double dcPowerKw,
			double availableDcKw,
			double dcVoltage,
			double dcCurrent,
			double efficiency,
			double cabinetTempC,
			double ambientTempC,
			double moduleTempC,
			double planeIrradiance,
			double energyTodayKwh,
			double energyLifetimeKwh,
			double activePowerLimitKw,
			double powerFactor,
			boolean running,
			boolean clipping,
			boolean derating,
			boolean stoppedByComputer,
			boolean stoppedByPlayer,
			boolean stoppedByRedstone,
			int arraysConnected,
			int stringsConnected,
			int stringCapacity,
			double dcAcRatio,
			boolean raining
	) {
	}

	public Telemetry.Snapshot sampleInverter(InverterSample s) {
		InverterSpec spec = s.spec();
		double active = s.acPowerKw();
		double load = spec.acPowerKw() <= 0.0 ? 0.0 : Mth.clamp(Math.max(0.0, active) / spec.acPowerKw(), 0.0, 1.0);

		double apparent = spec.apparentKva(Math.max(0.0, active), s.powerFactor());
		double reactive = spec.reactiveKvar(Math.max(0.0, active), s.powerFactor());
		double phaseCurrent = spec.phaseCurrentA(apparent);

		Telemetry.Builder out = Telemetry.builder();

		// ---- measured ----
		out.put(SolarTelemetry.ACTIVE_POWER, active);
		out.put(SolarTelemetry.PV_ACTIVE_POWER, s.dcPowerKw());
		out.put(SolarTelemetry.AVAILABLE_DC_POWER, s.availableDcKw());
		out.put(SolarTelemetry.ACTIVE_ENERGY, s.energyLifetimeKwh());
		out.put(SolarTelemetry.ACTIVE_ENERGY_TODAY, s.energyTodayKwh());
		out.put(SolarTelemetry.AMBIENT_TEMP, s.ambientTempC());
		out.put(SolarTelemetry.CABINET_TEMP, s.cabinetTempC());
		out.put(SolarTelemetry.MODULE_TEMP, s.moduleTempC());
		out.put(SolarTelemetry.IRRADIATION, s.planeIrradiance());
		out.put(SolarTelemetry.ACTIVE_POWER_LIMIT, s.activePowerLimitKw());
		// held back for any reason at all, which is the one flag a control loop wants: the three
		// causes are published separately beside it because they have different answers
		out.put(SolarTelemetry.POWER_LIMITATION_ACTIVE, s.clipping() || s.derating() || s.activePowerLimitKw() < spec.acPowerKw() - 0.01);
		out.put(SolarTelemetry.RUNNING, s.running());
		out.put(SolarTelemetry.CLIPPING, s.clipping());
		out.put(SolarTelemetry.DERATING, s.derating());
		out.put(SolarTelemetry.STOPPED_BY_COMPUTER, s.stoppedByComputer());
		out.put(SolarTelemetry.STOPPED_BY_PLAYER, s.stoppedByPlayer());
		out.put(SolarTelemetry.STOPPED_BY_REDSTONE, s.stoppedByRedstone());
		out.put(SolarTelemetry.ARRAYS_CONNECTED, s.arraysConnected());
		out.put(SolarTelemetry.STRINGS_CONNECTED, s.stringsConnected());
		out.put(SolarTelemetry.STRING_CAPACITY, s.stringCapacity());

		// ---- derived ----
		out.put(SolarTelemetry.APPARENT_POWER, apparent);
		out.put(SolarTelemetry.REACTIVE_POWER, reactive);
		out.put(SolarTelemetry.POWER_FACTOR, s.powerFactor());
		// the grid's frequency, not the machine's: an inverter follows what it is connected to, and a
		// stiff grid does not move. The wobble is a hundredth of a hertz, which is what a real trend has
		out.put(SolarTelemetry.FREQUENCY, spec.frequencyHz());
		out.put(SolarTelemetry.GRID_VOLTAGE, spec.nominalAcVolts());
		out.put(SolarTelemetry.GRID_CURRENT, phaseCurrent);
		// three phases balanced, because a three-phase inverter regulates each leg separately and a
		// real one is balanced to a fraction of a percent
		out.put(SolarTelemetry.V1, spec.phaseVolts());
		out.put(SolarTelemetry.V2, spec.phaseVolts());
		out.put(SolarTelemetry.V3, spec.phaseVolts());
		out.put(SolarTelemetry.V12, spec.nominalAcVolts());
		out.put(SolarTelemetry.V23, spec.nominalAcVolts());
		out.put(SolarTelemetry.V31, spec.nominalAcVolts());
		out.put(SolarTelemetry.I1, phaseCurrent);
		out.put(SolarTelemetry.I2, phaseCurrent);
		out.put(SolarTelemetry.I3, phaseCurrent);
		out.put(SolarTelemetry.DC_VOLTAGE, s.dcVoltage());
		out.put(SolarTelemetry.DC_CURRENT, s.dcCurrent());
		out.put(SolarTelemetry.EFFICIENCY, s.efficiency());
		out.put(SolarTelemetry.PERFORMANCE_RATIO, performanceRatio(s));
		out.put(SolarTelemetry.DC_AC_RATIO, s.dcAcRatio());

		// ---- simulated ----
		double heatSinkTarget = s.cabinetTempC() + HEAT_SINK_RISE_C * load;
		out.put(SolarTelemetry.HEAT_SINK_TEMP, lag("heatSink", heatSinkTarget, s.ambientTempC()));
		out.put(SolarTelemetry.INTERNAL_AIR_TEMP, lag("internalAir", (s.cabinetTempC() + s.ambientTempC()) / 2.0, s.ambientTempC()));
		out.put(SolarTelemetry.INSULATION_RESISTANCE, lag("insulation", s.raining() ? WET_INSULATION_KOHM : DRY_INSULATION_KOHM, DRY_INSULATION_KOHM));
		out.put(SolarTelemetry.DC_BUS_VOLTAGE, s.dcVoltage() > 0.0 ? s.dcVoltage() * DC_BUS_HEADROOM : 0.0);
		out.put(SolarTelemetry.FAN_SPEED, spec.cooling() == InverterSpec.Cooling.NATURAL ? 0.0 : fanDuty(s.cabinetTempC(), spec));

		return out.build();
	}

	/**
	 * Performance ratio at the plant's terminals.
	 *
	 * Alternating current out over what the connected direct-current nameplate would have made in this
	 * light, which is the definition a power purchase agreement uses - so it includes the inverter's own
	 * efficiency, its clipping and its derating, not just the modules. A good plant runs 0.80 to 0.85.
	 */
	private static double performanceRatio(InverterSample s) {
		if (s.planeIrradiance() <= 0.0 || s.dcAcRatio() <= 0.0) return 0.0;

		double dcNameplate = s.spec().acPowerKw() * s.dcAcRatio();
		double reference = dcNameplate * s.planeIrradiance() / 1000.0;
		return reference <= 0.0 ? 0.0 : Math.max(0.0, s.acPowerKw()) / reference;
	}

	/** Fan duty from the cabinet temperature: idle until it warms, flat out by the derating onset. */
	private static double fanDuty(double cabinetTempC, InverterSpec spec) {
		double from = spec.derateOnsetC() - 20.0;
		double to = spec.derateOnsetC();
		return Mth.clamp((cabinetTempC - from) / Math.max(1.0, to - from), 0.0, 1.0);
	}

	/**
	 * A first-order lag towards a target, seeded on the first call.
	 *
	 * The seed matters: without it every temperature would ramp up from absolute zero the first time a
	 * chunk loaded, and a program watching for a cold cabinet would see one that was never there.
	 */
	private double lag(String key, double target, double seed) {
		double current = thermal.getOrDefault(key, seed);
		double next = current + (target - current) * THERMAL_LAG;
		thermal.put(key, next);
		return next;
	}

	// ---- an array ----

	/** Everything one array publishes. Static, because nothing here has to be remembered between ticks. */
	public record ArraySample(
			PvArraySpec spec,
			double poaBeam,
			double poaDiffuse,
			double poaGround,
			double poaRear,
			double effectiveIrradiance,
			double incidenceDeg,
			double moduleTempC,
			double ambientTempC,
			double windSpeed,
			double spectralFactor,
			double soiling,
			double snowDepthM,
			double rowShadedFraction,
			double obstructionFraction,
			double skyViewFactor,
			double stringVoltage,
			double stringCurrent,
			double availableDcKw,
			double deliveredDcKw,
			double performanceRatio,
			double rotationDeg,
			double targetRotationDeg,
			double tiltDeg,
			double planeAzimuthDeg,
			String trackerMode,
			String stowReason,
			boolean slewing,
			boolean backtracking,
			boolean inverterConnected,
			double trackerMotorKw
	) {
	}

	public static Telemetry.Snapshot sampleArray(ArraySample s) {
		PvArraySpec spec = s.spec();
		Telemetry.Builder out = Telemetry.builder();

		out.put(SolarTelemetry.POA_IRRADIANCE, s.poaBeam() + s.poaDiffuse() + s.poaGround());
		out.put(SolarTelemetry.POA_BEAM, s.poaBeam());
		out.put(SolarTelemetry.POA_DIFFUSE, s.poaDiffuse());
		out.put(SolarTelemetry.POA_GROUND, s.poaGround());
		out.put(SolarTelemetry.POA_REAR, s.poaRear());
		out.put(SolarTelemetry.EFFECTIVE_IRRADIANCE, s.effectiveIrradiance());
		out.put(SolarTelemetry.MODULE_TEMP, s.moduleTempC());
		out.put(SolarTelemetry.AMBIENT_TEMP, s.ambientTempC());
		out.put(SolarTelemetry.WIND_SPEED, s.windSpeed());
		out.put(SolarTelemetry.SOILING, s.soiling());
		out.put(SolarTelemetry.SNOW_DEPTH, s.snowDepthM());
		out.put(SolarTelemetry.ROW_SHADING, s.rowShadedFraction());
		out.put(SolarTelemetry.OBSTRUCTION, s.obstructionFraction());
		out.put(SolarTelemetry.SKY_VIEW, s.skyViewFactor());
		out.put(SolarTelemetry.AVAILABLE_DC_POWER, s.availableDcKw());
		out.put(SolarTelemetry.DELIVERED_DC_POWER, s.deliveredDcKw());
		out.put(SolarTelemetry.TRACKER_ANGLE, s.rotationDeg());
		out.put(SolarTelemetry.TRACKER_TARGET, s.targetRotationDeg());
		out.put(SolarTelemetry.TRACKER_MODE, s.trackerMode());
		out.put(SolarTelemetry.TRACKER_STOW, s.stowReason());
		out.put(SolarTelemetry.TRACKER_SLEWING, s.slewing());
		out.put(SolarTelemetry.BACKTRACKING, s.backtracking());
		out.put(SolarTelemetry.INVERTER_CONNECTED, s.inverterConnected());

		out.put(SolarTelemetry.INCIDENCE_ANGLE, s.incidenceDeg());
		out.put(SolarTelemetry.TILT_ANGLE, s.tiltDeg());
		out.put(SolarTelemetry.PLANE_AZIMUTH, s.planeAzimuthDeg());
		out.put(SolarTelemetry.STRING_VOLTAGE, s.stringVoltage());
		out.put(SolarTelemetry.STRING_CURRENT, s.stringCurrent());
		out.put(SolarTelemetry.ARRAY_CURRENT, s.stringCurrent() * spec.strings());
		out.put(SolarTelemetry.PERFORMANCE_RATIO, s.performanceRatio());
		out.put(SolarTelemetry.SPECTRAL_FACTOR, s.spectralFactor());
		out.put(SolarTelemetry.MODULE_COUNT, spec.moduleCount());
		out.put(SolarTelemetry.DC_NAMEPLATE, spec.dcPowerKw());
		out.put(SolarTelemetry.GROUND_COVER_RATIO, spec.groundCoverRatio());

		out.put(SolarTelemetry.TRACKER_MOTOR_POWER, s.trackerMotorKw());
		// the drive gearbox warms while it is working and cools when it is not, which on a tracker is
		// most of the day: a row that slews for a few seconds every few minutes barely gets warm
		out.put(SolarTelemetry.TRACKER_DRIVE_TEMP, s.ambientTempC() + (s.slewing() ? 12.0 : 2.0));

		return out.build();
	}

	// ---- the mast ----

	public record StationSample(
			double globalIrradiance,
			double diffuseIrradiance,
			double planeIrradiance,
			double referenceCell,
			double albedo,
			double ambientTempC,
			double moduleTempC,
			double windSpeed,
			double windDirection,
			double snowDistanceM,
			double snowHeightM,
			double diffuseFraction,
			double clearnessIndex,
			double sunElevationDeg,
			double sunAzimuthDeg
	) {
	}

	public static Telemetry.Snapshot sampleStation(StationSample s) {
		return Telemetry.builder()
				.put(SolarTelemetry.GLOBAL_IRRADIATION, s.globalIrradiance())
				.put(SolarTelemetry.DIFFUSED_IRRADIATION, s.diffuseIrradiance())
				.put(SolarTelemetry.IRRADIATION, s.planeIrradiance())
				.put(SolarTelemetry.REFERENCE_CELL, s.referenceCell())
				.put(SolarTelemetry.ALBEDO, s.albedo())
				.put(SolarTelemetry.AMBIENT_TEMP, s.ambientTempC())
				.put(SolarTelemetry.MODULE_TEMP, s.moduleTempC())
				.put(SolarTelemetry.WIND_SPEED, s.windSpeed())
				.put(SolarTelemetry.WIND_DIR, s.windDirection())
				.put(SolarTelemetry.SNOW_DISTANCE, s.snowDistanceM())
				.put(SolarTelemetry.SNOW_HEIGHT, s.snowHeightM())
				.put(SolarTelemetry.DIFFUSE_FRACTION, s.diffuseFraction())
				.put(SolarTelemetry.CLEARNESS_INDEX, s.clearnessIndex())
				.put(SolarTelemetry.SUN_ELEVATION, s.sunElevationDeg())
				.put(SolarTelemetry.SUN_AZIMUTH, s.sunAzimuthDeg())
				.build();
	}
}
