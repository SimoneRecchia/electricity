package com.dooji.electricity.block;

import com.dooji.electricity.api.power.ConductorSpec;
import com.dooji.electricity.api.power.SwitchgearSpec;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.wire.InsulatorHost;
import com.dooji.electricity.wire.InsulatorPartHelper;
import javax.annotation.Nonnull;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The six fittings of a switch, and what open does to them.
 *
 * {@link #busOf} is the whole of the electrical behaviour: closed, all six are one bus and power crosses the
 * switch; open, the line side and the load side are two, and PowerNetwork has two clusters at one position
 * with no connection between them. Nothing else about the network had to change.
 */
public class SwitchgearBlockEntity extends FittedBlockEntity {
	/** Above this fraction of the rating there is enough current in it to draw an arc on a bare blade. */
	private static final double LOADED = 0.02;
	/** How often a breaker looks at what is going through it, in ticks. */
	private static final int TRIP_INTERVAL = 20;

	private double currentPower = 0.0;

	public SwitchgearBlockEntity(BlockPos pos, BlockState state) {
		super(Electricity.SWITCHGEAR_BLOCK_ENTITY.get(), pos, state);
	}

	private SwitchgearSpec spec() {
		return getBlockState().getBlock() instanceof SwitchgearBlock gear ? gear.spec() : null;
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
	 * Which conductor may land on it is {@link #takesConductor}, not this - a switch does not care what is on
	 * the other side of the span, only that it is at its own voltage.
	 */
	@Override
	public boolean feeds(InsulatorHost other) {
		return true;
	}

	/** A switch is in a line, not at an end of one - see {@link InsulatorHost#passesThrough}. */
	@Override
	public boolean passesThrough() {
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
