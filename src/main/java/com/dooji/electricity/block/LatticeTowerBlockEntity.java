package com.dooji.electricity.block;

import com.dooji.electricity.api.power.ConductorSpec;
import com.dooji.electricity.api.power.TowerSpec.Duty;
import com.dooji.electricity.client.render.obj.ObjBoundingBoxRegistry;
import com.dooji.electricity.client.render.obj.ObjModel;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.wire.InsulatorHost;
import com.dooji.electricity.wire.InsulatorPartHelper;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A lattice transmission tower: six phase fittings and the earth peak above them.
 *
 * The six are {@code insulator_1..6} in the order gen_tower_models writes PHASES - the lower arm outward-in,
 * then the upper arm - and a wire is stored against the index, so that order cannot change.
 */
public class LatticeTowerBlockEntity extends FittedBlockEntity {
	private double currentPower = 0.0;

	public LatticeTowerBlockEntity(BlockPos pos, BlockState state) {
		super(Electricity.LATTICE_TOWER_BLOCK_ENTITY.get(), pos, state);
	}

	/**
	 * Where on a string the conductor is actually clamped, which is not the middle of it.
	 *
	 * Anchored at the box's centre every span landed halfway up the discs. A terminal tower dead-ends the
	 * line on one side, so its clamp is at the far end of the string, out on -z. The other two carry the
	 * phase across the tower - a suspension string hangs from the arm and a tension tower's jumper loops
	 * under it - so for both the phase's station is under the hanger.
	 */
	@Nullable @Override
	protected Vec3 landing(String group) {
		ObjModel.BoundingBox box = ObjBoundingBoxRegistry.getBoundingBox(getBlockState().getBlock(), group);
		if (box == null) return null;

		return duty() == Duty.TERMINAL
				? new Vec3(box.center.x, box.center.y, box.min.z)
				: new Vec3(box.center.x, box.min.y, box.center.z);
	}

	private Duty duty() {
		return getBlockState().getBlock() instanceof LatticeTowerBlock tower ? tower.spec().duty() : Duty.SUSPENSION;
	}

	@Override
	public String fittingType() {
		return InsulatorPartHelper.TYPE_LATTICE_TOWER;
	}

	/**
	 * A tower carries a line on to the next tower and delivers it to a substation or a pole.
	 *
	 * And to a run laid on the ground, which is what a line does at its end: it comes off the tower's
	 * dead-end into a trench or a ground bus. A run feeds a tower too, so the link is two-way. The
	 * transformer is in the list because a line has two ends - a substation unit already fed a tower, and
	 * without the other direction a 400 kV line could leave a substation and never arrive at one.
	 */
	@Override
	public boolean feeds(InsulatorHost other) {
		return other instanceof LatticeTowerBlockEntity || other instanceof ElectricCabinBlockEntity
				|| other instanceof UtilityPoleBlockEntity || other instanceof GroundConductorBlockEntity
				|| other instanceof TransformerBlockEntity;
	}

	/** Transmission and nothing else: a tower's insulator string is 0.7 m of glass for a reason. */
	@Override
	public boolean takesConductor(ConductorSpec conductor, int index) {
		return conductor.voltageClass() == ConductorSpec.VoltageClass.HIGH;
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
	}

	@Override
	public void load(@Nonnull CompoundTag tag) {
		super.load(tag);
		currentPower = tag.getDouble("currentPower");
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = super.getUpdateTag();
		tag.putDouble("currentPower", currentPower);
		return tag;
	}

	@Override
	public void handleUpdateTag(CompoundTag tag) {
		super.handleUpdateTag(tag);
		currentPower = tag.getDouble("currentPower");
	}
}
