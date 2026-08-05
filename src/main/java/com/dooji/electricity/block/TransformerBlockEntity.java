package com.dooji.electricity.block;

import com.dooji.electricity.api.power.ConductorSpec;
import com.dooji.electricity.api.power.TransformerSpec;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.registry.TransformerCatalog;
import com.dooji.electricity.wire.InsulatorHost;
import com.dooji.electricity.wire.InsulatorPartHelper;
import javax.annotation.Nonnull;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A transformer, of either duty.
 *
 * The bushings are its fittings, low-voltage side first, in the order gen_transformer_models writes them.
 * What a transformer *does* to power is take a loss off it: {@link #throughput()} is what came in less what
 * the core and the load cost, which is the only reason a player would rather have one big machine unit than
 * three small ones.
 */
public class TransformerBlockEntity extends FittedBlockEntity {
	private double incomingKw = 0.0;

	public TransformerBlockEntity(BlockPos pos, BlockState state) {
		super(Electricity.TRANSFORMER_BLOCK_ENTITY.get(), pos, state);
	}

	public TransformerSpec spec() {
		if (getBlockState().getBlock() instanceof TransformerBlock block) return block.spec();

		return TransformerCatalog.MACHINE;
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

	/**
	 * Which side of the transformer a bushing is, and so what may be strung to it.
	 *
	 * ObjDefinitions names them low-voltage side first, so on a substation unit the first three are the
	 * 33 kV collector and the last three the 400 kV line. A machine unit has one side of three, and all of
	 * it is the collector.
	 */
	@Override
	public boolean takesConductor(ConductorSpec conductor, int index) {
		boolean transmission = spec().bushings() > 3 && index >= spec().bushings() / 2;
		return conductor.voltageClass() == (transmission ? ConductorSpec.VoltageClass.HIGH
				: ConductorSpec.VoltageClass.MEDIUM);
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
	}

	@Override
	public void load(@Nonnull CompoundTag tag) {
		super.load(tag);
		incomingKw = tag.getDouble("incomingKw");
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = super.getUpdateTag();
		tag.putDouble("incomingKw", incomingKw);
		return tag;
	}

	@Override
	public void handleUpdateTag(CompoundTag tag) {
		super.handleUpdateTag(tag);
		incomingKw = tag.getDouble("incomingKw");
	}
}
