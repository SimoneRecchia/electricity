package com.dooji.electricity.block;

import com.dooji.electricity.api.power.ElectricityCapabilities;
import com.dooji.electricity.api.power.IElectricPowerConsumer;
import com.dooji.electricity.api.power.PowerDeliveryEvent;
import com.dooji.electricity.client.render.obj.ObjBoundingBoxRegistry;
import com.dooji.electricity.client.render.obj.ObjModel;
import com.dooji.electricity.client.wire.InsulatorLookup;
import com.dooji.electricity.client.wire.WireManagerClient;
import com.dooji.electricity.api.power.ConductorSpec;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.ElectricityServerConfig;
import com.dooji.electricity.main.registry.ObjBlockDefinition;
import com.dooji.electricity.main.registry.ObjDefinitions;
import com.dooji.electricity.wire.InsulatorIdRegistry;
import com.dooji.electricity.wire.InsulatorPartHelper;
import com.dooji.electricity.power.PowerFieldManager;
import com.dooji.electricity.wire.InsulatorHost;
import com.dooji.electricity.wire.InsulatorPartHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.joml.Vector3f;

public class PowerBoxBlockEntity extends BlockEntity implements InsulatorHost {
	private Vec3[] wirePositions;
	private int[] insulatorIds;

	private double currentPower = 0.0;
	private static final double POWER_THRESHOLD = 0.1;
	private static final String[] POWER_PROPERTY_NAMES = {"powered", "lit"};
	private PowerDeliveryEvent incomingEvent = PowerDeliveryEvent.none();
	private boolean breakerTripped = false;
	private int breakerCooldown = 0;
	private final Set<BlockPos> poweredBlocks = new HashSet<>();
	private boolean powerFieldActive = false;

	private static final int FE_CAPACITY = 100000;
	private static final int FE_TRANSFER_PER_TICK = 2000;
	private static final double FE_PER_POWER_TICK = 50.0;
	private int feBuffer = 0;
	private final LazyOptional<IEnergyStorage> energy = LazyOptional.of(this::createEnergyStorage);

	public PowerBoxBlockEntity(BlockPos pos, BlockState state) {
		super(getBlockEntityType(), pos, state);
		ensureArraySizes();
		generateInsulatorIds();
		updateWirePositions();
	}

	private static BlockEntityType<PowerBoxBlockEntity> getBlockEntityType() {
		if (Electricity.POWER_BOX_BLOCK_ENTITY != null) return Electricity.POWER_BOX_BLOCK_ENTITY.get();

		return null;
	}

	private ObjBlockDefinition definition() {
		return ObjDefinitions.get(getBlockState().getBlock());
	}

	private int insulatorCount() {
		ObjBlockDefinition definition = definition();
		if (definition != null && !definition.insulators().isEmpty()) return definition.insulators().size();
		return 0;
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

	private void generateInsulatorIds() {
		ensureArraySizes();
		InsulatorIdRegistry.claimMissing(insulatorIds);
	}

	public static void removeInsulatorIds(int[] ids) {
		InsulatorIdRegistry.releaseIds(ids);
	}

	public Vec3 getWirePosition(int index) {
		if (index < 0 || index >= wirePositions.length) return null;

		return wirePositions[index];
	}

	public void setWirePosition(int index, Vec3 position) {
		if (index >= 0 && index < wirePositions.length) {
			wirePositions[index] = position;
			setChanged();
		}
	}

	public int getInsulatorId(int index) {
		if (index < 0 || index >= insulatorIds.length) return -1;

		return insulatorIds[index];
	}

	public int[] getInsulatorIds() {
		return insulatorIds.clone();
	}

	public double getCurrentPower() {
		return currentPower;
	}

	public int getForgeEnergyStored() {
		return Math.min(getEnergyStoredInternal(), FE_CAPACITY);
	}
	public int getForgeTransferRate() {
		return FE_TRANSFER_PER_TICK;
	}

	public void setCurrentPower(double power) {
		this.currentPower = power;
	}

	public Vec3 calculateOrientedInsulatorCenter(int index) {
		if (index < 0 || index >= wirePositions.length) return null;

		String groupName = InsulatorPartHelper.insulatorName(definition(), index);
		if (groupName == null) return null;
		ObjModel.BoundingBox boundingBox = ObjBoundingBoxRegistry.getBoundingBox(getBlockState().getBlock(), groupName);

		Vec3 localCenter;
		if (boundingBox != null) {
			Vector3f center = boundingBox.center;
			localCenter = new Vec3(center.x, center.y, center.z);
		} else {
			return null;
		}

		Direction facing = getBlockState().getValue(PowerBoxBlock.FACING);
		Vec3 rotatedCenter = ModelFacing.turned(localCenter, PowerBoxBlock.AUTHORED, facing);

		Vec3 worldOffset = Vec3.atLowerCornerOf(getBlockPos()).add(0.5, 0, 0.5);
		return worldOffset.add(rotatedCenter);
	}


	public void tick() {
		updateWirePositions();
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
			InsulatorLookup.register(this);
			WireManagerClient.invalidateInsulatorCache(this.getInsulatorIds());
		});

		if (level == null || level.isClientSide()) return;

		if (breakerCooldown > 0) {
			breakerCooldown--;
			if (breakerCooldown == 0) breakerTripped = false;
		}

		pushForgeEnergy();

		boolean shouldActivate = currentPower > POWER_THRESHOLD;
		if (shouldActivate && !powerFieldActive) {
			powerFieldActive = true;
			refreshPowerField();
		} else if (!shouldActivate && powerFieldActive) {
			deactivatePowerField();
		} else if (shouldActivate) {
			refreshPowerField();
		}

		incomingEvent = PowerDeliveryEvent.none();
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

	private void refreshPowerField() {
		if (level == null) return;
		PowerFieldManager.clearSource(worldPosition);
		Set<BlockPos> newlyPowered = new HashSet<>();
		var consumers = new ArrayList<PowerConsumerTarget>();

		iteratePowerFieldPositions(pos -> {
			BlockPos immutablePos = pos.immutable();
			if (applyPowerToBlock(immutablePos, true)) {
				newlyPowered.add(immutablePos);
			}

			BlockEntity blockEntity = level.getBlockEntity(immutablePos);
			if (blockEntity != null) {
				blockEntity.getCapability(ElectricityCapabilities.ELECTRIC_CONSUMER, null).ifPresent(consumer -> {
					consumers.add(new PowerConsumerTarget(immutablePos, consumer));
				});
			}
		});

		distributePowerToConsumers(consumers);

		poweredBlocks.clear();
		poweredBlocks.addAll(newlyPowered);
	}

	private void deactivatePowerField() {
		if (level == null) return;

		PowerFieldManager.clearSource(worldPosition);
		for (BlockPos pos : poweredBlocks) {
			applyPowerToBlock(pos, false);
		}

		notifyConsumersOfShutdown();
		poweredBlocks.clear();
		powerFieldActive = false;
	}

	private void iteratePowerFieldPositions(Consumer<BlockPos.MutableBlockPos> consumer) {
		BlockPos origin = getBlockPos();
		MutableBlockPos mutable = new MutableBlockPos();
		int radius = Math.max(1, ElectricityServerConfig.powerBoxRadius());
		int radiusSq = radius * radius;

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					int distSq = dx * dx + dy * dy + dz * dz;

					if (distSq > radiusSq) continue;
					if (dx == 0 && dy == 0 && dz == 0) continue;

					mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					consumer.accept(mutable);
				}
			}
		}
	}

	private boolean applyPowerToBlock(BlockPos pos, boolean powered) {
		if (level == null) return false;
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) return false;

		BooleanProperty property = findPowerProperty(state);
		if (property == null || state.getValue(property) == powered) return property != null;

		BlockState newState = state.setValue(property, powered);
		level.setBlock(pos, newState, Block.UPDATE_ALL);
		return true;
	}

	private void distributePowerToConsumers(List<PowerConsumerTarget> consumers) {
		if (consumers.isEmpty() || level == null) return;

		int count = consumers.size();
		double[] demands = new double[count];
		double[] minimums = new double[count];
		double totalDemand = 0.0;

		for (int i = 0; i < count; i++) {
			PowerConsumerTarget target = consumers.get(i);
			double required = Math.max(0.0, target.requiredPower());
			double minimum = Math.max(0.0, target.consumer().getMinimumOperationalPower());
			double demand = Math.max(required, minimum);

			demands[i] = demand;
			minimums[i] = minimum;
			totalDemand += demand;
		}

		double available = Math.max(0.0, currentPower);
		double effectiveAvailable = available;
		for (int i = 0; i < count; i++) {
			PowerConsumerTarget target = consumers.get(i);
			double demand = demands[i];
			double minimum = minimums[i];

			double share = totalDemand <= 0.0 ? effectiveAvailable : effectiveAvailable * (demand / totalDemand);
			double maxDemand = demand * 1.1;
			double regulated = Math.min(share, maxDemand);
			regulated *= 0.92;

			PowerDeliveryEvent event = incomingEvent != null ? incomingEvent : PowerDeliveryEvent.none();
			boolean trip = breakerTripped;
			if (event.surgeSeverity() > 0.85 && event.surgeDuration() > 4) {
				double tripChance = 0.1 + (event.surgeSeverity() - 0.85) * 0.4;
				if (event.surgeDuration() > 8) tripChance += 0.1;
				if (share < minimum * 0.5) {
					tripChance += 0.05;
				}

				if (tripChance > 0.0 && level.getRandom().nextDouble() < tripChance) {
					trip = true;
					breakerTripped = true;
					breakerCooldown = 60;
				}
			}

			if (breakerTripped) {
				trip = true;
			}

			if (event.disconnectActive()) {
				regulated = 0.0;
				trip = true;
			} else if (event.brownoutFactor() < 1.0) {
				regulated = regulated * Mth.clamp(event.brownoutFactor(), 0.0, 1.0);
			}

			PowerDeliveryEvent consumerEvent = event;
			if (trip) {
				regulated = 0.0;
				consumerEvent = new PowerDeliveryEvent(event.surgeSeverity(), Math.max(event.surgeDuration(), 4), true, 0.0);
			} else if (demand > 0.0 && regulated < demand) {
				double brownout = Mth.clamp(regulated / demand, 0.0, 1.0);
				consumerEvent = new PowerDeliveryEvent(event.surgeSeverity(), event.surgeDuration(), false, brownout);
			}

			boolean meets = regulated >= minimum;

			target.consumer().onPowerSupplied(regulated, meets, consumerEvent);
			PowerFieldManager.markPowered(worldPosition, target.position(), regulated);
		}
	}

	private void notifyConsumersOfShutdown() {
		if (level == null) return;
		iteratePowerFieldPositions(pos -> {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (blockEntity != null) {
				blockEntity.getCapability(ElectricityCapabilities.ELECTRIC_CONSUMER, null).ifPresent(consumer -> consumer.onPowerSupplied(0.0, false, PowerDeliveryEvent.none()));
			}
		});
	}

	private record PowerConsumerTarget(BlockPos position, IElectricPowerConsumer consumer, double requiredPower) {
		PowerConsumerTarget(BlockPos position, IElectricPowerConsumer consumer) {
			this(position, consumer, consumer.getRequiredPower());
		}
	}

	public void setIncomingEvent(PowerDeliveryEvent event) {
		this.incomingEvent = event != null ? event : PowerDeliveryEvent.none();
	}

	private BooleanProperty findPowerProperty(BlockState state) {
		for (Property<?> property : state.getProperties()) {
			if (property instanceof BooleanProperty boolProp) {
				for (String target : POWER_PROPERTY_NAMES) {
					if (property.getName().equalsIgnoreCase(target)) return boolProp;
				}
			}
		}

		return null;
	}

	@Override
	public void load(@Nonnull CompoundTag tag) {
		super.load(tag);
		ensureArraySizes();

		if (tag.contains("wirePositions", Tag.TAG_LIST)) {
			ListTag wirePositionsList = tag.getList("wirePositions", Tag.TAG_COMPOUND);
			for (int i = 0; i < Math.min(wirePositionsList.size(), wirePositions.length); i++) {
				CompoundTag posTag = wirePositionsList.getCompound(i);
				if (posTag.contains("x") && posTag.contains("y") && posTag.contains("z")) {
					wirePositions[i] = new Vec3(posTag.getDouble("x"), posTag.getDouble("y"), posTag.getDouble("z"));
				}
			}
		}

		if (tag.contains("insulatorIds", Tag.TAG_LIST)) {
			ListTag insulatorIdsList = tag.getList("insulatorIds", Tag.TAG_INT);
			for (int i = 0; i < Math.min(insulatorIdsList.size(), insulatorIds.length); i++) {
				insulatorIds[i] = insulatorIdsList.getInt(i);
				InsulatorIdRegistry.registerExistingId(insulatorIds[i]);
			}
		} else {
			generateInsulatorIds();
		}

		if (tag.contains("currentPower")) {
			currentPower = tag.getDouble("currentPower");
		}

		if (tag.contains("feBuffer")) {
			feBuffer = tag.getInt("feBuffer");
		}

		updateWirePositions();
	}

	@Override
	protected void saveAdditional(@Nonnull CompoundTag tag) {
		super.saveAdditional(tag);

		ListTag wirePositionsList = new ListTag();
		for (Vec3 pos : wirePositions) {
			if (pos != null) {
				CompoundTag posTag = new CompoundTag();
				posTag.putDouble("x", pos.x);
				posTag.putDouble("y", pos.y);
				posTag.putDouble("z", pos.z);
				wirePositionsList.add(posTag);
			} else {
				wirePositionsList.add(new CompoundTag());
			}
		}

		tag.put("wirePositions", wirePositionsList);

		ListTag insulatorIdsList = new ListTag();
		for (int id : insulatorIds) {
			insulatorIdsList.add(IntTag.valueOf(id));
		}

		tag.put("insulatorIds", insulatorIdsList);
		tag.putDouble("currentPower", currentPower);
		tag.putInt("feBuffer", feBuffer);
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
	public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
		if (cap == ForgeCapabilities.ENERGY) {
			return energy.cast();
		}

		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		energy.invalidate();
	}

	@Override
	public void onLoad() {
		super.onLoad();
		ClientTracking.track(this);
		updateWirePositions();
		if (level != null && level.isClientSide()) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
				InsulatorLookup.register(this);
				WireManagerClient.invalidateInsulatorCache(this.getInsulatorIds());
			});
		}
	}

	private IEnergyStorage createEnergyStorage() {
		return new IEnergyStorage() {
			@Override
			public int receiveEnergy(int maxReceive, boolean simulate) {
				return 0;
			}

			@Override
			public int extractEnergy(int maxExtract, boolean simulate) {
				if (maxExtract <= 0) return 0;
				int available = Math.min(getEnergyStoredInternal(), FE_CAPACITY);
				int extracted = Math.min(maxExtract, available);

				if (!simulate && extracted > 0) {
					if (feBuffer >= extracted) {
						feBuffer -= extracted;
					} else {
						int remaining = extracted - feBuffer;
						feBuffer = 0;
						double powerReduction = remaining / FE_PER_POWER_TICK;
						currentPower = Math.max(0.0, currentPower - powerReduction);
					}

					setChanged();
				}

				return extracted;
			}

			@Override
			public int getEnergyStored() {
				return Math.min(getEnergyStoredInternal(), FE_CAPACITY);
			}

			@Override
			public int getMaxEnergyStored() {
				return FE_CAPACITY;
			}

			@Override
			public boolean canExtract() {
				return true;
			}

			@Override
			public boolean canReceive() {
				return false;
			}
		};
	}

	private int getEnergyStoredInternal() {
		double powerEnergy = Math.max(0.0, currentPower) * FE_PER_POWER_TICK;
		int stored = feBuffer + (int) Math.floor(powerEnergy);
		return stored;
	}

	private void pushForgeEnergy() {
		if (level == null) return;
		IEnergyStorage storage = energy.orElse(null);
		if (storage == null) return;

		for (Direction direction : Direction.values()) {
			if (storage.getEnergyStored() <= 0) break;

			BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(direction));
			if (neighbor == null) continue;

			neighbor.getCapability(ForgeCapabilities.ENERGY, direction.getOpposite()).ifPresent(target -> {
				int available = Math.min(storage.getEnergyStored(), FE_TRANSFER_PER_TICK);
				if (available <= 0) return;

				int accepted = target.receiveEnergy(available, false);
				if (accepted > 0) {
					storage.extractEnergy(accepted, false);
					setChanged();
				}
			});
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		ClientTracking.untrack(this);
		InsulatorIdRegistry.releaseIds(this.getInsulatorIds());
		if (powerFieldActive && level != null && !level.isClientSide()) {
			deactivatePowerField();
		}

		if (level != null && level.isClientSide()) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
				InsulatorLookup.unregister(this.getInsulatorIds());
				WireManagerClient.invalidateInsulatorCache(this.getInsulatorIds());
			});
		}
	}

	@Override
	public String fittingType() {
		return InsulatorPartHelper.TYPE_POWER_BOX;
	}

	/** Low voltage only: a kiosk is the end of the line, and what arrives at one is a street bundle. */
	@Override
	public boolean takesConductor(ConductorSpec conductor, int index) {
		return conductor.voltageClass() == ConductorSpec.VoltageClass.LOW;
	}

	/** The end of the line: a kiosk distributes, it does not feed another machine. */
	@Override
	public boolean feeds(InsulatorHost other) {
		return false;
	}
}
