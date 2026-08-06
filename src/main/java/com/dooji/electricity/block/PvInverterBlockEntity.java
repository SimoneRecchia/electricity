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
import com.dooji.electricity.api.power.ConductorSpec;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.ElectricityServerConfig;
import com.dooji.electricity.api.power.CombinerSpec;
import com.dooji.electricity.main.registry.CableCatalog;
import com.dooji.electricity.main.registry.CombinerCatalog;
import com.dooji.electricity.main.registry.InverterCatalog;
import com.dooji.electricity.main.registry.ObjBlockDefinition;
import com.dooji.electricity.main.registry.ObjDefinitions;
import com.dooji.electricity.main.weather.GlobalWeatherManager;
import com.dooji.electricity.power.SolarTelemetrySimulator;
import com.dooji.electricity.wire.InsulatorIdRegistry;
import com.dooji.electricity.wire.InsulatorPartHelper;
import com.dooji.electricity.wire.InsulatorHost;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.DistExecutor;
import org.joml.Vector3f;

/** The inverter: the whole electrical side of a photovoltaic plant, in one block. */
public class PvInverterBlockEntity extends BlockEntity implements InsulatorHost, IEnergyBudget {
	/** How often the plant is surveyed for arrays again, in ticks. */
	private static final int RESCAN_TICKS = 40;
	/** Ticks in a day, for the energy counters that reset with it. */
	private static final long TICKS_PER_DAY = 24000L;

	private final List<BlockPos> arrays = new ArrayList<>();
	/** The combiner boxes wired to this cabinet on trunk cable. */
	private final List<BlockPos> combiners = new ArrayList<>();
	private int rescanCountdown = 0;
	/** The box fitted inside the cabinet, if any. */
	private CombinerSpec integrated = null;

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
	/** The strings are lit but at a voltage this machine cannot track. */
	private boolean stringsOutOfWindow = false;
	private int stringsConnected = 0;
	/** Direct-current input the machine has left, in amps. */
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

	/** The wire fitting on top of the cabinet. */
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

	/** Which machine this is. */
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

	/** Where the fitting is in the world, with the cabinet's own scale applied. */
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

		return Vec3.atLowerCornerOf(getBlockPos()).add(0.5, 0.0, 0.5).add(ModelFacing.turned(local, PvInverterBlock.AUTHORED, getBlockState().getValue(PvInverterBlock.FACING)));
	}

	/** How large this machine is drawn, restated from the renderer. */
	private static double renderScale(InverterSpec spec) {
		if (spec.acPowerKw() >= 1000.0) return 1.0;
		if (spec.acPowerKw() <= 30.0) return 0.62;

		return 0.92;
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

	/** Works out what the plant is doing and pushes the operating point back to the arrays. */
	private void gatherAndConvert(ServerLevel serverLevel, InverterSpec spec) {
		availableDcKw = 0.0;
		double highestStringVoltage = 0.0;
		List<PvArrayBlockEntity> live = new ArrayList<>();

		for (BlockPos pos : arrays) {
			PvArrayBlockEntity array = LoadedBlockEntities.find(serverLevel, pos, PvArrayBlockEntity.class);
			// an array whose ground has gone out of memory is a part of the plant that is not there for
			// the moment, and a claim it cannot renew lapses at its end
			if (array == null) continue;
			// a shorter run may have taken it since the last scan, and until this one rescans it would
			// otherwise go on counting an array it no longer owns
			if (!worldPosition.equals(array.collectorPos())) continue;

			live.add(array);
			// what arrives, not what the modules made: the difference is burnt in the run, and on a long
			// home run it is a real percent or two rather than a rounding error
			availableDcKw += array.offeredDcKw();
			highestStringVoltage = Math.max(highestStringVoltage, array.stringVoltageAtCollector());
		}

		// the first array in the list is the nearest, because that is the order the scan claimed them
		// in - which makes it the one a real plant would have bolted its reference instruments to
		if (!live.isEmpty()) {
			referenceIrradiance = live.get(0).poaFront();
			referenceModuleTempC = live.get(0).moduleTempC();
		}

		List<PvCombinerBlockEntity> liveBoxes = new ArrayList<>();
		for (BlockPos pos : combiners) {
			PvCombinerBlockEntity box = LoadedBlockEntities.find(serverLevel, pos, PvCombinerBlockEntity.class);
			if (box == null) continue;
			if (!worldPosition.equals(box.inverterPos())) continue;

			liveBoxes.add(box);
			availableDcKw += box.offeredDcKw();
			highestStringVoltage = Math.max(highestStringVoltage, box.busVoltageAtInverter());
		}

		boolean allowed = isRunning();
		// below the startup voltage there is nothing to track: the strings are lit but not lit enough,
		boolean trackable = highestStringVoltage >= spec.startupVolts() && spec.withinMpptWindow(highestStringVoltage);
		stringsOutOfWindow = availableDcKw > 0.0 && highestStringVoltage > 0.0 && !trackable;
		boolean awake = allowed && trackable && availableDcKw > 0.0;

		double ceiling = Math.min(spec.thermalLimitKw(ambientTempC), Math.max(0.0, activePowerLimitKw));
		derating = spec.derating(ambientTempC);

		if (!awake) {
			dcPowerKw = 0.0;
			// the night draw, reported as a negative output.
			acPowerKw = -spec.nightDrawKw();
			efficiency = 0.0;
			clipping = false;
			dcVoltage = highestStringVoltage;
			dcCurrent = 0.0;
			for (PvArrayBlockEntity array : live) {
				array.setMpptFraction(0.0);
			}

			for (PvCombinerBlockEntity box : liveBoxes) {
				box.setMpptFraction(0.0);
			}

			cabinetTempC = spec.cabinetTemperature(ambientTempC, 0.0);
			return;
		}

		clipping = spec.clipping(availableDcKw, ceiling);
		double wantedAc = spec.acPowerFromDcKw(availableDcKw, ceiling);
		// the direct current the machine actually pulls: what it needs to make that output.
		efficiency = availableDcKw <= 0.0 ? 0.0 : Math.max(0.01, spec.efficiencyAt(wantedAc / spec.acPowerKw()));
		dcPowerKw = Math.min(availableDcKw, wantedAc / efficiency);
		acPowerKw = wantedAc;

		double fraction = availableDcKw <= 0.0 ? 0.0 : Mth.clamp(dcPowerKw / availableDcKw, 0.0, 1.0);
		for (PvArrayBlockEntity array : live) {
			array.setMpptFraction(fraction);
		}

		// the boxes get the same fraction and hand it on to their own strings
		// visible in the current reading of every module behind every box
		for (PvCombinerBlockEntity box : liveBoxes) {
			box.setMpptFraction(fraction);
		}

		dcVoltage = highestStringVoltage;
		dcCurrent = dcVoltage <= 0.0 ? 0.0 : dcPowerKw * 1000.0 / dcVoltage;
		cabinetTempC = spec.cabinetTemperature(ambientTempC, acPowerKw / spec.acPowerKw());
	}

	/** Finds the arrays this inverter is wired to, by following the cable. */
	private void rescan(ServerLevel serverLevel, InverterSpec spec) {
		releaseArrays();
		arrays.clear();
		combiners.clear();
		stringsConnected = 0;

		// two limits, and which one binds depends on what the modules are.
		double currentCeiling = spec.mpptCount() * spec.maxCurrentPerMppt();
		double currentConnected = 0.0;
		double dcConnected = 0.0;

		for (DcNetwork.Reach reach : reachableArrays(serverLevel, spec)) {
			PvArrayBlockEntity array = LoadedBlockEntities.find(serverLevel, reach.pos(), PvArrayBlockEntity.class);
			if (array == null) continue;

			PvArraySpec wanted = array.spec();
			int strings = wanted.strings();
			double current = wanted.stringCurrent(PvModuleSpec.STC_IRRADIANCE, PvModuleSpec.STC_TEMPERATURE) * strings;

			// three limits off the same datasheet, and any of them can be the one that bites
			if (stringsConnected + strings > stringInputs(spec)) continue;
			if (currentConnected + current > currentCeiling) continue;
			if (dcConnected + wanted.dcPowerKw() > spec.maxDcPowerKw()) continue;
			// and the fitted box's own four
			if (integrated != null
					&& integrated.accepts(wanted, stringsConnected, currentConnected, integrated.outputAmps()) != CombinerSpec.Refusal.NONE) {
				continue;
			}

			if (!array.claim(worldPosition, reach, CableCatalog.STRING_6)) continue;

			arrays.add(array.getBlockPos().immutable());
			stringsConnected += strings;
			currentConnected += current;
			dcConnected += wanted.dcPowerKw();
		}

		// then the boxes, on the other cable.
		// a terminal count on a central machine means: strings arriving through combiner boxes
		for (DcNetwork.Reach reach : reachableCombiners(serverLevel, spec)) {
			PvCombinerBlockEntity box = LoadedBlockEntities.find(serverLevel, reach.pos(), PvCombinerBlockEntity.class);
			if (box == null) continue;

			if (stringsConnected + box.stringsConnected() > stringInputs(spec)) continue;
			if (currentConnected + box.designAmps() > currentCeiling) continue;
			if (dcConnected + box.nameplateDcKw() > spec.maxDcPowerKw()) continue;
			if (!box.claim(worldPosition, reach, CableCatalog.TRUNK_240)) continue;

			combiners.add(box.getBlockPos().immutable());
			stringsConnected += box.stringsConnected();
			currentConnected += box.designAmps();
			dcConnected += box.nameplateDcKw();
		}

		stringCurrentHeadroom = currentCeiling - currentConnected;
	}

	/** String terminals the machine has to offer. */
	private int stringInputs(InverterSpec spec) {
		if (spec.stringTerminals() || integrated == null) return spec.stringInputs();

		return Math.min(spec.stringInputs(), integrated.fusedInputs());
	}

	/** Every set of leads a run of string cable reaches from this cabinet */
	private List<DcNetwork.Reach> reachableArrays(ServerLevel serverLevel, InverterSpec spec) {
		if (!spec.stringTerminals() && integrated == null) return List.of();

		return PvStrings.reachable(serverLevel, worldPosition, CableCatalog.STRING_6,
				ElectricityServerConfig.maxCableRun());
	}

	/** Every combiner box a run of trunk cable reaches */
	private List<DcNetwork.Reach> reachableCombiners(ServerLevel serverLevel, InverterSpec spec) {
		if (!spec.trunkTerminals()) return List.of();

		return DcNetwork.reachable(serverLevel, worldPosition, CableCatalog.TRUNK_240,
				state -> state.getBlock() instanceof PvCombinerBlock, ElectricityServerConfig.maxCableRun());
	}

	/** The box fitted inside the cabinet, or null. */
	@Nullable
	public CombinerSpec integratedCombiner() {
		return integrated;
	}

	/** Records the box a player has just worked into the cabinet. */
	public void fitCombiner(CombinerSpec spec) {
		integrated = spec;
		rescanCountdown = 0;
		setChanged();
		ClientSync.now(this);
	}

	/** Runs the energy meters. */
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

	/** The latest published snapshot. */
	public Telemetry.Snapshot getTelemetry() {
		return telemetry;
	}

	private void updateTelemetry(ServerLevel serverLevel, InverterSpec spec) {
		telemetry = telemetrySimulator.sampleInverter(new SolarTelemetrySimulator.InverterSample(
				spec, acPowerKw, dcPowerKw, availableDcKw, dcVoltage, dcCurrent, efficiency, cabinetTempC,
				ambientTempC, referenceModuleTempC, referenceIrradiance, energyTodayKwh, energyLifetimeKwh,
				activePowerLimitKw, powerFactorSetpoint, isRunning(), clipping, derating,
				stoppedByComputer, stoppedByPlayer, isStoppedByRedstone(), arrays.size(), stringsConnected, combiners.size(),
				stringCapacity(), stringCurrentHeadroom, dcAcRatio(), serverLevel.isRainingAt(worldPosition.above())));
	}

	// ---- readings ----

	public double availableDcKw() {
		return availableDcKw;
	}

	public double dcPowerKw() {
		return dcPowerKw;
	}

	/** Active power at the terminals, kW. */
	public double acPowerKw() {
		return acPowerKw;
	}

	/** A machine that makes power rather than passing it reads what it is sending out. */
	@Override
	public double getCurrentPower() {
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

	/** Where the arrays this inverter is wired to are. */
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

	/** Sets the power factor the machine is asked to hold. */
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

	/** Gross production this tick in Joules */
	public double getGrossJoulesPerTick() {
		return Math.max(0.0, acPowerKw) * EnergyBridge.JOULES_PER_KW;
	}

	/** What the wire network may carry away this tick, in kW. */
	public double getGeneratedPower() {
		return Math.max(0.0, acPowerKw - budget.claimed() / EnergyBridge.JOULES_PER_KW);
	}

	/** Whether this generator is putting a disturbance onto the network. */
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

		// the meters are real accumulated state and have to survive a reload
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

		long[] boxes = new long[combiners.size()];
		for (int i = 0; i < boxes.length; i++) {
			boxes[i] = combiners.get(i).asLong();
		}

		tag.putLongArray("combiners", boxes);
		if (integrated != null) tag.putString("combiner", integrated.id().toString());
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

		combiners.clear();
		for (long packed : tag.getLongArray("combiners")) {
			combiners.add(BlockPos.of(packed));
		}

		integrated = tag.contains("combiner") ? CombinerCatalog.byId(new ResourceLocation(tag.getString("combiner"))) : null;

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

	/** Tells the arrays this inverter is letting them go. */
	void releaseArrays() {
		for (BlockPos pos : arrays) {
			PvArrayBlockEntity array = LoadedBlockEntities.find(level, pos, PvArrayBlockEntity.class);
			if (array != null) {
				array.releaseClaim(worldPosition);
			}
		}

		for (BlockPos pos : combiners) {
			PvCombinerBlockEntity box = LoadedBlockEntities.find(level, pos, PvCombinerBlockEntity.class);
			if (box != null) {
				box.releaseClaim(worldPosition);
			}
		}
	}

	@Override
	public String fittingType() {
		return InsulatorPartHelper.TYPE_PV_INVERTER;
	}

	/** A generator, like a turbine. */
	@Override
	public boolean feeds(InsulatorHost other) {
		return other instanceof ElectricCabinBlockEntity || other instanceof PvInverterBlockEntity
				|| other instanceof TransformerBlockEntity;
	}

	/** An inverter's output goes to the plant's collector network, which is 33 kV. */
	@Override
	public boolean takesConductor(ConductorSpec conductor, int index) {
		return conductor.voltageClass() == ConductorSpec.VoltageClass.MEDIUM;
	}

	@Override
	public String powerType(String partName) {
		return "output";
	}

	/** Nothing to tell it: an inverter's reading is what it makes, not what reaches it. */
	@Override
	public void deliverPower(double power) {
	}
}
