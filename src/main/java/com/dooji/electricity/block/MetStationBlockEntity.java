package com.dooji.electricity.block;

import com.dooji.electricity.api.power.PvModuleSpec;
import com.dooji.electricity.api.power.SensorSpec;
import com.dooji.electricity.api.power.Telemetry;
import com.dooji.electricity.client.TrackedBlockEntities;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.ElectricityServerConfig;
import com.dooji.electricity.main.registry.SensorCatalog;
import com.dooji.electricity.main.weather.Atmosphere;
import com.dooji.electricity.main.weather.GlobalWeatherManager;
import com.dooji.electricity.main.weather.PlaneIrradiance;
import com.dooji.electricity.main.weather.Shading;
import com.dooji.electricity.main.weather.SkyConditions;
import com.dooji.electricity.main.weather.WeatherSnapshot;
import com.dooji.electricity.power.SolarTelemetrySimulator;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * The met mast's instruments, each answering at its own speed.
 *
 * <h2>Why the readings are not simply the weather</h2>
 *
 * Because a plant's monitoring reads instruments, and instruments lag. A thermopile pyranometer is a
 * black disc that has to change temperature before it can change its output, so it takes five seconds
 * to answer a cloud edge that the modules answered instantly - which is why a real plant's irradiance
 * trend is visibly smoother than the power trend beside it, and why noticing that the power moved first
 * is how an operator knows the sensor is telling the truth slowly rather than the array telling lies.
 *
 * A reference cell has no thermal mass and answers in microseconds, so it tracks the modules closely and
 * disagrees with the pyranometer beside it in a different way: it only sees the wavelengths the modules
 * see, so it reads low whenever the light is red. Both are in here, both are right, and they do not
 * agree.
 *
 * <h2>Snow, and why there are two numbers for it</h2>
 *
 * An ultrasonic gauge does not measure snow. It measures the distance to whatever is under it, and the
 * depth is a subtraction from a surveyed clear-ground reference done afterwards. Publishing both is what
 * a real plant does, and it is how you find out the transducer has iced over or something has been
 * parked beneath it - the two stop being consistent. The sensor's range also starts half a metre out,
 * so mounted two metres up it genuinely cannot see snow deeper than a metre and a half.
 */
public class MetStationBlockEntity extends BlockEntity {
	/**
	 * Height the snow gauge is mounted at, in metres.
	 *
	 * Two, which is where a real one goes: high enough to be clear of the deepest snow the site expects
	 * and low enough that the echo comes back. It is also what makes the two snow readings differ, since
	 * the distance is measured from here and the depth is worked out from it.
	 */
	private static final double SNOW_SENSOR_HEIGHT_M = 2.0;
	/** How often the mast looks again for the array its two on-array instruments are fitted to, in ticks. */
	private static final int RESCAN_TICKS = 40;
	/** What a reference cell is made of. Crystalline silicon, whatever the array beside it is made of. */
	private static final PvModuleSpec.Cell REFERENCE_CELL_TECHNOLOGY = PvModuleSpec.Cell.PERC;

	private int rescanCountdown = 0;
	private BlockPos referenceArray = null;

	private double globalIrradiance = 0.0;
	private double diffuseIrradiance = 0.0;
	private double planeIrradiance = 0.0;
	private double referenceCell = 0.0;
	private double albedo = 0.0;
	private double ambientTempC = 15.0;
	private double moduleTempC = 15.0;
	private double windSpeed = 0.0;
	private double windDirection = 0.0;
	private double snowDistanceM = SNOW_SENSOR_HEIGHT_M;
	private double snowHeightM = 0.0;

	private long lastSyncTick = 0L;

	private volatile Telemetry.Snapshot telemetry = Telemetry.Snapshot.EMPTY;

	public MetStationBlockEntity(BlockPos pos, BlockState state) {
		super(Electricity.MET_STATION_BLOCK_ENTITY.get(), pos, state);
	}

	public void serverTick() {
		if (!(level instanceof ServerLevel serverLevel)) return;

		if (--rescanCountdown <= 0) {
			referenceArray = findReferenceArray(serverLevel);
			rescanCountdown = RESCAN_TICKS;
		}

		WeatherSnapshot weather = GlobalWeatherManager.get(serverLevel).sample(worldPosition, 1);
		SkyConditions sky = weather.sky();

		PvArrayBlockEntity array = referenceArray != null && serverLevel.getBlockEntity(referenceArray) instanceof PvArrayBlockEntity found ? found : null;
		double tilt = array != null ? array.tiltDeg() : 0.0;
		double azimuth = array != null ? array.planeAzimuthDeg() : 90.0;
		PlaneIrradiance plane = Atmosphere.planeOfArray(sky, tilt, azimuth, 0.0);

		// each instrument reads the truth through its own time constant. The pyranometers smooth, the
		// reference cell does not, and the thermometers smooth a great deal
		globalIrradiance = SensorCatalog.SP_11.reading(globalIrradiance, sky.globalHorizontal(), 1);
		diffuseIrradiance = SensorCatalog.SD_11.reading(diffuseIrradiance, sky.diffuseHorizontal(), 1);
		planeIrradiance = SensorCatalog.SP_11.reading(planeIrradiance, plane.front(), 1);
		// the reference cell only sees what a cell sees, so the spectral factor applies to it and not to
		// the pyranometers - which is exactly why the two disagree at first light and agree at noon. It
		// is a crystalline silicon device whatever the array it stands beside is made of, which is a real
		// source of error on a thin-film plant and the reason those are commissioned against pyranometers
		double spectral = Atmosphere.spectralFactor(sky.airMass(), sky.diffuseFraction(), REFERENCE_CELL_TECHNOLOGY.spectralSensitivity());
		referenceCell = SensorCatalog.RC_1.reading(referenceCell, plane.front() * spectral, 1);
		albedo = SensorCatalog.SA_11.reading(albedo, sky.albedo(), 1);
		ambientTempC = SensorCatalog.AT_7.reading(ambientTempC, sky.ambientTempC(), 1);
		moduleTempC = SensorCatalog.MT_1000.reading(moduleTempC, array != null ? array.moduleTempC() : sky.ambientTempC(), 1);
		windSpeed = SensorCatalog.WS_3.reading(windSpeed, weather.instantWind(), 1);
		// a vane answers by turning, so it is smoothed like the rest, but across the 0/360 seam rather
		// than through it - a wind backing from 5 degrees to 355 has turned ten degrees, not three hundred
		windDirection = smoothBearing(windDirection, weather.direction());

		double snowOnGround = Shading.snowDepthOver(serverLevel, worldPosition.below());
		snowDistanceM = SensorCatalog.SN_50.reading(snowDistanceM, SNOW_SENSOR_HEIGHT_M - snowOnGround, 1);
		snowHeightM = Math.max(0.0, SNOW_SENSOR_HEIGHT_M - snowDistanceM);

		telemetry = SolarTelemetrySimulator.sampleStation(new SolarTelemetrySimulator.StationSample(
				globalIrradiance, diffuseIrradiance, planeIrradiance, referenceCell, albedo, ambientTempC,
				moduleTempC, windSpeed, windDirection, snowDistanceM, snowHeightM, diffuseFraction(),
				sky.clearnessIndex(), sky.sun().elevationDeg(), sky.sun().azimuthDeg()));

		maybeSync(serverLevel);
	}

	/**
	 * The array the two on-array instruments are fitted to: the nearest one.
	 *
	 * A real plant picks a representative array and bolts the tilted pyranometer and the back-of-module
	 * thermometer to it, and every performance calculation for the whole site is then written against
	 * that one array's plane. Nearest is as good a choice as any and better than most, because a mast is
	 * put where the plant is.
	 */
	@Nullable
	private BlockPos findReferenceArray(ServerLevel serverLevel) {
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
					if (blockEntity instanceof PvArrayBlockEntity array && array.getBlockPos().distSqr(worldPosition) <= (double) radius * radius) {
						candidates.add(array);
					}
				}
			}
		}

		return candidates.stream().min(Comparator.comparingDouble(array -> array.getBlockPos().distSqr(worldPosition)))
				.map(array -> array.getBlockPos().immutable()).orElse(null);
	}

	/** A first-order lag on a bearing, taken the short way round the compass. */
	private static double smoothBearing(double previous, double target) {
		double delta = ((target - previous + 540.0) % 360.0) - 180.0;
		double stepped = previous + delta * 0.15;
		return (stepped % 360.0 + 360.0) % 360.0;
	}

	/** The latest published snapshot. Safe to read from any thread; never null. */
	public Telemetry.Snapshot getTelemetry() {
		return telemetry;
	}

	// ---- readings ----

	/** Global horizontal irradiance, W/m2: the pyranometer lying flat. */
	public double globalIrradiance() {
		return globalIrradiance;
	}

	/** Diffuse horizontal irradiance, W/m2: the pyranometer under the shadow ring. */
	public double diffuseIrradiance() {
		return diffuseIrradiance;
	}

	/** Irradiance in the plane of the reference array, W/m2. Equal to the global when there is no array to follow. */
	public double planeIrradiance() {
		return planeIrradiance;
	}

	/** What the reference cell reads: the plane irradiance as a silicon cell sees it. */
	public double referenceCell() {
		return referenceCell;
	}

	public double albedo() {
		return albedo;
	}

	public double ambientTempC() {
		return ambientTempC;
	}

	/** Back-of-module temperature from the reference array, or air temperature if there is no array. */
	public double moduleTempC() {
		return moduleTempC;
	}

	public double windSpeed() {
		return windSpeed;
	}

	public double windDirection() {
		return windDirection;
	}

	/** What the gauge measures: distance to the surface below it, in metres. */
	public double snowDistanceM() {
		return snowDistanceM;
	}

	/** What is worked out from it: depth of snow, in metres. */
	public double snowHeightM() {
		return snowHeightM;
	}

	/** Diffuse over global, as the two pyranometers report it rather than as the sky model knows it. */
	public double diffuseFraction() {
		return globalIrradiance <= 0.0 ? 1.0 : Math.min(1.0, diffuseIrradiance / globalIrradiance);
	}

	@Nullable
	public BlockPos referenceArray() {
		return referenceArray;
	}

	/** The instruments this mast carries, for a panel that wants to list them. */
	public List<SensorSpec> instruments() {
		return SensorCatalog.all();
	}

	// ---- persistence and syncing ----

	private void maybeSync(ServerLevel serverLevel) {
		long now = serverLevel.getGameTime();
		if (now - lastSyncTick < 10L) return;

		lastSyncTick = now;
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void saveAdditional(@Nonnull CompoundTag tag) {
		super.saveAdditional(tag);

		// every reading here is written, and none of it is state: an instrument that has just been
		// loaded has to start from something, and starting from the last reading rather than from zero
		// is what stops a freshly loaded mast reporting a hard frost at noon while its thermometers
		// warm up through their thirty-second time constants
		tag.putDouble("global", globalIrradiance);
		tag.putDouble("diffuse", diffuseIrradiance);
		tag.putDouble("plane", planeIrradiance);
		tag.putDouble("referenceCell", referenceCell);
		tag.putDouble("albedo", albedo);
		tag.putDouble("ambientTemp", ambientTempC);
		tag.putDouble("moduleTemp", moduleTempC);
		tag.putDouble("windSpeed", windSpeed);
		tag.putDouble("windDirection", windDirection);
		tag.putDouble("snowDistance", snowDistanceM);
		tag.putDouble("snowHeight", snowHeightM);
		if (referenceArray != null) {
			tag.putLong("referenceArray", referenceArray.asLong());
		}
	}

	@Override
	public void load(@Nonnull CompoundTag tag) {
		super.load(tag);

		globalIrradiance = tag.getDouble("global");
		diffuseIrradiance = tag.getDouble("diffuse");
		planeIrradiance = tag.getDouble("plane");
		referenceCell = tag.getDouble("referenceCell");
		albedo = tag.getDouble("albedo");
		ambientTempC = tag.contains("ambientTemp") ? tag.getDouble("ambientTemp") : 15.0;
		moduleTempC = tag.contains("moduleTemp") ? tag.getDouble("moduleTemp") : 15.0;
		windSpeed = tag.getDouble("windSpeed");
		windDirection = tag.getDouble("windDirection");
		snowDistanceM = tag.contains("snowDistance") ? tag.getDouble("snowDistance") : SNOW_SENSOR_HEIGHT_M;
		snowHeightM = tag.getDouble("snowHeight");
		referenceArray = tag.contains("referenceArray") ? BlockPos.of(tag.getLong("referenceArray")) : null;
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
}
