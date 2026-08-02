package com.dooji.electricity.compat.computercraft;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Every reference to a CC:Tweaked class in this mod lives here, reached only from
 * {@link ComputerCraftBridge} after it has confirmed the mod is loaded.
 *
 * Four peripherals, one per kind of node: a wind turbine, a photovoltaic array, an inverter, and a met
 * mast. Each publishes its own tag set through {@link IDynamicPeripheral}, so a getter appears for
 * every signal without anyone writing one - and adding a tag to a telemetry class costs nothing here.
 *
 * <h2>Threading</h2>
 *
 * Reads come off the computer thread and never block, because every value is served from the immutable
 * snapshot the machine publishes once a tick. A call can therefore never observe a half-updated
 * machine and never has to wait for a tick boundary. Anything that *changes* a machine runs with
 * {@code mainThread = true}, because a write marks the block entity dirty and pushes state to clients,
 * which is only legal on the server thread.
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

	/**
	 * The turbine a modem at this position should see, or null.
	 *
	 * A tower answers on behalf of the machine it carries. The nacelle sits at the top of its tower now,
	 * up to thirteen blocks off the ground, and requiring the modem up there would put every computer on
	 * a ladder for no reason - the cables of a real turbine are gathered at the foot of the tower, which
	 * is exactly where a player will build.
	 *
	 * Two modems on the same structure resolve to the same block entity, and the peripheral compares by
	 * that identity, so ComputerCraft already treats them as one peripheral.
	 */
	@Nullable
	private static WindTurbineBlockEntity turbineAt(Level level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof WindTurbineBlockEntity turbine) return turbine;

		if (level.getBlockState(pos).getBlock() instanceof TurbineTowerBlock) {
			BlockPos machine = TurbineTowerBlock.findTurbineAbove(level, pos);
			if (machine != null && level.getBlockEntity(machine) instanceof WindTurbineBlockEntity turbine) return turbine;
		}

		return null;
	}

	/** A tag-to-kind map as Lua sees it: the kinds by name rather than as enum constants. */
	private static Map<String, Object> namedKinds(Map<String, Telemetry.Kind> kinds) {
		Map<String, Object> named = new LinkedHashMap<>();
		kinds.forEach((tag, kind) -> named.put(tag, kind.name()));
		return named;
	}

	/**
	 * One generated getter per telemetry tag.
	 *
	 * The whole reason this is a base class rather than four copies: the index bookkeeping is subtle in a
	 * way that is easy to get wrong once and impossible to get wrong the same way twice. CC hands back an
	 * index into {@link #getMethodNames()}, so the tag array and the name array have to stay exactly
	 * aligned - and a name colliding with an annotated method has to be dropped from *both*.
	 *
	 * That collision is not theoretical. CC registers annotated methods first and dynamic ones second,
	 * into a plain map, so a dynamic name that collides silently overwrites the annotated method. It
	 * happened once already, with the generated getter for {@code activePowerLimit} shadowing the real
	 * accessor that could also set it.
	 */
	private abstract static class TagPeripheral implements IDynamicPeripheral {
		private final String[] tags;
		private final String[] methodNames;

		private TagPeripheral(Map<String, Telemetry.Kind> kinds, Set<String> reserved) {
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

		/** The machine's latest published readings. Never null. */
		protected abstract Telemetry.Snapshot snapshot();

		/** What the machine is, for the error message when a tag has not been published yet. */
		protected abstract String describe();

		@Override
		public final String[] getMethodNames() {
			return methodNames;
		}

		@Override
		public final MethodResult callMethod(IComputerAccess computer, ILuaContext context, int method, IArguments arguments) throws LuaException {
			if (method < 0 || method >= tags.length) {
				// unreachable through CC, which only ever passes back an index it took from
				// getMethodNames(). Raised rather than answered with an empty result, which Lua would show
				// as an indistinguishable nil.
				throw new LuaException("telemetry method index " + method + " out of range");
			}

			Object value = snapshot().get(tags[method]);
			if (value == null) {
				// the machine's chunk is loaded but it has not ticked yet. Saying so beats a bare nil that
				// looks like a missing method
				throw new LuaException("no telemetry for '" + tags[method] + "' yet, the " + describe() + " has not ticked");
			}

			return MethodResult.of(value);
		}
	}

	/**
	 * The turbine as seen from Lua.
	 *
	 * Two families of method side by side. The handful of annotated ones carry the names Mekanism uses on
	 * its own generators, so a program written against a Mekanism generator reads this turbine unchanged.
	 * Everything else is generated from the tag list.
	 */
	public static final class WindTurbinePeripheral extends TagPeripheral {
		private static final Set<String> RESERVED_NAMES = Set.of(
				"getProductionRate", "getMaxOutput", "getEnergy", "getMaxEnergy", "getEnergyNeeded", "getEnergyFilledPercentage",
				"isBlacklistedDimension", "stop", "start", "isStopped", "isRunning", "isWindCutOut", "isStoppedByRedstone",
				"getRedstoneMode", "setRedstoneMode", "getActivePowerLimit", "setActivePowerLimit", "getTelemetry", "getTelemetryKinds"
		);

		private final WindTurbineBlockEntity turbine;

		private WindTurbinePeripheral(WindTurbineBlockEntity turbine) {
			super(TurbineTelemetry.kinds(), RESERVED_NAMES);
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
		public Object getTarget() {
			return turbine;
		}

		@Override
		protected Telemetry.Snapshot snapshot() {
			return turbine.getTelemetry();
		}

		@Override
		protected String describe() {
			return "turbine";
		}

		// ---- names shared with Mekanism's generators ----

		/** Joules produced in the last tick. Mekanism's own generators answer the same question under the same name. */
		@LuaFunction
		public final double getProductionRate() {
			return turbine.getGrossJoulesPerTick();
		}

		/** Ceiling on what this generator will hand out per tick, in Joules. */
		@LuaFunction
		public final double getMaxOutput() {
			return turbine.getMaxJoulesPerTick();
		}

		/** Joules still unclaimed in this tick's budget. */
		@LuaFunction
		public final double getEnergy() {
			return turbine.getAvailableJoules();
		}

		@LuaFunction
		public final double getMaxEnergy() {
			return turbine.getMaxJoulesPerTick();
		}

		/** Always zero: the turbine is a source and accepts nothing. */
		@LuaFunction
		public final double getEnergyNeeded() {
			return 0.0;
		}

		@LuaFunction
		public final double getEnergyFilledPercentage() {
			double max = turbine.getMaxJoulesPerTick();
			return max <= 0.0 ? 0.0 : turbine.getAvailableJoules() / max;
		}

		/**
		 * Present for parity with Mekanism's Wind Generator, which can be barred from generating in
		 * configured dimensions. This mod has no such list, so the answer is always false.
		 */
		@LuaFunction
		public final boolean isBlacklistedDimension() {
			return false;
		}

		// ---- control ----

		/** Applies the brake. The rotor stops, the blades feather and output goes to zero. */
		@LuaFunction(mainThread = true)
		public final void stop() {
			turbine.setStoppedByComputer(true);
		}

		/** Releases a stop issued by {@link #stop()}. Does not override a redstone stop or a wind cut-out. */
		@LuaFunction(mainThread = true)
		public final void start() {
			turbine.setStoppedByComputer(false);
		}

		/** Whether this turbine is stopped specifically by a computer command. */
		@LuaFunction
		public final boolean isStopped() {
			return turbine.isStoppedByComputer();
		}

		/** Whether the rotor is turning and allowed to generate, for any reason. */
		@LuaFunction
		public final boolean isRunning() {
			return turbine.isRunning();
		}

		/** True when the machine stopped itself because the wind exceeded its cut-out speed. */
		@LuaFunction
		public final boolean isWindCutOut() {
			return turbine.isWindCutOut();
		}

		/** True when the current redstone mode and signal are holding the turbine down. */
		@LuaFunction
		public final boolean isStoppedByRedstone() {
			return turbine.isStoppedByRedstone();
		}

		/** "DISABLED", "HIGH" or "LOW". Same names Mekanism uses. */
		@LuaFunction
		public final String getRedstoneMode() {
			return turbine.getRedstoneMode().name();
		}

		/**
		 * Sets how the turbine reacts to redstone. Mekanism's fourth mode, PULSE, is not accepted: a
		 * generator runs continuously and has nothing to pulse, so taking it silently would be a lie.
		 */
		@LuaFunction(mainThread = true)
		public final void setRedstoneMode(String mode) throws LuaException {
			turbine.setRedstoneMode(parseRedstoneMode(mode));
		}

		/** Curtailment setpoint in kW. */
		@LuaFunction
		public final double getActivePowerLimit() {
			return turbine.getActivePowerLimit();
		}

		/**
		 * Caps output at {@code limitKw}. Clamped to the machine's rated power, so it cannot be used to make
		 * the turbine produce more than the wind allows. Setting zero curtails it fully, which stops the
		 * output without applying the brake.
		 */
		@LuaFunction(mainThread = true)
		public final void setActivePowerLimit(double limitKw) throws LuaException {
			turbine.setActivePowerLimit(requireFinite(limitKw, "active power limit"));
		}

		// ---- telemetry ----

		/** Every signal at once, as a table keyed by tag. One tick-consistent snapshot. */
		@LuaFunction
		public final Map<String, Object> getTelemetry() {
			return turbine.getTelemetry().values();
		}

		/**
		 * Which tags are instrument readings and which are invented, as a table of tag to "MEASURED",
		 * "DERIVED" or "SIMULATED". Worth checking before treating a number as ground truth.
		 */
		@LuaFunction
		public final Map<String, Object> getTelemetryKinds() {
			return namedKinds(TurbineTelemetry.kinds());
		}
	}

	/**
	 * The inverter as seen from Lua: the node a plant's control program actually talks to.
	 *
	 * It is the inverter rather than the arrays because that is where a real plant's SCADA lives, and
	 * because it is the only part of a plant a program can usefully *change*: an array has no controls
	 * beyond its tracker, while the inverter holds the curtailment setpoint, the power factor and the stop.
	 */
	public static final class PvInverterPeripheral extends TagPeripheral {
		private static final Set<String> RESERVED_NAMES = Set.of(
				"getProductionRate", "getMaxOutput", "getEnergy", "getMaxEnergy", "getEnergyNeeded", "getEnergyFilledPercentage",
				"isBlacklistedDimension", "stop", "start", "isStopped", "isRunning", "isClipping", "isDerating", "isStoppedByRedstone",
				"getRedstoneMode", "setRedstoneMode", "getActivePowerLimit", "setActivePowerLimit", "getPowerFactor", "setPowerFactor",
				"getTelemetry", "getTelemetryKinds", "getRatedPower", "getArrays"
		);

		private final PvInverterBlockEntity inverter;

		private PvInverterPeripheral(PvInverterBlockEntity inverter) {
			super(SolarTelemetry.inverterKinds(), RESERVED_NAMES);
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
		public Object getTarget() {
			return inverter;
		}

		@Override
		protected Telemetry.Snapshot snapshot() {
			return inverter.getTelemetry();
		}

		@Override
		protected String describe() {
			return "inverter";
		}

		// ---- names shared with Mekanism's generators ----

		@LuaFunction
		public final double getProductionRate() {
			return inverter.getGrossJoulesPerTick();
		}

		@LuaFunction
		public final double getMaxOutput() {
			return inverter.getMaxJoulesPerTick();
		}

		@LuaFunction
		public final double getEnergy() {
			return inverter.getAvailableJoules();
		}

		@LuaFunction
		public final double getMaxEnergy() {
			return inverter.getMaxJoulesPerTick();
		}

		@LuaFunction
		public final double getEnergyNeeded() {
			return 0.0;
		}

		@LuaFunction
		public final double getEnergyFilledPercentage() {
			double max = inverter.getMaxJoulesPerTick();
			return max <= 0.0 ? 0.0 : inverter.getAvailableJoules() / max;
		}

		@LuaFunction
		public final boolean isBlacklistedDimension() {
			return false;
		}

		/** AC nameplate in kW: what this machine can pass however much glass is in front of it. */
		@LuaFunction
		public final double getRatedPower() {
			return inverter.spec().acPowerKw();
		}

		// ---- control ----

		/**
		 * Disconnects from the grid and stops converting.
		 *
		 * The arrays go open-circuit and stop producing, which is what happens on a real plant: there is no
		 * load, so there is no current. It is not a brake and there is nothing to spin down.
		 */
		@LuaFunction(mainThread = true)
		public final void stop() {
			inverter.setStoppedByComputer(true);
		}

		/** Releases a stop issued by {@link #stop()}. Does not override a redstone stop. */
		@LuaFunction(mainThread = true)
		public final void start() {
			inverter.setStoppedByComputer(false);
		}

		@LuaFunction
		public final boolean isStopped() {
			return inverter.isStoppedByComputer();
		}

		@LuaFunction
		public final boolean isRunning() {
			return inverter.isRunning();
		}

		/** Whether the arrays are offering more than the nameplate can pass. Deliberate, not a fault. */
		@LuaFunction
		public final boolean isClipping() {
			return inverter.clipping();
		}

		/** Whether the air is hot enough that the machine is holding itself back. Cured by shade, not by size. */
		@LuaFunction
		public final boolean isDerating() {
			return inverter.derating();
		}

		@LuaFunction
		public final boolean isStoppedByRedstone() {
			return inverter.isStoppedByRedstone();
		}

		@LuaFunction
		public final String getRedstoneMode() {
			return inverter.getRedstoneMode().name();
		}

		@LuaFunction(mainThread = true)
		public final void setRedstoneMode(String mode) throws LuaException {
			inverter.setRedstoneMode(parseRedstoneMode(mode));
		}

		@LuaFunction
		public final double getActivePowerLimit() {
			return inverter.getActivePowerLimit();
		}

		/** Caps output at {@code limitKw}, clamped to the machine's nameplate. Zero curtails it fully. */
		@LuaFunction(mainThread = true)
		public final void setActivePowerLimit(double limitKw) throws LuaException {
			inverter.setActivePowerLimit(requireFinite(limitKw, "active power limit"));
		}

		@LuaFunction
		public final double getPowerFactor() {
			return inverter.powerFactor();
		}

		/**
		 * Asks the machine to hold a power factor, clamped to what it can do.
		 *
		 * Worth understanding before using: a deep power factor at full output asks for more apparent power
		 * than the machine has, and active power is what gives way. That is not a bug, it is what a grid
		 * operator requesting reactive support is buying.
		 */
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

		// ---- telemetry ----

		@LuaFunction
		public final Map<String, Object> getTelemetry() {
			return inverter.getTelemetry().values();
		}

		@LuaFunction
		public final Map<String, Object> getTelemetryKinds() {
			return namedKinds(SolarTelemetry.inverterKinds());
		}
	}

	/**
	 * One array as seen from Lua.
	 *
	 * Answers to {@code electricity_solar_panel} as well as its own name, and keeps the nine methods the
	 * placeholder had, because programs written against those exist and there is no reason to break them.
	 * Two of the nine changed meaning and both changed for the better: irradiance is now the
	 * plane-of-array figure the modules actually respond to, and the rated power is this product's own DC
	 * nameplate rather than a constant.
	 */
	public static final class PvArrayPeripheral extends TagPeripheral {
		private static final Set<String> RESERVED_NAMES = Set.of(
				"getProductionRate", "canSeeSun", "getActivePower", "getRatedPower", "getIrradiance", "getCellTemperature",
				"getAmbientTemperature", "getCloudCover", "getPerformanceRatio", "getTrackerMode", "setTrackerMode",
				"getTrackerAngle", "setTrackerAngle", "stow", "getTelemetry", "getTelemetryKinds", "getModule", "getInverter"
		);

		private final PvArrayBlockEntity array;

		private PvArrayPeripheral(PvArrayBlockEntity array) {
			super(SolarTelemetry.arrayKinds(), RESERVED_NAMES);
			this.array = array;
		}

		@Override
		public String getType() {
			return "electricity_pv_array";
		}

		/**
		 * The name the placeholder answered to, kept so existing programs still find this block.
		 *
		 * An additional type rather than a rename in either direction: new programs get a name that says
		 * what it is, and {@code peripheral.find("electricity_solar_panel")} goes on working.
		 */
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

		/**
		 * Whether the array has a clear enough view of the sun to be worth anything.
		 *
		 * A threshold rather than a yes or no about the sky, because the answer stopped being binary: an
		 * array under glass can see the sun perfectly well and an array under a passing player only partly.
		 */
		@LuaFunction
		public final boolean canSeeSun() {
			return array.obstructionFraction() > 0.5;
		}

		/** What the modules are delivering, in kW. Zero with no inverter in range, as an open circuit is. */
		@LuaFunction
		public final double getActivePower() {
			return array.deliveredDcKw();
		}

		/** This product's DC nameplate at standard test conditions, in kW. */
		@LuaFunction
		public final double getRatedPower() {
			return array.spec().dcPowerKw();
		}

		/** Irradiance in the plane of the modules, W/m2. A clear zenith sun on the flat is just over 1000. */
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

		/** The diffuse share of the light reaching the plane, which is what cloud over an array amounts to. */
		@LuaFunction
		public final double getCloudCover() {
			double front = array.poaFront();
			return front <= 0.0 ? 1.0 : 1.0 - array.poaBeam() / front;
		}

		/**
		 * Output over what the nameplate would make in this light, 0 to 1.
		 *
		 * The one figure worth logging if only one is: it folds the temperature, the soiling, the snow, the
		 * shading, the spectrum and this array's own module binning into the number a plant is judged on.
		 */
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

		/**
		 * Sets the tracker's mode. Throws on a fixed mounting rather than accepting it quietly, because a
		 * program that thinks it is commanding a tracker and is not would be worse than an error.
		 */
		@LuaFunction(mainThread = true)
		public final void setTrackerMode(String mode) throws LuaException {
			requireTracker();
			TrackerMode parsed = TrackerMode.byName(mode);
			if (parsed == null) {
				throw new LuaException("unknown tracker mode '" + mode + "', expected AUTO, MANUAL or STOW");
			}

			array.setTrackerMode(parsed);
		}

		/** Where the row actually is, in degrees from horizontal. Positive faces west. */
		@LuaFunction
		public final double getTrackerAngle() {
			return array.rotationDeg();
		}

		/**
		 * Points the row at an angle by hand, clamped to what the drive can reach.
		 *
		 * Switches the mode to MANUAL, because holding an angle and tracking the sun are the same drive and
		 * it cannot do both - leaving the mode alone would have the next tick's tracking quietly undo the
		 * command, which is the sort of thing that takes an afternoon to work out.
		 */
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

		/** Where the inverter this array is wired to is, or nil if there is none in range. */
		@LuaFunction
		public final Object getInverter() {
			BlockPos pos = array.inverterPos();
			if (pos == null || !array.hasInverter()) return null;

			Map<String, Object> out = new LinkedHashMap<>();
			out.put("x", pos.getX());
			out.put("y", pos.getY());
			out.put("z", pos.getZ());
			return out;
		}

		@LuaFunction
		public final Map<String, Object> getTelemetry() {
			return array.getTelemetry().values();
		}

		@LuaFunction
		public final Map<String, Object> getTelemetryKinds() {
			return namedKinds(SolarTelemetry.arrayKinds());
		}
	}

	/**
	 * The met mast as seen from Lua: the sky, and nothing about any particular array.
	 *
	 * Every reading here has been through its instrument's own time constant, which is the point of reading
	 * a mast rather than asking the weather. A program that logs the pyranometer beside an inverter's output
	 * will see the power move first on a cloud edge, exactly as a real plant's trends do.
	 */
	public static final class MetStationPeripheral extends TagPeripheral {
		private static final Set<String> RESERVED_NAMES = Set.of("getTelemetry", "getTelemetryKinds", "getInstruments");

		private final MetStationBlockEntity station;

		private MetStationPeripheral(MetStationBlockEntity station) {
			super(SolarTelemetry.stationKinds(), RESERVED_NAMES);
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

		/**
		 * What the mast is carrying, instrument by instrument.
		 *
		 * Published because the specification is the reading's context: a pyranometer's five-second response
		 * time is why its trend is smooth, and its class is what decides whether a performance test written
		 * against it means anything.
		 */
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

		@LuaFunction
		public final Map<String, Object> getTelemetry() {
			return station.getTelemetry().values();
		}

		@LuaFunction
		public final Map<String, Object> getTelemetryKinds() {
			return namedKinds(SolarTelemetry.stationKinds());
		}
	}

	/** Parses a redstone mode name, or says what the three are. */
	private static RedstoneMode parseRedstoneMode(String mode) throws LuaException {
		RedstoneMode parsed = RedstoneMode.byName(mode);
		if (parsed == null) {
			throw new LuaException("unknown redstone mode '" + mode + "', expected DISABLED, HIGH or LOW");
		}

		return parsed;
	}

	/**
	 * Refuses a setpoint that is not a number.
	 *
	 * Lua will hand over a NaN or an infinity without complaint, and a setpoint clamped from one of those
	 * comes out as a plausible-looking number that then poisons everything downstream. Better to say so.
	 */
	private static double requireFinite(double value, String what) throws LuaException {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			throw new LuaException(what + " must be a finite number");
		}

		return value;
	}
}
