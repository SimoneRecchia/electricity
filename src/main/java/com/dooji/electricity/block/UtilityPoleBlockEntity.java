package com.dooji.electricity.block;

import com.dooji.electricity.client.render.obj.ObjBoundingBoxRegistry;
import com.dooji.electricity.client.wire.InsulatorLookup;
import com.dooji.electricity.client.wire.WireManagerClient;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.registry.ObjBlockDefinition;
import com.dooji.electricity.main.registry.ObjDefinitions;
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

public class UtilityPoleBlockEntity extends BlockEntity {
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
		try {
			Vector3f localCenter = ObjBoundingBoxRegistry.getCenterSafe(getBlockState().getBlock(), insulatorGroup);
			if (localCenter == null) return null;

			Vector3f facingRotated = applyFacingRotation(localCenter);
			facingRotated.add(-offsetX, offsetY, -offsetZ);

			Vector3f rotatedCenter = applyYawPitchRotation(facingRotated, yaw, pitch);

			Vec3 worldPos = new Vec3(rotatedCenter.x, rotatedCenter.y, rotatedCenter.z);
			worldPos = worldPos.add(Vec3.atLowerCornerOf(getBlockPos())).add(0.5, 0, 0.5);

			return worldPos;
		} catch (Exception e) {
			return null;
		}
	}

	private Vector3f applyFacingRotation(Vector3f point) {
		Vector3f result = new Vector3f(point);
		var facing = getBlockState().getValue(UtilityPoleBlock.FACING);

		// the same quarter turn the renderer poses the model by and the cells are turned by, from the one
		// place that arithmetic lives - this was a copy of a table the pole kept for its mirrored model,
		// and a copy is how the anchors came to disagree with the geometry in the first place
		float facingRotation = ModelFacing.degrees(UtilityPoleBlock.AUTHORED, facing);

		if (facingRotation != 0) {
			float cosYaw = (float) Math.cos(Math.toRadians(facingRotation));
			float sinYaw = (float) Math.sin(Math.toRadians(facingRotation));
			float x = result.x * cosYaw + result.z * sinYaw;
			float z = -result.x * sinYaw + result.z * cosYaw;
			result.x = x;
			result.z = z;
		}

		return result;
	}

	private Vector3f applyYawPitchRotation(Vector3f point, float yawRadians, float pitchRadians) {
		Vector3f result = new Vector3f(point);

		if (pitchRadians != 0) {
			float cosPitch = (float) Math.cos(-pitchRadians);
			float sinPitch = (float) Math.sin(-pitchRadians);
			float y = result.y * cosPitch - result.z * sinPitch;
			float z = result.y * sinPitch + result.z * cosPitch;
			result.y = y;
			result.z = z;
		}

		if (yawRadians != 0) {
			float cosYaw = (float) Math.cos(yawRadians);
			float sinYaw = (float) Math.sin(yawRadians);
			float x = result.x * cosYaw + result.z * sinYaw;
			float z = -result.x * sinYaw + result.z * cosYaw;
			result.x = x;
			result.z = z;
		}

		return result;
	}

	public Vec3 getWirePosition(int index) {
		if (index >= 0 && index < wirePositions.length) return wirePositions[index];
		return Vec3.atCenterOf(getBlockPos());
	}

	public float getOffsetX() {
		return offsetX;
	}

	public void setOffsetX(float offsetX) {
		this.offsetX = offsetX;
		updateWirePositions();
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
		}
	}

	public float getOffsetY() {
		return offsetY;
	}

	public void setOffsetY(float offsetY) {
		this.offsetY = offsetY;
		updateWirePositions();
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
		}
	}

	public float getOffsetZ() {
		return offsetZ;
	}

	public void setOffsetZ(float offsetZ) {
		this.offsetZ = offsetZ;
		updateWirePositions();
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
		}
	}

	public float getYaw() {
		return yaw;
	}

	public void setYaw(float yaw) {
		this.yaw = yaw;
		updateWirePositions();
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
		}
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
		updateWirePositions();
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
		}
	}

	private void updateWirePositions() {
		initializeWirePositions();
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

		updateWirePositions();
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

		updateWirePositions();
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
