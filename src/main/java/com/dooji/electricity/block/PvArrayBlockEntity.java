package com.dooji.electricity.block;

import com.dooji.electricity.api.power.DcCableSpec;
import com.dooji.electricity.api.power.PvArraySpec;
import com.dooji.electricity.api.power.PvMounting;
import com.dooji.electricity.api.power.Telemetry;
import com.dooji.electricity.api.power.TrackerMode;
import com.dooji.electricity.api.power.TrackerSpec;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.registry.CableCatalog;
import com.dooji.electricity.main.registry.PvCatalog;
import com.dooji.electricity.main.weather.Atmosphere;
import com.dooji.electricity.main.weather.GlobalWeatherManager;
import com.dooji.electricity.main.weather.PlaneIrradiance;
import com.dooji.electricity.main.weather.Shading;
import com.dooji.electricity.main.weather.SkyConditions;
import com.dooji.electricity.main.weather.WeatherNoise;
import com.dooji.electricity.main.weather.WeatherSnapshot;
import com.dooji.electricity.power.SolarTelemetrySimulator;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** One block of photovoltaic array, working out what its modules are making. */
public class PvArrayBlockEntity extends BlockEntity {
	/** How often the sky view is surveyed again, in ticks. */
	private static final int SKY_VIEW_TTL = 200;
	/** Flash-test binning, peak to peak: real datasheets print plus or minus three percent. */
	private static final double MODULE_TOLERANCE = 0.03;
	/** How much better lying flat has to be before a tracker gives up on the sun, as a ratio. */
	private static final double DIFFUSE_MARGIN = 1.25;
	/** How long the diffuse decision is held after it has reversed, in ticks. */
	private static final int DIFFUSE_DWELL_TICKS = 100;
	/** How much of what a shower can wash off it takes away per tick, as a fraction. */
	private static final double RAIN_WASH_PER_TICK = 0.01;
	/** How far the wind has to fall below the stow threshold before a row comes back out, as a fraction. */
	private static final double STOW_RELEASE = 0.8;
	/** How long a weather stow is held after its cause has gone, in ticks. */
	private static final int STOW_DWELL_TICKS = 600;
	/** Ticks of soiling accumulation per day-clock day. */
	private static final double TICKS_PER_DAY = 24000.0;
	/** How long a claim outlives the last word from the inverter that made it, in ticks. */
	private static final int CLAIM_LEASE_TICKS = 100;

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
	/** What the row is being put away for, and for how much longer after the cause has gone. */
	private TrackerMode.Stow heldStow = TrackerMode.Stow.NONE;
	private int stowHoldTicks = 0;
	/** Whether the row is lying flat because flat collects more, and its own shorter dwell. */
	private boolean diffuseMode = false;
	private int diffuseHoldTicks = 0;

	// ---- what it is making ----

	private double availableDcKw = 0.0;
	/** How much of the maximum power point the inverter is actually letting the array sit at */
	private double mpptFraction = 0.0;
	/** Whichever machine is collecting this array's strings, or null. */
	private BlockPos collectorPos = null;
	/** The cable its leads are wired with */
	private DcCableSpec cable = null;
	private double runMetres = 0.0;
	private double runBuriedFraction = 0.0;
	/** Ticks since the inverter last said anything, against which the claim is a lease. */
	private int claimAge = 0;

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
	/** String current at the maximum power point */
	private double stringCurrentMpp = 0.0;

	private final ClientSync clientSync = new ClientSync();

	/** The latest published snapshot, safe to read from any thread. */
	private volatile Telemetry.Snapshot telemetry = Telemetry.Snapshot.EMPTY;

	public PvArrayBlockEntity(BlockPos pos, BlockState state) {
		super(Electricity.PV_ARRAY_BLOCK_ENTITY.get(), pos, state);
	}

	/** Which product this is. */
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

		expireClaim();

		if (--skyViewCountdown <= 0) {
			skyViewFactor = Shading.skyViewFactor(serverLevel, worldPosition);
			skyViewCountdown = SKY_VIEW_TTL;
		}

		snowDepthM = Shading.snowDepthOver(serverLevel, worldPosition);
		updateSoiling(serverLevel, spec, weather);
		updateTracker(spec, sky, weather);
		updatePlane(serverLevel, spec, sky);
		shedSnow(serverLevel, spec);
		updateTelemetry(spec);

		clientSync.throttled(this);
	}

	/** Interpolates the tracker on the client, so the plane moves between the server's ten-tick updates. */
	public void clientTick() {
		if (level == null || !level.isClientSide()) return;

		TrackerSpec tracker = tracker();
		if (tracker != null) {
			slewTowardsTarget(tracker);
		}
	}

	/** Runs the drive, if it is worth running. */
	private void slewTowardsTarget(TrackerSpec tracker) {
		double error = targetRotationDeg - rotationDeg;
		if (!slewing && followingTheSun() && !tracker.worthMoving(error)) return;

		double step = tracker.slewPerTick();
		slewing = Math.abs(error) > step;
		rotationDeg = slewing ? rotationDeg + Math.signum(error) * step : targetRotationDeg;
	}

	// ---- the optics ----

	private void updatePlane(ServerLevel serverLevel, PvArraySpec spec, SkyConditions sky) {
		double tilt = spec.tiltDeg(rotationDeg);
		double azimuth = planeAzimuthDeg();

		PlaneIrradiance plane = Atmosphere.planeOfArray(sky, tilt, azimuth, spec.mounting().rearViewFactor());
		incidenceDeg = plane.incidenceAngleDeg();

		// three separate ways the beam can be taken away, and they multiply: what is built or grown
		// over the array, what is standing on it
		rowShadedFraction = Shading.rowShadedFraction(spec.groundCoverRatio(), tilt, sky.sun().elevationDeg());
		obstructionFraction = Shading.beamFraction(serverLevel, worldPosition, sky.sun())
				 * Shading.entityBeamFraction(serverLevel, worldPosition, sky.sun());

		plane = plane.withBeamFraction(obstructionFraction * (1.0 - rowShadedFraction))
				.withSkyFraction(skyViewFactor)
				// both faces, and for two different reasons: snow buries a module
				// whatever is facing up - which on a tracker at sixty degrees is very nearly both of them
				.scaled(Shading.snowTransmittance(snowDepthM) * (1.0 - soiling));

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
		stringCurrentMpp = spec.stringCurrent(effectiveIrradiance, moduleTempC);
		stringCurrent = stringCurrentMpp * mpptFraction;
	}

	/** This array's own share of the factory's tolerance band. */
	private double moduleTolerance(ServerLevel serverLevel) {
		double unit = WeatherNoise.hashUnit(serverLevel.getSeed() ^ 0x9105L, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
		return 1.0 + (unit - 0.5) * MODULE_TOLERANCE;
	}

	/** Dust building up and rain washing it off. */
	private void updateSoiling(ServerLevel serverLevel, PvArraySpec spec, WeatherSnapshot weather) {
		double perDay = Shading.soilingPerDay(GlobalWeatherManager.get(serverLevel).siteAt(worldPosition.below()).downfall());

		if (serverLevel.isRainingAt(worldPosition.above())) {
			double washed = Shading.washedByRain(spec.tiltDeg(rotationDeg));
			// a shower washes over a few minutes rather than instantly, which is why this is a decay
			// rather than an assignment
			soiling *= 1.0 - washed * RAIN_WASH_PER_TICK;
		} else {
			soiling = Math.min(Shading.soilingCeiling(), soiling + perDay / TICKS_PER_DAY);
		}
	}

	/** Snow sliding off, and taking the block above with it. */
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
		slewTowardsTarget(tracker);
	}

	/** Whether the drive is following the sun, as opposed to being sent somewhere. */
	private boolean followingTheSun() {
		return trackerMode == TrackerMode.AUTO && stowReason == TrackerMode.Stow.NONE;
	}

	/** Where the controller wants the row, and why. */
	private double commandedRotation(TrackerSpec tracker, PvArraySpec spec, SkyConditions sky, WeatherSnapshot weather) {
		if (trackerMode == TrackerMode.MANUAL) {
			stowReason = TrackerMode.Stow.NONE;
			backtracking = false;
			releaseDiffuse();
			return tracker.clampRotation(manualRotationDeg);
		}

		if (trackerMode == TrackerMode.STOW) {
			stowReason = TrackerMode.Stow.COMMANDED;
			backtracking = false;
			releaseDiffuse();
			return tracker.nightStowDeg();
		}

		// night is not held, because its release is the one condition here that cannot chatter: the sun
		TrackerMode.Stow putAway = sky.sun().up() ? heldPutAway(putAwayStow(tracker, sky, weather)) : TrackerMode.Stow.NIGHT;
		if (putAway == TrackerMode.Stow.NIGHT) {
			stowReason = TrackerMode.Stow.NIGHT;
			backtracking = false;
			releaseDiffuse();
			heldStow = TrackerMode.Stow.NONE;
			stowHoldTicks = 0;
			return tracker.nightStowDeg();
		}

		if (putAway != TrackerMode.Stow.NONE) {
			stowReason = putAway;
			backtracking = false;
			releaseDiffuse();
			// snow is the one stow that is not flat, and it goes whichever way the row is already
			return switch (putAway) {
				case SNOW -> rotationDeg < 0.0 ? -tracker.snowStowDeg() : tracker.snowStowDeg();
				case WIND -> tracker.windStowDeg();
				default -> tracker.nightStowDeg();
			};
		}

		double elevation = sky.sun().elevationDeg();
		double trueRotation = tracker.trueRotation(elevation, sky.sun().afternoon());
		double commanded = tracker.clampRotation(tracker.backtrackRotation(trueRotation, elevation, spec.groundCoverRatio()));

		// and the last decision is not a stow at all but a choice between two angles
		// working out what each would collect rather than by testing the sky against a number
		if (heldDiffuse(flatIsBetter(spec, sky, commanded))) {
			stowReason = TrackerMode.Stow.DIFFUSE;
			backtracking = false;
			return 0.0;
		}

		stowReason = TrackerMode.Stow.NONE;
		backtracking = Math.abs(commanded - tracker.clampRotation(trueRotation)) > 0.01;
		return commanded;
	}

	/** Which condition the row has to be put away for, in order of precedence. */
	private TrackerMode.Stow putAwayStow(TrackerSpec tracker, SkyConditions sky, WeatherSnapshot weather) {
		// Supervised on the mean and on the gust separately, against their own limits
		if (weather.meanWind() >= tracker.windStowSpeed() || weather.gustWind() >= tracker.gustStowSpeed()) {
			windStowLatched = true;
		} else if (windStowLatched && weather.meanWind() < tracker.windStowSpeed() * STOW_RELEASE) {
			windStowLatched = false;
		}

		if (windStowLatched) return TrackerMode.Stow.WIND;
		if (snowDepthM >= tracker.snowStowDepth()) return TrackerMode.Stow.SNOW;

		return TrackerMode.Stow.NONE;
	}

	/** Whether the row would collect more lying flat than pointed where it means to point. */
	private boolean flatIsBetter(PvArraySpec spec, SkyConditions sky, double commanded) {
		if (!sky.sun().up()) return false;

		double tracking = Atmosphere.planeOfArray(sky, spec.tiltDeg(commanded), planeAzimuthFor(commanded), 0.0).front();
		double flat = Atmosphere.planeOfArray(sky, 0.0, planeAzimuthFor(commanded), 0.0).front();

		return diffuseMode ? tracking < flat * DIFFUSE_MARGIN : flat > tracking * DIFFUSE_MARGIN;
	}

	/** Holds a put-away stow for a while after its cause has gone. */
	private TrackerMode.Stow heldPutAway(TrackerMode.Stow wanted) {
		if (wanted != TrackerMode.Stow.NONE) {
			heldStow = wanted;
			stowHoldTicks = STOW_DWELL_TICKS;
			return wanted;
		}

		if (heldStow != TrackerMode.Stow.NONE && stowHoldTicks > 0) {
			stowHoldTicks--;
			return heldStow;
		}

		heldStow = TrackerMode.Stow.NONE;
		return TrackerMode.Stow.NONE;
	}

	/** Drops the diffuse choice and its dwell. */
	private void releaseDiffuse() {
		diffuseMode = false;
		diffuseHoldTicks = 0;
	}

	/** The same for the diffuse choice, which keeps its own dwell because it is a much shorter one. */
	private boolean heldDiffuse(boolean wanted) {
		if (wanted) {
			diffuseMode = true;
			diffuseHoldTicks = DIFFUSE_DWELL_TICKS;
			return true;
		}

		if (diffuseMode && diffuseHoldTicks > 0) {
			diffuseHoldTicks--;
			return true;
		}

		diffuseMode = false;
		return false;
	}

	// ---- what the inverter talks to ----

	/** Whether the strings have leads on them at all, which is the first thing anything else asks. */
	public boolean harnessed() {
		return PvArrayBlock.harnessed(getBlockState());
	}

	/** Offers this array to a collector down a measured run */
	public boolean claim(BlockPos candidate, DcNetwork.Reach run, DcCableSpec through) {
		if (level == null) return false;
		if (collectorPos != null && !collectorPos.equals(candidate) && runMetres <= run.metres()) return false;

		collectorPos = candidate.immutable();
		cable = through;
		runMetres = run.metres();
		runBuriedFraction = run.buriedFraction();
		claimAge = 0;
		return true;
	}

	public void releaseClaim(BlockPos claimant) {
		if (claimant.equals(collectorPos)) forgetCollector();
	}

	/** Lets the claim go when the collector has stopped renewing it. */
	private void expireClaim() {
		if (collectorPos == null) return;
		if (++claimAge <= CLAIM_LEASE_TICKS) return;

		forgetCollector();
	}

	private void forgetCollector() {
		collectorPos = null;
		cable = null;
		runMetres = 0.0;
		runBuriedFraction = 0.0;
		mpptFraction = 0.0;
	}

	@Nullable
	public BlockPos collectorPos() {
		return collectorPos;
	}

	/** Whether something is collecting this array's strings and still saying so. */
	public boolean wired() {
		return collectorPos != null;
	}

	/** Length of the run to the collector, in metres. */
	public double runMetres() {
		return runMetres;
	}

	/** How much of that run is in the ground */
	public double runBuriedFraction() {
		return runBuriedFraction;
	}

	/** What the run burns, as a fraction of what is going down it. */
	public double dcLossFraction() {
		if (cable == null || runMetres <= 0.0) return 0.0;

		return cable.lossFraction(stringCurrentMpp, stringVoltage, runMetres);
	}

	/** What arrives at the collector, in kW: everything the modules can make, less what the copper takes. */
	public double offeredDcKw() {
		return availableDcKw * (1.0 - dcLossFraction());
	}

	/** The string voltage as the collector sees it, which is lower by the drop down the run. */
	public double stringVoltageAtCollector() {
		if (cable == null || runMetres <= 0.0) return stringVoltage;

		return Math.max(0.0, stringVoltage - cable.voltageDrop(stringCurrentMpp, runMetres));
	}

	/** What the modules could deliver at their maximum power point, in kW, at the array's own terminals. */
	public double availableDcKw() {
		return availableDcKw;
	}

	/** What they are actually delivering, which is less whenever the inverter is holding them back. */
	public double deliveredDcKw() {
		return availableDcKw * mpptFraction;
	}

	/** Tells the array where the inverter has put its operating point */
	public void setMpptFraction(double fraction) {
		mpptFraction = Mth.clamp(fraction, 0.0, 1.0);
		claimAge = 0;
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

	/** Everything on the front of the plane, W/m2. */
	public double poaFront() {
		return poaBeam + poaDiffuse + poaGround;
	}

	/** Front plus whatever the back is worth after bifaciality */
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

	/** What is left of the beam after everything built, grown or standing over the array */
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
		return planeAzimuthFor(rotationDeg);
	}

	/** Which way the plane would face at a given tracker rotation. */
	private double planeAzimuthFor(double rotation) {
		if (spec().tracked()) {
			// a tracked plane faces east in the morning and west in the afternoon
			return rotation >= 0.0 ? 180.0 : 0.0;
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

	/** Performance ratio: what the array is making over what its nameplate would make in this light. */
	public double performanceRatio() {
		if (effectiveIrradiance <= 0.0) return 0.0;

		double reference = spec().dcPowerKw() * effectiveIrradiance / 1000.0;
		return reference <= 0.0 ? 0.0 : deliveredDcKw() / reference;
	}

	/** Motor draw of the tracker drive while it is slewing, in kW. */
	public double trackerMotorKw() {
		TrackerSpec tracker = tracker();
		return tracker != null && slewing ? tracker.motorW() / 1000.0 : 0.0;
	}

	/** The latest published snapshot. */
	public Telemetry.Snapshot getTelemetry() {
		return telemetry;
	}

	private void updateTelemetry(PvArraySpec spec) {
		telemetry = SolarTelemetrySimulator.sampleArray(new SolarTelemetrySimulator.ArraySample(
				spec, poaBeam, poaDiffuse, poaGround, poaRear, effectiveIrradiance, incidenceDeg,
				moduleTempC, ambientTempC, windSpeed, spectralFactor, soiling, snowDepthM,
				rowShadedFraction, obstructionFraction, skyViewFactor, stringVoltage, stringCurrent,
				availableDcKw, deliveredDcKw(), performanceRatio(), rotationDeg, targetRotationDeg,
				tiltDeg(), planeAzimuthDeg(), trackerMode.name(), stowReason.name(), slewing, backtracking,
				wired(), harnessed(), runMetres, runBuriedFraction, dcLossFraction(), trackerMotorKw()));
	}

	// ---- control ----

	public void setTrackerMode(TrackerMode mode) {
		if (mode == null || trackerMode == mode || tracker() == null) return;

		trackerMode = mode;
		setChanged();
		ClientSync.now(this);
	}

	public void setManualRotation(double degrees) {
		TrackerSpec tracker = tracker();
		if (tracker == null) return;

		manualRotationDeg = tracker.clampRotation(degrees);
		setChanged();
		ClientSync.now(this);
	}

	public double manualRotationDeg() {
		return manualRotationDeg;
	}

	// ---- persistence and syncing ----

	@Override
	protected void saveAdditional(@Nonnull CompoundTag tag) {
		super.saveAdditional(tag);

		// soiling is the only genuinely accumulated state here.
		tag.putDouble("soiling", soiling);
		tag.putDouble("rotation", rotationDeg);
		tag.putDouble("targetRotation", targetRotationDeg);
		tag.putDouble("manualRotation", manualRotationDeg);
		tag.putString("trackerMode", trackerMode.name());
		tag.putString("stowReason", stowReason.name());
		tag.putBoolean("slewing", slewing);
		tag.putBoolean("backtracking", backtracking);
		tag.putBoolean("windStowLatched", windStowLatched);
		tag.putBoolean("diffuseMode", diffuseMode);
		tag.putInt("diffuseHoldTicks", diffuseHoldTicks);
		tag.putString("heldStow", heldStow.name());
		tag.putInt("stowHoldTicks", stowHoldTicks);

		tag.putDouble("availableDc", availableDcKw);
		tag.putDouble("mpptFraction", mpptFraction);
		tag.putDouble("runMetres", runMetres);
		tag.putDouble("runBuried", runBuriedFraction);
		if (cable != null) tag.putString("cable", cable.id().toString());
		if (collectorPos != null) {
			tag.putLong("collector", collectorPos.asLong());
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
		diffuseMode = tag.getBoolean("diffuseMode");
		diffuseHoldTicks = tag.getInt("diffuseHoldTicks");
		heldStow = stowByName(tag.getString("heldStow"));
		stowHoldTicks = tag.getInt("stowHoldTicks");

		availableDcKw = tag.getDouble("availableDc");
		mpptFraction = tag.getDouble("mpptFraction");
		runMetres = tag.getDouble("runMetres");
		runBuriedFraction = tag.getDouble("runBuried");
		cable = tag.contains("cable") ? CableCatalog.byId(new ResourceLocation(tag.getString("cable"))) : null;
		collectorPos = tag.contains("collector") ? BlockPos.of(tag.getLong("collector")) : null;

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
		ClientTracking.track(this);
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		ClientTracking.untrack(this);
	}

	/** Whether this mounting is one whose plane a renderer has to turn. */
	public boolean tracked() {
		return spec().tracked();
	}

	/** Whether the tracker has two axes, which the renderer needs to know to build the right pivot chain. */
	public boolean dualAxis() {
		return spec().mounting() == PvMounting.DUAL_AXIS;
	}
}
