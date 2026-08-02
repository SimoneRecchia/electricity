package com.dooji.electricity.block;

import com.dooji.electricity.api.power.IEnergyBudget;
import com.dooji.electricity.api.power.InverterSpec;
import com.dooji.electricity.api.power.RedstoneMode;
import com.dooji.electricity.api.power.TickBudget;
import com.dooji.electricity.compat.energy.EnergyBridge;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.ElectricityServerConfig;
import com.dooji.electricity.main.registry.InverterCatalog;
import com.dooji.electricity.main.weather.GlobalWeatherManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

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
	/**
	 * String terminals on each maximum power point tracker.
	 *
	 * Two, which is what a real multi-tracker string inverter offers and what makes the input capacity
	 * come out near the DC capacity: a nine-tracker machine takes eighteen strings, which at the
	 * string sizes in this catalogue is about the fifteen array blocks its 165 kW of DC input wants.
	 * The two limits agreeing was not arranged - it falls out of both being real figures.
	 */
	private static final int STRINGS_PER_MPPT = 2;
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
	private int stringsConnected = 0;

	private double energyTodayKwh = 0.0;
	private double energyLifetimeKwh = 0.0;
	private long lastDay = -1L;

	private volatile boolean stoppedByComputer = false;
	private volatile boolean stoppedByPlayer = false;
	private volatile RedstoneMode redstoneMode = RedstoneMode.DISABLED;
	private volatile boolean redstonePowered = false;
	private volatile double activePowerLimitKw;
	private volatile double powerFactorSetpoint = 1.0;

	private final TickBudget budget = new TickBudget();
	private final LazyOptional<IEnergyStorage> forgeEnergy = LazyOptional.of(() -> EnergyBridge.forgeEnergyView(this));
	private final LazyOptional<?> mekanismEnergy = EnergyBridge.createMekanismHandler(this);

	private long lastSyncTick = 0L;

	public PvInverterBlockEntity(BlockPos pos, BlockState state) {
		super(Electricity.PV_INVERTER_BLOCK_ENTITY.get(), pos, state);
		this.activePowerLimitKw = spec().acPowerKw();
	}

	/** Which machine this is. Read off the block, so there is no saved model id to migrate. */
	public InverterSpec spec() {
		if (getBlockState().getBlock() instanceof PvInverterBlock inverter) return inverter.spec();

		return InverterCatalog.fallback();
	}

	// ---- the tick ----

	public void serverTick() {
		if (!(level instanceof ServerLevel serverLevel)) return;

		InverterSpec spec = spec();
		if (--rescanCountdown <= 0) {
			rescan(serverLevel, spec);
			rescanCountdown = RESCAN_TICKS;
		}

		pollRedstone();
		ambientTempC = GlobalWeatherManager.get(serverLevel).sample(worldPosition, 1).temperatureC();

		gatherAndConvert(serverLevel, spec);
		accumulateEnergy(serverLevel);

		budget.open(Math.max(0.0, acPowerKw) * EnergyBridge.JOULES_PER_KW);
		EnergyBridge.emit(this, this, worldPosition, energyFaces());

		maybeSync(serverLevel);
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
			if (!(serverLevel.getBlockEntity(pos) instanceof PvArrayBlockEntity array)) continue;
			// a closer inverter may have taken it since the last scan, and until this one rescans it
			// would otherwise go on counting an array it no longer owns
			if (!worldPosition.equals(array.inverterPos())) continue;

			live.add(array);
			availableDcKw += array.availableDcKw();
			highestStringVoltage = Math.max(highestStringVoltage, array.stringVoltage());
		}

		boolean allowed = isRunning();
		// below the startup voltage there is nothing to track: the strings are lit but not lit enough,
		// which is why a real plant sits at exactly zero for a few minutes after sunrise rather than
		// producing a trickle
		boolean awake = allowed && highestStringVoltage >= spec.startupVolts() && availableDcKw > 0.0;

		double ceiling = Math.min(spec.thermalLimitKw(ambientTempC), Math.max(0.0, activePowerLimitKw));
		derating = spec.derating(ambientTempC);

		if (!awake) {
			dcPowerKw = 0.0;
			// the night draw, reported as a negative output. A real meter reads it and so should this one
			acPowerKw = allowed || availableDcKw > 0.0 ? -spec.nightDrawKw() : -spec.nightDrawKw();
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
		double wantedAc = Math.min(spec.unclippedAcKw(availableDcKw), ceiling);
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
		for (BlockPos pos : arrays) {
			if (serverLevel.getBlockEntity(pos) instanceof PvArrayBlockEntity array) {
				array.releaseClaim(worldPosition);
			}
		}

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

		int stringCapacity = spec.mpptCount() * STRINGS_PER_MPPT;
		for (PvArrayBlockEntity array : candidates) {
			int strings = array.spec().strings();
			if (stringsConnected + strings > stringCapacity) continue;
			if (!array.claim(worldPosition)) continue;

			arrays.add(array.getBlockPos().immutable());
			stringsConnected += strings;
		}
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

	private void pollRedstone() {
		if (level == null || level.isClientSide()) return;

		boolean powered = level.hasNeighborSignal(worldPosition);
		if (powered == redstonePowered) return;

		redstonePowered = powered;
		if (redstoneMode != RedstoneMode.DISABLED) {
			onControlChanged();
		} else {
			setChanged();
		}
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

	public int arraysConnected() {
		return arrays.size();
	}

	public int stringsConnected() {
		return stringsConnected;
	}

	public int stringCapacity() {
		return spec().mpptCount() * STRINGS_PER_MPPT;
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
			if (level.getBlockEntity(pos) instanceof PvArrayBlockEntity array) {
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
		return !stoppedByComputer && !stoppedByPlayer && redstoneMode.allowsRunning(redstonePowered);
	}

	public boolean isStoppedByComputer() {
		return stoppedByComputer;
	}

	public boolean isStoppedByPlayer() {
		return stoppedByPlayer;
	}

	public boolean isStoppedByRedstone() {
		return !redstoneMode.allowsRunning(redstonePowered);
	}

	public RedstoneMode getRedstoneMode() {
		return redstoneMode;
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
		if (mode == null || redstoneMode == mode) return;

		redstoneMode = mode;
		onControlChanged();
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
		sync();
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

	private void maybeSync(ServerLevel serverLevel) {
		long now = serverLevel.getGameTime();
		if (now - lastSyncTick < 10L) return;

		lastSyncTick = now;
		setChanged();
		sync();
	}

	private void sync() {
		if (level == null || level.isClientSide()) return;

		level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}

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
		tag.putInt("stringsConnected", stringsConnected);

		tag.putBoolean("stoppedByComputer", stoppedByComputer);
		tag.putBoolean("stoppedByPlayer", stoppedByPlayer);
		tag.putBoolean("redstonePowered", redstonePowered);
		tag.putString("redstoneMode", redstoneMode.name());
		tag.putDouble("activePowerLimitKw", activePowerLimitKw);
		tag.putDouble("powerFactor", powerFactorSetpoint);

		ListTag positions = new ListTag();
		for (BlockPos pos : arrays) {
			positions.add(LongTag.valueOf(pos.asLong()));
		}

		tag.put("arrays", positions);
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
		stringsConnected = tag.getInt("stringsConnected");

		stoppedByComputer = tag.getBoolean("stoppedByComputer");
		stoppedByPlayer = tag.getBoolean("stoppedByPlayer");
		redstonePowered = tag.getBoolean("redstonePowered");
		RedstoneMode savedMode = RedstoneMode.byName(tag.getString("redstoneMode"));
		redstoneMode = savedMode != null ? savedMode : RedstoneMode.DISABLED;
		// an inverter saved before this field existed is uncurtailed rather than limited to nothing,
		// which would silently switch it off on load
		activePowerLimitKw = tag.contains("activePowerLimitKw") ? tag.getDouble("activePowerLimitKw") : spec().acPowerKw();
		powerFactorSetpoint = tag.contains("powerFactor") ? tag.getDouble("powerFactor") : 1.0;

		arrays.clear();
		ListTag positions = tag.getList("arrays", 4);
		for (int i = 0; i < positions.size(); i++) {
			arrays.add(BlockPos.of(((LongTag) positions.get(i)).getAsLong()));
		}
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
	public void setRemoved() {
		super.setRemoved();
		if (level != null && !level.isClientSide()) {
			// the arrays have to be told, or they would go on believing they were wired to a cabinet
			// that is no longer there and would wait for a claim that never comes
			for (BlockPos pos : arrays) {
				if (level.getBlockEntity(pos) instanceof PvArrayBlockEntity array) {
					array.releaseClaim(worldPosition);
				}
			}
		}
	}
}
