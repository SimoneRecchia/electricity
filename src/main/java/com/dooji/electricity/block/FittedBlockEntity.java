package com.dooji.electricity.block;

import com.dooji.electricity.client.render.obj.ObjBoundingBoxRegistry;
import com.dooji.electricity.client.wire.InsulatorLookup;
import com.dooji.electricity.client.wire.WireManagerClient;
import com.dooji.electricity.main.registry.ObjBlockDefinition;
import com.dooji.electricity.main.registry.ObjDefinitions;
import com.dooji.electricity.wire.InsulatorHost;
import com.dooji.electricity.wire.InsulatorIdRegistry;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
 * The fittings a machine carries, and everything that is the same for every machine that carries them.
 *
 * Three block entities repeated this byte for byte - the two parallel arrays, the persistent ids, the update
 * packet, the client-side registration on load and the release on removal. What is left to a subclass is what
 * actually differs about it: where on its own model a wire lands, and what it does with the power that
 * arrives.
 *
 * The ids are a save-format contract: a wire is stored against a fitting's <em>index</em> in
 * {@link #fittingGroups()}, so that order cannot change without moving every wire in every existing world.
 */
public abstract class FittedBlockEntity extends BlockEntity implements InsulatorHost {
	private Vec3[] wirePositions = new Vec3[0];
	private int[] insulatorIds = new int[0];

	protected FittedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		refresh();
		InsulatorIdRegistry.claimMissing(insulatorIds);
	}

	/** The groups this machine's fittings are drawn as, in the order a wire's index means. */
	protected List<String> fittingGroups() {
		ObjBlockDefinition definition = ObjDefinitions.get(getBlockState().getBlock());
		return definition == null ? List.of() : definition.insulators();
	}

	/**
	 * Where a wire lands on one fitting, in the model's own frame, or null while the model is not loaded.
	 *
	 * The top of the fitting's box by default, which is where a conductor lands on an upright one: a pin
	 * insulator holds it in the groove on its head and a bushing on the palm at its top. A lattice tower
	 * overrides it, because its strings hang.
	 */
	@Nullable
	protected Vec3 landing(String group) {
		return topOf(group);
	}

	/** The top of a fitting's box, over the middle of it. */
	@Nullable
	protected final Vec3 topOf(String group) {
		Vector3f top = ObjBoundingBoxRegistry.getTopSafe(getBlockState().getBlock(), group);
		return top == null ? null : new Vec3(top.x, top.y, top.z);
	}

	/**
	 * How far the model has been turned from the way it was authored.
	 *
	 * Off {@link MachineShell}, which every machine that carries a fitting also is: it already has to say
	 * which way its geometry was drawn and which way it has been placed, and this is the same question.
	 */
	private float turnedDegrees() {
		return getBlockState().getBlock() instanceof MachineShell machine
				? ModelFacing.degrees(machine.shellAuthored(), machine.shellFacing(getBlockState()))
				: 0.0f;
	}

	/** Puts every fitting where the model says it is, turned onto this block's own facing. */
	protected final void refresh() {
		List<String> groups = fittingGroups();
		grow(groups.size());

		double radians = Math.toRadians(turnedDegrees());
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		for (int index = 0; index < groups.size(); index++) {
			Vec3 at = landing(groups.get(index));
			if (at == null) continue;

			wirePositions[index] = new Vec3(at.x * cos + at.z * sin, at.y, -at.x * sin + at.z * cos)
					.add(Vec3.atLowerCornerOf(getBlockPos())).add(0.5, 0.0, 0.5);
		}
	}

	/** Both arrays to the number of fittings the model draws, keeping what is already in them. */
	private void grow(int count) {
		if (wirePositions.length != count) {
			Vec3[] positions = new Vec3[count];
			System.arraycopy(wirePositions, 0, positions, 0, Math.min(wirePositions.length, count));
			wirePositions = positions;
		}

		if (insulatorIds.length != count) {
			int[] ids = new int[count];
			System.arraycopy(insulatorIds, 0, ids, 0, Math.min(insulatorIds.length, count));
			insulatorIds = ids;
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
	protected void saveAdditional(@Nonnull CompoundTag tag) {
		super.saveAdditional(tag);
		writeIds(tag);
	}

	@Override
	public void load(@Nonnull CompoundTag tag) {
		super.load(tag);
		readIds(tag);
		refresh();
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = super.getUpdateTag();
		writeIds(tag);
		return tag;
	}

	@Override
	public void handleUpdateTag(CompoundTag tag) {
		super.handleUpdateTag(tag);
		readIds(tag);
		refresh();
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
			InsulatorLookup.register(this);
			WireManagerClient.invalidateInsulatorCache(getInsulatorIds());
		});
	}

	private void writeIds(CompoundTag tag) {
		ListTag ids = new ListTag();
		for (int id : insulatorIds) ids.add(IntTag.valueOf(id));
		tag.put("insulatorIds", ids);
	}

	private void readIds(CompoundTag tag) {
		grow(fittingGroups().size());
		if (!tag.contains("insulatorIds", Tag.TAG_LIST)) {
			InsulatorIdRegistry.claimMissing(insulatorIds);
			return;
		}

		ListTag ids = tag.getList("insulatorIds", Tag.TAG_INT);
		for (int index = 0; index < Math.min(ids.size(), insulatorIds.length); index++) {
			insulatorIds[index] = ids.getInt(index);
			InsulatorIdRegistry.registerExistingId(insulatorIds[index]);
		}
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
}
