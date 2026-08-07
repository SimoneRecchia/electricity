package com.dooji.electricity.compat.computercraft;

import com.dooji.electricity.api.power.Dispatchable;
import com.dooji.electricity.api.power.RedstoneMode;
import com.dooji.electricity.api.power.SolarTelemetry;
import com.dooji.electricity.api.power.Telemetry;
import com.dooji.electricity.api.power.TrackerMode;
import com.dooji.electricity.api.power.TrackerSpec;
import com.dooji.electricity.api.power.TurbineTelemetry;
import com.dooji.electricity.block.MetStationBlockEntity;
import com.dooji.electricity.block.PvArrayBlockEntity;
import com.dooji.electricity.block.PvInverterBlockEntity;
import com.dooji.electricity.block.TurbineTowerBlock;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import com.dooji.electricity.compat.energy.EnergyBridge;
import dan200.computercraft.api.ForgeComputerCraftAPI;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Every reference to a CC:Tweaked class in this mod lives here, reached only from {@link ComputerCraftBridge} after it has confirmed the mod is loaded.
 */
public final class CCTweakedPeripherals {
	private CCTweakedPeripherals() {
	}

	public static void register() {
		ForgeComputerCraftAPI.registerPeripheralProvider((level, pos, side) -> {
			WindTurbineBlockEntity turbine = turbineAt(level, pos);
			if (turbine != null) {
				return LazyOptional.of(() -> new WindTurbinePeripheral(turbine));
			}

			if (level.getBlockEntity(pos) instanceof PvInverterBlockEntity inverter) {
				return LazyOptional.of(() -> new PvInverterPeripheral(inverter));
			}

			if (level.getBlockEntity(pos) instanceof PvArrayBlockEntity array) {
				return LazyOptional.of(() -> new PvArrayPeripheral(array));
			}

			if (level.getBlockEntity(pos) instanceof MetStationBlockEntity station) {
				return LazyOptional.of(() -> new MetStationPeripheral(station));
			}

			return LazyOptional.empty();
		});
	}

	/** The turbine a modem at this position should see, or null. */
	@Nullable
	private static WindTurbineBlockEntity turbineAt(Level level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof WindTurbineBlockEntity turbine) return turbine;

		if (level.getBlockState(pos).getBlock() instanceof TurbineTowerBlock) {
			BlockPos machine = TurbineTowerBlock.findTurbineAbove(level, pos);
			if (machine != null && level.getBlockEntity(machine) instanceof WindTurbineBlockEntity turbine) return turbine;
		}

		return null;
	}

	/**
	 * One generated getter per telemetry tag, plus the two readings every machine here publishes.
	 *
	 * Public because CC:Tweaked finds an annotated method by reflecting over the concrete peripheral class,
	 * and a method it cannot reach is a method Lua cannot call.
	 */
	public abstract static class TagPeripheral implements IDynamicPeripheral {
		private final Map<String, Telemetry.Kind> kinds;
		private final String[] tags;
		private final String[] methodNames;

		private TagPeripheral(Map<String, Telemetry.Kind> kinds) {
			this.kinds = kinds;
			Set<String> reserved = annotatedNames();
			List<String> keptTags = new ArrayList<>();
			List<String> keptNames = new ArrayList<>();

			for (String tag : kinds.keySet()) {
				String name = "get" + Character.toUpperCase(tag.charAt(0)) + tag.substring(1);
				if (reserved.contains(name)) continue;

				keptTags.add(tag);
				keptNames.add(name);
			}

			this.tags = keptTags.toArray(String[]::new);
			this.methodNames = keptNames.toArray(String[]::new);
		}

		/**
		 * Every name this peripheral already answers to, so a tag cannot generate a second method of that name.
		 *
		 * Read off the class rather than from a list each peripheral kept by hand: a @LuaFunction added without
		 * remembering the list would have been a duplicate method name, which is not a mistake worth being able
		 * to make.
		 */
		private Set<String> annotatedNames() {
			Set<String> names = new HashSet<>();
			for (Method method : getClass().getMethods()) {
				LuaFunction annotation = method.getAnnotation(LuaFunction.class);
				if (annotation == null) continue;

				names.add(method.getName());
				names.addAll(Arrays.asList(annotation.value()));
			}

			return names;
		}

		/** The machine's latest published readings. */
		protected abstract Telemetry.Snapshot snapshot();

		/** What the machine is, for the error message when a tag has not been published yet. */
		protected abstract String describe();

		/** Every signal at once, as a table keyed by tag. */
		@LuaFunction
		public final Map<String, Object> getTelemetry() {
			return snapshot().values();
		}

		/**
		 * Which tags are instrument readings and which are invented, as a table of tag to "MEASURED",
		 * "DERIVED" or "SIMULATED".
		 */
		@LuaFunction
		public final Map<String, Object> getTelemetryKinds() {
			Map<String, Object> named = new LinkedHashMap<>();
			kinds.forEach((tag, kind) -> named.put(tag, kind.name()));
			return named;
		}

		@Override
		public final String[] getMethodNames() {
			return methodNames;
		}

		@Override
		public final MethodResult callMethod(IComputerAccess computer, ILuaContext context, int method, IArguments arguments) throws LuaException {
			if (method < 0 || method >= tags.length) {
				// unreachable through CC
				throw new LuaException("telemetry method index " + method + " out of range");
			}

			Object value = snapshot().get(tags[method]);
			if (value == null) {
				// the machine's chunk is loaded but it has not ticked yet.
				// looks like a missing method
				throw new LuaException("no telemetry for '" + tags[method] + "' yet, the " + describe() + " has not ticked");
			}

			return MethodResult.of(value);
		}
	}

	/**
	 * A machine a computer can dispatch, as Lua sees it.
	 *
	 * The sixteen names below were written twice, once against the turbine and once against the inverter, in
	 * bodies that differed only in which field they read. Seven of them are Mekanism's own generator names,
	 * kept so a program written for those works here.
	 */
	public abstract static class DispatchablePeripheral extends TagPeripheral {
		private final Dispatchable machine;

		private DispatchablePeripheral(Dispatchable machine, Map<String, Telemetry.Kind> kinds) {
			super(kinds);
			this.machine = machine;
		}

		@Override
		public final Object getTarget() {
			return machine;
		}

		// ---- names shared with Mekanism's generators ----

		/** Joules produced in the last tick. */
		@LuaFunction
		public final double getProductionRate() {
			return machine.getGrossJoulesPerTick();
		}

		/** Ceiling on what this generator will hand out per tick, in Joules. */
		@LuaFunction
		public final double getMaxOutput() {
			return machine.getMaxJoulesPerTick();
		}

		/** Joules still unclaimed in this tick's budget. */
		@LuaFunction
		public final double getEnergy() {
			return machine.getAvailableJoules();
		}

		@LuaFunction
		public final double getMaxEnergy() {
			return machine.getMaxJoulesPerTick();
		}

		/** Always zero: these machines are sources and accept nothing. */
		@LuaFunction
		public final double getEnergyNeeded() {
			return 0.0;
		}

		@LuaFunction
		public final double getEnergyFilledPercentage() {
			double max = machine.getMaxJoulesPerTick();
			return max <= 0.0 ? 0.0 : machine.getAvailableJoules() / max;
		}

		/**
		 * Present for parity with Mekanism's Wind Generator, which can be barred from generating in configured
		 * dimensions.
		 */
		@LuaFunction
		public final boolean isBlacklistedDimension() {
			return false;
		}

		// ---- control ----

		/** Stops the machine, and keeps it stopped until {@link #start()}. */
		@LuaFunction(mainThread = true)
		public final void stop() {
			machine.setStoppedByComputer(true);
		}

		/** Releases a stop issued by {@link #stop()}. */
		@LuaFunction(mainThread = true)
		public final void start() {
			machine.setStoppedByComputer(false);
		}

		/** Whether it is stopped specifically by a computer command. */
		@LuaFunction
		public final boolean isStopped() {
			return machine.isStoppedByComputer();
		}

		/** Whether it is running and allowed to generate, for any reason. */
		@LuaFunction
		public final boolean isRunning() {
			return machine.isRunning();
		}

		/** True when the current redstone mode and signal are holding it down. */
		@LuaFunction
		public final boolean isStoppedByRedstone() {
			return machine.isStoppedByRedstone();
		}

		/** "DISABLED", "HIGH" or "LOW". */
		@LuaFunction
		public final String getRedstoneMode() {
			return machine.getRedstoneMode().name();
		}

		/** Sets how it reacts to redstone. */
		@LuaFunction(mainThread = true)
		public final void setRedstoneMode(String mode) throws LuaException {
			machine.setRedstoneMode(parseRedstoneMode(mode));
		}

		/** Curtailment setpoint in kW. */
		@LuaFunction
		public final double getActivePowerLimit() {
			return machine.getActivePowerLimit();
		}

		/** Caps output at {@code limitKw}, clamped to what the machine can do. */
		@LuaFunction(mainThread = true)
		public final void setActivePowerLimit(double limitKw) throws LuaException {
			machine.setActivePowerLimit(requireFinite(limitKw, "active power limit"));
		}
	}

	/** The turbine as seen from Lua. */
	public static final class WindTurbinePeripheral extends DispatchablePeripheral {
		private final WindTurbineBlockEntity turbine;

		private WindTurbinePeripheral(WindTurbineBlockEntity turbine) {
			super(turbine, TurbineTelemetry.kinds());
			this.turbine = turbine;
		}

		@Override
		public String getType() {
			return "electricity_wind_turbine";
		}

		@Override
		public boolean equals(@Nullable IPeripheral other) {
			return other instanceof WindTurbinePeripheral peripheral && peripheral.turbine == turbine;
		}

		@Override
		protected Telemetry.Snapshot snapshot() {
			return turbine.getTelemetry();
		}

		@Override
		protected String describe() {
			return "turbine";
		}

		/** True when the machine stopped itself because the wind exceeded its cut-out speed. */
		@LuaFunction
		public final boolean isWindCutOut() {
			return turbine.isWindCutOut();
		}
	}

	/** The inverter as seen from Lua: the node a plant's control program actually talks to. */
	public static final class PvInverterPeripheral extends DispatchablePeripheral {
		private final PvInverterBlockEntity inverter;

		private PvInverterPeripheral(PvInverterBlockEntity inverter) {
			super(inverter, SolarTelemetry.inverterKinds());
			this.inverter = inverter;
		}

		@Override
		public String getType() {
			return "electricity_pv_inverter";
		}

		@Override
		public boolean equals(@Nullable IPeripheral other) {
			return other instanceof PvInverterPeripheral peripheral && peripheral.inverter == inverter;
		}

		@Override
		protected Telemetry.Snapshot snapshot() {
			return inverter.getTelemetry();
		}

		@Override
		protected String describe() {
			return "inverter";
		}

		/** AC nameplate in kW: what this machine can pass however much glass is in front of it. */
		@LuaFunction
		public final double getRatedPower() {
			return inverter.spec().acPowerKw();
		}

		/** Whether the arrays are offering more than the nameplate can pass. */
		@LuaFunction
		public final boolean isClipping() {
			return inverter.clipping();
		}

		/** Whether the air is hot enough that the machine is holding itself back. */
		@LuaFunction
		public final boolean isDerating() {
			return inverter.derating();
		}

		@LuaFunction
		public final double getPowerFactor() {
			return inverter.powerFactor();
		}

		/** Asks the machine to hold a power factor, clamped to what it can do. */
		@LuaFunction(mainThread = true)
		public final void setPowerFactor(double factor) throws LuaException {
			inverter.setPowerFactor(requireFinite(factor, "power factor"));
		}

		/** Where the arrays this inverter is wired to are, as a list of {x, y, z} tables. */
		@LuaFunction
		public final List<Map<String, Object>> getArrays() {
			List<Map<String, Object>> out = new ArrayList<>();
			for (BlockPos pos : inverter.arrayPositions()) {
				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("x", pos.getX());
				entry.put("y", pos.getY());
				entry.put("z", pos.getZ());
				out.add(entry);
			}

			return out;
		}

	}

	/** One array as seen from Lua. */
	public static final class PvArrayPeripheral extends TagPeripheral {
		private final PvArrayBlockEntity array;

		private PvArrayPeripheral(PvArrayBlockEntity array) {
			super(SolarTelemetry.arrayKinds());
			this.array = array;
		}

		@Override
		public String getType() {
			return "electricity_pv_array";
		}

		/** The name the placeholder answered to, kept so existing programs still find this block. */
		@Override
		public Set<String> getAdditionalTypes() {
			return Set.of("electricity_solar_panel");
		}

		@Override
		public boolean equals(@Nullable IPeripheral other) {
			return other instanceof PvArrayPeripheral peripheral && peripheral.array == array;
		}

		@Override
		public Object getTarget() {
			return array;
		}

		@Override
		protected Telemetry.Snapshot snapshot() {
			return array.getTelemetry();
		}

		@Override
		protected String describe() {
			return "array";
		}

		// ---- the placeholder's nine, kept working ----

		/** Joules delivered in the last tick, the unit and the name Mekanism's generators use. */
		@LuaFunction
		public final double getProductionRate() {
			return array.deliveredDcKw() * EnergyBridge.JOULES_PER_KW;
		}

		/** Whether the array has a clear enough view of the sun to be worth anything. */
		@LuaFunction
		public final boolean canSeeSun() {
			return array.obstructionFraction() > 0.5;
		}

		/** What the modules are delivering, in kW. */
		@LuaFunction
		public final double getActivePower() {
			return array.deliveredDcKw();
		}

		/** This product's DC nameplate at standard test conditions, in kW. */
		@LuaFunction
		public final double getRatedPower() {
			return array.spec().dcPowerKw();
		}

		/** Irradiance in the plane of the modules, W/m2. */
		@LuaFunction
		public final double getIrradiance() {
			return array.poaFront();
		}

		@LuaFunction
		public final double getCellTemperature() {
			return array.moduleTempC();
		}

		@LuaFunction
		public final double getAmbientTemperature() {
			return array.ambientTempC();
		}

		/** The diffuse share of the light reaching the plane */
		@LuaFunction
		public final double getCloudCover() {
			double front = array.poaFront();
			return front <= 0.0 ? 1.0 : 1.0 - array.poaBeam() / front;
		}

		/** Output over what the nameplate would make in this light */
		@LuaFunction
		public final double getPerformanceRatio() {
			return array.performanceRatio();
		}

		// ---- the tracker ----

		/** "AUTO", "MANUAL" or "STOW". */
		@LuaFunction
		public final String getTrackerMode() {
			return array.trackerMode().name();
		}

		/** Sets the tracker's mode. */
		@LuaFunction(mainThread = true)
		public final void setTrackerMode(String mode) throws LuaException {
			requireTracker();
			TrackerMode parsed = TrackerMode.byName(mode);
			if (parsed == null) {
				throw new LuaException("unknown tracker mode '" + mode + "', expected AUTO, MANUAL or STOW");
			}

			array.setTrackerMode(parsed);
		}

		/** Where the row actually is, in degrees from horizontal. */
		@LuaFunction
		public final double getTrackerAngle() {
			return array.rotationDeg();
		}

		/** Points the row at an angle by hand, clamped to what the drive can reach. */
		@LuaFunction(mainThread = true)
		public final void setTrackerAngle(double degrees) throws LuaException {
			requireTracker();
			array.setTrackerMode(TrackerMode.MANUAL);
			array.setManualRotation(requireFinite(degrees, "tracker angle"));
		}

		/** Sends the row to its stow position and keeps it there until the mode is changed back. */
		@LuaFunction(mainThread = true)
		public final void stow() throws LuaException {
			requireTracker();
			array.setTrackerMode(TrackerMode.STOW);
		}

		private void requireTracker() throws LuaException {
			TrackerSpec tracker = array.tracker();
			if (tracker == null) {
				throw new LuaException("this array is on a fixed mounting and has no tracker");
			}
		}

		/** The module this array is built from, as a table: designation, technology, watts, efficiency. */
		@LuaFunction
		public final Map<String, Object> getModule() {
			Map<String, Object> out = new LinkedHashMap<>();
			out.put("designation", array.spec().module().displayName());
			out.put("technology", array.spec().module().cell().label());
			out.put("ratedPowerW", array.spec().module().ratedPowerW());
			out.put("efficiency", array.spec().module().efficiency());
			out.put("bifaciality", array.spec().module().bifaciality());
			out.put("count", array.spec().moduleCount());
			return out;
		}

		/** Where this array's strings actually land, or nil if they land nowhere. */
		@LuaFunction
		public final Object getCollector() {
			BlockPos pos = array.collectorPos();
			if (pos == null) return null;

			Map<String, Object> out = new LinkedHashMap<>();
			out.put("x", pos.getX());
			out.put("y", pos.getY());
			out.put("z", pos.getZ());
			return out;
		}

	}

	/** The met mast as seen from Lua: the sky, and nothing about any particular array. */
	public static final class MetStationPeripheral extends TagPeripheral {
		private final MetStationBlockEntity station;

		private MetStationPeripheral(MetStationBlockEntity station) {
			super(SolarTelemetry.stationKinds());
			this.station = station;
		}

		@Override
		public String getType() {
			return "electricity_met_station";
		}

		@Override
		public boolean equals(@Nullable IPeripheral other) {
			return other instanceof MetStationPeripheral peripheral && peripheral.station == station;
		}

		@Override
		public Object getTarget() {
			return station;
		}

		@Override
		protected Telemetry.Snapshot snapshot() {
			return station.getTelemetry();
		}

		@Override
		protected String describe() {
			return "mast";
		}

		/** What the mast is carrying, instrument by instrument. */
		@LuaFunction
		public final List<Map<String, Object>> getInstruments() {
			List<Map<String, Object>> out = new ArrayList<>();
			for (var spec : station.instruments()) {
				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("designation", spec.displayName());
				entry.put("instrument", spec.instrument().key());
				entry.put("class", spec.instrumentClass());
				entry.put("responseTimeS", spec.responseTimeS());
				entry.put("uncertaintyPercent", spec.uncertaintyPercent());
				entry.put("unit", spec.unit());
				entry.put("onArray", spec.instrument().onArray());
				// only the radiometers have one, and it is the reason two of them in the same light
				// disagree: a reference cell stops at 1150 nm where a pyranometer runs to 2800
				if (spec.spectralHighNm() > spec.spectralLowNm()) {
					entry.put("spectralLowNm", spec.spectralLowNm());
					entry.put("spectralHighNm", spec.spectralHighNm());
				}
				out.add(entry);
			}

			return out;
		}

	}

	/** Parses a redstone mode name */
	private static RedstoneMode parseRedstoneMode(String mode) throws LuaException {
		RedstoneMode parsed = RedstoneMode.byName(mode);
		if (parsed == null) {
			throw new LuaException("unknown redstone mode '" + mode + "', expected DISABLED, HIGH or LOW");
		}

		return parsed;
	}

	/** Refuses a setpoint that is not a number. */
	private static double requireFinite(double value, String what) throws LuaException {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			throw new LuaException(what + " must be a finite number");
		}

		return value;
	}
}
