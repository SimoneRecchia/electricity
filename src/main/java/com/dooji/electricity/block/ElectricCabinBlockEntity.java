package com.dooji.electricity.block;

import com.dooji.electricity.client.render.obj.ObjBoundingBoxRegistry;
import com.dooji.electricity.client.render.obj.ObjModel;
import com.dooji.electricity.client.wire.InsulatorLookup;
import com.dooji.electricity.client.wire.WireManagerClient;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.registry.ObjBlockDefinition;
import com.dooji.electricity.main.registry.ObjDefinitions;
import com.dooji.electricity.wire.InsulatorIdRegistry;
import com.dooji.electricity.wire.InsulatorPartHelper;
import javax.annotation.Nonnull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.dooji.electricity.wire.InsulatorHost;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.joml.Vector3f;

public class ElectricCabinBlockEntity extends BlockEntity implements InsulatorHost {
	private Vec3[] wirePositions;
	private int[] insulatorIds;

	private double currentPower = 0.0;

	public ElectricCabinBlockEntity(BlockPos pos, BlockState state) {
		super(getBlockEntityType(), pos, state);
		ensureArraySizes();
		generateInsulatorIds();
		updateWirePositions();
	}

	private static BlockEntityType<ElectricCabinBlockEntity> getBlockEntityType() {
		if (Electricity.ELECTRIC_CABIN_BLOCK_ENTITY != null) return Electricity.ELECTRIC_CABIN_BLOCK_ENTITY.get();

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

		Direction facing = getBlockState().getValue(ElectricCabinBlock.FACING);
		Vec3 rotatedCenter = ModelFacing.turned(localCenter, ElectricCabinBlock.AUTHORED, facing);

		Vec3 worldOffset = Vec3.atLowerCornerOf(getBlockPos()).add(0.5, 0, 0.5);
		return worldOffset.add(rotatedCenter);
	}


	public void tick() {
		updateWirePositions();
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
			InsulatorLookup.register(this);
			WireManagerClient.invalidateInsulatorCache(this.getInsulatorIds());
		});
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
		updateWirePositions();
		if (level != null && level.isClientSide()) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
				InsulatorLookup.register(this);
				WireManagerClient.invalidateInsulatorCache(this.getInsulatorIds());
			});
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		ClientTracking.untrack(this);
		InsulatorIdRegistry.releaseIds(this.getInsulatorIds());
		if (level != null && level.isClientSide()) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
				InsulatorLookup.unregister(this.getInsulatorIds());
				WireManagerClient.invalidateInsulatorCache(this.getInsulatorIds());
			});
		}
	}

	@Override
	public String fittingType() {
		return InsulatorPartHelper.TYPE_ELECTRIC_CABIN;
	}

	/**
	 * A collection substation: it takes machines in and puts a line out.
	 *
	 * Out to a distribution pole, or to a lattice tower where the line is a transmission one.
	 */
	@Override
	public boolean feeds(InsulatorHost other) {
		return other instanceof UtilityPoleBlockEntity || other instanceof LatticeTowerBlockEntity
				|| other instanceof TransformerBlockEntity || other instanceof GroundConductorBlockEntity;
	}

	/** Which of the two fittings this is, from the group's own name. */
	@Override
	public String powerType(String partName) {
		if (partName == null) return "bidirectional";
		if (partName.toLowerCase(java.util.Locale.ROOT).contains("output")) return "output";
		if (partName.toLowerCase(java.util.Locale.ROOT).contains("input")) return "input";

		return "bidirectional";
	}

	@Override
	public void deliverPower(double power) {
		setCurrentPower(power);
	}
}
