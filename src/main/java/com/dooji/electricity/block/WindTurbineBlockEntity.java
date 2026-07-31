package com.dooji.electricity.block;

import com.dooji.electricity.api.WorldConditions;
import com.dooji.electricity.api.power.IEnergyBudget;
import com.dooji.electricity.api.power.RedstoneMode;
import com.dooji.electricity.api.power.TickBudget;
import com.dooji.electricity.api.power.TurbineSpec;
import com.dooji.electricity.api.power.TurbineTelemetry;
import com.dooji.electricity.client.TrackedBlockEntities;
import com.dooji.electricity.client.render.obj.ObjBoundingBoxRegistry;
import com.dooji.electricity.client.wire.InsulatorLookup;
import com.dooji.electricity.client.wire.WireManagerClient;
import com.dooji.electricity.compat.energy.EnergyBridge;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.ElectricityServerConfig;
import com.dooji.electricity.main.registry.ObjBlockDefinition;
import com.dooji.electricity.main.registry.ObjDefinitions;
import com.dooji.electricity.main.registry.TurbineCatalog;
import com.dooji.electricity.main.weather.GlobalWeatherManager;
import com.dooji.electricity.main.weather.WeatherSnapshot;
import com.dooji.electricity.power.TurbineTelemetrySimulator;
import com.dooji.electricity.wire.InsulatorIdRegistry;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.DistExecutor;
import org.joml.Vector3f;

public class WindTurbineBlockEntity extends BlockEntity implements IEnergyBudget {
	private Vec3[] wirePositions;
	private int[] insulatorIds;

	private float rotationSpeed1 = 0.0f;
	private float rotationSpeed2 = 0.0f;
	private float rotation1 = 0.0f;
	private float rotation2 = 0.0f;

	private double generatedPower = 0.0;
	private double currentPower = 0.0;
	/** What the rotor is in right now: the mean wind plus its turbulence. Drives the power curve. */
	private float lastEffectiveWindSpeed = 0.0f;
	private float lastAlignedWindSpeed = 0.0f;
	/**
	 * Ten-minute mean and three-second gust at the hub.
	 *
	 * The controller supervises on these rather than on the instantaneous wind, exactly as a
	 * real one does: a machine that tripped every time a gust brushed its cut-out would spend
	 * a windy afternoon starting and stopping, and one that only watched the mean would ride a
	 * squall it should have shut down for. So the mean decides the shutdown and the gust
	 * decides the trip.
	 */
	private float meanWindSpeed = 0.0f;
	private float gustWindSpeed = 0.0f;
	private float windDirection = 0.0f;
	/** Turbulence intensity: the standard deviation of the wind over its mean, so 0.13 is ordinary. */
	private double turbulence = 0.0;
	/**
	 * Exponent of the wind profile at this site, synced because the panel quotes what one more
	 * block of tower is worth and only the server knows the ground the tower stands on.
	 */
	private float shearExponent = 0.14f;
	private double ambientTempC = 15.0;
	private double airPressureHpa = WorldConditions.SEA_LEVEL_PRESSURE;
	private boolean cutOutActive = false;
	private boolean yawInitialized = false;
	private float yaw = 0.0f;
	private float lastSentYaw = Float.NaN;
	private long lastSyncTick = 0L;
	/**
	 * Smallest gap below the cut-out at which a storm shutdown may release.
	 *
	 * Releasing at the storm onset rather than just under the cut-out brings the machine
	 * back through the derating ramp at 80% instead of slamming straight to full output,
	 * which is the same strain on the drivetrain that derating exists to avoid. A machine
	 * with no storm control has its onset sitting on its cut-out and would chatter there,
	 * so this is the floor that keeps it honest.
	 */
	private static final double MINIMUM_CUT_OUT_HYSTERESIS = 3.0;
	/**
	 * How far past the cut-out a gust has to reach to trip the machine on its own.
	 *
	 * A fifth, so a machine rated to 25 m/s over ten minutes also comes off load for a 30 m/s
	 * gust, which is the pair of limits real datasheets print.
	 */
	private static final double GUST_TRIP_RATIO = 1.2;
	private static final float YAW_STEP = 0.25f;
	private static final float YAW_DEADBAND = 7.5f;

	// this tick's offer to other mods' energy systems, and how much of it they took
	private final TickBudget budget = new TickBudget();
	private final LazyOptional<IEnergyStorage> forgeEnergy = LazyOptional.of(() -> EnergyBridge.forgeEnergyView(this));
	private final LazyOptional<?> mekanismEnergy = EnergyBridge.createMekanismHandler(this);

	// Telemetry is published as one finished snapshot per tick rather than read
	// field by field, because ComputerCraft calls in from the computer thread: a
	// reader would otherwise race the server thread and could mix values from two
	// different ticks. Volatile makes the completed snapshot visible atomically.
	private final TurbineTelemetrySimulator telemetrySimulator = new TurbineTelemetrySimulator();
	private volatile TurbineTelemetry telemetry = TurbineTelemetry.EMPTY;
	private double yawCableTwist = 0.0;
	private boolean yawing = false;

	// Control state. Volatile because a ComputerCraft program reads it from the
	// computer thread; writes come back through the server thread, see the setters.
	private volatile boolean stoppedByComputer = false;
	/**
	 * The brake a player applied from the control panel.
	 *
	 * Kept apart from {@link #stoppedByComputer} rather than sharing it, because a program
	 * asks {@code isStopped()} to find out whether *it* stopped the machine. Folding a
	 * player's hand-stop into the same flag would answer yes to a program that had issued
	 * no such command, and a control loop written on that answer would then leave a
	 * turbine down believing it had put it there.
	 */
	private volatile boolean stoppedByPlayer = false;
	private volatile RedstoneMode redstoneMode = RedstoneMode.DISABLED;
	private volatile boolean redstonePowered = false;
	private volatile double activePowerLimitKw;
	/** What the wind alone would have produced, before curtailment. */
	private double uncappedPower = 0.0;

	/** Air density at the nacelle, from ambient temperature. Cold air is denser and carries more power. */
	private double airDensity = WorldConditions.REFERENCE_AIR_DENSITY;

	public WindTurbineBlockEntity(BlockPos pos, BlockState state) {
		super(getBlockEntityType(), pos, state);
		this.activePowerLimitKw = spec().ratedPowerKw();
		ensureArraySizes();
		initializeWirePositions();
		generateInsulatorIds();
	}

	/**
	 * Which machine this is.
	 *
	 * Read back off the block rather than persisted, so a placed turbine cannot disagree
	 * with the item that placed it and there is no saved model id to migrate. The
	 * fallback only comes up if a turbine somehow outlives its own block.
	 */
	public TurbineSpec spec() {
		if (getBlockState().getBlock() instanceof WindTurbineBlock turbine) return turbine.spec();

		return TurbineCatalog.fallback();
	}

	/**
	 * How many tower segments stand under the machine.
	 *
	 * Counted out of the world rather than stored, which is the whole reason the tower is
	 * real blocks: the height the physics uses and the tower a player can walk up are one
	 * fact with one source, so they cannot come apart. Cheap enough to ask every tick -
	 * thirteen block lookups is less than a redstone wire does idling.
	 *
	 * Both sides count for themselves, so none of this needs syncing.
	 */
	public int getTowerSegments() {
		if (level == null) return spec().minTowerSegments();

		return spec().clampTowerSegments(TurbineTowerBlock.countBelow(level, worldPosition));
	}

	public double getHubHeightM() {
		return spec().hubHeightM(getTowerSegments());
	}

	public double getAirDensity() {
		return airDensity;
	}

	/**
	 * The output the server last published, in kW, without recomputing it.
	 *
	 * {@link #getGeneratedPower()} runs the generation model again, which is what the wire
	 * network wants but wrong for a readout on the client: the client has no air density of
	 * its own, so it would recompute a slightly different number than the machine is
	 * actually making. This returns what was synced.
	 */
	public double getReportedPowerKw() {
		return Math.max(0.0, generatedPower);
	}

	/** What the wind offered before the curtailment setpoint took anything off it, in kW. */
	public double getUncappedPower() {
		return uncappedPower;
	}


	private static BlockEntityType<WindTurbineBlockEntity> getBlockEntityType() {
		return Electricity.WIND_TURBINE_BLOCK_ENTITY.get();
	}

	private ObjBlockDefinition definition() {
		return ObjDefinitions.get(getBlockState().getBlock());
	}

	private int insulatorCount() {
		ObjBlockDefinition definition = definition();
		if (definition != null && !definition.insulators().isEmpty()) return definition.insulators().size();
		return 0;
	}

	private String insulatorName(int index) {
		ObjBlockDefinition definition = definition();
		if (definition != null && index < definition.insulators().size()) return definition.insulators().get(index);
		return null;
	}

	private void ensureArraySizes() {
		int count = insulatorCount();
		if (count <= 0) count = 0;
		if (wirePositions == null || wirePositions.length != count) {
			Vec3[] copy = new Vec3[count];
			if (wirePositions != null) {
				System.arraycopy(wirePositions, 0, copy, 0, Math.min(wirePositions.length, count));
			}
			wirePositions = copy;
		}

		if (insulatorIds == null || insulatorIds.length != count) {
			int[] ids = new int[count];
			if (insulatorIds != null) {
				System.arraycopy(insulatorIds, 0, ids, 0, Math.min(insulatorIds.length, count));
			}
			insulatorIds = ids;
		}
	}

	private void initializeWirePositions() {
		ensureArraySizes();
		for (int i = 0; i < wirePositions.length; i++) {
			wirePositions[i] = calculateOrientedInsulatorCenter(i);
		}
	}

	private void generateInsulatorIds() {
		ensureArraySizes();
		for (int i = 0; i < insulatorIds.length; i++) {
			insulatorIds[i] = InsulatorIdRegistry.claimId();
		}
	}

	public static void removeInsulatorIds(int[] ids) {
		InsulatorIdRegistry.releaseIds(ids);
	}

	public Vec3 getWirePosition(int index) {
		if (index >= 0 && index < wirePositions.length) return wirePositions[index];

		return null;
	}

	public void setWirePosition(int index, Vec3 position) {
		if (index >= 0 && index < wirePositions.length) {
			wirePositions[index] = position;
		}
	}

	public int getInsulatorId(int index) {
		if (index >= 0 && index < insulatorIds.length) return insulatorIds[index];

		return -1;
	}

	public int[] getInsulatorIds() {
		return insulatorIds.clone();
	}

	public float getRotation1() {
		return rotation1;
	}

	public float getRotation2() {
		return rotation2;
	}

	/** The wind the rotor is in right now, gusts and lulls included. */
	public float getWindSpeed() {
		return lastEffectiveWindSpeed;
	}

	/** Ten-minute mean at the hub: the figure a wind report would quote and the panel shows. */
	public float getMeanWindSpeed() {
		return meanWindSpeed;
	}

	/** Three-second gust at the hub, which is the second limit a shutdown is judged against. */
	public float getGustWindSpeed() {
		return gustWindSpeed;
	}

	/** Turbulence intensity at the hub, sigma over mean: 0.08 over water, 0.20 over forest. */
	public double getTurbulenceIntensity() {
		return turbulence;
	}

	/** Exponent of the wind profile here, which is what another block of tower is worth. */
	public float getShearExponent() {
		return shearExponent;
	}
	/**
	 * What the wire network may carry away this tick: everything generated, minus
	 * whatever another mod's cables already claimed. The subtraction is what stops
	 * the same Joule being spent twice, because the wire network reads a generator
	 * without ever debiting it.
	 */
	public double getGeneratedPower() {
		updateGeneratedPower();
		return Math.max(0.0, generatedPower - budget.claimed() / EnergyBridge.JOULES_PER_KW);
	}

	public boolean isSurging() {
		// still possible right through the storm-control band, since the machine is
		// running there; only a real shutdown rules a surge out.
		//
		// The threshold is on real turbulence intensity now the weather model reports one:
		// 0.22 is rough air, the sort a forest or a squall makes, and the old 0.35 would
		// never have been reached again - it belonged to a scale that ran to 1.0
		return turbulence >= 0.22 && meanWindSpeed < spec().cutOutSpeed() && !isBraked();
	}

	public double getCurrentPower() {
		return currentPower;
	}

	public void setCurrentPower(double power) {
		this.currentPower = power;
	}

	private void updateGeneratedPower() {
		if (isBraked()) {
			uncappedPower = 0.0;
			generatedPower = 0.0;
			return;
		}

		// the cut-in, the plateau and the storm derating all live in the model's own curve, so
		// this is the whole generation model. Air density goes in with it, which is why a
		// turbine on a freezing night out-produces the same machine on a hot afternoon in
		// identical wind.
		//
		// The wind going in is the instantaneous one, not the mean, so the output moves the way
		// a real machine's does. The cut-out is not applied here because the controller above
		// has already decided that, on the mean and the gust, with hysteresis on both.
		uncappedPower = spec().powerWhileRunningKw(Math.max(0.0, lastAlignedWindSpeed), airDensity);
		generatedPower = Math.min(uncappedPower, Math.max(0.0, activePowerLimitKw));
	}

	/**
	 * Wind speed at which a storm shutdown releases.
	 *
	 * The storm onset, so the machine comes back through the derating ramp at 80% rather
	 * than slamming straight to full output. A machine with no storm control has its
	 * onset sitting on its cut-out, where that rule would leave no hysteresis at all and
	 * the brake would chatter, so the floor applies instead.
	 */
	private static double cutOutResetSpeed(TurbineSpec spec) {
		return Math.min(spec.stormOnsetSpeed(), spec.cutOutSpeed() - MINIMUM_CUT_OUT_HYSTERESIS);
	}

	// ---- control ----

	/**
	 * Whether the rotor is held stopped, for any reason: the machine protecting
	 * itself in a gale, a command from a computer, or a redstone signal. Everything
	 * that should not happen on a stopped turbine keys off this rather than off the
	 * wind cut-out alone.
	 */
	public boolean isBraked() {
		return cutOutActive || stoppedByComputer || stoppedByPlayer || !redstoneMode.allowsRunning(redstonePowered);
	}

	public boolean isRunning() {
		return !isBraked();
	}

	public boolean isStoppedByComputer() {
		return stoppedByComputer;
	}

	public boolean isStoppedByPlayer() {
		return stoppedByPlayer;
	}

	/** Applies or releases the hand brake. Server thread only, like the other controls. */
	public void setStoppedByPlayer(boolean stopped) {
		if (stoppedByPlayer == stopped) return;

		stoppedByPlayer = stopped;
		onControlChanged();
	}

	public boolean isStoppedByRedstone() {
		return !redstoneMode.allowsRunning(redstonePowered);
	}

	public boolean isWindCutOut() {
		return cutOutActive;
	}

	public RedstoneMode getRedstoneMode() {
		return redstoneMode;
	}

	public double getActivePowerLimit() {
		return activePowerLimitKw;
	}

	/**
	 * Stops or releases the turbine. Must be called from the server thread: it marks
	 * the block entity dirty and pushes the new state to clients so the rotor stops
	 * on screen too.
	 */
	public void setStoppedByComputer(boolean stopped) {
		if (stoppedByComputer == stopped) return;

		stoppedByComputer = stopped;
		onControlChanged();
	}

	public void setRedstoneMode(RedstoneMode mode) {
		if (mode == null || redstoneMode == mode) return;

		redstoneMode = mode;
		onControlChanged();
	}

	/** Curtailment setpoint in kW, clamped to what the machine can actually produce. */
	public void setActivePowerLimit(double limitKw) {
		double clamped = Mth.clamp(limitKw, 0.0, spec().ratedPowerKw());
		if (activePowerLimitKw == clamped) return;

		activePowerLimitKw = clamped;
		onControlChanged();
	}

	private void onControlChanged() {
		setChanged();
		// pushed immediately rather than waiting for the periodic sync, so a stop
		// command is visible on the rotor at once instead of up to half a second later
		syncStateToClients();
	}

	private void pollRedstone() {
		if (level == null || level.isClientSide()) return;

		boolean powered = level.hasNeighborSignal(worldPosition);
		if (powered == redstonePowered) return;

		redstonePowered = powered;
		// only matters visually when the mode actually reacts to redstone
		if (redstoneMode != RedstoneMode.DISABLED) {
			onControlChanged();
		} else {
			setChanged();
		}
	}

	/** Gross production before the output cap and before anything claims it, in Joules per tick. */
	public double getGrossJoulesPerTick() {
		return Math.max(0.0, generatedPower) * EnergyBridge.JOULES_PER_KW;
	}

	/**
	 * The latest published snapshot. Safe to read from any thread; never null.
	 */
	public TurbineTelemetry getTelemetry() {
		return telemetry;
	}

	private void updateTelemetry() {
		if (level == null || level.isClientSide()) return;

		// precipitation is checked at this position rather than globally, the same way
		// GlobalWeatherManager samples it: it is not raining inside a desert or under
		// a roof, and the thermometer should agree with what is actually overhead
		boolean precipitating = level.isRainingAt(worldPosition.above());
		boolean storming = level.isThundering() && precipitating;
		TurbineSpec spec = spec();
		// the machine is not giving everything it could: braked, pitching out above the
		// rated wind, or held down by a curtailment setpoint
		boolean powerLimited = isBraked() || lastAlignedWindSpeed > spec.ratedSpeed() || uncappedPower > generatedPower;

		telemetry = telemetrySimulator.sample(new TurbineTelemetrySimulator.Sample(
				Math.max(0.0, generatedPower),
				spec.ratedPowerKw(),
				activePowerLimitKw,
				powerLimited,
				meanWindSpeed,
				lastAlignedWindSpeed,
				windDirection,
				getYaw(),
				turbulence,
				rotationSpeed1,
				isBraked(),
				cutOutActive,
				stoppedByComputer,
				stoppedByPlayer,
				isStoppedByRedstone(),
				yawing,
				ambientTempC,
				airPressureHpa,
				precipitating,
				storming,
				level.getGameTime(),
				Math.abs(worldPosition.hashCode() % 1024),
				yawCableTwist,
				spec.ratedSpeed(),
				spec.stormOnsetSpeed(),
				spec.cutOutSpeed(),
				spec.gearboxRatio()
		));
	}

	/**
	 * Opens a fresh budget for the current tick. The cap only limits what other mods
	 * can draw; the wire network still receives everything left over.
	 */
	private void refreshEnergyBudget() {
		double cap = getMaxJoulesPerTick();
		budget.open(cap <= 0.0 ? 0.0 : Math.min(Math.max(0.0, generatedPower) * EnergyBridge.JOULES_PER_KW, cap));
	}

	/**
	 * Mirrors Mekanism's Wind Generator, which exposes energy on its front and
	 * bottom rather than on every face: cables belong at the foot of the tower.
	 */
	/**
	 * Where this machine's cables come out: the foot of its tower.
	 *
	 * A real turbine gathers its cables at the tower base, and so does Mekanism's Wind
	 * Generator, whose block sits on the ground with a decorative tower above it. Since this
	 * machine moved to the top of a tower a player builds, the block that stands where
	 * Mekanism's does is the foot - so that is where energy has to leave and arrive, not
	 * thirteen blocks up beside the nacelle.
	 */
	public BlockPos energyOrigin() {
		return worldPosition.below(getTowerSegments());
	}

	private List<Direction> energyFaces() {
		return List.of(Direction.DOWN, getBlockState().getValue(WindTurbineBlock.FACING).getOpposite());
	}

	private boolean isEnergyFace(@Nullable Direction side) {
		if (side == null) return true;

		return energyFaces().contains(side);
	}

	@Override
	public double getAvailableJoules() {
		if (level == null || level.isClientSide() || !ElectricityServerConfig.externalEnergyEnabled()) return 0.0;

		return budget.available();
	}

	@Override
	public double getMaxJoulesPerTick() {
		if (level == null || level.isClientSide() || !ElectricityServerConfig.externalEnergyEnabled()) return 0.0;

		// a share of this machine's own nameplate, not one figure for every machine: the
		// catalogue spans 10 kW to 4 MW, so a fixed number of Joules would strangle the
		// large models and hand the small ones more than they are able to make
		double share = spec().ratedPowerKw() * EnergyBridge.JOULES_PER_KW * ElectricityServerConfig.turbineExportFraction();
		return Math.min(share, ElectricityServerConfig.turbineMaxJoulesPerTick());
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

	public Vec3 calculateOrientedInsulatorCenter(int index) {
		if (index < 0 || index >= wirePositions.length) return null;

		String groupName = insulatorName(index);
		if (groupName == null) return null;
		var boundingBox = ObjBoundingBoxRegistry.getBoundingBox(getBlockState().getBlock(), groupName);
		Vec3 localCenter;
		if (boundingBox != null) {
			Vector3f center = boundingBox.center;
			localCenter = new Vec3(center.x(), center.y(), center.z());
		} else {
			return null;
		}

		Direction facing = getBlockState().getValue(WindTurbineBlock.FACING);
		Vec3 rotatedCenter = rotateVector(localCenter, facing);
		// The machine is the top block of the structure and the wire fitting is at the foot of
		// the tower, so the connection point drops by the whole tower.
		//
		// ObjTransforms.resolve says the same thing for drawing and for hit-testing, and the two
		// have to agree or a wire attaches somewhere it cannot be seen. They are stated twice
		// because this runs on the server too and that class is client-only.
		return Vec3.atLowerCornerOf(getBlockPos()).add(0.5, -getTowerSegments(), 0.5).add(rotatedCenter);
	}

	private Vec3 rotateVector(Vec3 vector, Direction facing) {
		float facingRotation = switch (facing) {
			case EAST -> 90.0f;
			case SOUTH -> 0.0f;
			case WEST -> 270.0f;
			default -> 180.0f;
		};

		double radians = Math.toRadians(facingRotation);
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);

		double newX = vector.x * cos - vector.z * sin;
		double newZ = vector.x * sin + vector.z * cos;

		return new Vec3(newX, vector.y, newZ);
	}

	public void tick() {
		updateWirePositions();
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
			InsulatorLookup.register(this);
			WireManagerClient.invalidateInsulatorCache(this.getInsulatorIds());
		});

		if (level == null) return;

		if (level.isClientSide) {
			clientVisualTick();
			return;
		}

		TurbineSpec spec = spec();
		// asked for the wind at this machine's own hub over this machine's own ground: the
		// profile down from the blending height depends on both, so a block of tower is worth
		// several times as much in a forest as it is on a beach
		WeatherSnapshot weather = GlobalWeatherManager.get((ServerLevel) level).sample(worldPosition, getTowerSegments());

		meanWindSpeed = (float) weather.meanWind();
		gustWindSpeed = (float) weather.gustWind();
		lastEffectiveWindSpeed = (float) weather.instantWind();
		windDirection = weather.direction();
		turbulence = weather.turbulence();
		shearExponent = (float) weather.shearExponent();
		airDensity = weather.airDensity();
		ambientTempC = weather.temperatureC();
		airPressureHpa = weather.pressureHpa();

		updateYaw();
		float alignment = alignmentFactor();
		lastAlignedWindSpeed = lastEffectiveWindSpeed * alignment;

		// The brake waits for the cut-out: between the storm onset and there the machine stays
		// on load, just derated.
		//
		// Supervised on the mean and on the gust separately, which is how the real limits are
		// written - 25 m/s over ten minutes, or a gust a fifth past it. Watching only the
		// instantaneous wind would trip the machine on the first gust that touched 25 and
		// release it a second later, which is chatter rather than protection.
		if (meanWindSpeed >= spec.cutOutSpeed() || gustWindSpeed >= spec.cutOutSpeed() * GUST_TRIP_RATIO) {
			cutOutActive = true;
		} else if (cutOutActive && meanWindSpeed <= cutOutResetSpeed(spec)) {
			cutOutActive = false;
		}

		pollRedstone();
		// power first: the rotor speed reads the setpoint against what the wind was offering, so
		// working them out in the other order would have it answering last tick's curtailment
		updateGeneratedPower();
		updateRotorSpeeds(lastAlignedWindSpeed);

		// push before the wire network runs. Block entities tick inside the level tick,
		// while PowerNetwork.updatePowerNetwork() runs on ServerTickEvent END, so the
		// residual reaches the wires in this same tick instead of a tick late.
		refreshEnergyBudget();
		EnergyBridge.emit(this, this, energyOrigin(), energyFaces());

		updateTelemetry();
		maybeSync();
	}

	@Override
	public void load(@Nonnull CompoundTag tag) {
		super.load(tag);
		ensureArraySizes();

		if (tag.contains("wirePositions")) {
			ListTag positionsList = tag.getList("wirePositions", 10);
			for (int i = 0; i < Math.min(positionsList.size(), wirePositions.length); i++) {
				CompoundTag posTag = positionsList.getCompound(i);
				wirePositions[i] = new Vec3(posTag.getDouble("x"), posTag.getDouble("y"), posTag.getDouble("z"));
			}
		}

		if (tag.contains("insulatorIds")) {
			ListTag insulatorIdsList = tag.getList("insulatorIds", 3);
			for (int i = 0; i < Math.min(insulatorIdsList.size(), insulatorIds.length); i++) {
				insulatorIds[i] = insulatorIdsList.getInt(i);
				InsulatorIdRegistry.registerExistingId(insulatorIds[i]);
			}
		}

		rotationSpeed1 = tag.getFloat("rotationSpeed1");
		rotationSpeed2 = tag.getFloat("rotationSpeed2");
		generatedPower = tag.getDouble("generatedPower");
		uncappedPower = tag.getDouble("uncappedPower");
		airDensity = tag.contains("airDensity") ? tag.getDouble("airDensity") : WorldConditions.REFERENCE_AIR_DENSITY;
		currentPower = tag.getDouble("currentPower");
		lastEffectiveWindSpeed = tag.getFloat("lastEffectiveWindSpeed");
		lastAlignedWindSpeed = tag.contains("lastAlignedWindSpeed") ? tag.getFloat("lastAlignedWindSpeed") : lastEffectiveWindSpeed;
		// a turbine saved before the weather model reported a mean and a gust has only the one
		// wind speed, so both read back off it rather than off zero, which would show a panel
		// with a full power bar over a dead calm until the next tick corrected it
		meanWindSpeed = tag.contains("meanWindSpeed") ? tag.getFloat("meanWindSpeed") : lastEffectiveWindSpeed;
		gustWindSpeed = tag.contains("gustWindSpeed") ? tag.getFloat("gustWindSpeed") : lastEffectiveWindSpeed;
		shearExponent = tag.contains("shearExponent") ? tag.getFloat("shearExponent") : 0.14f;
		ambientTempC = tag.contains("ambientTempC") ? tag.getDouble("ambientTempC") : 15.0;
		airPressureHpa = tag.contains("airPressureHpa") ? tag.getDouble("airPressureHpa") : WorldConditions.SEA_LEVEL_PRESSURE;
		cutOutActive = tag.contains("cutOutActive") && tag.getBoolean("cutOutActive");
		yawInitialized = tag.contains("yaw");
		yaw = yawInitialized ? tag.getFloat("yaw") : yaw;

		windDirection = tag.getFloat("windDirection");
		turbulence = tag.contains("turbulence") ? tag.getDouble("turbulence") : 0.0;
		yawCableTwist = tag.getDouble("yawCableTwist");

		stoppedByComputer = tag.getBoolean("stoppedByComputer");
		stoppedByPlayer = tag.getBoolean("stoppedByPlayer");
		redstonePowered = tag.getBoolean("redstonePowered");
		RedstoneMode savedMode = RedstoneMode.byName(tag.getString("redstoneMode"));
		redstoneMode = savedMode != null ? savedMode : RedstoneMode.DISABLED;
		// an older turbine has no setpoint saved, so it defaults to uncurtailed rather
		// than to a limit of zero, which would silently switch it off on load
		activePowerLimitKw = tag.contains("activePowerLimitKw") ? tag.getDouble("activePowerLimitKw") : spec().ratedPowerKw();

		updateWirePositions();
	}

	private void updateWirePositions() {
		ensureArraySizes();
		for (int i = 0; i < wirePositions.length; i++) {
			Vec3 calculatedPos = calculateOrientedInsulatorCenter(i);
			if (calculatedPos != null) {
				wirePositions[i] = calculatedPos;
			}
		}
	}

	@Override
	protected void saveAdditional(@Nonnull CompoundTag tag) {
		super.saveAdditional(tag);
		ensureYawInitialized();

		// An entry is null whenever calculateOrientedInsulatorCenter could not find the
		// insulator's bounding box, and on a dedicated server that is *always*: every
		// method that fills ObjBoundingBoxRegistry is @OnlyIn(Dist.CLIENT), so the
		// registry is permanently empty there. Writing an empty tag as a placeholder
		// keeps the list index-aligned with the array, which is what ElectricCabin and
		// PowerBox already do; load() reads a missing key back as 0 and then recomputes
		// the real positions anyway.
		ListTag positionsList = new ListTag();
		for (Vec3 pos : wirePositions) {
			if (pos != null) {
				CompoundTag posTag = new CompoundTag();
				posTag.putDouble("x", pos.x);
				posTag.putDouble("y", pos.y);
				posTag.putDouble("z", pos.z);
				positionsList.add(posTag);
			} else {
				positionsList.add(new CompoundTag());
			}
		}

		tag.put("wirePositions", positionsList);

		ListTag insulatorIdsList = new ListTag();
		for (int id : insulatorIds) {
			insulatorIdsList.add(IntTag.valueOf(id));
		}

		tag.put("insulatorIds", insulatorIdsList);

		tag.putFloat("rotationSpeed1", rotationSpeed1);
		tag.putFloat("rotationSpeed2", rotationSpeed2);
		tag.putDouble("generatedPower", generatedPower);
		// both exist for the control panel: without them the client would have to redo the
		// generation calculation, and it cannot - air density comes from the server's own
		// weather sampling, so the two would quietly disagree by a few percent
		tag.putDouble("uncappedPower", uncappedPower);
		tag.putDouble("airDensity", airDensity);
		tag.putDouble("currentPower", currentPower);
		tag.putFloat("lastEffectiveWindSpeed", lastEffectiveWindSpeed);
		tag.putFloat("lastAlignedWindSpeed", lastAlignedWindSpeed);
		tag.putFloat("meanWindSpeed", meanWindSpeed);
		tag.putFloat("gustWindSpeed", gustWindSpeed);
		// the panel quotes what one more block of tower would be worth, and that depends on the
		// ground the tower stands on - which only the server has surveyed
		tag.putFloat("shearExponent", shearExponent);
		tag.putDouble("ambientTempC", ambientTempC);
		tag.putDouble("airPressureHpa", airPressureHpa);
		tag.putBoolean("cutOutActive", cutOutActive);
		tag.putFloat("yaw", yaw);
		tag.putFloat("windDirection", windDirection);
		tag.putDouble("turbulence", turbulence);
		// the one telemetry value that is real accumulated state rather than a
		// reading, so it has to survive a reload; the simulated temperatures do not,
		// they just warm up from ambient again
		tag.putDouble("yawCableTwist", yawCableTwist);

		// all of these are written because getUpdateTag() routes through here: the client
		// needs every input to isBraked() to decide whether to animate the rotor
		tag.putBoolean("stoppedByComputer", stoppedByComputer);
		tag.putBoolean("stoppedByPlayer", stoppedByPlayer);
		tag.putBoolean("redstonePowered", redstonePowered);
		tag.putString("redstoneMode", redstoneMode.name());
		tag.putDouble("activePowerLimitKw", activePowerLimitKw);
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
		if (level != null && level.isClientSide()) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
				TrackedBlockEntities.track(this);
				InsulatorLookup.register(this);
				WireManagerClient.invalidateInsulatorCache(this.getInsulatorIds());
			});
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		InsulatorIdRegistry.releaseIds(this.getInsulatorIds());
		if (level != null && level.isClientSide()) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
				TrackedBlockEntities.untrack(this);
				InsulatorLookup.unregister(this.getInsulatorIds());
				WireManagerClient.invalidateInsulatorCache(this.getInsulatorIds());
			});
		}
	}

	private void syncStateToClients() {
		if (level == null || level.isClientSide) return;
		setChanged();
		level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}

	/**
	 * How far the rotor turns in a tick, in degrees, at this wind.
	 *
	 * Straight off the model's own rotor speed rather than a figure tuned to look right:
	 * 20 ticks a second and 360 degrees a revolution make it rpm times 0.3. The visible
	 * consequence is that the catalogue no longer spins as one — the C130 comes out near
	 * 3 degrees a tick and the small-wind machine near 60, so a big machine reads as
	 * heavy from the ground and a small one as busy.
	 */
	/**
	 * How fast the rotor should be turning, in degrees a tick.
	 *
	 * Three states, and the difference between the last two is the whole point: a machine
	 * somebody has switched off has its brake on and is genuinely still, while a machine that
	 * has shut itself down for the weather has feathered its blades and is idling. Turning that
	 * distinction into one flag is what left a turbine standing frozen in the middle of a gale.
	 */
	private float rotorDegreesPerTick(float windSpeed) {
		if (isParked()) return 0.0f;

		TurbineSpec spec = spec();
		double rpm = isIdling() ? spec.idlingRpmAt(windSpeed) : spec.rotorRpmAt(curtailedWind(windSpeed));
		return (float) (rpm * TurbineSpec.DEGREES_PER_TICK_PER_RPM);
	}

	/**
	 * Whether the rotor is being held still on somebody's orders rather than by the weather.
	 *
	 * A maintenance stop, a program's stop and a redstone stop all put the brake on. A storm
	 * shutdown does not - the brake is for emergencies, and holding a rotor against a gale with
	 * it would be one.
	 */
	private boolean isParked() {
		return stoppedByComputer || stoppedByPlayer || isStoppedByRedstone();
	}

	/**
	 * Whether the rotor is turning without working: off load, blades feathered, free to spin.
	 *
	 * Two ways in, and they are the same state. A storm shutdown is one. A setpoint of zero is
	 * the other, and that is worth stating because it is not a stop: curtailment is an
	 * instruction from the grid, so a machine told to export nothing disconnects and idles
	 * rather than braking, both because it has to be able to come back in seconds and because
	 * standing still is what harms a main bearing. The brake is what the stop button is for, and
	 * the two controls stay distinct.
	 */
	private boolean isIdling() {
		return cutOutActive || activePowerLimitKw <= 0.0;
	}

	/**
	 * The wind the rotor is allowed to answer, which is less than the real one while a setpoint
	 * is holding the machine down.
	 *
	 * Curtailment is done by pitching the blades out, and a rotor with its blades part way out
	 * of the wind runs slower - so a curtailed machine is visibly lazier than a machine in the
	 * same wind at full output, which is the whole point of the reading. Power goes with the
	 * cube of the wind, so the wind that would have made the permitted power is the cube root
	 * of the ratio: half output is four fifths of the speed, not half.
	 */
	private double curtailedWind(double windSpeed) {
		if (uncappedPower <= 0.0 || activePowerLimitKw >= uncappedPower) return windSpeed;

		return windSpeed * Math.cbrt(Math.max(0.0, activePowerLimitKw) / uncappedPower);
	}

	private void updateRotorSpeeds(float effectiveWindSpeed) {
		float target = rotorDegreesPerTick(effectiveWindSpeed);

		// slowing down is quicker than speeding up, because coming off load the brake or the
		// feathered blades are working against the rotor rather than the wind working with it
		float acceleration = target < rotationSpeed1 ? 0.12f : 0.05f;
		rotationSpeed1 += (target - rotationSpeed1) * acceleration;
		rotationSpeed2 += (target - rotationSpeed2) * acceleration;

		// a first-order ramp never quite arrives, so a rotor meant to be still would creep for
		// ever at a millionth of a degree. Only snapped when the target really is zero, or an
		// idling rotor would be snapped to a stop as well.
		if (target == 0.0f) {
			if (Math.abs(rotationSpeed1) < 0.01f) rotationSpeed1 = 0.0f;
			if (Math.abs(rotationSpeed2) < 0.01f) rotationSpeed2 = 0.0f;
		}
	}

	private void clientVisualTick() {
		advanceRotations(lastAlignedWindSpeed);
	}

	private void advanceRotations(float effectiveSpeed) {
		float appliedSpeed1 = rotationSpeed1;
		float appliedSpeed2 = rotationSpeed2;
		// a braked rotor must not be spun up by the client's own guess. All the inputs
		// to isBraked() are synced, so the client reaches the same conclusion.
		if (!isBraked()) {
			if (rotationSpeed1 == 0.0f && effectiveSpeed > 0.0f) {
				appliedSpeed1 = rotorDegreesPerTick(effectiveSpeed);
			}

			if (rotationSpeed2 == 0.0f && effectiveSpeed > 0.0f) {
				appliedSpeed2 = appliedSpeed1;
			}
		}

		// scaled for drawing only, and the same scale for both: rotate_2 is the spinner cone
		// bolted to the blade roots, so it is the same shaft. Driving the two at different rates
		// let the cone creep round relative to the blades it is part of, which no amount of wind
		// does to a real rotor.
		//
		// There used to be a ten percent sine wobble on top of this to keep the rotor from
		// looking mechanical. The wind is genuinely gusty now and the rotor already breathes
		// with it through its own inertia, so the wobble was a second, fake copy of an effect
		// the physics provides - and it put the drawn speed ten percent away from the speed the
		// machine was reporting.
		TurbineSpec spec = spec();
		rotation1 = (float) ((rotation1 + spec.drawnRotation(appliedSpeed1)) % 360.0);
		rotation2 = (float) ((rotation2 + spec.drawnRotation(appliedSpeed2)) % 360.0);
		if (rotation1 < 0) rotation1 += 360.0f;
		if (rotation2 < 0) rotation2 += 360.0f;
	}

	private void updateYaw() {
		if (!yawInitialized) {
			Direction facing = getBlockState().getValue(WindTurbineBlock.FACING);
			yaw = baseFacingYaw(facing);
			yawInitialized = true;
		}

		yawing = false;
		float target = windDirection;
		float delta = Mth.wrapDegrees(target - yaw);
		if (Math.abs(delta) <= YAW_DEADBAND) return;
		float step = Mth.clamp(delta, -YAW_STEP, YAW_STEP);
		yaw = Mth.wrapDegrees(yaw + step);
		yawing = true;

		// signed accumulation, so the twist tracks net rotation rather than total
		// travel. The wind wanders both ways, so this random-walks around zero
		// instead of growing without bound; the mod does not model an untwist cycle.
		yawCableTwist += step;
	}

	private float alignmentFactor() {
		float delta = Math.abs(Mth.wrapDegrees(windDirection - yaw));
		if (delta >= 90.0f) return 0.0f;
		float cos = (float) Math.cos(Math.toRadians(delta));
		if (cos <= 0.0f) return 0.0f;
		return cos * cos * cos;
	}

	private static float baseFacingYaw(Direction facing) {
		return switch (facing) {
			case EAST -> 90.0f;
			case SOUTH -> 0.0f;
			case WEST -> 270.0f;
			default -> 180.0f;
		};
	}

	public float getYaw() {
		ensureYawInitialized();
		return yaw;
	}

	private void maybeSync() {
		if (level == null || level.isClientSide) return;
		long gameTime = level.getGameTime();
		float delta = Float.isNaN(lastSentYaw) ? Float.MAX_VALUE : Math.abs(Mth.wrapDegrees(yaw - lastSentYaw));
		boolean shouldSync = delta > 1.0f || gameTime - lastSyncTick >= 10;

		if (shouldSync) {
			lastSentYaw = yaw;
			lastSyncTick = gameTime;
			syncStateToClients();
		}
	}

	private void ensureYawInitialized() {
		if (yawInitialized) return;
		Direction facing = getBlockState().getValue(WindTurbineBlock.FACING);
		yaw = baseFacingYaw(facing);
		yawInitialized = true;
	}
}
