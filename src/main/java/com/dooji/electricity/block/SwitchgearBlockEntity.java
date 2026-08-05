package com.dooji.electricity.block;

import com.dooji.electricity.api.power.ConductorSpec;
import com.dooji.electricity.api.power.SwitchgearSpec;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.joml.Vector3f;

/**
 * The six fittings of a switch, and what open does to them.
 *
 * {@link #busOf} is the whole of the electrical behaviour: closed, all six are one bus and power crosses the
 * switch; open, the line side and the load side are two, and PowerNetwork has two clusters at one position
 * with no connection between them. Nothing else about the network had to change.
 */
public class SwitchgearBlockEntity extends BlockEntity implements InsulatorHost {
	/** Above this fraction of the rating there is enough current in it to draw an arc on a bare blade. */
	private static final double LOADED = 0.02;
	/** How often a breaker looks at what is going through it, in ticks. */
	private static final int TRIP_INTERVAL = 20;

	private Vec3[] wirePositions;
	private int[] insulatorIds;
	private double currentPower = 0.0;

	public SwitchgearBlockEntity(BlockPos pos, BlockState state) {
		super(Electricity.SWITCHGEAR_BLOCK_ENTITY.get(), pos, state);
		ensureArraySizes();
		refresh();
		InsulatorIdRegistry.claimMissing(insulatorIds);
	}

	private SwitchgearSpec spec() {
		return getBlockState().getBlock() instanceof SwitchgearBlock gear ? gear.spec() : null;
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

	/** Where each palm is, from the model's own boxes, turned onto the block's facing. */
	private void refresh() {
		ensureArraySizes();
		List<String> groups = insulatorGroups();
		for (int i = 0; i < groups.size() && i < wirePositions.length; i++) {
			Vector3f top = ObjBoundingBoxRegistry.getTopSafe(getBlockState().getBlock(), groups.get(i));
			if (top == null) continue;

			double radians = Math.toRadians(ModelFacing.degrees(SwitchgearBlock.AUTHORED,
					getBlockState().getValue(SwitchgearBlock.FACING)));
			double cos = Math.cos(radians);
			double sin = Math.sin(radians);
			wirePositions[i] = new Vec3(top.x * cos + top.z * sin, top.y, -top.x * sin + top.z * cos)
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
		return InsulatorPartHelper.TYPE_SWITCHGEAR;
	}

	/**
	 * Which side of the switch a fitting is on, and whether the two sides are still one bus.
	 *
	 * The first half of the fittings is the line side and the second the load side. Closed they share a bus,
	 * which is what makes power cross; open they do not, and there is nothing else in the network joining
	 * two fittings at one position.
	 */
	@Override
	public int busOf(int index) {
		SwitchgearSpec spec = spec();
		if (spec == null || !SwitchgearBlock.open(getBlockState())) return 0;

		return index < spec.poles() ? 0 : 1;
	}

	/**
	 * A switch is a series element: whatever the line carries, it carries.
	 *
	 * Which conductor may land on it is {@link #takesConductor}, not this - a switch does not care what is
	 * on the other side of the span, only that it is at its own voltage.
	 */
	@Override
	public boolean feeds(InsulatorHost other) {
		return true;
	}

	/** Its own voltage class and nothing else: a 24 kV unit is not where a transmission line lands. */
	@Override
	public boolean takesConductor(ConductorSpec conductor, int index) {
		SwitchgearSpec spec = spec();
		return spec == null || conductor.voltageClass() == spec.voltageClass();
	}

	@Override
	public void deliverPower(double power) {
		currentPower = power;
	}

	public double getCurrentPower() {
		return currentPower;
	}

	/** Whether there is enough in it that parting bare contacts would draw an arc. */
	public boolean underLoad() {
		SwitchgearSpec spec = spec();
		return spec != null && currentPower > spec.ratedKw() * LOADED;
	}

	/**
	 * What a breaker is for: above its rating it opens itself.
	 *
	 * A disconnector does not, because a real one cannot - its contacts simply carry more than they were
	 * built for until something gives.
	 */
	public void serverTick() {
		if (level == null || level.getGameTime() % TRIP_INTERVAL != 0) return;

		SwitchgearSpec spec = spec();
		BlockState state = getBlockState();
		if (spec == null || !spec.breaksLoad() || state.getValue(SwitchgearBlock.OPEN)) return;
		if (currentPower <= spec.ratedKw()) return;

		((SwitchgearBlock) state.getBlock()).throwSwitch(level, getBlockPos(), state, true);
		level.playSound(null, getBlockPos(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 1.4f);
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

	public static BlockEntityType<SwitchgearBlockEntity> type() {
		return Electricity.SWITCHGEAR_BLOCK_ENTITY.get();
	}
}
