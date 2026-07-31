package com.dooji.electricity.block;

import com.dooji.electricity.api.power.IEnergyBudget;
import com.dooji.electricity.api.power.RedstoneMode;
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
	private float lastEffectiveWindSpeed = 0.0f;
	private float lastAlignedWindSpeed = 0.0f;
	private float windDirection = 0.0f;
	private double turbulence = 0.0;
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
	private static final float YAW_STEP = 0.25f;
	private static final float YAW_DEADBAND = 7.5f;

	// this tick's offer to other mods' energy systems, and how much of it they took.
	// Nothing carries over between ticks, so neither value is persisted.
	private double tickBudgetJoules = 0.0;
	private double claimedJoules = 0.0;
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
	private volatile RedstoneMode redstoneMode = RedstoneMode.DISABLED;
	private volatile boolean redstonePowered = false;
	private volatile double activePowerLimitKw;
	/** What the wind alone would have produced, before curtailment. */
	private double uncappedPower = 0.0;

	/**
	 * One-block tower segments standing under the nacelle.
	 *
	 * The only thing about a placed turbine the player chooses, and the reason it matters
	 * is {@link TurbineSpec#windAtHubHeight}: wind is faster away from the ground, and
	 * because power goes with its cube a taller tower is worth far more than the height
	 * suggests. It cuts both ways, which is what makes it a decision — in a storm the
	 * tallest tower is the first one shear pushes past the derating threshold.
	 */
	private int towerSegments;
	/** Air density at the nacelle, from ambient temperature. Cold air is denser and carries more power. */
	private double airDensity = TurbineSpec.REFERENCE_AIR_DENSITY;

	public WindTurbineBlockEntity(BlockPos pos, BlockState state) {
		super(getBlockEntityType(), pos, state);
		TurbineSpec spec = spec();
		this.activePowerLimitKw = spec.ratedPowerKw();
		this.towerSegments = spec.minTowerSegments();
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

	public int getTowerSegments() {
		return spec().clampTowerSegments(towerSegments);
	}

	public double getHubHeightM() {
		return spec().hubHeightM(getTowerSegments());
	}

	public double getAirDensity() {
		return airDensity;
	}

	/** What the wind offered before the curtailment setpoint took anything off it, in kW. */
	public double getUncappedPower() {
		return uncappedPower;
	}

	public double getTurbulence() {
		return turbulence;
	}

	/**
	 * Sets the tower height, in segments, clamped to what this model is sold on.
	 *
	 * Server-side only: it changes the power the machine makes and how tall it draws, so
	 * both sides have to be told.
	 */
	public boolean setTowerSegments(int segments) {
		int clamped = spec().clampTowerSegments(segments);
		if (clamped == towerSegments) return false;

		towerSegments = clamped;
		onControlChanged();
		return true;
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

	public float getWindSpeed() {
		return lastEffectiveWindSpeed;
	}

	public float getWindDirection() {
		return windDirection;
	}

	/**
	 * What the wire network may carry away this tick: everything generated, minus
	 * whatever another mod's cables already claimed. The subtraction is what stops
	 * the same Joule being spent twice, because the wire network reads a generator
	 * without ever debiting it.
	 */
	public double getGeneratedPower() {
		updateGeneratedPower();
		return Math.max(0.0, generatedPower - claimedJoules / EnergyBridge.JOULES_PER_KW);
	}

	public boolean isSurging() {
		// still possible right through the storm-control band, since the machine is
		// running there; only a real shutdown rules a surge out
		return turbulence >= 0.35 && lastEffectiveWindSpeed < spec().cutOutSpeed() && !isBraked();
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

		// the cut-in, the plateau, the storm derating and the cut-out all live in the
		// model's own curve now, so this is the whole generation model. Air density goes
		// in with it, which is why a turbine on a freezing night out-produces the same
		// machine on a hot afternoon in identical wind.
		uncappedPower = spec().powerAtKw(Math.max(0.0, lastAlignedWindSpeed), airDensity);
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
		return cutOutActive || stoppedByComputer || !redstoneMode.allowsRunning(redstonePowered);
	}

	public boolean isRunning() {
		return !isBraked();
	}

	public boolean isStoppedByComputer() {
		return stoppedByComputer;
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
				lastEffectiveWindSpeed,
				lastAlignedWindSpeed,
				windDirection,
				getYaw(),
				turbulence,
				rotationSpeed1,
				isBraked(),
				cutOutActive,
				stoppedByComputer,
				isStoppedByRedstone(),
				yawing,
				ambientTemperature(precipitating, storming),
				worldPosition.getY(),
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

	/** Ambient at the nacelle, sampling the sky for itself. */
	private double ambientTemperature() {
		if (level == null) return 15.0;

		boolean precipitating = level.isRainingAt(worldPosition.above());
		return ambientTemperature(precipitating, level.isThundering() && precipitating);
	}

	/**
	 * Air temperature at the nacelle, in Celsius.
	 *
	 * Minecraft has no ambient temperature, so this is assembled from the things
	 * that would actually drive one: the biome's climate, height, the day cycle and
	 * what the sky is doing. The biome scale runs 0..2, mapped so a snowy biome
	 * reads about -5C, plains 11C and a desert 35C.
	 */
	private double ambientTemperature(boolean precipitating, boolean storming) {
		double celsius = level.getBiome(worldPosition).value().getBaseTemperature() * 20.0 - 5.0;

		// the same height cooling vanilla applies to biome temperature above y=80,
		// converted into this scale, so a mountaintop turbine reads colder than one
		// on the plain below it exactly as the game would have it
		celsius -= Math.max(0, worldPosition.getY() - 80) * 0.025;

		// diurnal swing, peaking in the early afternoon and bottoming before dawn.
		// Cloud cover flattens it, which is why an overcast night is milder than a
		// clear one
		double swing = 6.0;
		if (storming) {
			swing *= 0.25;
		} else if (precipitating) {
			swing *= 0.5;
		}

		double dayPhase = (level.getDayTime() % 24000L) / 24000.0;
		celsius += Math.sin((dayPhase - 2000.0 / 24000.0) * 2.0 * Math.PI) * swing;

		if (storming) {
			celsius -= 6.0;
		} else if (precipitating) {
			celsius -= 3.0;
		}

		return celsius;
	}

	/**
	 * Opens a fresh budget for the current tick. The cap only limits what other mods
	 * can draw; the wire network still receives everything left over.
	 */
	private void refreshEnergyBudget() {
		claimedJoules = 0.0;
		double cap = getMaxJoulesPerTick();
		tickBudgetJoules = cap <= 0.0 ? 0.0 : Math.min(Math.max(0.0, generatedPower) * EnergyBridge.JOULES_PER_KW, cap);
	}

	/**
	 * Mirrors Mekanism's Wind Generator, which exposes energy on its front and
	 * bottom rather than on every face: cables belong at the foot of the tower.
	 */
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

		return Math.max(0.0, tickBudgetJoules - claimedJoules);
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
		if (!(joules > 0.0)) return 0.0;

		double claimable = Math.min(joules, getAvailableJoules());
		if (claimable <= 0.0) return 0.0;
		if (!simulate) claimedJoules += claimable;

		return claimable;
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
		return Vec3.atLowerCornerOf(getBlockPos()).add(0.5, 0, 0.5).add(rotatedCenter);
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
		WeatherSnapshot weather = GlobalWeatherManager.get((ServerLevel) level).sample(worldPosition);
		double blend = Mth.clamp(weather.turbulence(), 0.0, 1.0);
		double zoneWind = Mth.lerp(blend, weather.windSpeed(), weather.gustSpeed());

		// the zone wind is one figure for the whole area with no height to it, so it is
		// read as the wind at the reference hub height and this machine's own tower moves
		// it up or down from there. That is the entire payoff of building tall.
		lastEffectiveWindSpeed = (float) TurbineSpec.windAtHubHeight(zoneWind, spec.hubHeightM(getTowerSegments()));
		windDirection = weather.direction();
		turbulence = weather.turbulence();
		airDensity = TurbineSpec.airDensityAt(ambientTemperature());

		updateYaw();
		float alignment = alignmentFactor();
		lastAlignedWindSpeed = lastEffectiveWindSpeed * alignment;

		// the brake waits for the cut-out: between the storm onset and there the machine
		// stays on load, just derated
		if (lastEffectiveWindSpeed >= spec.cutOutSpeed()) {
			cutOutActive = true;
		} else if (cutOutActive && lastEffectiveWindSpeed <= cutOutResetSpeed(spec)) {
			cutOutActive = false;
		}

		pollRedstone();
		updateRotorSpeeds(lastAlignedWindSpeed, isBraked());
		updateGeneratedPower();

		// push before the wire network runs. Block entities tick inside the level tick,
		// while PowerNetwork.updatePowerNetwork() runs on ServerTickEvent END, so the
		// residual reaches the wires in this same tick instead of a tick late.
		refreshEnergyBudget();
		EnergyBridge.emit(this, this, energyFaces());

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
		currentPower = tag.getDouble("currentPower");
		lastEffectiveWindSpeed = tag.getFloat("lastEffectiveWindSpeed");
		lastAlignedWindSpeed = tag.contains("lastAlignedWindSpeed") ? tag.getFloat("lastAlignedWindSpeed") : lastEffectiveWindSpeed;
		cutOutActive = tag.contains("cutOutActive") && tag.getBoolean("cutOutActive");
		yawInitialized = tag.contains("yaw");
		yaw = yawInitialized ? tag.getFloat("yaw") : yaw;

		windDirection = tag.getFloat("windDirection");
		turbulence = tag.contains("turbulence") ? tag.getDouble("turbulence") : 0.0;
		yawCableTwist = tag.getDouble("yawCableTwist");

		stoppedByComputer = tag.getBoolean("stoppedByComputer");
		redstonePowered = tag.getBoolean("redstonePowered");
		RedstoneMode savedMode = RedstoneMode.byName(tag.getString("redstoneMode"));
		redstoneMode = savedMode != null ? savedMode : RedstoneMode.DISABLED;
		// an older turbine has no setpoint saved, so it defaults to uncurtailed rather
		// than to a limit of zero, which would silently switch it off on load
		activePowerLimitKw = tag.contains("activePowerLimitKw") ? tag.getDouble("activePowerLimitKw") : spec().ratedPowerKw();
		// a turbine saved before towers were adjustable stands at the shortest its model is
		// sold on, which is also what a freshly placed one gets
		towerSegments = spec().clampTowerSegments(tag.contains("towerSegments") ? tag.getInt("towerSegments") : spec().minTowerSegments());

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
		tag.putDouble("currentPower", currentPower);
		tag.putFloat("lastEffectiveWindSpeed", lastEffectiveWindSpeed);
		tag.putFloat("lastAlignedWindSpeed", lastAlignedWindSpeed);
		tag.putBoolean("cutOutActive", cutOutActive);
		tag.putFloat("yaw", yaw);
		tag.putFloat("windDirection", windDirection);
		tag.putDouble("turbulence", turbulence);
		// the one telemetry value that is real accumulated state rather than a
		// reading, so it has to survive a reload; the simulated temperatures do not,
		// they just warm up from ambient again
		tag.putDouble("yawCableTwist", yawCableTwist);

		// all four are written because getUpdateTag() routes through here: the client
		// needs every input to isBraked() to decide whether to animate the rotor
		tag.putBoolean("stoppedByComputer", stoppedByComputer);
		tag.putBoolean("redstonePowered", redstonePowered);
		tag.putString("redstoneMode", redstoneMode.name());
		tag.putDouble("activePowerLimitKw", activePowerLimitKw);
		// the client draws the tower this tall and the GUI reports the hub height it implies
		tag.putInt("towerSegments", getTowerSegments());
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
	private float rotorDegreesPerTick(float windSpeed) {
		return (float) (spec().rotorRpmAt(windSpeed) * 0.3);
	}

	private void updateRotorSpeeds(float effectiveWindSpeed, boolean cutOut) {
		float targetRotationSpeed = cutOut ? 0.0f : rotorDegreesPerTick(effectiveWindSpeed);

		float rotationAcceleration = cutOut ? 0.12f : 0.05f;
		float rotationDiff1 = targetRotationSpeed - rotationSpeed1;
		float rotationDiff2 = targetRotationSpeed - rotationSpeed2;

		if (Math.abs(rotationDiff1) > 0.001f) {
			rotationSpeed1 += rotationDiff1 * rotationAcceleration;
		}

		if (Math.abs(rotationDiff2) > 0.001f) {
			rotationSpeed2 += rotationDiff2 * rotationAcceleration;
		}

		if (cutOut) {
			if (Math.abs(rotationSpeed1) < 0.01f) rotationSpeed1 = 0.0f;
			if (Math.abs(rotationSpeed2) < 0.01f) rotationSpeed2 = 0.0f;
		}
	}

	private void clientVisualTick() {
		float effectiveSpeed = lastAlignedWindSpeed;
		float currentTime = level.getGameTime() + level.getGameTime() * 0.05f;
		advanceRotations(currentTime, effectiveSpeed);
	}

	private void advanceRotations(float currentTime, float effectiveSpeed) {
		// One variation for both, because rotate_2 is the spinner cone bolted to the blade
		// roots: it is the same shaft. Driving the two at separate rates let the cone
		// creep round relative to the blades it is part of, which no amount of wind does
		// to a real rotor.
		float variation = 1.0f + (float) Math.sin(currentTime * 0.05) * 0.1f;

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

		rotation1 = (rotation1 + appliedSpeed1 * variation) % 360.0f;
		rotation2 = (rotation2 + appliedSpeed2 * variation) % 360.0f;
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
