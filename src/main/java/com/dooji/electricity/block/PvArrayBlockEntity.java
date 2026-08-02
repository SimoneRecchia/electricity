package com.dooji.electricity.block;

import com.dooji.electricity.api.power.PvArraySpec;
import com.dooji.electricity.api.power.PvMounting;
import com.dooji.electricity.api.power.TrackerMode;
import com.dooji.electricity.api.power.TrackerSpec;
import com.dooji.electricity.client.TrackedBlockEntities;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.registry.PvCatalog;
import com.dooji.electricity.main.weather.Atmosphere;
import com.dooji.electricity.main.weather.GlobalWeatherManager;
import com.dooji.electricity.main.weather.PlaneIrradiance;
import com.dooji.electricity.main.weather.Shading;
import com.dooji.electricity.main.weather.SkyConditions;
import com.dooji.electricity.main.weather.WeatherNoise;
import com.dooji.electricity.main.weather.WeatherSnapshot;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * One block of photovoltaic array, working out what its modules are making.
 *
 * <h2>What this block is responsible for</h2>
 *
 * The optics and the modules, and nothing electrical beyond direct current. The sky belongs to the
 * weather model, the shading to {@link Shading}, the module physics to
 * {@link com.dooji.electricity.api.power.PvModuleSpec}, and everything on the far side of the DC
 * terminals belongs to the inverter. What is left here is the chain that turns a sky into a plane
 * into a temperature into a current, and the tracker drive if there is one.
 *
 * <h2>Why an array with no inverter makes nothing</h2>
 *
 * Because that is what modules with no load do. An open-circuit string sits at its open-circuit
 * voltage and passes no current, so it produces exactly zero watts however bright the day is - and
 * that is not a technicality, it is the reason a photovoltaic plant is an inverter with modules
 * attached rather than the other way round. The array still works out and reports what it *could*
 * have made, so a panel can say "no inverter in range" rather than showing a dead array on a clear
 * afternoon and leaving the player to guess.
 *
 * The same mechanism is what makes clipping physical rather than cosmetic. When the inverter cannot
 * take everything on offer it moves its maximum power point tracker off the peak, and the array's
 * direct current genuinely falls - so {@link #mpptFraction} scales the readings as well as the output.
 *
 * <h2>Why two arrays side by side do not agree exactly</h2>
 *
 * Three reasons, and all three are the real ones.
 *
 * A cloud reaches one before the other. The cumulus field is sampled at each array's own coordinates
 * and drifts downwind, so a shadow crossing a field arrives a few seconds apart along it. This
 * dominates, and it is what shows up in a trend as two lines diverging and rejoining.
 *
 * The modules are not identical. Every real module is flash tested and sorted into a bin a couple of
 * percent wide, and that difference is fixed for the life of the panel - so
 * {@link #moduleTolerance} is drawn from the block position, never changes, and costs nothing to
 * remember.
 *
 * And dirt does not settle evenly. Soiling is accumulated per array against its own local weather,
 * so an array under a tree that is sheltered from the rain stays dirty while its neighbour in the
 * open is washed.
 *
 * What is deliberately *not* a reason: row-to-row shading between neighbouring blocks. See
 * {@link Shading} for why counting that would be counting it twice, and why a large field therefore
 * produces very nearly the same everywhere.
 */
public class PvArrayBlockEntity extends BlockEntity {
	/**
	 * How often the sky view is surveyed again, in ticks.
	 *
	 * A minute. What it measures is geometry - walls, roofs, trees - which changes when somebody
	 * builds rather than when the sun moves, and surveying it costs nine ray marches. The beam is
	 * traced every tick instead, because that one follows the sun.
	 */
	private static final int SKY_VIEW_TTL = 1200;
	/** Flash-test binning, peak to peak: real datasheets print plus or minus three percent. */
	private static final double MODULE_TOLERANCE = 0.03;
	/**
	 * Diffuse fraction above which a tracker gives up on the beam and lies flat.
	 *
	 * Four fifths, which is a heavy overcast. Past it there is no beam left worth pointing at, and a
	 * flat plane sees more of the dome than a tilted one - so going flat is worth a few percent. It is
	 * a real strategy on real controllers and it is why an overcast day is slightly less bad for a
	 * tracker than the loss of tracking would suggest.
	 */
	private static final double DIFFUSE_MODE_THRESHOLD = 0.80;
	/**
	 * How far the wind has to fall below the stow threshold before a row comes back out, as a fraction.
	 *
	 * Four fifths, so a gust that sits on the threshold does not have the drive going back and forth.
	 * The same hysteresis the turbines use on their cut-out, for the same reason.
	 */
	private static final double STOW_RELEASE = 0.8;
	/** Ticks of soiling accumulation per day-clock day. */
	private static final double TICKS_PER_DAY = 24000.0;

	// ---- what the block is ----

	private double skyViewFactor = 1.0;
	private int skyViewCountdown = 0;

	// ---- the tracker ----

	private double rotationDeg = 0.0;
	private double targetRotationDeg = 0.0;
	private double manualRotationDeg = 0.0;
	private TrackerMode trackerMode = TrackerMode.AUTO;
	private TrackerMode.Stow stowReason = TrackerMode.Stow.NONE;
	private boolean slewing = false;
	private boolean backtracking = false;
	private boolean windStowLatched = false;

	// ---- what it is making ----

	private double availableDcKw = 0.0;
	/** How much of the maximum power point the inverter is actually letting the array sit at, 0 to 1. */
	private double mpptFraction = 0.0;
	private BlockPos inverterPos = null;

	private double poaBeam = 0.0;
	private double poaDiffuse = 0.0;
	private double poaGround = 0.0;
	private double poaRear = 0.0;
	private double incidenceDeg = 90.0;
	private double effectiveIrradiance = 0.0;
	private double moduleTempC = 15.0;
	private double ambientTempC = 15.0;
	private double windSpeed = 0.0;
	private double spectralFactor = 1.0;
	private double soiling = 0.0;
	private double snowDepthM = 0.0;
	private double rowShadedFraction = 0.0;
	private double obstructionFraction = 1.0;
	private double stringVoltage = 0.0;
	private double stringCurrent = 0.0;

	private long lastSyncTick = 0L;

	public PvArrayBlockEntity(BlockPos pos, BlockState state) {
		super(Electricity.PV_ARRAY_BLOCK_ENTITY.get(), pos, state);
	}

	/**
	 * Which product this is.
	 *
	 * Read back off the block rather than persisted, exactly as a turbine reads its own spec, so a
	 * placed array cannot disagree with the item that placed it and there is no saved id to migrate.
	 */
	public PvArraySpec spec() {
		if (getBlockState().getBlock() instanceof PvArrayBlock array) return array.spec();

		return PvCatalog.fallback();
	}

	@Nullable
	public TrackerSpec tracker() {
		return spec().tracker();
	}

	// ---- the tick ----

	public void serverTick() {
		if (!(level instanceof ServerLevel serverLevel)) return;

		PvArraySpec spec = spec();
		// sampled as a one-block mast, which is the height a met station keeps its instruments and
		// near enough the height of the modules themselves
		WeatherSnapshot weather = GlobalWeatherManager.get(serverLevel).sample(worldPosition, 1);
		SkyConditions sky = weather.sky();

		ambientTempC = sky.ambientTempC();
		windSpeed = sky.windSpeed();

		if (--skyViewCountdown <= 0) {
			skyViewFactor = Shading.skyViewFactor(serverLevel, worldPosition);
			skyViewCountdown = SKY_VIEW_TTL;
		}

		snowDepthM = Shading.snowDepthOver(serverLevel, worldPosition);
		updateSoiling(serverLevel, spec, weather);
		updateTracker(spec, sky, weather);
		updatePlane(serverLevel, spec, sky);
		shedSnow(serverLevel, spec);

		maybeSync(serverLevel);
	}

	/** Interpolates the tracker on the client, so the plane moves between the server's ten-tick updates. */
	public void clientTick() {
		if (level == null || !level.isClientSide()) return;

		TrackerSpec tracker = tracker();
		if (tracker == null) return;

		double step = tracker.slewPerTick();
		double delta = targetRotationDeg - rotationDeg;
		if (Math.abs(delta) <= step) {
			rotationDeg = targetRotationDeg;
		} else {
			rotationDeg += Math.signum(delta) * step;
		}
	}

	// ---- the optics ----

	private void updatePlane(ServerLevel serverLevel, PvArraySpec spec, SkyConditions sky) {
		double tilt = spec.tiltDeg(rotationDeg);
		double azimuth = planeAzimuthDeg();

		PlaneIrradiance plane = Atmosphere.planeOfArray(sky, tilt, azimuth, spec.mounting().rearViewFactor());
		incidenceDeg = plane.incidenceAngleDeg();

		// three separate ways the beam can be taken away, and they multiply: what is built or grown
		// over the array, what is standing on it, and the row in front of it
		rowShadedFraction = Shading.rowShadedFraction(spec.groundCoverRatio(), tilt, sky.sun().elevationDeg());
		obstructionFraction = Shading.beamFraction(serverLevel, worldPosition, sky.sun())
				* Shading.entityBeamFraction(serverLevel, worldPosition, sky.sun());

		Shading.Shade snow = new Shading.Shade(1.0, 1.0, snowDepthM);
		plane = plane.withBeamFraction(obstructionFraction * (1.0 - rowShadedFraction))
				.withSkyFraction(skyViewFactor)
				.scaled(snow.snowTransmittance() * (1.0 - soiling));

		poaBeam = plane.beam();
		poaDiffuse = plane.skyDiffuse();
		poaGround = plane.groundReflected();
		poaRear = plane.rear();

		effectiveIrradiance = spec.effectiveIrradiance(plane.front(), plane.rear());
		moduleTempC = spec.module().cellTemperature(ambientTempC, effectiveIrradiance, windSpeed);
		spectralFactor = Atmosphere.spectralFactor(sky.airMass(), sky.diffuseFraction(), spec.module().cell().spectralSensitivity());

		availableDcKw = spec.dcPowerKw(effectiveIrradiance, moduleTempC, spectralFactor, moduleTolerance(serverLevel));
		// the string readings follow the operating point, not the maximum power point, so a clipped
		// array shows the voltage the inverter has pushed it to rather than the one it would prefer
		stringVoltage = spec.stringVoltage(moduleTempC);
		stringCurrent = spec.stringCurrent(effectiveIrradiance, moduleTempC) * mpptFraction;
	}

	/**
	 * This array's own share of the factory's tolerance band.
	 *
	 * Drawn from where it stands, so it is the same every tick, the same after a reload, and costs
	 * nothing to remember.
	 */
	private double moduleTolerance(ServerLevel serverLevel) {
		double unit = WeatherNoise.hashUnit(serverLevel.getSeed() ^ 0x9105L, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
		return 1.0 + (unit - 0.5) * MODULE_TOLERANCE;
	}

	/**
	 * Dust building up and rain washing it off.
	 *
	 * Real accumulated state rather than a reading, so it is one of the few things here that has to
	 * survive a reload: an array that has been standing in a dry season for a month is genuinely
	 * dirtier than one placed this morning, and forgetting that on chunk unload would hand a free
	 * clean to anybody who walked away and came back.
	 *
	 * How much a shower takes off depends on the tilt, which is one of the quieter reasons to tilt an
	 * array at all: rain running off a tilted module carries the dust with it, while rain on a flat
	 * one pools, spreads it about and dries dirty.
	 */
	private void updateSoiling(ServerLevel serverLevel, PvArraySpec spec, WeatherSnapshot weather) {
		double perDay = Shading.soilingPerDay(GlobalWeatherManager.get(serverLevel).siteAt(worldPosition.below()).downfall());

		if (serverLevel.isRainingAt(worldPosition.above())) {
			double washed = Shading.washedByRain(spec.tiltDeg(rotationDeg));
			// a shower washes over a few minutes rather than instantly, which is why this is a decay
			// rather than an assignment
			soiling *= 1.0 - washed * 0.01;
		} else {
			soiling = Math.min(Shading.soilingCeiling(), soiling + perDay / TICKS_PER_DAY);
		}
	}

	/**
	 * Snow sliding off, and taking the block above with it.
	 *
	 * The one place this block writes to the world, and it earns that: the snow a player can see on
	 * top of an array is the same snow that is stopping it working, so an array that sheds has to
	 * actually clear it. Otherwise a tracker would stand up into its snow stow, report itself clear,
	 * and go on producing nothing under a snow layer that never went anywhere.
	 *
	 * Needs the modules above freezing as well as the tilt, because what frees the snow is the film of
	 * meltwater under it - which is why a buried array on a bright cold day stays buried: it is
	 * producing nothing, so it is making no waste heat, so it stays at air temperature.
	 */
	private void shedSnow(ServerLevel serverLevel, PvArraySpec spec) {
		if (snowDepthM <= 0.0 || !spec.shedsSnow(rotationDeg, moduleTempC)) return;

		BlockPos above = worldPosition.above();
		BlockState state = serverLevel.getBlockState(above);
		if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
			serverLevel.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			snowDepthM = 0.0;
		}
	}

	// ---- the tracker drive ----

	private void updateTracker(PvArraySpec spec, SkyConditions sky, WeatherSnapshot weather) {
		TrackerSpec tracker = spec.tracker();
		if (tracker == null) {
			rotationDeg = spec.mounting().fixedTiltDeg();
			targetRotationDeg = rotationDeg;
			return;
		}

		targetRotationDeg = commandedRotation(tracker, spec, sky, weather);

		double step = tracker.slewPerTick();
		double delta = targetRotationDeg - rotationDeg;
		slewing = Math.abs(delta) > step;
		rotationDeg = slewing ? rotationDeg + Math.signum(delta) * step : targetRotationDeg;
	}

	/**
	 * Where the controller wants the row, and why.
	 *
	 * The order is the safe one and it is the order a real controller uses. A hand position and a
	 * commanded stow come first because somebody asked for them. Then wind, because a row standing up
	 * in a gale is how trackers get destroyed and no automatic decision may outrank that. Then snow,
	 * then night, then the sky - and only if none of those apply does it point at the sun.
	 */
	private double commandedRotation(TrackerSpec tracker, PvArraySpec spec, SkyConditions sky, WeatherSnapshot weather) {
		if (trackerMode == TrackerMode.MANUAL) {
			stowReason = TrackerMode.Stow.NONE;
			backtracking = false;
			return tracker.clampRotation(manualRotationDeg);
		}

		if (trackerMode == TrackerMode.STOW) {
			stowReason = TrackerMode.Stow.COMMANDED;
			backtracking = false;
			return tracker.nightStowDeg();
		}

		// latched with hysteresis, so a gust sitting on the threshold does not have the drive going
		// back and forth across it
		if (weather.gustWind() >= tracker.windStowSpeed()) {
			windStowLatched = true;
		} else if (windStowLatched && weather.gustWind() < tracker.windStowSpeed() * STOW_RELEASE) {
			windStowLatched = false;
		}

		if (windStowLatched) {
			stowReason = TrackerMode.Stow.WIND;
			backtracking = false;
			return tracker.windStowDeg();
		}

		if (snowDepthM >= tracker.snowStowDepth()) {
			stowReason = TrackerMode.Stow.SNOW;
			backtracking = false;
			// whichever way it is already leaning, so a row does not swing through the whole range to
			// shed snow it could have dropped by carrying on
			return rotationDeg < 0.0 ? -tracker.snowStowDeg() : tracker.snowStowDeg();
		}

		if (!sky.sun().up()) {
			stowReason = TrackerMode.Stow.NIGHT;
			backtracking = false;
			return tracker.nightStowDeg();
		}

		if (sky.diffuseFraction() >= DIFFUSE_MODE_THRESHOLD) {
			stowReason = TrackerMode.Stow.DIFFUSE;
			backtracking = false;
			return 0.0;
		}

		stowReason = TrackerMode.Stow.NONE;
		double elevation = sky.sun().elevationDeg();
		double trueRotation = tracker.trueRotation(elevation, sky.sun().afternoon());
		double commanded = tracker.backtrackRotation(trueRotation, elevation, spec.groundCoverRatio());
		backtracking = Math.abs(commanded - trueRotation) > 0.01;
		return tracker.clampRotation(commanded);
	}

	// ---- what the inverter talks to ----

	/**
	 * Offers this array to an inverter, and answers whether that inverter now owns it.
	 *
	 * First claim wins, and a closer one takes it over. Ownership matters because an array feeds
	 * exactly one inverter through exactly one set of DC cables, so letting two claim the same array
	 * would have the same modules producing twice.
	 */
	public boolean claim(BlockPos candidate) {
		if (level == null) return false;
		if (inverterPos != null && !inverterPos.equals(candidate)) {
			boolean stillThere = level.getBlockEntity(inverterPos) instanceof PvInverterBlockEntity;
			if (stillThere && inverterPos.distSqr(worldPosition) <= candidate.distSqr(worldPosition)) return false;
		}

		inverterPos = candidate.immutable();
		return true;
	}

	public void releaseClaim(BlockPos claimant) {
		if (claimant.equals(inverterPos)) {
			inverterPos = null;
			mpptFraction = 0.0;
		}
	}

	@Nullable
	public BlockPos inverterPos() {
		return inverterPos;
	}

	public boolean hasInverter() {
		return inverterPos != null && level != null && level.getBlockEntity(inverterPos) instanceof PvInverterBlockEntity;
	}

	/** What the modules could deliver at their maximum power point, in kW. */
	public double availableDcKw() {
		return availableDcKw;
	}

	/** What they are actually delivering, which is less whenever the inverter is holding them back. */
	public double deliveredDcKw() {
		return availableDcKw * mpptFraction;
	}

	/**
	 * Tells the array where the inverter has put its operating point, 0 to 1.
	 *
	 * One means the maximum power point. Less means the inverter cannot take everything and has pushed
	 * the string off the peak, which is what clipping physically is - so the array's own current
	 * reading falls with it rather than reporting a maximum power point it is not sitting at.
	 */
	public void setMpptFraction(double fraction) {
		mpptFraction = Mth.clamp(fraction, 0.0, 1.0);
	}

	// ---- readings ----

	public double poaBeam() {
		return poaBeam;
	}

	public double poaDiffuse() {
		return poaDiffuse;
	}

	public double poaGround() {
		return poaGround;
	}

	public double poaRear() {
		return poaRear;
	}

	/** Everything on the front of the plane, W/m2. What a reference cell in the array reads. */
	public double poaFront() {
		return poaBeam + poaDiffuse + poaGround;
	}

	/** Front plus whatever the back is worth after bifaciality, which is what the modules answer to. */
	public double effectiveIrradiance() {
		return effectiveIrradiance;
	}

	public double incidenceDeg() {
		return incidenceDeg;
	}

	public double moduleTempC() {
		return moduleTempC;
	}

	public double ambientTempC() {
		return ambientTempC;
	}

	public double windSpeed() {
		return windSpeed;
	}

	public double spectralFactor() {
		return spectralFactor;
	}

	public double soiling() {
		return soiling;
	}

	public double snowDepthM() {
		return snowDepthM;
	}

	public double rowShadedFraction() {
		return rowShadedFraction;
	}

	/** What is left of the beam after everything built, grown or standing over the array, 0 to 1. */
	public double obstructionFraction() {
		return obstructionFraction;
	}

	public double skyViewFactor() {
		return skyViewFactor;
	}

	public double stringVoltage() {
		return stringVoltage;
	}

	public double stringCurrent() {
		return stringCurrent;
	}

	public double arrayCurrent() {
		return stringCurrent * spec().strings();
	}

	public double rotationDeg() {
		return rotationDeg;
	}

	public double targetRotationDeg() {
		return targetRotationDeg;
	}

	public double tiltDeg() {
		return spec().tiltDeg(rotationDeg);
	}

	public double planeAzimuthDeg() {
		PvArraySpec spec = spec();
		if (spec.tracked()) {
			// a tracked plane faces east in the morning and west in the afternoon, by the sign of its
			// roll. Lying exactly flat it faces neither, and the tilt is zero so the bearing does not
			// enter the arithmetic
			return rotationDeg >= 0.0 ? 180.0 : 0.0;
		}

		return PvArrayBlock.planeAzimuthDeg(getBlockState().getValue(PvArrayBlock.FACING));
	}

	public TrackerMode trackerMode() {
		return trackerMode;
	}

	public TrackerMode.Stow stowReason() {
		return stowReason;
	}

	public boolean slewing() {
		return slewing;
	}

	public boolean backtracking() {
		return backtracking;
	}

	/**
	 * Performance ratio: what the array is making over what its nameplate would make in this light.
	 *
	 * The one figure worth logging if only one is, because it folds everything above into the number a
	 * plant is actually judged on - the temperature, the soiling, the snow, the shading, the spectrum
	 * and the module binning. A good array on a cool clear day is near 0.9; the same array at seventy
	 * degrees of cell temperature under a dirty sky is nearer 0.7.
	 */
	public double performanceRatio() {
		if (effectiveIrradiance <= 0.0) return 0.0;

		double reference = spec().dcPowerKw() * effectiveIrradiance / 1000.0;
		return reference <= 0.0 ? 0.0 : deliveredDcKw() / reference;
	}

	/** Motor draw of the tracker drive while it is slewing, in kW. Zero on a fixed mounting. */
	public double trackerMotorKw() {
		TrackerSpec tracker = tracker();
		return tracker != null && slewing ? tracker.motorW() / 1000.0 : 0.0;
	}

	// ---- control ----

	public void setTrackerMode(TrackerMode mode) {
		if (mode == null || trackerMode == mode || tracker() == null) return;

		trackerMode = mode;
		setChanged();
		sync();
	}

	public void setManualRotation(double degrees) {
		TrackerSpec tracker = tracker();
		if (tracker == null) return;

		manualRotationDeg = tracker.clampRotation(degrees);
		setChanged();
		sync();
	}

	public double manualRotationDeg() {
		return manualRotationDeg;
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

		// soiling is the only genuinely accumulated state here. Everything else is a reading that the
		// next tick recomputes from the weather, and is written only so a freshly loaded chunk shows a
		// number rather than a zero
		tag.putDouble("soiling", soiling);
		tag.putDouble("rotation", rotationDeg);
		tag.putDouble("targetRotation", targetRotationDeg);
		tag.putDouble("manualRotation", manualRotationDeg);
		tag.putString("trackerMode", trackerMode.name());
		tag.putString("stowReason", stowReason.name());
		tag.putBoolean("slewing", slewing);
		tag.putBoolean("backtracking", backtracking);
		tag.putBoolean("windStowLatched", windStowLatched);

		tag.putDouble("availableDc", availableDcKw);
		tag.putDouble("mpptFraction", mpptFraction);
		if (inverterPos != null) {
			tag.putLong("inverter", inverterPos.asLong());
		}

		tag.putDouble("poaBeam", poaBeam);
		tag.putDouble("poaDiffuse", poaDiffuse);
		tag.putDouble("poaGround", poaGround);
		tag.putDouble("poaRear", poaRear);
		tag.putDouble("incidence", incidenceDeg);
		tag.putDouble("effectiveIrradiance", effectiveIrradiance);
		tag.putDouble("moduleTemp", moduleTempC);
		tag.putDouble("ambientTemp", ambientTempC);
		tag.putDouble("windSpeed", windSpeed);
		tag.putDouble("spectral", spectralFactor);
		tag.putDouble("snowDepth", snowDepthM);
		tag.putDouble("rowShaded", rowShadedFraction);
		tag.putDouble("obstruction", obstructionFraction);
		tag.putDouble("skyView", skyViewFactor);
		tag.putDouble("stringVoltage", stringVoltage);
		tag.putDouble("stringCurrent", stringCurrent);
	}

	@Override
	public void load(@Nonnull CompoundTag tag) {
		super.load(tag);

		soiling = tag.getDouble("soiling");
		rotationDeg = tag.getDouble("rotation");
		targetRotationDeg = tag.contains("targetRotation") ? tag.getDouble("targetRotation") : rotationDeg;
		manualRotationDeg = tag.getDouble("manualRotation");
		TrackerMode savedMode = TrackerMode.byName(tag.getString("trackerMode"));
		trackerMode = savedMode != null ? savedMode : TrackerMode.AUTO;
		stowReason = stowByName(tag.getString("stowReason"));
		slewing = tag.getBoolean("slewing");
		backtracking = tag.getBoolean("backtracking");
		windStowLatched = tag.getBoolean("windStowLatched");

		availableDcKw = tag.getDouble("availableDc");
		mpptFraction = tag.getDouble("mpptFraction");
		inverterPos = tag.contains("inverter") ? BlockPos.of(tag.getLong("inverter")) : null;

		poaBeam = tag.getDouble("poaBeam");
		poaDiffuse = tag.getDouble("poaDiffuse");
		poaGround = tag.getDouble("poaGround");
		poaRear = tag.getDouble("poaRear");
		incidenceDeg = tag.contains("incidence") ? tag.getDouble("incidence") : 90.0;
		effectiveIrradiance = tag.getDouble("effectiveIrradiance");
		moduleTempC = tag.getDouble("moduleTemp");
		ambientTempC = tag.getDouble("ambientTemp");
		windSpeed = tag.getDouble("windSpeed");
		spectralFactor = tag.contains("spectral") ? tag.getDouble("spectral") : 1.0;
		snowDepthM = tag.getDouble("snowDepth");
		rowShadedFraction = tag.getDouble("rowShaded");
		obstructionFraction = tag.contains("obstruction") ? tag.getDouble("obstruction") : 1.0;
		skyViewFactor = tag.contains("skyView") ? tag.getDouble("skyView") : 1.0;
		stringVoltage = tag.getDouble("stringVoltage");
		stringCurrent = tag.getDouble("stringCurrent");
	}

	private static TrackerMode.Stow stowByName(String name) {
		for (TrackerMode.Stow stow : TrackerMode.Stow.values()) {
			if (stow.name().equalsIgnoreCase(name)) return stow;
		}

		return TrackerMode.Stow.NONE;
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
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TrackedBlockEntities.track(this));
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if (level != null && level.isClientSide()) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TrackedBlockEntities.untrack(this));
		}
	}

	/** Whether this mounting is one whose plane a renderer has to turn. */
	public boolean tracked() {
		return spec().tracked();
	}

	/** Whether the tracker has two axes, which the renderer needs to know to build the right pivot chain. */
	public boolean dualAxis() {
		return spec().mounting() == PvMounting.DUAL_AXIS;
	}

	/** Where the modules face, for the renderer: the same bearing the physics used. */
	public Direction facing() {
		return getBlockState().getValue(PvArrayBlock.FACING);
	}
}
