package com.dooji.electricity.block;

import com.dooji.electricity.api.power.TransformerSpec;
import com.dooji.electricity.client.render.obj.ObjBoundingBoxRegistry;
import com.dooji.electricity.client.wire.InsulatorLookup;
import com.dooji.electricity.client.wire.WireManagerClient;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.registry.ObjBlockDefinition;
import com.dooji.electricity.main.registry.ObjDefinitions;
import com.dooji.electricity.main.registry.TransformerCatalog;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.joml.Vector3f;

/**
 * A transformer, of either duty.
 *
 * The bushings are its fittings, low-voltage side first, in the order gen_transformer_models writes them.
 * What a transformer *does* to power is take a loss off it: {@link #throughput()} is what came in less
 * what the core and the load cost, which is the only reason a player would rather have one big machine
 * unit than three small ones.
 */
public class TransformerBlockEntity extends BlockEntity implements InsulatorHost {
	private Vec3[] wirePositions;
	private int[] insulatorIds;
	private double incomingKw = 0.0;

	public TransformerBlockEntity(BlockPos pos, BlockState state) {
		super(Electricity.TRANSFORMER_BLOCK_ENTITY.get(), pos, state);
		ensureArraySizes();
		refresh();
		InsulatorIdRegistry.claimMissing(insulatorIds);
	}

	public TransformerSpec spec() {
		if (getBlockState().getBlock() instanceof TransformerBlock block) return block.spec();

		return TransformerCatalog.MACHINE;
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

	private void refresh() {
		ensureArraySizes();
		List<String> groups = insulatorGroups();
		for (int i = 0; i < groups.size() && i < wirePositions.length; i++) {
			Vector3f centre = ObjBoundingBoxRegistry.getCenterSafe(getBlockState().getBlock(), groups.get(i));
			if (centre == null) continue;

			double radians = Math.toRadians(ModelFacing.degrees(TransformerBlock.AUTHORED,
					getBlockState().getValue(TransformerBlock.FACING)));
			double cos = Math.cos(radians);
			double sin = Math.sin(radians);
			wirePositions[i] = new Vec3(centre.x * cos + centre.z * sin, centre.y, -centre.x * sin + centre.z * cos)
					.add(Vec3.atLowerCornerOf(getBlockPos())).add(0.5, 0.0, 0.5);
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
		return InsulatorPartHelper.TYPE_TRANSFORMER;
	}

	/**
	 * Where a transformer sits in the chain, which is the whole reason it exists.
	 *
	 * A machine unit takes generators in and puts a collector network out, so it feeds a substation - or
	 * another machine unit, which is how a row of machines shares one run back. A substation unit puts a
	 * transmission line out, so it feeds towers, and nothing else.
	 */
	@Override
	public boolean feeds(InsulatorHost other) {
		return switch (spec().duty()) {
			// a machine unit puts a collector network out: into the substation, into another machine unit,
			// or into a run laid along the ground between them
			case MACHINE -> other instanceof ElectricCabinBlockEntity
					|| other instanceof TransformerBlockEntity
					|| other instanceof GroundConductorBlockEntity;
			// and a substation unit puts a transmission line out, which starts at a tower or at a run
			case SUBSTATION -> other instanceof LatticeTowerBlockEntity
					|| other instanceof GroundConductorBlockEntity;
		};
	}

	@Override
	public void deliverPower(double power) {
		incomingKw = power;
	}

	/** What came in. */
	public double getCurrentPower() {
		return incomingKw;
	}

	/** What leaves: what came in, less the core loss and the load's share of it. */
	public double throughput() {
		double loss = spec().lossKw(incomingKw);
		return Math.max(0.0, Math.abs(incomingKw) - loss) * Math.signum(incomingKw == 0.0 ? 1.0 : incomingKw);
	}

	@Override
	protected void saveAdditional(@Nonnull CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putDouble("incomingKw", incomingKw);
		ListTag ids = new ListTag();
		for (int id : insulatorIds) ids.add(IntTag.valueOf(id));
		tag.put("insulatorIds", ids);
	}

	@Override
	public void load(@Nonnull CompoundTag tag) {
		super.load(tag);
		ensureArraySizes();
		incomingKw = tag.getDouble("incomingKw");
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
		tag.putDouble("incomingKw", incomingKw);
		ListTag ids = new ListTag();
		for (int id : insulatorIds) ids.add(IntTag.valueOf(id));
		tag.put("insulatorIds", ids);
		return tag;
	}

	@Override
	public void handleUpdateTag(CompoundTag tag) {
		super.handleUpdateTag(tag);
		ensureArraySizes();
		incomingKw = tag.getDouble("incomingKw");
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
}
