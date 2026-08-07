package com.dooji.electricity.block;

import com.dooji.electricity.api.power.ConductorSpec;
import com.dooji.electricity.client.wire.InsulatorLookup;
import com.dooji.electricity.client.wire.WireManagerClient;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.wire.InsulatorHost;
import com.dooji.electricity.wire.InsulatorIdRegistry;
import com.dooji.electricity.wire.InsulatorPartHelper;
import javax.annotation.Nonnull;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * The one fitting a length of ground-laid conductor offers a span.
 *
 * A run is laid up to a structure and the span comes off it, which is exactly what the dead-end clamp on
 * the {@code end} piece is for - so this block entity exists to give that clamp an insulator id, and a
 * span may be strung from a run to a tower or from a run to a run.
 *
 * One fitting rather than one per sub-conductor: a bundle is dead-ended in one place and the four clamps
 * are the same termination, which is also how {@link com.dooji.electricity.client.wire.WireRenderer} draws
 * a bundle - one connection, four sub-conductors.
 */
public class GroundConductorBlockEntity extends BlockEntity implements InsulatorHost {
	/** Where the dead-end clamp's palm is above the block's floor, from gen_conductor_models' CLAMP. */
	private static final double CLAMP_Y = 1.55 / 16.0;

	private final int[] insulatorIds = new int[1];
	private double currentPower = 0.0;

	public GroundConductorBlockEntity(BlockPos pos, BlockState state) {
		super(Electricity.GROUND_CONDUCTOR_BLOCK_ENTITY.get(), pos, state);
		InsulatorIdRegistry.claimMissing(insulatorIds);
	}

	public ConductorSpec spec() {
		if (getBlockState().getBlock() instanceof GroundConductorBlock block) return block.spec();

		return null;
	}

	@Override
	public int[] getInsulatorIds() {
		return insulatorIds.clone();
	}

	@Override
	public Vec3 getWirePosition(int index) {
		return Vec3.atLowerCornerOf(getBlockPos()).add(0.5, CLAMP_Y, 0.5);
	}

	@Override
	public String fittingType() {
		return InsulatorPartHelper.TYPE_GROUND_CONDUCTOR;
	}

	/**
	 * A run carries power on to whatever it is laid up to, and takes it from a span.
	 *
	 * Only to the same conductor where the other end is a run, for the reason the blocks do not join
	 * either: a street bundle does not splice onto a transmission phase.
	 */
	@Override
	public boolean feeds(InsulatorHost other) {
		if (other instanceof GroundConductorBlockEntity run) {
			ConductorSpec mine = spec();
			ConductorSpec theirs = run.spec();
			return mine != null && theirs != null && mine.id().equals(theirs.id());
		}

		return other instanceof LatticeTowerBlockEntity || other instanceof UtilityPoleBlockEntity
				|| other instanceof ElectricCabinBlockEntity || other instanceof PowerBoxBlockEntity
				|| other instanceof TransformerBlockEntity;
	}

	/** Its own product and nothing else: a run of street bundle is not where a transmission phase lands. */
	@Override
	public boolean takesConductor(ConductorSpec conductor, int index) {
		ConductorSpec mine = spec();
		return mine == null || mine.id().equals(conductor.id());
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
		tag.putInt("insulatorId", insulatorIds[0]);
		tag.putDouble("currentPower", currentPower);
	}

	@Override
	public void load(@Nonnull CompoundTag tag) {
		super.load(tag);
		currentPower = tag.getDouble("currentPower");
		if (tag.contains("insulatorId")) {
			insulatorIds[0] = tag.getInt("insulatorId");
			InsulatorIdRegistry.registerExistingId(insulatorIds[0]);
		} else {
			InsulatorIdRegistry.claimMissing(insulatorIds);
		}
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = super.getUpdateTag();
		tag.putInt("insulatorId", insulatorIds[0]);
		tag.putDouble("currentPower", currentPower);
		return tag;
	}

	@Override
	public void handleUpdateTag(CompoundTag tag) {
		super.handleUpdateTag(tag);
		load(tag);
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
