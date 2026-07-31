package com.dooji.electricity.power;

import com.dooji.electricity.api.power.TurbineTelemetry;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Mth;

/**
 * Turns what the mod actually knows about a turbine into the signal set a real
 * turbine's controller would publish.
 *
 * Only the electrical and mechanical relations here are real physics; the
 * temperatures, pressures and vibration are invented instrumentation. They are
 * invented carefully though: every one of them tracks load and ambient
 * temperature through a first-order lag, so they ramp the way a thermal mass
 * ramps instead of snapping to a new value the instant the wind changes. A
 * control program written against these will behave the same way it would against
 * a real machine, which is the point.
 *
 * One simulator instance belongs to one turbine and keeps that turbine's thermal
 * state between ticks.
 */
public final class TurbineTelemetrySimulator {
	/** Line-to-line volts, the usual LV level for a machine this size. */
	private static final double NOMINAL_VOLTAGE = 690.0;
	private static final double NOMINAL_FREQUENCY = 50.0;
	private static final double SEA_LEVEL_PRESSURE = 1013.25;
	/** Reaches 63% of a step in roughly 25 seconds. Slow enough to read as thermal mass. */
	private static final double THERMAL_LAG = 0.002;
	/** Degrees of blade pitch at the top of the normal regulating range. */
	private static final double MAX_REGULATING_PITCH = 25.0;
	/** Degrees reached at the shutdown speed, on the way to full feather. */
	private static final double MAX_STORM_PITCH = 60.0;

	private final Map<String, Double> thermal = new HashMap<>();

	/**
	 * Everything the simulator needs for one tick. Passed as a value so the
	 * simulator never reaches back into the block entity and cannot observe a
	 * half-updated turbine.
	 */
	public record Sample(
			double activePowerKw,
			double ratedPowerKw,
			double activePowerLimitKw,
			boolean powerLimited,
			double windSpeed,
			double alignedWindSpeed,
			double windDirection,
			double nacelleDir,
			double turbulence,
			double rotorDegreesPerTick,
			/** Rotor is held stopped, whatever the reason: wind cut-out or a command. */
			boolean braked,
			boolean windCutOut,
			boolean stoppedByComputer,
			boolean stoppedByPlayer,
			boolean stoppedByRedstone,
			boolean yawing,
			double ambientTempC,
			int blockY,
			boolean raining,
			boolean thundering,
			long gameTime,
			int seed,
			double yawCableTwist,
			// The machine's own curve thresholds, passed in rather than read off a
			// constant: the catalogue now runs from a 10 kW rotor to a 4 MW one, and their
			// rated speeds differ by nearly 2 m/s, so a shared figure would report the
			// blades pitching out on one machine while another was still at fine pitch in
			// the same wind.
			double ratedSpeed,
			double stormOnsetSpeed,
			double cutOutSpeed,
			/** Step-up to synchronous speed, derived per model: a slow wide rotor needs a taller one. */
			double gearboxRatio
	) {
	}

	public TurbineTelemetry sample(Sample s) {
		double rated = s.ratedPowerKw() > 0.0 ? s.ratedPowerKw() : 1.0;
		double load = Mth.clamp(s.activePowerKw() / rated, 0.0, 1.0);
		double ambient = s.ambientTempC();
		boolean spinning = s.rotorDegreesPerTick() > 0.05;

		double rotorRpm = s.rotorDegreesPerTick() * 20.0 * 60.0 / 360.0;
		double pitch = bladePitch(s);
		double pitchActivity = s.braked() || pitch > 0.5 ? 1.0 : 0.15;

		TurbineTelemetry.Builder out = TurbineTelemetry.builder();

		// ---- measured ----
		out.put(TurbineTelemetry.WIND_SPEED, s.windSpeed());
		// both headings are normalised to a 0..360 compass. The mod's weather uses its
		// own wrapDegrees, which returns 0..360, while the nacelle yaw goes through
		// Minecraft's, which returns -180..180: exposed raw, the two would be on
		// different conventions and comparing them would give nonsense
		out.put(TurbineTelemetry.WIND_DIR, compass(s.windDirection()));
		out.put(TurbineTelemetry.NACELLE_DIR, compass(s.nacelleDir()));
		out.put(TurbineTelemetry.ROTOR_RPM, rotorRpm);
		out.put(TurbineTelemetry.ACTIVE_POWER, s.activePowerKw());
		out.put(TurbineTelemetry.ACTIVE_POWER_LIMIT, s.activePowerLimitKw());
		out.put(TurbineTelemetry.POWER_LIMITATION_ACTIVE, s.powerLimited());
		out.put(TurbineTelemetry.AMBIENT_TEMP, ambient);
		out.put(TurbineTelemetry.TURBULENCE, s.turbulence());
		out.put(TurbineTelemetry.YAW_CABLE_TWIST, s.yawCableTwist());
		// the reasons are kept apart from the fact: a program that shuts a turbine down
		// needs to tell its own command from the machine protecting itself in a gale
		out.put(TurbineTelemetry.RUNNING, !s.braked());
		out.put(TurbineTelemetry.WIND_CUT_OUT, s.windCutOut());
		out.put(TurbineTelemetry.STOPPED_BY_COMPUTER, s.stoppedByComputer());
		out.put(TurbineTelemetry.STOPPED_BY_PLAYER, s.stoppedByPlayer());
		out.put(TurbineTelemetry.STOPPED_BY_REDSTONE, s.stoppedByRedstone());

		// ---- derived ----
		out.put(TurbineTelemetry.GENERATOR_RPM, rotorRpm * s.gearboxRatio());

		// power factor improves as the machine loads up, which is how an induction
		// generator behaves; below a hair of output there is nothing to have a phase
		// angle relative to, so it reads zero rather than a misleading 0.9
		double powerFactor = s.activePowerKw() <= 0.01 ? 0.0 : 0.90 + 0.08 * load;
		double apparent = powerFactor <= 0.0 ? 0.0 : s.activePowerKw() / powerFactor;
		double reactive = Math.sqrt(Math.max(0.0, apparent * apparent - s.activePowerKw() * s.activePowerKw()));
		out.put(TurbineTelemetry.POWER_FACTOR, powerFactor);
		out.put(TurbineTelemetry.APPARENT_POWER, apparent);
		out.put(TurbineTelemetry.REACTIVE_POWER, reactive);
		out.put(TurbineTelemetry.FREQUENCY, NOMINAL_FREQUENCY + wobble(s, 397.0, 0.04));

		// terminal volts sag slightly under load, and the three phases are never
		// perfectly balanced. Voltage and frequency stay present with the machine
		// idle because they are the grid connection, not the generator output.
		double busVoltage = NOMINAL_VOLTAGE * (1.0 - 0.012 * load);
		double v12 = busVoltage + wobble(s, 211.0, 1.8);
		double v23 = busVoltage + wobble(s, 233.0, 1.8) - 0.9;
		double v31 = busVoltage + wobble(s, 257.0, 1.8) + 0.6;
		out.put(TurbineTelemetry.V12, v12);
		out.put(TurbineTelemetry.V23, v23);
		out.put(TurbineTelemetry.V31, v31);

		// the per-phase variation is a fraction of the current rather than an offset on
		// top of it. An additive term would survive at zero current and report amps
		// flowing while apparent power read exactly zero, which cannot both be true; it
		// also could not go negative, so no clamp is needed either.
		double lineCurrent = busVoltage <= 0.0 ? 0.0 : apparent * 1000.0 / (Math.sqrt(3.0) * busVoltage);
		out.put(TurbineTelemetry.I1, lineCurrent * (1.004 + wobble(s, 197.0, 0.002)));
		out.put(TurbineTelemetry.I2, lineCurrent * (0.994 + wobble(s, 223.0, 0.002)));
		out.put(TurbineTelemetry.I3, lineCurrent * (1.002 + wobble(s, 241.0, 0.002)));

		// the three blades never track the collective demand perfectly, but they cannot
		// leave the mechanical range either: clamped to fine pitch at one end and full
		// feather at the other, or the per-blade jitter would report a negative angle
		// while the collective sits at zero
		out.put(TurbineTelemetry.BLADE_PITCH_ANGLE, pitch);
		out.put(TurbineTelemetry.BLADE_PITCH_ANGLE_1, clampPitch(pitch + wobble(s, 89.0, 0.25)));
		out.put(TurbineTelemetry.BLADE_PITCH_ANGLE_2, clampPitch(pitch + wobble(s, 97.0, 0.25) - 0.1));
		out.put(TurbineTelemetry.BLADE_PITCH_ANGLE_3, clampPitch(pitch + wobble(s, 101.0, 0.25) + 0.15));

		// pressure falls with altitude, and with the weather. The wind term is the one
		// that matters for coherence: wind exists because of a pressure gradient, so a
		// gale reading 1013 hPa would be nonsense sitting next to a 22 m/s wind speed.
		// At the cut-out wind this lands near 990 hPa, which is a real storm low.
		double pressure = SEA_LEVEL_PRESSURE - (s.blockY() - 64) * 0.12 - s.windSpeed() * 0.9;
		if (s.thundering()) {
			pressure -= 8.0;
		} else if (s.raining()) {
			pressure -= 4.0;
		}
		out.put(TurbineTelemetry.AIR_PRESSURE, pressure + wobble(s, 1201.0, 0.6));

		// ---- simulated ----
		out.put(TurbineTelemetry.GEARBOX_OIL_TEMP, lag(TurbineTelemetry.GEARBOX_OIL_TEMP, ambient + 18.0 + 42.0 * load, ambient));
		out.put(TurbineTelemetry.GEARBOX_OIL_TEMP_SUMP, lag(TurbineTelemetry.GEARBOX_OIL_TEMP_SUMP, ambient + 12.0 + 34.0 * load, ambient));
		out.put(TurbineTelemetry.GEAR_BEAR_TEMP_GEN, lag(TurbineTelemetry.GEAR_BEAR_TEMP_GEN, ambient + 22.0 + 48.0 * load, ambient));
		out.put(TurbineTelemetry.GEAR_BEAR_TEMP_ROT, lag(TurbineTelemetry.GEAR_BEAR_TEMP_ROT, ambient + 20.0 + 44.0 * load, ambient));
		out.put(TurbineTelemetry.GEN_BEAR_TEMP_BS, lag(TurbineTelemetry.GEN_BEAR_TEMP_BS, ambient + 25.0 + 50.0 * load, ambient));
		out.put(TurbineTelemetry.GEN_BEAR_TEMP_D_END, lag(TurbineTelemetry.GEN_BEAR_TEMP_D_END, ambient + 24.0 + 47.0 * load, ambient));
		out.put(TurbineTelemetry.MAIN_BEAR_TEMP, lag(TurbineTelemetry.MAIN_BEAR_TEMP, ambient + 15.0 + 30.0 * load, ambient));

		// stator windings are the hottest thing in the nacelle, and they heat with the
		// square of current rather than with power. Current follows apparent power, so
		// taking the ratio there instead of from active power matters: at part load the
		// power factor is worse, so the machine carries more current than its kW
		// suggest and runs correspondingly hotter
		double ratedApparent = rated / 0.98;
		double copperLoss = Mth.clamp(apparent / ratedApparent, 0.0, 1.0);
		copperLoss *= copperLoss;
		out.put(TurbineTelemetry.GENERATOR_L1_TEMP, lag(TurbineTelemetry.GENERATOR_L1_TEMP, ambient + 30.0 + 78.0 * copperLoss, ambient));
		out.put(TurbineTelemetry.GENERATOR_L2_TEMP, lag(TurbineTelemetry.GENERATOR_L2_TEMP, ambient + 30.0 + 80.0 * copperLoss, ambient));
		out.put(TurbineTelemetry.GENERATOR_L3_TEMP, lag(TurbineTelemetry.GENERATOR_L3_TEMP, ambient + 30.0 + 76.0 * copperLoss, ambient));

		double coolantInlet = lag(TurbineTelemetry.GEN_CW_TEMP_INLET, ambient + 8.0 + 15.0 * load, ambient);
		out.put(TurbineTelemetry.GEN_CW_TEMP_INLET, coolantInlet);
		out.put(TurbineTelemetry.GEN_CW_TEMP_OUTLET, lag(TurbineTelemetry.GEN_CW_TEMP_OUTLET, coolantInlet + 6.0 + 14.0 * load, ambient));

		out.put(TurbineTelemetry.NACELLE_TEMP, lag(TurbineTelemetry.NACELLE_TEMP, ambient + 8.0 + 12.0 * load, ambient));
		out.put(TurbineTelemetry.HYD_OIL_TEMP, lag(TurbineTelemetry.HYD_OIL_TEMP, ambient + 10.0 + 20.0 * load, ambient));
		out.put(TurbineTelemetry.AIR_TEMP_CTRL_CAB, lag(TurbineTelemetry.AIR_TEMP_CTRL_CAB, ambient + 6.0 + 10.0 * load, ambient));
		out.put(TurbineTelemetry.AIR_TEMP_PWR_CAB_CTRL_FLD, lag(TurbineTelemetry.AIR_TEMP_PWR_CAB_CTRL_FLD, ambient + 9.0 + 16.0 * load, ambient));
		out.put(TurbineTelemetry.AIR_TEMP_PWR_CAB_PWR_FLD, lag(TurbineTelemetry.AIR_TEMP_PWR_CAB_PWR_FLD, ambient + 12.0 + 24.0 * load, ambient));
		out.put(TurbineTelemetry.AIR_TEMP_TOWER_BOTT, lag(TurbineTelemetry.AIR_TEMP_TOWER_BOTT, ambient + 3.0 + 4.0 * load, ambient));
		out.put(TurbineTelemetry.MV_TRAFO_TEMP_AREA_COIL, lag(TurbineTelemetry.MV_TRAFO_TEMP_AREA_COIL, ambient + 20.0 + 55.0 * copperLoss, ambient));

		out.put(TurbineTelemetry.PITCH_1_MOTOR_TEMP, lag(TurbineTelemetry.PITCH_1_MOTOR_TEMP, ambient + 5.0 + 9.0 * pitchActivity, ambient));
		out.put(TurbineTelemetry.PITCH_2_MOTOR_TEMP, lag(TurbineTelemetry.PITCH_2_MOTOR_TEMP, ambient + 5.0 + 8.0 * pitchActivity, ambient));
		out.put(TurbineTelemetry.PITCH_3_MOTOR_TEMP, lag(TurbineTelemetry.PITCH_3_MOTOR_TEMP, ambient + 5.0 + 9.5 * pitchActivity, ambient));
		out.put(TurbineTelemetry.PITCH_1_BOX_TEMP, lag(TurbineTelemetry.PITCH_1_BOX_TEMP, ambient + 4.0 + 6.0 * pitchActivity, ambient));
		out.put(TurbineTelemetry.PITCH_2_BOX_TEMP, lag(TurbineTelemetry.PITCH_2_BOX_TEMP, ambient + 4.0 + 5.5 * pitchActivity, ambient));
		out.put(TurbineTelemetry.PITCH_3_BOX_TEMP, lag(TurbineTelemetry.PITCH_3_BOX_TEMP, ambient + 4.0 + 6.5 * pitchActivity, ambient));

		// hydraulics respond in well under a tick, so these are not lagged
		out.put(TurbineTelemetry.GEARBOX_OIL_PRESS, spinning ? 2.0 + 1.5 * load + wobble(s, 73.0, 0.06) : 0.0);
		out.put(TurbineTelemetry.GEARBOX_OIL_PRESS_PUMP, spinning ? 4.5 + 2.0 * load + wobble(s, 79.0, 0.08) : 0.0);
		out.put(TurbineTelemetry.HYDR_SYSTEM_PRESS, 190.0 + 20.0 * load + wobble(s, 151.0, 1.2));
		// the main brake only goes on when the machine has shut itself down
		out.put(TurbineTelemetry.HYDR_MAIN_BRAKES_PRESS, s.braked() ? 178.0 + wobble(s, 167.0, 1.5) : 4.0 + wobble(s, 167.0, 0.3));
		out.put(TurbineTelemetry.YAW_H_ACCU_PRESS, 148.0 + 6.0 * load + wobble(s, 181.0, 1.0));
		out.put(TurbineTelemetry.YAW_HYDR_BRK_PRESS, s.yawing() ? 58.0 + wobble(s, 139.0, 1.4) : 12.0 + wobble(s, 139.0, 0.4));

		// turbulence shakes the machine harder than raw load does
		out.put(TurbineTelemetry.VIB_Y, 0.4 + 1.8 * load + 2.5 * s.turbulence() + wobble(s, 31.0, 0.12));
		out.put(TurbineTelemetry.VIB_Z, 0.3 + 1.5 * load + 2.0 * s.turbulence() + wobble(s, 37.0, 0.1));

		return out.build();
	}

	/**
	 * Blade pitch implied by the mod's own power curve. Below the cut-in speed and
	 * after a cut-out the blades are feathered; between cut-in and rated they sit at
	 * fine pitch to take everything the wind offers; above rated they pitch out,
	 * which is exactly the plateau the generation formula already produces by
	 * clamping wind speed to the rated value.
	 */
	private static double bladePitch(Sample s) {
		// feathered only on a real shutdown. Below the cut-in speed the rotor is still
		// freewheeling, because updateRotorSpeeds only drives it to a stop on cut-out,
		// and a feathered rotor does not turn: reporting 90 degrees there would
		// contradict the rotor speed sitting right next to it in the same snapshot
		if (s.braked()) return 90.0;

		double wind = s.alignedWindSpeed();
		if (wind <= s.ratedSpeed()) return 0.0;

		// between rated and the storm onset the blades pitch out just enough to hold the
		// plateau
		double regulatingSpan = s.stormOnsetSpeed() - s.ratedSpeed();
		if (wind < s.stormOnsetSpeed()) {
			if (regulatingSpan <= 0.0) return 0.0;

			return Mth.clamp((wind - s.ratedSpeed()) / regulatingSpan, 0.0, 1.0) * MAX_REGULATING_PITCH;
		}

		// past the onset they keep going, well past the regulating range: pitching out is
		// how storm control sheds the power, so the angle has to track the derating
		double stormSpan = s.cutOutSpeed() - s.stormOnsetSpeed();
		if (stormSpan <= 0.0) return MAX_STORM_PITCH;

		double into = Mth.clamp((wind - s.stormOnsetSpeed()) / stormSpan, 0.0, 1.0);
		return MAX_REGULATING_PITCH + into * (MAX_STORM_PITCH - MAX_REGULATING_PITCH);
	}

	/** Any heading onto a 0..360 compass, whichever wrapping convention it arrived with. */
	private static double compass(double degrees) {
		return ((degrees % 360.0) + 360.0) % 360.0;
	}

	/** Fine pitch to full feather is the whole mechanical travel; nothing reports outside it. */
	private static double clampPitch(double degrees) {
		return Mth.clamp(degrees, 0.0, 90.0);
	}

	/**
	 * First-order lag toward {@code target}. The first call seeds the reading at
	 * {@code seed} so a freshly loaded turbine warms up from ambient rather than
	 * reporting a hot gearbox the instant its chunk loads.
	 */
	private double lag(String tag, double target, double seed) {
		Double current = thermal.get(tag);
		if (current == null) {
			thermal.put(tag, seed);
			return seed;
		}

		double next = current + (target - current) * THERMAL_LAG;
		thermal.put(tag, next);
		return next;
	}

	/**
	 * Smooth, repeatable variation. Real instruments never sit perfectly still, but
	 * per-tick randomness would read as noise rather than drift, so this is a sine
	 * offset by the turbine's position: two turbines side by side do not report
	 * identical numbers.
	 */
	private static double wobble(Sample s, double periodTicks, double amplitude) {
		double phase = (s.gameTime() + s.seed()) * (2.0 * Math.PI / periodTicks);
		return Math.sin(phase) * amplitude;
	}
}
