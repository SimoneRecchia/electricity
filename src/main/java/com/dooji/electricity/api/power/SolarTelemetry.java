package com.dooji.electricity.api.power;

import java.util.Map;

/**
 * The tags a photovoltaic plant's SCADA publishes, in three sets.
  *
 * The snapshot machinery, the builder and the three kinds are in {@link Telemetry}.
 */
public final class SolarTelemetry {

	// ---- the inverter: measured ----

	/** Active power at the terminals, kW. */
	public static final String ACTIVE_POWER = "activePower";
	/** Direct-current power arriving from the arrays, kW. */
	public static final String PV_ACTIVE_POWER = "pvActivePower";
	/** What the arrays could deliver at their maximum power point, kW. */
	public static final String AVAILABLE_DC_POWER = "availableDcPower";
	/** Lifetime generation, kWh. */
	public static final String ACTIVE_ENERGY = "activeEnergy";
	/** Generation since the day rolled over, kWh. */
	public static final String ACTIVE_ENERGY_TODAY = "activeEnergyToday";
	public static final String AMBIENT_TEMP = "ambientTemp";
	/** Cabinet temperature, which is ambient plus the losses and is what the fan is holding down. */
	public static final String CABINET_TEMP = "cabinetTemp";
	/** Back-of-module temperature from the reference array. */
	public static final String MODULE_TEMP = "moduleTemp";
	/** Irradiance in the plane of the reference array, W/m2. */
	public static final String IRRADIATION = "irradiation";
	public static final String ACTIVE_POWER_LIMIT = "activePowerLimit";
	public static final String POWER_LIMITATION_ACTIVE = "powerLimitationActive";
	public static final String RUNNING = "running";
	/** The array is offering more than the nameplate can pass. */
	public static final String CLIPPING = "clipping";
	/** The air is hot enough that the machine is holding itself back. */
	public static final String DERATING = "derating";
	public static final String STOPPED_BY_COMPUTER = "stoppedByComputer";
	public static final String STOPPED_BY_PLAYER = "stoppedByPlayer";
	public static final String STOPPED_BY_REDSTONE = "stoppedByRedstone";
	public static final String ARRAYS_CONNECTED = "arraysConnected";
	public static final String STRINGS_CONNECTED = "stringsConnected";
	/** Combiner boxes wired to the cabinet on trunk cable. */
	public static final String COMBINERS_CONNECTED = "combinersConnected";
	/** String terminals the machine has. */
	public static final String STRING_CAPACITY = "stringCapacity";
	/** Direct-current input the machine has left, in amps. */
	public static final String DC_CURRENT_HEADROOM = "dcCurrentHeadroom";

	// ---- the inverter: derived ----

	public static final String APPARENT_POWER = "apparentPower";
	public static final String REACTIVE_POWER = "reactivePower";
	public static final String POWER_FACTOR = "pf";
	public static final String FREQUENCY = "f";
	public static final String GRID_VOLTAGE = "gridVoltage";
	public static final String GRID_CURRENT = "gridCurrent";
	public static final String V1 = "v1";
	public static final String V2 = "v2";
	public static final String V3 = "v3";
	public static final String V12 = "v12";
	public static final String V23 = "v23";
	public static final String V31 = "v31";
	public static final String I1 = "i1";
	public static final String I2 = "i2";
	public static final String I3 = "i3";
	public static final String DC_VOLTAGE = "dcVoltage";
	public static final String DC_CURRENT = "dcCurrent";
	public static final String EFFICIENCY = "efficiency";
	public static final String PERFORMANCE_RATIO = "performanceRatio";
	/** DC nameplate of everything wired in over the AC nameplate: this plant's own loading ratio. */
	public static final String DC_AC_RATIO = "dcAcRatio";

	// ---- the inverter: simulated ----

	public static final String HEAT_SINK_TEMP = "heatSinkTemp";
	public static final String INTERNAL_AIR_TEMP = "internalAirTemp";
	/** Insulation resistance of the array to earth, in kilohms. */
	public static final String INSULATION_RESISTANCE = "insulationResistance";
	public static final String DC_BUS_VOLTAGE = "dcBusVoltage";
	/** Fan duty */
	public static final String FAN_SPEED = "fanSpeed";

	// ---- an array: measured ----

	/** Everything arriving on the front of the plane, W/m2. */
	public static final String POA_IRRADIANCE = "poaIrradiance";
	public static final String POA_BEAM = "poaBeam";
	public static final String POA_DIFFUSE = "poaDiffuse";
	public static final String POA_GROUND = "poaGround";
	public static final String POA_REAR = "poaRear";
	/** Front plus whatever the back is worth after bifaciality: what the modules answer to. */
	public static final String EFFECTIVE_IRRADIANCE = "effectiveIrradiance";
	public static final String WIND_SPEED = "windSpeed";
	/** Fraction of output lost to dust */
	public static final String SOILING = "soiling";
	/** Snow lying on the modules, in metres. */
	public static final String SNOW_DEPTH = "snowDepth";
	/** Fraction of the rows the row in front is shading */
	public static final String ROW_SHADING = "rowShading";
	/** Fraction of the beam surviving whatever is built, grown or standing over the array. */
	public static final String OBSTRUCTION = "obstruction";
	/** Fraction of the sky dome the array can see */
	public static final String SKY_VIEW = "skyView";
	public static final String DELIVERED_DC_POWER = "deliveredDcPower";
	public static final String TRACKER_ANGLE = "trackerAngle";
	public static final String TRACKER_TARGET = "trackerTarget";
	public static final String TRACKER_MODE = "trackerMode";
	/**
	 * Why the row is stowed, or NONE.
	  *
	 * NIGHT, WIND, SNOW, DIFFUSE and COMMANDED all mean different things.
	 */
	public static final String TRACKER_STOW = "trackerStow";
	public static final String TRACKER_SLEWING = "trackerSlewing";
	public static final String BACKTRACKING = "backtracking";
	public static final String WIRED = "wired";
	/** Whether the strings have leads on them. */
	public static final String HARNESSED = "harnessed";
	/** Length of the run from this array to whatever collects it, in metres. */
	public static final String DC_RUN_LENGTH = "dcRunLength";
	/** How much of that run is in the ground */
	public static final String DC_RUN_BURIED = "dcRunBuried";
	/** What the run burns, as a fraction of what goes down it. */
	public static final String DC_CABLE_LOSS = "dcCableLoss";

	// ---- an array: derived ----

	public static final String INCIDENCE_ANGLE = "incidenceAngle";
	public static final String TILT_ANGLE = "tiltAngle";
	public static final String PLANE_AZIMUTH = "planeAzimuth";
	public static final String STRING_VOLTAGE = "stringVoltage";
	public static final String STRING_CURRENT = "stringCurrent";
	public static final String ARRAY_CURRENT = "arrayCurrent";
	public static final String SPECTRAL_FACTOR = "spectralFactor";
	public static final String MODULE_COUNT = "moduleCount";
	public static final String DC_NAMEPLATE = "dcNameplate";
	public static final String GROUND_COVER_RATIO = "groundCoverRatio";

	// ---- an array: simulated ----

	public static final String TRACKER_MOTOR_POWER = "trackerMotorPower";
	public static final String TRACKER_DRIVE_TEMP = "trackerDriveTemp";

	// ---- the mast: measured ----

	public static final String GLOBAL_IRRADIATION = "globalIrradiation";
	public static final String DIFFUSED_IRRADIATION = "diffusedIrradiation";
	/** What a reference cell reads: the plane irradiance as a silicon cell sees it, spectrum included. */
	public static final String REFERENCE_CELL = "referenceCell";
	public static final String ALBEDO = "albedo";
	public static final String WIND_DIR = "windDir";
	/** What the gauge measures: distance to the surface below it, in metres. */
	public static final String SNOW_DISTANCE = "snowDistance";
	/** What is worked out from it: depth of snow, in metres. */
	public static final String SNOW_HEIGHT = "snowHeight";

	// ---- the mast: derived ----

	public static final String DIFFUSE_FRACTION = "diffuseFraction";
	public static final String CLEARNESS_INDEX = "clearnessIndex";
	public static final String SUN_ELEVATION = "sunElevation";
	public static final String SUN_AZIMUTH = "sunAzimuth";

	private static final Map<String, Telemetry.Kind> INVERTER = Telemetry.tags()
			.measured(ACTIVE_POWER, PV_ACTIVE_POWER, AVAILABLE_DC_POWER, ACTIVE_ENERGY, ACTIVE_ENERGY_TODAY, AMBIENT_TEMP, CABINET_TEMP, MODULE_TEMP,
					IRRADIATION, ACTIVE_POWER_LIMIT, POWER_LIMITATION_ACTIVE, RUNNING, CLIPPING, DERATING, STOPPED_BY_COMPUTER, STOPPED_BY_PLAYER,
					STOPPED_BY_REDSTONE, ARRAYS_CONNECTED, STRINGS_CONNECTED, COMBINERS_CONNECTED, STRING_CAPACITY, DC_CURRENT_HEADROOM)
			.derived(APPARENT_POWER, REACTIVE_POWER, POWER_FACTOR, FREQUENCY, GRID_VOLTAGE, GRID_CURRENT, V1, V2, V3, V12, V23, V31, I1, I2, I3,
					DC_VOLTAGE, DC_CURRENT, EFFICIENCY, PERFORMANCE_RATIO, DC_AC_RATIO)
			.simulated(HEAT_SINK_TEMP, INTERNAL_AIR_TEMP, INSULATION_RESISTANCE, DC_BUS_VOLTAGE, FAN_SPEED)
			.build();

	private static final Map<String, Telemetry.Kind> ARRAY = Telemetry.tags()
			.measured(POA_IRRADIANCE, POA_BEAM, POA_DIFFUSE, POA_GROUND, POA_REAR, EFFECTIVE_IRRADIANCE, MODULE_TEMP, AMBIENT_TEMP, WIND_SPEED,
					SOILING, SNOW_DEPTH, ROW_SHADING, OBSTRUCTION, SKY_VIEW, AVAILABLE_DC_POWER, DELIVERED_DC_POWER, TRACKER_ANGLE, TRACKER_TARGET,
					TRACKER_MODE, TRACKER_STOW, TRACKER_SLEWING, BACKTRACKING, WIRED, HARNESSED)
			.derived(INCIDENCE_ANGLE, TILT_ANGLE, PLANE_AZIMUTH, STRING_VOLTAGE, STRING_CURRENT, ARRAY_CURRENT, PERFORMANCE_RATIO, SPECTRAL_FACTOR,
					MODULE_COUNT, DC_NAMEPLATE, GROUND_COVER_RATIO, DC_RUN_LENGTH, DC_RUN_BURIED, DC_CABLE_LOSS)
			.simulated(TRACKER_MOTOR_POWER, TRACKER_DRIVE_TEMP)
			.build();

	private static final Map<String, Telemetry.Kind> STATION = Telemetry.tags()
			.measured(GLOBAL_IRRADIATION, DIFFUSED_IRRADIATION, IRRADIATION, REFERENCE_CELL, ALBEDO, AMBIENT_TEMP, MODULE_TEMP, WIND_SPEED, WIND_DIR,
					SNOW_DISTANCE, SNOW_HEIGHT)
			.derived(DIFFUSE_FRACTION, CLEARNESS_INDEX, SUN_ELEVATION, SUN_AZIMUTH)
			.build();

	private static final Map<String, Telemetry.Kind> ALL = Telemetry.merge(Telemetry.merge(INVERTER, ARRAY), STATION);

	private SolarTelemetry() {
	}

	/** What an inverter publishes: the plant as the grid and the control room see it. */
	public static Map<String, Telemetry.Kind> inverterKinds() {
		return INVERTER;
	}

	/** What one array publishes: the optics and the mechanism of its own patch of glass. */
	public static Map<String, Telemetry.Kind> arrayKinds() {
		return ARRAY;
	}

	/** What the mast publishes: the sky, once, for the whole site. */
	public static Map<String, Telemetry.Kind> stationKinds() {
		return STATION;
	}

	/**
	 * @return whether {@code tag} is measured, derived or simulated, or null when the tag is not one this mod reports.
	 */
	public static Telemetry.Kind kind(String tag) {
		return ALL.get(tag);
	}
}
