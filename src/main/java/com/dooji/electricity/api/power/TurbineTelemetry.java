package com.dooji.electricity.api.power;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An immutable snapshot of everything a wind turbine can report about itself,
 * keyed by tag name the way a real turbine's SCADA system presents its signals.
 *
 * The snapshot exists because readers arrive off the server thread. A
 * ComputerCraft program calls into the peripheral from the computer thread, so
 * reading the block entity's live fields would be a data race and could hand out
 * a mix of values from two different ticks. The turbine instead publishes one
 * finished snapshot per tick to a volatile field; a reader gets a single
 * self-consistent view with no locking and no hop to the server thread.
 *
 * Three kinds of value live in here, and the difference matters to anyone
 * building control logic on top:
 *
 * MEASURED  - read straight from mod state or the world: wind, yaw, rotor speed,
 *             generated power, ambient temperature.
 * DERIVED   - computed from measured values by a fixed relation that would hold
 *             on a real machine: apparent and reactive power, phase currents,
 *             generator speed through the gearbox ratio, blade pitch implied by
 *             the mod's own power curve.
 * SIMULATED - plausible instrumentation the mod does not model, produced by
 *             {@code TurbineTelemetrySimulator} with realistic thermal lag:
 *             oil and bearing temperatures, hydraulic pressures, vibration.
 *             These react correctly to load and ambient conditions, but they are
 *             not a physical simulation and must not be read as one.
 *
 * {@link #kind(String)} reports which category a tag falls into, so a program can
 * tell instrument readings from invented ones at runtime.
 */
public final class TurbineTelemetry {
	public enum Kind {
		MEASURED,
		DERIVED,
		SIMULATED
	}

	// measured
	public static final String WIND_SPEED = "windSpeed";
	public static final String WIND_DIR = "windDir";
	public static final String NACELLE_DIR = "nacelleDir";
	public static final String ROTOR_RPM = "rotorRpm";
	public static final String ACTIVE_POWER = "activePower";
	public static final String ACTIVE_POWER_LIMIT = "activePowerLimit";
	public static final String POWER_LIMITATION_ACTIVE = "powerLimitationActive";
	public static final String AMBIENT_TEMP = "ambientTemp";
	public static final String TURBULENCE = "turbulence";
	public static final String YAW_CABLE_TWIST = "yawCableTwist";
	public static final String RUNNING = "running";
	public static final String WIND_CUT_OUT = "windCutOut";
	public static final String STOPPED_BY_COMPUTER = "stoppedByComputer";
	/** A player applied the brake from the machine's own control panel. */
	public static final String STOPPED_BY_PLAYER = "stoppedByPlayer";
	public static final String STOPPED_BY_REDSTONE = "stoppedByRedstone";

	// derived
	public static final String GENERATOR_RPM = "generatorRpm";
	public static final String APPARENT_POWER = "apparentPower";
	public static final String REACTIVE_POWER = "reactivePower";
	public static final String POWER_FACTOR = "pf";
	public static final String FREQUENCY = "f";
	public static final String V12 = "v12";
	public static final String V23 = "v23";
	public static final String V31 = "v31";
	public static final String I1 = "i1";
	public static final String I2 = "i2";
	public static final String I3 = "i3";
	public static final String BLADE_PITCH_ANGLE = "bladePitchAngle";
	public static final String BLADE_PITCH_ANGLE_1 = "bladePitchAngle1";
	public static final String BLADE_PITCH_ANGLE_2 = "bladePitchAngle2";
	public static final String BLADE_PITCH_ANGLE_3 = "bladePitchAngle3";
	public static final String AIR_PRESSURE = "airPressure";

	// simulated
	public static final String GEARBOX_OIL_TEMP = "gearBoxOilTemp";
	public static final String GEARBOX_OIL_TEMP_SUMP = "gearBoxOilTempSump";
	public static final String GEARBOX_OIL_PRESS = "gearBoxOilPress";
	public static final String GEARBOX_OIL_PRESS_PUMP = "gearBoxOilPressPmp";
	public static final String GEAR_BEAR_TEMP_GEN = "gearBearTemp1Gen";
	public static final String GEAR_BEAR_TEMP_ROT = "gearBearTemp2Rot";
	public static final String GEN_BEAR_TEMP_BS = "genBearTempBS";
	public static final String GEN_BEAR_TEMP_D_END = "genBearTempDEnd";
	public static final String GEN_CW_TEMP_INLET = "genCWTempGenInlt";
	public static final String GEN_CW_TEMP_OUTLET = "genCWTempGenOutlt";
	public static final String GENERATOR_L1_TEMP = "generatorL1Temp";
	public static final String GENERATOR_L2_TEMP = "generatorL2Temp";
	public static final String GENERATOR_L3_TEMP = "generatorL3Temp";
	public static final String MAIN_BEAR_TEMP = "mainBearTemp1";
	public static final String NACELLE_TEMP = "nacelleTemp";
	public static final String HYD_OIL_TEMP = "hydOilTemp";
	public static final String HYDR_SYSTEM_PRESS = "hydrSystemPress";
	public static final String HYDR_MAIN_BRAKES_PRESS = "hydrMainBrakesPressure";
	public static final String YAW_H_ACCU_PRESS = "yawHAccuPress";
	public static final String YAW_HYDR_BRK_PRESS = "yawHydrBrkPress";
	public static final String AIR_TEMP_CTRL_CAB = "airTempCtrlCab";
	public static final String AIR_TEMP_PWR_CAB_CTRL_FLD = "airTempPwrCabCtrlFld";
	public static final String AIR_TEMP_PWR_CAB_PWR_FLD = "airTempPwrCabPwrFld";
	public static final String AIR_TEMP_TOWER_BOTT = "airTempTowerBott";
	public static final String MV_TRAFO_TEMP_AREA_COIL = "mvTrafoTempAreaCoil";
	public static final String PITCH_1_MOTOR_TEMP = "pitch1MotorTemp";
	public static final String PITCH_2_MOTOR_TEMP = "pitch2MotorTemp";
	public static final String PITCH_3_MOTOR_TEMP = "pitch3MotorTemp";
	public static final String PITCH_1_BOX_TEMP = "pitch1BoxTemp";
	public static final String PITCH_2_BOX_TEMP = "pitch2BoxTemp";
	public static final String PITCH_3_BOX_TEMP = "pitch3BoxTemp";
	public static final String VIB_Y = "vibYDirection";
	public static final String VIB_Z = "vibZDirection";

	private static final Map<String, Kind> KINDS = buildKinds();
	public static final TurbineTelemetry EMPTY = new TurbineTelemetry(new LinkedHashMap<>());

	private final Map<String, Object> values;

	private TurbineTelemetry(Map<String, Object> values) {
		this.values = Collections.unmodifiableMap(values);
	}

	public static Builder builder() {
		return new Builder();
	}

	public Map<String, Object> values() {
		return values;
	}

	public Object get(String tag) {
		return values.get(tag);
	}

	public double number(String tag) {
		Object value = values.get(tag);
		if (value instanceof Number number) return number.doubleValue();
		if (value instanceof Boolean flag) return flag ? 1.0 : 0.0;

		return 0.0;
	}

	public boolean flag(String tag) {
		Object value = values.get(tag);
		if (value instanceof Boolean bool) return bool;
		if (value instanceof Number number) return number.doubleValue() != 0.0;

		return false;
	}

	/**
	 * @return whether {@code tag} is measured, derived or simulated, or null when
	 *         the tag is not one this mod reports.
	 */
	public static Kind kind(String tag) {
		return KINDS.get(tag);
	}

	public static Map<String, Kind> kinds() {
		return KINDS;
	}

	private static Map<String, Kind> buildKinds() {
		Map<String, Kind> kinds = new LinkedHashMap<>();
		for (String tag : new String[]{WIND_SPEED, WIND_DIR, NACELLE_DIR, ROTOR_RPM, ACTIVE_POWER, ACTIVE_POWER_LIMIT, POWER_LIMITATION_ACTIVE, AMBIENT_TEMP, TURBULENCE, YAW_CABLE_TWIST,
				RUNNING, WIND_CUT_OUT, STOPPED_BY_COMPUTER, STOPPED_BY_PLAYER, STOPPED_BY_REDSTONE}) {
			kinds.put(tag, Kind.MEASURED);
		}

		for (String tag : new String[]{GENERATOR_RPM, APPARENT_POWER, REACTIVE_POWER, POWER_FACTOR, FREQUENCY, V12, V23, V31, I1, I2, I3, BLADE_PITCH_ANGLE, BLADE_PITCH_ANGLE_1,
				BLADE_PITCH_ANGLE_2, BLADE_PITCH_ANGLE_3, AIR_PRESSURE}) {
			kinds.put(tag, Kind.DERIVED);
		}

		for (String tag : new String[]{GEARBOX_OIL_TEMP, GEARBOX_OIL_TEMP_SUMP, GEARBOX_OIL_PRESS, GEARBOX_OIL_PRESS_PUMP, GEAR_BEAR_TEMP_GEN, GEAR_BEAR_TEMP_ROT, GEN_BEAR_TEMP_BS,
				GEN_BEAR_TEMP_D_END, GEN_CW_TEMP_INLET, GEN_CW_TEMP_OUTLET, GENERATOR_L1_TEMP, GENERATOR_L2_TEMP, GENERATOR_L3_TEMP, MAIN_BEAR_TEMP, NACELLE_TEMP, HYD_OIL_TEMP,
				HYDR_SYSTEM_PRESS, HYDR_MAIN_BRAKES_PRESS, YAW_H_ACCU_PRESS, YAW_HYDR_BRK_PRESS, AIR_TEMP_CTRL_CAB, AIR_TEMP_PWR_CAB_CTRL_FLD, AIR_TEMP_PWR_CAB_PWR_FLD,
				AIR_TEMP_TOWER_BOTT, MV_TRAFO_TEMP_AREA_COIL, PITCH_1_MOTOR_TEMP, PITCH_2_MOTOR_TEMP, PITCH_3_MOTOR_TEMP, PITCH_1_BOX_TEMP, PITCH_2_BOX_TEMP, PITCH_3_BOX_TEMP,
				VIB_Y, VIB_Z}) {
			kinds.put(tag, Kind.SIMULATED);
		}

		return Collections.unmodifiableMap(kinds);
	}

	public static final class Builder {
		private final Map<String, Object> values = new LinkedHashMap<>();

		private Builder() {
		}

		/**
		 * Stores a reading, rounded to two decimals. Instruments do not report
		 * fifteen significant digits, and an unrounded double turns into noise the
		 * moment a Lua program prints it.
		 */
		public Builder put(String tag, double value) {
			values.put(tag, Math.round(value * 100.0) / 100.0);
			return this;
		}

		public Builder put(String tag, boolean value) {
			values.put(tag, value);
			return this;
		}

		public TurbineTelemetry build() {
			return new TurbineTelemetry(values);
		}
	}
}
