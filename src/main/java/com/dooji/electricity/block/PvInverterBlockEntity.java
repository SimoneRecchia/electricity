package com.dooji.electricity.block;

import com.dooji.electricity.api.power.IEnergyBudget;
import com.dooji.electricity.api.power.InverterSpec;
import com.dooji.electricity.api.power.PvArraySpec;
import com.dooji.electricity.api.power.PvModuleSpec;
import com.dooji.electricity.api.power.RedstoneMode;
import com.dooji.electricity.api.power.Telemetry;
import com.dooji.electricity.api.power.TickBudget;
import com.dooji.electricity.client.render.obj.ObjBoundingBoxRegistry;
import com.dooji.electricity.client.render.obj.ObjModel;
import com.dooji.electricity.client.wire.InsulatorLookup;
import com.dooji.electricity.client.wire.WireManagerClient;
import com.dooji.electricity.compat.energy.EnergyBridge;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.ElectricityServerConfig;
import com.dooji.electricity.main.registry.InverterCatalog;
import com.dooji.electricity.main.registry.ObjBlockDefinition;
import com.dooji.electricity.main.registry.ObjDefinitions;
import com.dooji.electricity.main.weather.GlobalWeatherManager;
import com.dooji.electricity.power.SolarTelemetrySimulator;
import com.dooji.electricity.wire.InsulatorIdRegistry;
import com.dooji.electricity.wire.InsulatorPartHelper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.DistExecutor;
import org.joml.Vector3f;

/**
 * The inverter: the whole electrical side of a photovoltaic plant, in one block.
 *
 * <h2>What it does each tick</h2>
 *
 * Gathers the direct current its arrays are offering, decides how much of it to take, converts it,
 * and reports. The deciding is where all the interesting behaviour is, and it is four separate limits
 * that look identical from outside and have completely different answers:
 *
 * <ul>
 * <li><b>Input capacity</b> - the machine has a finite number of maximum power point trackers and a
 *     finite number of string terminals on each. Arrays beyond that are simply not wired in, and
 *     say so.</li>
 * <li><b>Clipping</b> - the array is offering more than the nameplate can pass, so the operating
 *     point comes off the peak. Not a fault: it is what a DC-to-AC ratio above one buys, and every
 *     utility plant in the world is built to do it for a few hours of the best days.</li>
 * <li><b>Derating</b> - the air is too hot to run at nameplate without cooking the semiconductors, so
 *     the machine backs off. Looks exactly like clipping on a trend and is cured by shade rather than
 *     by a bigger inverter.</li>
 * <li><b>Curtailment</b> - somebody, or something, asked for less. A setpoint from a computer, a
 *     redstone signal, or a hand on the stop button.</li>
 * </ul>
 *
 * <h2>Why it draws power at night</h2>
 *
 * Because real ones do, and it is one of those details that makes a trend recognisable: a plant's
 * meter reads slightly negative between sunset and sunrise, because the controller, the
 * communications and the insulation monitoring all have to stay alive. A watt on a residential
 * machine and a hundred on a central one.
 */
public class PvInverterBlockEntity extends BlockEntity implements IEnergyBudget {
	/**
	 * How often the plant is surveyed for arrays again, in ticks.
	 *
	 * Two seconds. Frequent enough that an array placed beside a running inverter comes online while
	 * the player is still standing there, and rare enough that the walk over the loaded chunks in
	 * range costs nothing. The walk is over each chunk's block entity map rather than over every block
	 * position, which is the difference between a few dozen lookups and several thousand.
	 */
	private static final int RESCAN_TICKS = 40;
	/** Ticks in a day, for the energy counters that reset with it. */
	private static final long TICKS_PER_DAY = 24000L;

	private final List<BlockPos> arrays = new ArrayList<>();
	private int rescanCountdown = 0;

	private double availableDcKw = 0.0;
	private double dcPowerKw = 0.0;
	private double acPowerKw = 0.0;
	private double dcVoltage = 0.0;
	private double dcCurrent = 0.0;
	private double cabinetTempC = 15.0;
	private double ambientTempC = 15.0;
	private double efficiency = 0.0;
	private boolean clipping = false;
	private boolean derating = false;
	/**
	 * The strings are lit but at a voltage this machine cannot track.
	 *
	 * Worth publishing separately from plain standby, because it is the one silent mismatch a player
	 * can make and not diagnose: a central inverter is designed around one string length, so putting
	 * flat tables of twenty-two modules in front of one leaves it waiting for a voltage that will
	 * never arrive. The panel names the window rather than leaving it at "standby".
	 */
	private boolean stringsOutOfWindow = false;
	private int stringsConnected = 0;
	/**
	 * Direct-current input the machine has left, in amps.
	 *
	 * Published because it is the number that explains a refusal. An array that will not come online
	 * when there are terminals free is an array whose current would not fit, and without this the panel
	 * can only say "not wired in" and leave the player counting holes.
	 */
	private double stringCurrentHeadroom = 0.0;

	private double energyTodayKwh = 0.0;
	private double energyLifetimeKwh = 0.0;
	private long lastDay = -1L;

	private volatile boolean stoppedByComputer = false;
	private volatile boolean stoppedByPlayer = false;
	private final RedstoneStop redstone = new RedstoneStop();
	private volatile double activePowerLimitKw;
	private volatile double powerFactorSetpoint = 1.0;

	private final TickBudget budget = new TickBudget();
	private final LazyOptional<IEnergyStorage> forgeEnergy = LazyOptional.of(() -> EnergyBridge.forgeEnergyView(this));
	private final LazyOptional<?> mekanismEnergy = EnergyBridge.createMekanismHandler(this);

	private final ClientSync clientSync = new ClientSync();

	/**
	 * The wire fitting on top of the cabinet.
	 *
	 * One, because an inverter has one alternating-current output and a plant joins the grid at exactly
	 * one place. The arrays behind it are wired with direct-current cable that is not drawn, which is
	 * also how a real plant looks: the only overhead line on a solar farm is the one leaving it.
	 */
	private Vec3[] wirePositions;
	private int[] insulatorIds;

	private final SolarTelemetrySimulator telemetrySimulator = new SolarTelemetrySimulator();
	private volatile Telemetry.Snapshot telemetry = Telemetry.Snapshot.EMPTY;
	/** The reference array's readings, which are the plant's as far as its SCADA is concerned. */
	private double referenceIrradiance = 0.0;
	private double referenceModuleTempC = 15.0;

	public PvInverterBlockEntity(BlockPos pos, BlockState state) {
		super(Electricity.PV_INVERTER_BLOCK_ENTITY.get(), pos, state);
		this.activePowerLimitKw = spec().acPowerKw();
		ensureArraySizes();
		generateInsulatorIds();
	}

	/** Which machine this is. Read off the block, so there is no saved model id to migrate. */
	public InverterSpec spec() {
		if (getBlockState().getBlock() instanceof PvInverterBlock inverter) return inverter.spec();

		return InverterCatalog.fallback();
	}

	// ---- the wire fitting ----

	private ObjBlockDefinition definition() {
		return ObjDefinitions.get(getBlockState().getBlock());
	}

	private void ensureArraySizes() {
		ObjBlockDefinition definition = definition();
		int count = definition == null ? 0 : definition.insulators().size();

		if (wirePositions == null || wirePositions.length != count) {
			wirePositions = new Vec3[count];
		}

		if (insulatorIds == null || insulatorIds.length != count) {
			insulatorIds = new int[count];
		}
	}

	private void generateInsulatorIds() {
		ensureArraySizes();
		InsulatorIdRegistry.claimMissing(insulatorIds);
	}

	public Vec3 getWirePosition(int index) {
		ensureArraySizes();
		return index >= 0 && index < wirePositions.length ? wirePositions[index] : null;
	}

	public void setWirePosition(int index, Vec3 position) {
		ensureArraySizes();
		if (index >= 0 && index < wirePositions.length) {
			wirePositions[index] = position;
		}
	}

	public int getInsulatorId(int index) {
		ensureArraySizes();
		return index >= 0 && index < insulatorIds.length ? insulatorIds[index] : -1;
	}

	public int[] getInsulatorIds() {
		ensureArraySizes();
		return insulatorIds.clone();
	}

	/**
	 * Where the fitting is in the world, with the cabinet's own scale applied.
	 *
	 * The scale is the part that would otherwise go wrong: the model is authored at the commercial
	 * cabinet's size and drawn smaller for the residential machine, so a fitting placed from the raw
	 * geometry would float above a small one and a wire would attach to nothing.
	 */
	public Vec3 calculateOrientedInsulatorCenter(int index) {
		ensureArraySizes();
		if (index < 0 || index >= wirePositions.length) return null;

		String groupName = InsulatorPartHelper.insulatorName(definition(), index);
		if (groupName == null) return null;

		ObjModel.BoundingBox boundingBox = ObjBoundingBoxRegistry.getBoundingBox(getBlockState().getBlock(), groupName);
		if (boundingBox == null) return null;

		Vector3f centre = boundingBox.center;
		double scale = renderScale(spec());
		Vec3 local = new Vec3(centre.x() * scale, centre.y() * scale, centre.z() * scale);

		return Vec3.atLowerCornerOf(getBlockPos()).add(0.5, 0.0, 0.5).add(rotateVector(local, getBlockState().getValue(PvInverterBlock.FACING)));
	}

	/**
	 * How large this machine is drawn, restated from the renderer.
	 *
	 * Stated twice because the renderer is client-only and this runs on a dedicated server too, which is
	 * the same reason the turbine restates its tower offset in two places. Both copies are one line and
	 * both say they are a copy, which is the least bad of the arrangements available.
	 */
	private static double renderScale(InverterSpec spec) {
		if (spec.acPowerKw() >= 1000.0) return 1.0;
		if (spec.acPowerKw() <= 30.0) return 0.62;

		return 0.92;
	}

	private static Vec3 rotateVector(Vec3 vector, Direction facing) {
		double degrees = switch (facing) {
			case WEST -> 90.0;
			case SOUTH -> 180.0;
			case EAST -> 270.0;
			default -> 0.0;
		};

		return vector.yRot((float) Math.toRadians(degrees));
	}

	private void updateWirePositions() {
		ensureArraySizes();
		for (int i = 0; i < wirePositions.length; i++) {
			Vec3 calculated = calculateOrientedInsulatorCenter(i);
			if (calculated != null) {
				wirePositions[i] = calculated;
			}
		}
	}

	// ---- the tick ----

	public void serverTick() {
		if (!(level instanceof ServerLevel serverLevel)) return;

		updateWirePositions();

		InverterSpec spec = spec();
		if (--rescanCountdown <= 0) {
			rescan(serverLevel, spec);
			rescanCountdown = RESCAN_TICKS;
		}

		redstone.poll(this, this::onControlChanged);
		ambientTempC = GlobalWeatherManager.get(serverLevel).sample(worldPosition, 1).temperatureC();

		gatherAndConvert(serverLevel, spec);
		accumulateEnergy(serverLevel);
		updateTelemetry(serverLevel, spec);

		budget.open(Math.max(0.0, acPowerKw) * EnergyBridge.JOULES_PER_KW);
		EnergyBridge.emit(this, this, worldPosition, energyFaces());

		clientSync.throttled(this);
	}

	/**
	 * Works out what the plant is doing and pushes the operating point back to the arrays.
	 *
	 * The order is forced by the physics. The arrays cannot know what they are delivering until the
	 * inverter has decided how much it can take, and the inverter cannot decide until it knows what is
	 * on offer - so the offer is gathered first, the decision is made once, and the answer is handed
	 * back as a fraction of the maximum power point. That fraction is what makes clipping show up in
	 * the arrays' own current readings rather than only in the inverter's output.
	 */
	private void gatherAndConvert(ServerLevel serverLevel, InverterSpec spec) {
		availableDcKw = 0.0;
		double highestStringVoltage = 0.0;
		List<PvArrayBlockEntity> live = new ArrayList<>();

		for (BlockPos pos : arrays) {
			PvArrayBlockEntity array = LoadedBlockEntities.find(serverLevel, pos, PvArrayBlockEntity.class);
			// an array whose ground has gone out of memory is a part of the plant that is not there for
			// the moment, and a claim it cannot renew lapses at its end
			if (array == null) continue;
			// a closer inverter may have taken it since the last scan, and until this one rescans it
			// would otherwise go on counting an array it no longer owns
			if (!worldPosition.equals(array.inverterPos())) continue;

			live.add(array);
			availableDcKw += array.availableDcKw();
			highestStringVoltage = Math.max(highestStringVoltage, array.stringVoltage());
		}

		// the first array in the list is the nearest, because that is the order the scan claimed them
		// in - which makes it the one a real plant would have bolted its reference instruments to
		if (!live.isEmpty()) {
			referenceIrradiance = live.get(0).poaFront();
			referenceModuleTempC = live.get(0).moduleTempC();
		}

		boolean allowed = isRunning();
		// below the startup voltage there is nothing to track: the strings are lit but not lit enough,
		// which is why a real plant sits at exactly zero for a few minutes after sunrise rather than
		// producing a trickle. Above the window there is nothing to track either, and a real machine
		// locks out rather than tracking an input it cannot regulate - which is the failure mode a
		// string designed for a warmer site than it was built on actually has, on the coldest morning
		boolean trackable = highestStringVoltage >= spec.startupVolts() && spec.withinMpptWindow(highestStringVoltage);
		stringsOutOfWindow = availableDcKw > 0.0 && highestStringVoltage > 0.0 && !trackable;
		boolean awake = allowed && trackable && availableDcKw > 0.0;

		double ceiling = Math.min(spec.thermalLimitKw(ambientTempC), Math.max(0.0, activePowerLimitKw));
		derating = spec.derating(ambientTempC);

		if (!awake) {
			dcPowerKw = 0.0;
			// the night draw, reported as a negative output. It is there whether the machine is stopped or
			// merely waiting for the strings to come up, because what is drawing it is the controller and
			// the controller is on either way - which is exactly why a real plant's meter reads negative
			// overnight and why a stopped inverter is not a free inverter
			acPowerKw = -spec.nightDrawKw();
			efficiency = 0.0;
			clipping = false;
			dcVoltage = highestStringVoltage;
			dcCurrent = 0.0;
			for (PvArrayBlockEntity array : live) {
				array.setMpptFraction(0.0);
			}

			cabinetTempC = spec.cabinetTemperature(ambientTempC, 0.0);
			return;
		}

		clipping = spec.clipping(availableDcKw, ceiling);
		double wantedAc = spec.acPowerFromDcKw(availableDcKw, ceiling);
		// the direct current the machine actually pulls: what it needs to make that output. When it is
		// clipping this is less than the array could give, which is the maximum power point tracker
		// stepping off the peak
		efficiency = availableDcKw <= 0.0 ? 0.0 : Math.max(0.01, spec.efficiencyAt(wantedAc / spec.acPowerKw()));
		dcPowerKw = Math.min(availableDcKw, wantedAc / efficiency);
		acPowerKw = wantedAc;

		double fraction = availableDcKw <= 0.0 ? 0.0 : Mth.clamp(dcPowerKw / availableDcKw, 0.0, 1.0);
		for (PvArrayBlockEntity array : live) {
			array.setMpptFraction(fraction);
		}

		dcVoltage = highestStringVoltage;
		dcCurrent = dcVoltage <= 0.0 ? 0.0 : dcPowerKw * 1000.0 / dcVoltage;
		cabinetTempC = spec.cabinetTemperature(ambientTempC, acPowerKw / spec.acPowerKw());
	}

	/**
	 * Finds the arrays this inverter is wired to.
	 *
	 * Nearest first, up to the number of string terminals the machine actually has, because a real
	 * inverter runs out of inputs before it runs out of capacity - and an array with nowhere to plug in
	 * is a real situation with a real answer, which is to buy another inverter.
	 *
	 * The walk is over the block entities the loaded chunks already hold rather than over every block
	 * position in range. That is the difference between a few dozen map entries and five thousand block
	 * lookups, and it is what makes a two-second rescan free.
	 */
	private void rescan(ServerLevel serverLevel, InverterSpec spec) {
		releaseArrays();
		arrays.clear();
		stringsConnected = 0;

		int radius = ElectricityServerConfig.pvArrayRadius();
		List<PvArrayBlockEntity> candidates = new ArrayList<>();

		int minChunkX = (worldPosition.getX() - radius) >> 4;
		int maxChunkX = (worldPosition.getX() + radius) >> 4;
		int minChunkZ = (worldPosition.getZ() - radius) >> 4;
		int maxChunkZ = (worldPosition.getZ() + radius) >> 4;

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				if (!serverLevel.getChunkSource().hasChunk(chunkX, chunkZ)) continue;

				LevelChunk chunk = serverLevel.getChunk(chunkX, chunkZ);
				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					if (!(blockEntity instanceof PvArrayBlockEntity array)) continue;
					if (array.getBlockPos().distSqr(worldPosition) > (double) radius * radius) continue;

					candidates.add(array);
				}
			}
		}

		candidates.sort(Comparator.comparingDouble(array -> array.getBlockPos().distSqr(worldPosition)));

		// two limits, and which one binds depends on what the modules are. The terminal count is the
		// holes in the machine; the current limit is the copper behind them, and a designer usually
		// meets that one first - eighteen strings of a 210 mm cell module carry three hundred amps into
		// a machine whose nine trackers will take two hundred and thirty-four between them
		double currentCeiling = spec.mpptCount() * spec.maxCurrentPerMppt();
		double currentConnected = 0.0;
		double dcConnected = 0.0;

		for (PvArrayBlockEntity array : candidates) {
			PvArraySpec wanted = array.spec();
			int strings = wanted.strings();
			double current = wanted.stringCurrent(PvModuleSpec.STC_IRRADIANCE, PvModuleSpec.STC_TEMPERATURE) * strings;

			// three limits off the same datasheet, and any of them can be the one that bites
			if (stringsConnected + strings > spec.stringInputs()) continue;
			if (currentConnected + current > currentCeiling) continue;
			if (dcConnected + wanted.dcPowerKw() > spec.maxDcPowerKw()) continue;
			if (!array.claim(worldPosition)) continue;

			arrays.add(array.getBlockPos().immutable());
			stringsConnected += strings;
			currentConnected += current;
			dcConnected += wanted.dcPowerKw();
		}

		stringCurrentHeadroom = currentCeiling - currentConnected;
	}

	/**
	 * Runs the energy meters.
	 *
	 * Two counters, because both are on a real plant's front page and they answer different questions:
	 * today's tells an operator whether the weather has been kind, and the lifetime figure is what the
	 * plant is paid against. Today's resets when the day rolls over rather than on a timer, so it
	 * agrees with the sun.
	 *
	 * Only production is counted, not the night draw. That is the convention a generation meter uses -
	 * it is a one-way meter - and it is why a plant's reported yield and its net export differ by a few
	 * kilowatt hours a year.
	 */
	private void accumulateEnergy(ServerLevel serverLevel) {
		long day = serverLevel.getDayTime() / TICKS_PER_DAY;
		if (lastDay != day) {
			if (lastDay >= 0L) energyTodayKwh = 0.0;
			lastDay = day;
		}

		if (acPowerKw <= 0.0) return;

		// one tick is a twentieth of a second, and an hour is 3600 of them
		double kwh = acPowerKw / 20.0 / 3600.0;
		energyTodayKwh += kwh;
		energyLifetimeKwh += kwh;
	}

	/** The latest published snapshot. Safe to read from any thread; never null. */
	public Telemetry.Snapshot getTelemetry() {
		return telemetry;
	}

	private void updateTelemetry(ServerLevel serverLevel, InverterSpec spec) {
		telemetry = telemetrySimulator.sampleInverter(new SolarTelemetrySimulator.InverterSample(
				spec, acPowerKw, dcPowerKw, availableDcKw, dcVoltage, dcCurrent, efficiency, cabinetTempC,
				ambientTempC, referenceModuleTempC, referenceIrradiance, energyTodayKwh, energyLifetimeKwh,
				activePowerLimitKw, powerFactorSetpoint, isRunning(), clipping, derating,
				stoppedByComputer, stoppedByPlayer, isStoppedByRedstone(), arrays.size(), stringsConnected,
				stringCapacity(), stringCurrentHeadroom, dcAcRatio(), serverLevel.isRainingAt(worldPosition.above())));
	}

	// ---- readings ----

	public double availableDcKw() {
		return availableDcKw;
	}

	public double dcPowerKw() {
		return dcPowerKw;
	}

	/** Active power at the terminals, kW. Negative overnight, which is what the machine's own supply costs. */
	public double acPowerKw() {
		return acPowerKw;
	}

	public double dcVoltage() {
		return dcVoltage;
	}

	public double dcCurrent() {
		return dcCurrent;
	}

	public double cabinetTempC() {
		return cabinetTempC;
	}

	public double ambientTempC() {
		return ambientTempC;
	}

	public double efficiency() {
		return efficiency;
	}

	public boolean clipping() {
		return clipping;
	}

	public boolean derating() {
		return derating;
	}

	/** Whether the arrays are producing at a voltage this machine cannot track. */
	/** Direct-current input still free, in amps. */
	public double stringCurrentHeadroom() {
		return stringCurrentHeadroom;
	}

	public boolean stringsOutOfWindow() {
		return stringsOutOfWindow;
	}

	public int arraysConnected() {
		return arrays.size();
	}

	/** Where the arrays this inverter is wired to are. A copy, because callers arrive off other threads. */
	public List<BlockPos> arrayPositions() {
		return List.copyOf(arrays);
	}

	public int stringsConnected() {
		return stringsConnected;
	}

	public int stringCapacity() {
		return spec().stringInputs();
	}

	public double energyTodayKwh() {
		return energyTodayKwh;
	}

	public double energyLifetimeKwh() {
		return energyLifetimeKwh;
	}

	/** DC nameplate of everything wired in over the AC nameplate: the plant's own loading ratio. */
	public double dcAcRatio() {
		if (level == null) return 0.0;

		double dcNameplate = 0.0;
		for (BlockPos pos : arrays) {
			PvArrayBlockEntity array = LoadedBlockEntities.find(level, pos, PvArrayBlockEntity.class);
			if (array != null) {
				dcNameplate += array.spec().dcPowerKw();
			}
		}

		return spec().acPowerKw() <= 0.0 ? 0.0 : dcNameplate / spec().acPowerKw();
	}

	public double apparentKva() {
		return spec().apparentKva(Math.max(0.0, acPowerKw), powerFactorSetpoint);
	}

	public double reactiveKvar() {
		return spec().reactiveKvar(Math.max(0.0, acPowerKw), powerFactorSetpoint);
	}

	public double phaseCurrentA() {
		return spec().phaseCurrentA(apparentKva());
	}

	public double powerFactor() {
		return powerFactorSetpoint;
	}

	// ---- control ----

	public boolean isRunning() {
		return !stoppedByComputer && !stoppedByPlayer && !redstone.stopping();
	}

	public boolean isStoppedByComputer() {
		return stoppedByComputer;
	}

	public boolean isStoppedByPlayer() {
		return stoppedByPlayer;
	}

	public boolean isStoppedByRedstone() {
		return redstone.stopping();
	}

	public RedstoneMode getRedstoneMode() {
		return redstone.mode();
	}

	public double getActivePowerLimit() {
		return activePowerLimitKw;
	}

	public void setStoppedByComputer(boolean stopped) {
		if (stoppedByComputer == stopped) return;

		stoppedByComputer = stopped;
		onControlChanged();
	}

	public void setStoppedByPlayer(boolean stopped) {
		if (stoppedByPlayer == stopped) return;

		stoppedByPlayer = stopped;
		onControlChanged();
	}

	public void setRedstoneMode(RedstoneMode mode) {
		if (redstone.mode(mode)) {
			onControlChanged();
		}
	}

	public void setActivePowerLimit(double limitKw) {
		double clamped = Mth.clamp(limitKw, 0.0, spec().acPowerKw());
		if (activePowerLimitKw == clamped) return;

		activePowerLimitKw = clamped;
		onControlChanged();
	}

	/**
	 * Sets the power factor the machine is asked to hold.
	 *
	 * Clamped to what the machine can do, which is 0.8 either side of unity on everything in the
	 * catalogue. Asking for a deep power factor at full output is asking for more apparent power than
	 * the machine has, and the answer is that active power gives way - which is what a grid operator
	 * requesting reactive support is actually buying.
	 */
	public void setPowerFactor(double factor) {
		double clamped = Mth.clamp(Math.abs(factor), spec().minPowerFactor(), 1.0);
		if (powerFactorSetpoint == clamped) return;

		powerFactorSetpoint = clamped;
		onControlChanged();
	}

	private void onControlChanged() {
		setChanged();
		ClientSync.now(this);
	}

	// ---- energy out ----

	private List<Direction> energyFaces() {
		return List.of(Direction.DOWN, getBlockState().getValue(PvInverterBlock.FACING).getOpposite());
	}

	private boolean isEnergyFace(@Nullable Direction side) {
		return side == null || energyFaces().contains(side);
	}

	/** Gross production this tick in Joules, before anything claims it. */
	public double getGrossJoulesPerTick() {
		return Math.max(0.0, acPowerKw) * EnergyBridge.JOULES_PER_KW;
	}

	/**
	 * What the wire network may carry away this tick, in kW.
	 *
	 * Everything produced, less whatever another mod's cables already claimed. The subtraction is what
	 * stops the same Joule being spent twice, because the wire network reads a generator without ever
	 * debiting it - the same arrangement the turbines are under.
	 */
	public double getGeneratedPower() {
		return Math.max(0.0, acPowerKw - budget.claimed() / EnergyBridge.JOULES_PER_KW);
	}

	/**
	 * Whether this generator is putting a disturbance onto the network.
	 *
	 * Never, and that is worth stating rather than leaving as an unimplemented method. A turbine surges
	 * because a gust arrives at a rotor with tonnes of inertia and a gearbox behind it; an inverter has no
	 * moving parts at all and its whole purpose is to hold a clean waveform whatever the array does. A
	 * cloud crossing a solar plant is a smooth ramp rather than a shock, which is one of the few things
	 * photovoltaics are unambiguously better at than rotating machines.
	 */
	public boolean isSurging() {
		return false;
	}

	@Override
	public double getAvailableJoules() {
		if (level == null || level.isClientSide() || !ElectricityServerConfig.externalEnergyEnabled()) return 0.0;

		return budget.available();
	}

	@Override
	public double getMaxJoulesPerTick() {
		if (level == null || level.isClientSide() || !ElectricityServerConfig.externalEnergyEnabled()) return 0.0;

		double share = spec().acPowerKw() * EnergyBridge.JOULES_PER_KW * ElectricityServerConfig.pvExportFraction();
		return Math.min(share, ElectricityServerConfig.pvMaxJoulesPerTick());
	}

	@Override
	public double claimJoules(double joules, boolean simulate) {
		return budget.claim(joules, simulate);
	}

	@Nonnull
	@Override
	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
		if (isEnergyFace(side)) {
			if (cap == ForgeCapabilities.ENERGY) return forgeEnergy.cast();
			if (EnergyBridge.isMekanismEnergyCapability(cap)) return mekanismEnergy.cast();
		}

		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		forgeEnergy.invalidate();
		mekanismEnergy.invalidate();
	}

	// ---- persistence and syncing ----

	@Override
	protected void saveAdditional(@Nonnull CompoundTag tag) {
		super.saveAdditional(tag);

		// the meters are real accumulated state and have to survive a reload; everything else is a
		// reading, written only so a freshly loaded chunk shows a number rather than a zero
		tag.putDouble("energyToday", energyTodayKwh);
		tag.putDouble("energyLifetime", energyLifetimeKwh);
		tag.putLong("lastDay", lastDay);

		tag.putDouble("availableDc", availableDcKw);
		tag.putDouble("dcPower", dcPowerKw);
		tag.putDouble("acPower", acPowerKw);
		tag.putDouble("dcVoltage", dcVoltage);
		tag.putDouble("dcCurrent", dcCurrent);
		tag.putDouble("cabinetTemp", cabinetTempC);
		tag.putDouble("ambientTemp", ambientTempC);
		tag.putDouble("efficiency", efficiency);
		tag.putBoolean("clipping", clipping);
		tag.putBoolean("derating", derating);
		tag.putBoolean("stringsOutOfWindow", stringsOutOfWindow);
		tag.putInt("stringsConnected", stringsConnected);

		tag.putBoolean("stoppedByComputer", stoppedByComputer);
		tag.putBoolean("stoppedByPlayer", stoppedByPlayer);
		redstone.save(tag);
		tag.putDouble("activePowerLimitKw", activePowerLimitKw);
		tag.putDouble("powerFactor", powerFactorSetpoint);

		ListTag positions = new ListTag();
		for (BlockPos pos : arrays) {
			positions.add(LongTag.valueOf(pos.asLong()));
		}

		tag.put("arrays", positions);

		// an entry is empty whenever the insulator's bounding box could not be found, and on a dedicated
		// server that is always: every method that fills ObjBoundingBoxRegistry is client-only, so it is
		// permanently empty there. A placeholder keeps the list index-aligned with the array, which is
		// what the cabin and the power box already do
		ListTag wires = new ListTag();
		for (Vec3 pos : wirePositions) {
			CompoundTag entry = new CompoundTag();
			if (pos != null) {
				entry.putDouble("x", pos.x);
				entry.putDouble("y", pos.y);
				entry.putDouble("z", pos.z);
			}

			wires.add(entry);
		}

		tag.put("wirePositions", wires);

		ListTag ids = new ListTag();
		for (int id : insulatorIds) {
			ids.add(IntTag.valueOf(id));
		}

		tag.put("insulatorIds", ids);
	}

	@Override
	public void load(@Nonnull CompoundTag tag) {
		super.load(tag);

		energyTodayKwh = tag.getDouble("energyToday");
		energyLifetimeKwh = tag.getDouble("energyLifetime");
		lastDay = tag.contains("lastDay") ? tag.getLong("lastDay") : -1L;

		availableDcKw = tag.getDouble("availableDc");
		dcPowerKw = tag.getDouble("dcPower");
		acPowerKw = tag.getDouble("acPower");
		dcVoltage = tag.getDouble("dcVoltage");
		dcCurrent = tag.getDouble("dcCurrent");
		cabinetTempC = tag.getDouble("cabinetTemp");
		ambientTempC = tag.getDouble("ambientTemp");
		efficiency = tag.getDouble("efficiency");
		clipping = tag.getBoolean("clipping");
		derating = tag.getBoolean("derating");
		stringsOutOfWindow = tag.getBoolean("stringsOutOfWindow");
		stringsConnected = tag.getInt("stringsConnected");

		stoppedByComputer = tag.getBoolean("stoppedByComputer");
		stoppedByPlayer = tag.getBoolean("stoppedByPlayer");
		redstone.load(tag);
		// an inverter saved before this field existed is uncurtailed rather than limited to nothing,
		// which would silently switch it off on load
		activePowerLimitKw = tag.contains("activePowerLimitKw") ? tag.getDouble("activePowerLimitKw") : spec().acPowerKw();
		powerFactorSetpoint = tag.contains("powerFactor") ? tag.getDouble("powerFactor") : 1.0;

		arrays.clear();
		ListTag positions = tag.getList("arrays", 4);
		for (int i = 0; i < positions.size(); i++) {
			arrays.add(BlockPos.of(((LongTag) positions.get(i)).getAsLong()));
		}

		ensureArraySizes();
		if (tag.contains("wirePositions")) {
			ListTag wires = tag.getList("wirePositions", 10);
			for (int i = 0; i < Math.min(wires.size(), wirePositions.length); i++) {
				CompoundTag entry = wires.getCompound(i);
				if (entry.contains("x")) {
					wirePositions[i] = new Vec3(entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z"));
				}
			}
		}

		if (tag.contains("insulatorIds")) {
			ListTag ids = tag.getList("insulatorIds", 3);
			for (int i = 0; i < Math.min(ids.size(), insulatorIds.length); i++) {
				insulatorIds[i] = ids.getInt(i);
				InsulatorIdRegistry.registerExistingId(insulatorIds[i]);
			}
		}

		generateInsulatorIds();
		updateWirePositions();
	}

	@Override
	public void handleUpdateTag(CompoundTag tag) {
		load(tag);
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = new CompoundTag();
		saveAdditional(tag);
		return tag;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}


	@Override
	public void onLoad() {
		super.onLoad();
		ClientTracking.track(this);
		if (level != null && level.isClientSide()) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
				updateWirePositions();
				InsulatorLookup.register(this, getInsulatorIds());
				WireManagerClient.invalidateInsulatorCache(getInsulatorIds());
			});
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		ClientTracking.untrack(this);
		InsulatorIdRegistry.releaseIds(getInsulatorIds());
		if (level != null && level.isClientSide()) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
				InsulatorLookup.unregister(getInsulatorIds());
				WireManagerClient.invalidateInsulatorCache(getInsulatorIds());
			});
		}
	}

	/**
	 * Tells the arrays this inverter is letting them go.
	 *
	 * Called when the cabinet is broken and before every rescan, and deliberately not when its chunk
	 * unloads: an unloading block entity must not reach into another chunk, and an array does not need
	 * telling anyway, because a claim it stops hearing about lapses on its own.
	 */
	void releaseArrays() {
		for (BlockPos pos : arrays) {
			PvArrayBlockEntity array = LoadedBlockEntities.find(level, pos, PvArrayBlockEntity.class);
			if (array != null) {
				array.releaseClaim(worldPosition);
			}
		}
	}
}
