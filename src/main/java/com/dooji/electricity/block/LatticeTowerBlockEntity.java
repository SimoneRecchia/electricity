package com.dooji.electricity.block;

import com.dooji.electricity.client.render.obj.ObjBoundingBoxRegistry;
import com.dooji.electricity.client.wire.InsulatorLookup;
import com.dooji.electricity.client.wire.WireManagerClient;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.registry.ObjBlockDefinition;
import com.dooji.electricity.main.registry.ObjDefinitions;
import com.dooji.electricity.wire.InsulatorHost;
import com.dooji.electricity.wire.InsulatorIdRegistry;
import com.dooji.electricity.wire.InsulatorPartHelper;
import java.util.List;
import javax.annotation.Nonnull;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.joml.Vector3f;

/**
 * A lattice transmission tower: six phase fittings and the earth peak above them.
 *
 * The six are {@code insulator_1..6} in the order ObjDefinitions names them, which is the order
 * gen_tower_models writes PHASES in - lower arm outward-in, then the upper arm. A wire is stored against
 * the index, so that order cannot change without moving every wire in every world.
 */
public class LatticeTowerBlockEntity extends BlockEntity implements InsulatorHost {
	private Vec3[] wirePositions;
	private int[] insulatorIds;
	private double currentPower = 0.0;

	public LatticeTowerBlockEntity(BlockPos pos, BlockState state) {
		super(Electricity.LATTICE_TOWER_BLOCK_ENTITY.get(), pos, state);
		ensureArraySizes();
		refresh();
		InsulatorIdRegistry.claimMissing(insulatorIds);
	}

	private List<String> insulatorGroups() {
		ObjBlockDefinition definition = ObjDefinitions.get(getBlockState().getBlock());
		return definition == null ? List.of() : definition.insulators();
	}

	private void ensureArraySizes() {
		int count = insulatorGroups().size();
		if (wirePositions == null || wirePositions.length != count) {
			Vec3[] grown = new Vec3[count];
			if (wirePositions != null) System.arraycopy(wirePositions, 0, grown, 0, Math.min(wirePositions.length, count));
			wirePositions = grown;
		}

		if (insulatorIds == null || insulatorIds.length != count) {
			int[] grown = new int[count];
			if (insulatorIds != null) System.arraycopy(insulatorIds, 0, grown, 0, Math.min(insulatorIds.length, count));
			insulatorIds = grown;
		}
	}

	/** Where each fitting is, from the model's own bounding boxes, turned onto the tower's facing. */
	private void refresh() {
		ensureArraySizes();
		List<String> groups = insulatorGroups();
		for (int i = 0; i < groups.size() && i < wirePositions.length; i++) {
			Vector3f centre = ObjBoundingBoxRegistry.getCenterSafe(getBlockState().getBlock(), groups.get(i));
			if (centre == null) continue;

			float degrees = ModelFacing.degrees(LatticeTowerBlock.AUTHORED, getBlockState().getValue(LatticeTowerBlock.FACING));
			double radians = Math.toRadians(degrees);
			double cos = Math.cos(radians);
			double sin = Math.sin(radians);
			double x = centre.x * cos + centre.z * sin;
			double z = -centre.x * sin + centre.z * cos;
			wirePositions[i] = new Vec3(x, centre.y, z).add(Vec3.atLowerCornerOf(getBlockPos())).add(0.5, 0.0, 0.5);
		}
	}

	@Override
	public int[] getInsulatorIds() {
		return insulatorIds.clone();
	}

	@Override
	public Vec3 getWirePosition(int index) {
		if (index >= 0 && index < wirePositions.length && wirePositions[index] != null) return wirePositions[index];

		return Vec3.atCenterOf(getBlockPos());
	}

	@Override
	public String fittingType() {
		return InsulatorPartHelper.TYPE_LATTICE_TOWER;
	}

	/** A tower carries a line on to the next tower and delivers it to a substation or a pole. */
	@Override
	public boolean feeds(InsulatorHost other) {
		return other instanceof LatticeTowerBlockEntity || other instanceof ElectricCabinBlockEntity
				|| other instanceof UtilityPoleBlockEntity;
	}

	@Override
	public void deliverPower(double power) {
		currentPower = power;
	}

	public double getCurrentPower() {
		return currentPower;
	}

	@Override
	protected void saveAdditional(@Nonnull CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putDouble("currentPower", currentPower);
		ListTag ids = new ListTag();
		for (int id : insulatorIds) ids.add(IntTag.valueOf(id));
		tag.put("insulatorIds", ids);
	}

	@Override
	public void load(@Nonnull CompoundTag tag) {
		super.load(tag);
		ensureArraySizes();
		currentPower = tag.getDouble("currentPower");
		readIds(tag);
		refresh();
	}

	private void readIds(CompoundTag tag) {
		if (!tag.contains("insulatorIds", Tag.TAG_LIST)) {
			InsulatorIdRegistry.claimMissing(insulatorIds);
			return;
		}

		ListTag ids = tag.getList("insulatorIds", Tag.TAG_INT);
		for (int i = 0; i < Math.min(ids.size(), insulatorIds.length); i++) {
			insulatorIds[i] = ids.getInt(i);
			InsulatorIdRegistry.registerExistingId(insulatorIds[i]);
		}
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = super.getUpdateTag();
		tag.putDouble("currentPower", currentPower);
		ListTag ids = new ListTag();
		for (int id : insulatorIds) ids.add(IntTag.valueOf(id));
		tag.put("insulatorIds", ids);
		return tag;
	}

	@Override
	public void handleUpdateTag(CompoundTag tag) {
		super.handleUpdateTag(tag);
		ensureArraySizes();
		currentPower = tag.getDouble("currentPower");
		readIds(tag);
		refresh();
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
			InsulatorLookup.register(this);
			WireManagerClient.invalidateInsulatorCache(getInsulatorIds());
		});
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
		handleUpdateTag(packet.getTag());
	}

	@Override
	public void onLoad() {
		super.onLoad();
		ClientTracking.track(this);
		if (level != null && level.isClientSide()) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
				InsulatorLookup.register(this);
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

	public static BlockEntityType<LatticeTowerBlockEntity> type() {
		return Electricity.LATTICE_TOWER_BLOCK_ENTITY.get();
	}
}
