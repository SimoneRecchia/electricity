package com.dooji.electricity.compat.computercraft;

import com.dooji.electricity.api.power.RedstoneMode;
import com.dooji.electricity.api.power.TurbineTelemetry;
import com.dooji.electricity.block.PvArrayBlockEntity;
import com.dooji.electricity.block.TurbineTowerBlock;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import com.dooji.electricity.compat.energy.EnergyBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import dan200.computercraft.api.ForgeComputerCraftAPI;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.IArguments;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Every reference to a CC:Tweaked class in this mod lives here, reached only from
 * {@link ComputerCraftBridge} after it has confirmed the mod is loaded.
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

			if (level.getBlockEntity(pos) instanceof PvArrayBlockEntity array) {
				return LazyOptional.of(() -> new SolarPanelPeripheral(array));
			}

			return LazyOptional.empty();
		});
	}

	/**
	 * The turbine a modem at this position should see, or null.
	 *
	 * A tower answers on behalf of the machine it carries. The nacelle sits at the top of
	 * its tower now, up to thirteen blocks off the ground, and requiring the modem up there
	 * would put every computer on a ladder for no reason - the cables of a real turbine are
	 * gathered at the foot of the tower, which is exactly where a player will build.
	 *
	 * Two modems on the same structure resolve to the same block entity, and the peripheral
	 * compares by that identity, so ComputerCraft already treats them as one peripheral.
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

	/**
	 * The turbine as seen from Lua.
	 *
	 * Two families of method live side by side. The handful of annotated ones carry
	 * the names Mekanism uses on its own generators, so a program written against a
	 * Mekanism generator reads this turbine unchanged. Everything else is generated
	 * from the telemetry tag list through {@link IDynamicPeripheral}, which is why
	 * adding a tag costs nothing here.
	 *
	 * Reads come off the computer thread, never the server thread. That is safe
	 * because every value is served from the immutable snapshot the turbine
	 * publishes once per tick, so a call can never observe a half-updated turbine
	 * and never has to wait for a tick boundary to answer.
	 */
	public static final class WindTurbinePeripheral implements IDynamicPeripheral {
		/**
		 * Lua names already taken by the annotated methods above.
		 *
		 * CC registers the annotated methods first and the dynamic ones second, into a
		 * plain map, so a dynamic name that collides silently overwrites the annotated
		 * method of the same name. That happened with getActivePowerLimit, where the
		 * generated getter for the activePowerLimit tag shadowed the real accessor.
		 * Filtering here keeps the annotated method authoritative.
		 */
		private static final Set<String> RESERVED_NAMES = Set.of(
				"getProductionRate", "getMaxOutput", "getEnergy", "getMaxEnergy", "getEnergyNeeded", "getEnergyFilledPercentage",
				"isBlacklistedDimension", "stop", "start", "isStopped", "isRunning", "isWindCutOut", "isStoppedByRedstone",
				"getRedstoneMode", "setRedstoneMode", "getActivePowerLimit", "setActivePowerLimit", "getTelemetry", "getTelemetryKinds"
		);

		// TAGS and METHOD_NAMES are built together and stay index-aligned, which is what
		// callMethod relies on: CC passes back the index into getMethodNames().
		private static final String[] TAGS;
		private static final String[] METHOD_NAMES;

		static {
			List<String> tags = new ArrayList<>();
			List<String> names = new ArrayList<>();
			for (String tag : TurbineTelemetry.kinds().keySet()) {
				String name = "get" + Character.toUpperCase(tag.charAt(0)) + tag.substring(1);
				if (RESERVED_NAMES.contains(name)) continue;

				tags.add(tag);
				names.add(name);
			}

			TAGS = tags.toArray(String[]::new);
			METHOD_NAMES = names.toArray(String[]::new);
		}

		private final WindTurbineBlockEntity turbine;

		private WindTurbinePeripheral(WindTurbineBlockEntity turbine) {
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

		// ---- names shared with Mekanism's generators ----

		/**
		 * Joules produced in the last tick. Mekanism's own generators answer the same
		 * question under the same name, in the same unit.
		 */
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
		 * Present for parity with Mekanism's Wind Generator, which can be barred from
		 * generating in configured dimensions. This mod has no such list, so the
		 * answer is always false.
		 */
		@LuaFunction
		public final boolean isBlacklistedDimension() {
			return false;
		}

		// ---- control ----
		//
		// Every method that changes the turbine runs with mainThread = true. Reads are
		// served from the per-tick snapshot and are safe from the computer thread, but a
		// write has to mark the block entity dirty and push the new state to clients,
		// which is only legal on the server thread.

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
		 * Sets how the turbine reacts to redstone. Mekanism's fourth mode, PULSE, is not
		 * accepted: a generator runs continuously and has nothing to pulse, so taking it
		 * silently would be a lie.
		 */
		@LuaFunction(mainThread = true)
		public final void setRedstoneMode(String mode) throws LuaException {
			RedstoneMode parsed = RedstoneMode.byName(mode);
			if (parsed == null) {
				throw new LuaException("unknown redstone mode '" + mode + "', expected DISABLED, HIGH or LOW");
			}

			turbine.setRedstoneMode(parsed);
		}

		/** Curtailment setpoint in kW. */
		@LuaFunction
		public final double getActivePowerLimit() {
			return turbine.getActivePowerLimit();
		}

		/**
		 * Caps output at {@code limitKw}. Clamped to the machine's rated power, so it
		 * cannot be used to make the turbine produce more than the wind allows. Setting
		 * zero curtails it fully, which stops the output without applying the brake.
		 */
		@LuaFunction(mainThread = true)
		public final void setActivePowerLimit(double limitKw) throws LuaException {
			if (Double.isNaN(limitKw) || Double.isInfinite(limitKw)) {
				throw new LuaException("active power limit must be a finite number of kW");
			}

			turbine.setActivePowerLimit(limitKw);
		}

		// ---- telemetry ----

		/** Every signal at once, as a table keyed by tag. One tick-consistent snapshot. */
		@LuaFunction
		public final Map<String, Object> getTelemetry() {
			return turbine.getTelemetry().values();
		}

		/**
		 * Which tags are instrument readings and which are invented, as a table of tag
		 * to "MEASURED", "DERIVED" or "SIMULATED". Worth checking before treating a
		 * number as ground truth: the temperatures and pressures track load and
		 * ambient realistically but are not modelled by the mod.
		 */
		@LuaFunction
		public final Map<String, Object> getTelemetryKinds() {
			Map<String, Object> kinds = new LinkedHashMap<>();
			TurbineTelemetry.kinds().forEach((tag, kind) -> kinds.put(tag, kind.name()));
			return kinds;
		}

		// ---- one generated getter per tag ----

		@Override
		public String[] getMethodNames() {
			return METHOD_NAMES;
		}

		@Override
		public MethodResult callMethod(IComputerAccess computer, ILuaContext context, int method, IArguments arguments) throws LuaException {
			if (method < 0 || method >= TAGS.length) {
				// unreachable through CC, which only ever passes back an index it took from
				// getMethodNames(). Raised rather than answered with an empty result, which
				// Lua would show as an indistinguishable nil.
				throw new LuaException("telemetry method index " + method + " out of range");
			}

			Object value = turbine.getTelemetry().get(TAGS[method]);
			if (value == null) {
				// the turbine has not published a snapshot yet: its chunk is loaded but it
				// has not ticked. Saying so beats a bare nil that looks like a missing method.
				throw new LuaException("no telemetry for '" + TAGS[method] + "' yet, the turbine has not ticked");
			}

			return MethodResult.of(value);
		}
	}

	/**
	 * A block of photovoltaic array as seen from Lua.
	 *
	 * Deliberately still called {@code electricity_solar_panel} and deliberately still answering the
	 * same nine methods, because programs written against the placeholder exist and there is no reason
	 * to break them: every one of those questions still has an answer, it is just a better answer now.
	 * {@code getProductionRate} and {@code canSeeSun} carry the names Mekanism's own solar generators
	 * use, so a program written for one of those reads this unchanged too.
	 *
	 * Two of the answers changed meaning slightly and honestly. Irradiance is now the plane-of-array
	 * figure rather than the global horizontal one, which is what the modules actually respond to and
	 * what a real plant reports; on a flat table they are the same number anyway. And the rated power
	 * is this product's own DC nameplate rather than a constant, because there are six products now.
	 *
	 * Every reading is a plain field the array wrote during its own tick, so the computer thread can
	 * have them without waiting for a tick boundary.
	 */
	public static final class SolarPanelPeripheral implements IPeripheral {
		private final PvArrayBlockEntity array;

		private SolarPanelPeripheral(PvArrayBlockEntity array) {
			this.array = array;
		}

		@Override
		public String getType() {
			return "electricity_solar_panel";
		}

		@Override
		public boolean equals(@Nullable IPeripheral other) {
			return other instanceof SolarPanelPeripheral peripheral && peripheral.array == array;
		}

		@Override
		public Object getTarget() {
			return array;
		}

		/** Joules delivered in the last tick, the unit and the name Mekanism's generators use. */
		@LuaFunction
		public final double getProductionRate() {
			return array.deliveredDcKw() * EnergyBridge.JOULES_PER_KW;
		}

		/**
		 * Whether the array has a clear enough view of the sun to be worth anything.
		 *
		 * Not a yes or no about the sky any more but a threshold on how much of the beam is actually
		 * arriving, because the answer stopped being binary: an array under glass can see the sun
		 * perfectly well and an array under a passing player only partly.
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

		/** Fraction of the sky under cloud, 0 to 1, as the diffuse share of the light reaching the plane. */
		@LuaFunction
		public final double getCloudCover() {
			double front = array.poaFront();
			return front <= 0.0 ? 1.0 : 1.0 - array.poaBeam() / front;
		}

		/**
		 * Output over what the nameplate would make in this light, 0 to 1.
		 *
		 * The one figure worth logging if only one is: it folds the temperature, the soiling, the snow,
		 * the shading, the spectrum and this array's own module binning into the number a plant is
		 * actually judged on.
		 */
		@LuaFunction
		public final double getPerformanceRatio() {
			return array.performanceRatio();
		}
	}
}
