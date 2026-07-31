package com.dooji.electricity.block;

import com.dooji.electricity.api.power.IEnergyBudget;
import com.dooji.electricity.api.power.PhotovoltaicArray;
import com.dooji.electricity.compat.energy.EnergyBridge;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.ElectricityServerConfig;
import com.dooji.electricity.main.weather.GlobalWeatherManager;
import com.dooji.electricity.main.weather.WeatherNoise;
import com.dooji.electricity.main.weather.WeatherSnapshot;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * One block of photovoltaic array, generating from the weather model's own sunlight.
 *
 * The electrical side is {@link PhotovoltaicArray} and the sunlight is
 * {@link com.dooji.electricity.main.weather.Atmosphere}; what is left here is what belongs to
 * this particular block - whether it can see the sky, what its own modules are worth, and
 * pushing the result out to whatever is cabled to it. It is the same shape as the turbine,
 * one layer thinner, because a panel has no rotor to turn and nothing to yaw.
 *
 * <h2>Why two panels side by side do not agree</h2>
 *
 * Three reasons, and all three are the real ones.
 *
 * A cloud reaches one before the other. The cumulus field is sampled at each panel's own
 * coordinates and drifts downwind, so a shadow crossing an array arrives a few seconds apart
 * along it - which is the effect that dominates, and the one that shows up in a trend as two
 * lines diverging and rejoining.
 *
 * The modules are not identical. Every real module is flash tested and sorted into a bin a
 * couple of percent wide, and that difference is fixed for the life of the panel:
 * {@link #moduleTolerance} is drawn from the block position, so it never changes and never
 * needs saving.
 *
 * And dust settles unevenly. {@link #drift} is a slow, small wander of its own for each
 * panel, standing in for soiling and for how the air moves behind one module rather than
 * another. The mod does not track dust building up and washing off - it carries the loss
 * typical of the climate instead - but the wander is what stops two neighbours sitting at a
 * constant ratio forever.
 */
public class SolarPanelBlockEntity extends BlockEntity implements IEnergyBudget {
	/**
	 * Where the cables leave: everywhere but straight up, which is where the sunlight has to
	 * come from. The same choice Mekanism's solar generators make, for the same reason.
	 */
	private static final List<Direction> CABLE_FACES = List.of(Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
	/** Flash-test binning, peak to peak: real datasheets print plus or minus three percent. */
	private static final double MODULE_TOLERANCE = 0.03;
	/** Everything else two modules differ by, and how long it takes to wander across that range. */
	private static final double DRIFT_RANGE = 0.02;
	private static final double DRIFT_TICKS = 9000.0;
	private static final double DRIFT_NOISE_SD = 0.4040;

	private double generatedPower = 0.0;
	private double irradiance = 0.0;
	private double cellTemperature = 0.0;
	private double ambientTemperature = 0.0;
	private double cloudCover = 0.0;
	private boolean seesSky = true;

	// this tick's offer to other mods' energy systems, and how much of it they took
	private double tickBudgetJoules = 0.0;
	private double claimedJoules = 0.0;
	private final LazyOptional<IEnergyStorage> forgeEnergy = LazyOptional.of(() -> EnergyBridge.forgeEnergyView(this));
	private final LazyOptional<?> mekanismEnergy = EnergyBridge.createMekanismHandler(this);

	public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
		super(Electricity.SOLAR_PANEL_BLOCK_ENTITY.get(), pos, state);
	}

	public void serverTick() {
		if (!(level instanceof ServerLevel serverLevel)) return;

		// a panel is a metre off the ground, so it is sampled as a one-block tower: the same
		// call the turbines make, at the height a weather station keeps its instruments
		WeatherSnapshot weather = GlobalWeatherManager.get(serverLevel).sample(worldPosition, 1);
		seesSky = serverLevel.canSeeSky(worldPosition.above());

		cloudCover = weather.cloudCover();
		ambientTemperature = weather.temperatureC();
		irradiance = seesSky ? weather.irradiance() * moduleTolerance(serverLevel) * drift(serverLevel) : 0.0;
		cellTemperature = PhotovoltaicArray.cellTemperature(ambientTemperature, irradiance);
		generatedPower = PhotovoltaicArray.acPowerKw(irradiance, ambientTemperature);

		claimedJoules = 0.0;
		tickBudgetJoules = generatedPower * EnergyBridge.JOULES_PER_KW;
		EnergyBridge.emit(this, this, worldPosition, CABLE_FACES);
	}

	/**
	 * This panel's own share of the factory's tolerance band. Drawn from where it stands, so it
	 * is the same every tick, the same after a reload, and costs nothing to remember.
	 */
	private double moduleTolerance(ServerLevel level) {
		double unit = WeatherNoise.hashUnit(level.getSeed() ^ 0x9105L, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
		return 1.0 + (unit - 0.5) * MODULE_TOLERANCE;
	}

	/** Soiling and airflow: a slow wander, downward only, its own for each panel. */
	private double drift(ServerLevel level) {
		double n = WeatherNoise.sample(level.getSeed() ^ 0x501L, worldPosition.getX() * 0.37,
				worldPosition.getY() * 0.41 + worldPosition.getZ() * 0.37, level.getDayTime() / DRIFT_TICKS);
		return 1.0 - DRIFT_RANGE * 0.5 * (1.0 + n / DRIFT_NOISE_SD);
	}

	/** Output in kW, as the inverter's meter would read it. */
	public double getGeneratedPowerKw() {
		return generatedPower;
	}

	/** Global horizontal irradiance reaching this panel, W/m2. Zero if it cannot see the sky. */
	public double getIrradiance() {
		return irradiance;
	}

	public double getCellTemperature() {
		return cellTemperature;
	}

	public double getAmbientTemperature() {
		return ambientTemperature;
	}

	public double getCloudCover() {
		return cloudCover;
	}

	public boolean seesSky() {
		return seesSky;
	}

	@Override
	public double getAvailableJoules() {
		if (level == null || level.isClientSide() || !ElectricityServerConfig.externalEnergyEnabled()) return 0.0;

		return Math.max(0.0, tickBudgetJoules - claimedJoules);
	}

	@Override
	public double getMaxJoulesPerTick() {
		if (level == null || level.isClientSide() || !ElectricityServerConfig.externalEnergyEnabled()) return 0.0;

		// the whole array, not a share of it: unlike a turbine there is no wire network reading
		// this block as well, so there is no second claimant to leave anything for
		return PhotovoltaicArray.RATED_POWER_KW * EnergyBridge.JOULES_PER_KW;
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
		if (cap == ForgeCapabilities.ENERGY) return forgeEnergy.cast();
		if (EnergyBridge.isMekanismEnergyCapability(cap)) return mekanismEnergy.cast();

		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		forgeEnergy.invalidate();
		mekanismEnergy.invalidate();
	}

	@Override
	protected void saveAdditional(@Nonnull CompoundTag tag) {
		super.saveAdditional(tag);
		// nothing here is state: every reading is recomputed from the weather on the next tick,
		// and the panel's own tolerance follows from where it stands. Only the last output is
		// written, so a monitor reading a freshly loaded chunk has a number rather than a zero
		tag.putDouble("generatedPower", generatedPower);
	}

	@Override
	public void load(@Nonnull CompoundTag tag) {
		super.load(tag);
		generatedPower = tag.getDouble("generatedPower");
	}
}
