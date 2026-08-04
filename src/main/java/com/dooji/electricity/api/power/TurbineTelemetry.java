package com.dooji.electricity.api.power;

import java.util.Map;

/**
 * The tags a wind turbine's SCADA publishes, and what kind of number each one is.
 *
 * Names only: the snapshot, the builder and the three kinds live in {@link Telemetry}, because the
 * photovoltaic plant needed exactly the same machinery and a second copy of it would have been a
 * second place for the rounding and the boolean coercion to drift.
 *
 * What is here is what is specific to a turbine - about fifty signal names taken from what a real
 * machine's controller publishes, sorted into the three kinds so a program can tell an instrument
 * reading from an invented one at runtime.
 */
public final class TurbineTelemetry {
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

	private static final Map<String, Telemetry.Kind> KINDS = Telemetry.tags()
			.measured(WIND_SPEED, WIND_DIR, NACELLE_DIR, ROTOR_RPM, ACTIVE_POWER, ACTIVE_POWER_LIMIT, POWER_LIMITATION_ACTIVE, AMBIENT_TEMP, TURBULENCE,
					YAW_CABLE_TWIST, RUNNING, WIND_CUT_OUT, STOPPED_BY_COMPUTER, STOPPED_BY_PLAYER, STOPPED_BY_REDSTONE)
			.derived(GENERATOR_RPM, APPARENT_POWER, REACTIVE_POWER, POWER_FACTOR, FREQUENCY, V12, V23, V31, I1, I2, I3, BLADE_PITCH_ANGLE,
					BLADE_PITCH_ANGLE_1, BLADE_PITCH_ANGLE_2, BLADE_PITCH_ANGLE_3, AIR_PRESSURE)
			.simulated(GEARBOX_OIL_TEMP, GEARBOX_OIL_TEMP_SUMP, GEARBOX_OIL_PRESS, GEARBOX_OIL_PRESS_PUMP, GEAR_BEAR_TEMP_GEN, GEAR_BEAR_TEMP_ROT,
					GEN_BEAR_TEMP_BS, GEN_BEAR_TEMP_D_END, GEN_CW_TEMP_INLET, GEN_CW_TEMP_OUTLET, GENERATOR_L1_TEMP, GENERATOR_L2_TEMP, GENERATOR_L3_TEMP,
					MAIN_BEAR_TEMP, NACELLE_TEMP, HYD_OIL_TEMP, HYDR_SYSTEM_PRESS, HYDR_MAIN_BRAKES_PRESS, YAW_H_ACCU_PRESS, YAW_HYDR_BRK_PRESS,
					AIR_TEMP_CTRL_CAB, AIR_TEMP_PWR_CAB_CTRL_FLD, AIR_TEMP_PWR_CAB_PWR_FLD, AIR_TEMP_TOWER_BOTT, MV_TRAFO_TEMP_AREA_COIL,
					PITCH_1_MOTOR_TEMP, PITCH_2_MOTOR_TEMP, PITCH_3_MOTOR_TEMP, PITCH_1_BOX_TEMP, PITCH_2_BOX_TEMP, PITCH_3_BOX_TEMP, VIB_Y, VIB_Z)
			.build();

	private TurbineTelemetry() {
	}

	/**
	 * @return whether {@code tag} is measured, derived or simulated, or null when the tag is not one
	 *         this mod reports.
	 */
	public static Telemetry.Kind kind(String tag) {
		return KINDS.get(tag);
	}

	public static Map<String, Telemetry.Kind> kinds() {
		return KINDS;
	}
}
