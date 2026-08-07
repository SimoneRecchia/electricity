package com.dooji.electricity.block;

import com.dooji.electricity.client.render.obj.ObjBoundingBoxRegistry;
import com.dooji.electricity.client.wire.InsulatorLookup;
import com.dooji.electricity.client.wire.WireManagerClient;
import com.dooji.electricity.api.power.ConductorSpec;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.registry.ObjBlockDefinition;
import com.dooji.electricity.main.registry.ObjDefinitions;
import com.dooji.electricity.wire.InsulatorHost;
import com.dooji.electricity.wire.InsulatorPartHelper;
import com.dooji.electricity.wire.InsulatorIdRegistry;
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

public class UtilityPoleBlockEntity extends BlockEntity implements InsulatorHost {
	private Vec3[] wirePositions;
	private int[] insulatorIds;
	private float offsetX = 0.0f;
	private float offsetY = 0.0f;
	private float offsetZ = 0.0f;
	private float yaw = 0.0f;
	private float pitch = 0.0f;

	private double currentPower = 0.0;

	public UtilityPoleBlockEntity(BlockPos pos, BlockState state) {
		super(getBlockEntityType(), pos, state);
		ensureArraySizes();
		initializeWirePositions();
		generateInsulatorIds();
	}

	private static BlockEntityType<UtilityPoleBlockEntity> getBlockEntityType() {
		if (Electricity.UTILITY_POLE_BLOCK_ENTITY != null) return Electricity.UTILITY_POLE_BLOCK_ENTITY.get();

		return null;
	}

	private ObjBlockDefinition definition() {
		return ObjDefinitions.get(getBlockState().getBlock());
	}

	private List<String> insulatorGroups() {
		ObjBlockDefinition definition = definition();
		if (definition != null && !definition.insulators().isEmpty()) return definition.insulators();
		return List.of();
	}

	private int insulatorCount() {
		return insulatorGroups().size();
	}

	private void ensureArraySizes() {
		int count = insulatorCount();
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
		List<String> groups = insulatorGroups();
		for (int i = 0; i < groups.size() && i < wirePositions.length; i++) {
			Vec3 orientedCenter = calculateOrientedInsulatorCenter(groups.get(i));
			if (orientedCenter != null) {
				wirePositions[i] = orientedCenter;
			} else {
				break;
			}
		}
	}

	private Vec3 calculateOrientedInsulatorCenter(String insulatorGroup) {
		// the top of the porcelain, which is where a pin insulator's groove is and where the tie holds the
		// conductor - the box's centre put every span halfway down the shed stack
		Vector3f top = ObjBoundingBoxRegistry.getTopSafe(getBlockState().getBlock(), insulatorGroup);
		if (top == null) return null;

		// the same quarter turn the renderer poses the model by and the cells are turned by
		Vec3 turned = ModelFacing.turned(new Vec3(top), UtilityPoleBlock.AUTHORED,
				getBlockState().getValue(UtilityPoleBlock.FACING));
		return leaned(turned.add(-offsetX, offsetY, -offsetZ))
				.add(Vec3.atLowerCornerOf(getBlockPos())).add(0.5, 0, 0.5);
	}

	/** The lean the player has dialled into this pole: pitch about x, then yaw about y, both in radians. */
	private Vec3 leaned(Vec3 point) {
		return point.xRot(pitch).yRot(yaw);
	}

	@Override
	public String fittingType() {
		return InsulatorPartHelper.TYPE_UTILITY_POLE;
	}

	/**
	 * A street bundle or a medium-voltage line, which is what a pole is built to carry.
	 *
	 * Not transmission: a 400 kV quad bundle is 1.7 m between sub-conductors and needs 3.5 m of clearance to
	 * the ground. That goes on a tower, and this is the rule that says so.
	 */
	@Override
	public boolean takesConductor(ConductorSpec conductor, int index) {
		return conductor.voltageClass() != ConductorSpec.VoltageClass.HIGH;
	}

	/** A pole carries a line on to the next pole, to a kiosk, or into a run laid on the ground. */
	@Override
	public boolean feeds(InsulatorHost other) {
		return other instanceof UtilityPoleBlockEntity || other instanceof PowerBoxBlockEntity
				|| other instanceof GroundConductorBlockEntity;
	}

	@Override
	public void deliverPower(double power) {
		setCurrentPower(power);
	}

	@Override
	public Vec3 getWirePosition(int index) {
		if (index >= 0 && index < wirePositions.length) return wirePositions[index];
		return Vec3.atCenterOf(getBlockPos());
	}

	public float getOffsetX() {
		return offsetX;
	}

	public void setOffsetX(float offsetX) {
		this.offsetX = offsetX;
		geometryMoved();
	}

	public float getOffsetY() {
		return offsetY;
	}

	public void setOffsetY(float offsetY) {
		this.offsetY = offsetY;
		geometryMoved();
	}

	public float getOffsetZ() {
		return offsetZ;
	}

	public void setOffsetZ(float offsetZ) {
		this.offsetZ = offsetZ;
		geometryMoved();
	}

	public float getYaw() {
		return yaw;
	}

	public void setYaw(float yaw) {
		this.yaw = yaw;
		geometryMoved();
	}

	public float getPitch() {
		return pitch;
	}

	public double getCurrentPower() {
		return currentPower;
	}

	public void setCurrentPower(double power) {
		this.currentPower = power;
	}

	public void setPitch(float pitch) {
		this.pitch = pitch;
		geometryMoved();
	}

	/** Every dialled-in figure moves the fittings, so the wires are re-measured and the client told. */
	private void geometryMoved() {
		initializeWirePositions();
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
		}
	}
	@Override
	protected void saveAdditional(@Nonnull CompoundTag tag) {
		super.saveAdditional(tag);

		tag.putFloat("offsetX", offsetX);
		tag.putFloat("offsetY", offsetY);
		tag.putFloat("offsetZ", offsetZ);
		tag.putFloat("yaw", yaw);
		tag.putFloat("pitch", pitch);
		tag.putDouble("currentPower", currentPower);

		ListTag insulatorIdsList = new ListTag();
		for (int insulatorId : insulatorIds) {
			insulatorIdsList.add(IntTag.valueOf(insulatorId));
		}

		tag.put("insulatorIds", insulatorIdsList);
	}

	@Override
	public void load(@Nonnull CompoundTag tag) {
		super.load(tag);
		ensureArraySizes();

		offsetX = tag.getFloat("offsetX");
		offsetY = tag.getFloat("offsetY");
		offsetZ = tag.getFloat("offsetZ");
		yaw = tag.getFloat("yaw");
		pitch = tag.getFloat("pitch");
		currentPower = tag.getDouble("currentPower");

		if (tag.contains("insulatorIds", Tag.TAG_LIST)) {
			ListTag insulatorIdsList = tag.getList("insulatorIds", Tag.TAG_INT);
			for (int i = 0; i < Math.min(insulatorIdsList.size(), insulatorIds.length); i++) {
				insulatorIds[i] = insulatorIdsList.getInt(i);
				InsulatorIdRegistry.registerExistingId(insulatorIds[i]);
			}
		} else {
			generateInsulatorIds();
		}

		initializeWirePositions();
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = super.getUpdateTag();
		tag.putFloat("offsetX", offsetX);
		tag.putFloat("offsetY", offsetY);
		tag.putFloat("offsetZ", offsetZ);
		tag.putFloat("yaw", yaw);
		tag.putFloat("pitch", pitch);
		tag.putDouble("currentPower", currentPower);

		ListTag insulatorIdsList = new ListTag();
		for (int insulatorId : insulatorIds) {
			insulatorIdsList.add(IntTag.valueOf(insulatorId));
		}

		tag.put("insulatorIds", insulatorIdsList);
		return tag;
	}

	@Override
	public void handleUpdateTag(CompoundTag tag) {
		super.handleUpdateTag(tag);
		ensureArraySizes();
		offsetX = tag.getFloat("offsetX");
		offsetY = tag.getFloat("offsetY");
		offsetZ = tag.getFloat("offsetZ");
		yaw = tag.getFloat("yaw");
		pitch = tag.getFloat("pitch");
		currentPower = tag.getDouble("currentPower");

		if (tag.contains("insulatorIds", Tag.TAG_LIST)) {
			ListTag insulatorIdsList = tag.getList("insulatorIds", Tag.TAG_INT);
			for (int i = 0; i < Math.min(insulatorIdsList.size(), insulatorIds.length); i++) {
				insulatorIds[i] = insulatorIdsList.getInt(i);
				InsulatorIdRegistry.registerExistingId(insulatorIds[i]);
			}
		}

		initializeWirePositions();
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
			InsulatorLookup.register(this);
			WireManagerClient.invalidateInsulatorCache(this.getInsulatorIds());
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

	private void generateInsulatorIds() {
		ensureArraySizes();
		InsulatorIdRegistry.claimMissing(insulatorIds);
	}

	public int getInsulatorId(int index) {
		if (index >= 0 && index < insulatorIds.length) return insulatorIds[index];

		return -1;
	}

	public int[] getInsulatorIds() {
		return insulatorIds.clone();
	}

	public static void removeInsulatorIds(int[] ids) {
		InsulatorIdRegistry.releaseIds(ids);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		ClientTracking.track(this);
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
}
