package com.dooji.electricity.block;

import com.dooji.electricity.api.power.Dispatchable;
import com.dooji.electricity.main.Electricity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The power plant controller: one setpoint for a whole plant, shared out over the units that can meet it.
 *
 * A real one polls the meter at the point of interconnection and tells every inverter and turbine on the
 * site what to produce, in proportion to its rating.  This does the same over the radio link on its roof:
 * {@link #RANGE} blocks, machines in loaded chunks only, rescanned every {@link #RESCAN_TICKS}.
 *
 * The one rule that is easy to get wrong: a unit this has curtailed has to be *given back* - when the mode
 * goes to OFF, when it leaves range, and when the cabinet is broken - or it stays held down for ever with
 * nothing left in the world to say why.  {@link #release()} is called from all three.
 */
public class PlantControllerBlockEntity extends BlockEntity {
	/** What the cabinet's radio reaches, in blocks. */
	public static final int RANGE = 64;
	private static final int RESCAN_TICKS = 40;

	/** How the plant's setpoint is arrived at. */
	public enum Mode {
		/** Every unit keeps its own limit: the controller measures and reports, nothing more. */
		OFF,
		/** The plant is held to {@link #setpointKw}. */
		LIMIT,
		/** The setpoint follows the strongest redstone signal on the cabinet, full scale at fifteen. */
		REDSTONE;

		public Mode next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	private Mode mode = Mode.OFF;
	private double setpointKw = 0.0;

	/** The units in range, and the limit each was given, so each can be handed back what it had. */
	private final Map<BlockPos, Double> dispatched = new LinkedHashMap<>();
	private final List<BlockPos> units = new ArrayList<>();
	private final ClientSync sync = new ClientSync();
	/** How many units the panel is told about: the positions themselves are the server's business. */
	private int unitCount;
	private double capacityKw;
	private double outputKw;
	private int running;
	private int curtailed;
	private int ticks;

	public PlantControllerBlockEntity(BlockPos pos, BlockState state) {
		super(Electricity.PLANT_CONTROLLER_BLOCK_ENTITY.get(), pos, state);
	}

	public void serverTick() {
		if (!(level instanceof ServerLevel serverLevel)) return;

		if (ticks++ % RESCAN_TICKS == 0) rescan(serverLevel);
		measure(serverLevel);
		dispatch(serverLevel);
		sync.throttled(this);
	}

	/** Every dispatchable machine the radio reaches, taken chunk by chunk so nothing is pulled into memory. */
	private void rescan(ServerLevel serverLevel) {
		List<BlockPos> found = new ArrayList<>();
		int reach = Mth.ceil(RANGE / 16.0);
		ChunkPos here = new ChunkPos(worldPosition);
		for (int x = -reach; x <= reach; x++) {
			for (int z = -reach; z <= reach; z++) {
				LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(here.x + x, here.z + z);
				if (chunk == null) continue;

				for (var entry : chunk.getBlockEntities().entrySet()) {
					if (!(entry.getValue() instanceof Dispatchable)) continue;
					if (!entry.getKey().closerThan(worldPosition, RANGE)) continue;

					found.add(entry.getKey().immutable());
				}
			}
		}

		// whatever dropped out of range keeps the limit it had while it was in, which is wrong: hand it back
		for (BlockPos gone : List.copyOf(dispatched.keySet())) {
			if (!found.contains(gone)) restore(serverLevel, gone);
		}

		units.clear();
		units.addAll(found);
		unitCount = found.size();
	}

	private void measure(ServerLevel serverLevel) {
		double capacity = 0.0;
		double output = 0.0;
		int live = 0;
		int held = 0;
		for (BlockPos pos : units) {
			Dispatchable unit = unitAt(serverLevel, pos);
			if (unit == null) continue;

			capacity += unit.getNameplateKw();
			output += Math.max(0.0, unit.getCurrentPower());
			if (unit.isRunning()) live++;
			if (unit.getActivePowerLimit() < unit.getNameplateKw() - 0.01) held++;
		}

		capacityKw = capacity;
		outputKw = output;
		running = live;
		curtailed = held;
	}

	/** Shares the plant setpoint out in proportion to nameplate, which is what a real controller does. */
	private void dispatch(ServerLevel serverLevel) {
		if (mode == Mode.OFF) {
			if (!dispatched.isEmpty()) release();
			return;
		}

		double target = mode == Mode.REDSTONE
				? capacityKw * serverLevel.getBestNeighborSignal(worldPosition) / 15.0
				: Mth.clamp(setpointKw, 0.0, capacityKw);
		if (capacityKw <= 0.0) return;

		double share = target / capacityKw;
		for (BlockPos pos : units) {
			Dispatchable unit = unitAt(serverLevel, pos);
			if (unit == null) continue;

			double limit = unit.getNameplateKw() * share;
			// the limit it had before this cabinet touched it, kept so it can be handed back
			dispatched.putIfAbsent(pos, unit.getActivePowerLimit());
			if (Math.abs(unit.getActivePowerLimit() - limit) > 0.01) unit.setActivePowerLimit(limit);
		}
	}

	/** Hands every unit back the limit it had before this cabinet took hold of it. */
	public void release() {
		if (!(level instanceof ServerLevel serverLevel)) {
			dispatched.clear();
			return;
		}

		for (BlockPos pos : List.copyOf(dispatched.keySet())) {
			restore(serverLevel, pos);
		}
	}

	private void restore(ServerLevel serverLevel, BlockPos pos) {
		Double had = dispatched.remove(pos);
		Dispatchable unit = unitAt(serverLevel, pos);
		if (unit != null && had != null) unit.setActivePowerLimit(had);
	}

	private Dispatchable unitAt(ServerLevel serverLevel, BlockPos pos) {
		if (!serverLevel.isLoaded(pos)) return null;
		return serverLevel.getBlockEntity(pos) instanceof Dispatchable unit ? unit : null;
	}

	// ---- what the panel reads ----

	public Mode getMode() {
		return mode;
	}

	public double getSetpointKw() {
		return setpointKw;
	}

	/** The setpoint actually being held, which in REDSTONE is the signal rather than the slider. */
	public double getTargetKw() {
		if (mode == Mode.OFF) return capacityKw;
		if (mode == Mode.LIMIT) return Mth.clamp(setpointKw, 0.0, capacityKw);
		return level == null ? 0.0 : capacityKw * level.getBestNeighborSignal(worldPosition) / 15.0;
	}

	public double getCapacityKw() {
		return capacityKw;
	}

	public double getOutputKw() {
		return outputKw;
	}

	public int getUnitCount() {
		return unitCount;
	}

	public int getRunningCount() {
		return running;
	}

	public int getCurtailedCount() {
		return curtailed;
	}

	/** How loaded the plant is, as a comparator reads it: a vanilla circuit can follow the whole site. */
	public int getComparatorOutput() {
		if (capacityKw <= 0.0) return 0;
		return Mth.clamp((int) Math.round(outputKw / capacityKw * 15.0), 0, 15);
	}

	// ---- what the panel may change ----

	public void setMode(Mode wanted) {
		if (mode == wanted) return;

		mode = wanted;
		if (mode == Mode.OFF) release();
		ClientSync.now(this);
	}

	public void setSetpointKw(double kw) {
		setpointKw = Math.max(0.0, kw);
		ClientSync.now(this);
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putString("mode", mode.name());
		tag.putDouble("setpointKw", setpointKw);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		for (Mode candidate : Mode.values()) {
			if (candidate.name().equals(tag.getString("mode"))) mode = candidate;
		}

		setpointKw = tag.getDouble("setpointKw");
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = super.getUpdateTag();
		saveAdditional(tag);
		tag.putDouble("capacityKw", capacityKw);
		tag.putDouble("outputKw", outputKw);
		tag.putInt("units", unitCount);
		tag.putInt("running", running);
		tag.putInt("curtailed", curtailed);
		return tag;
	}

	@Override
	public void handleUpdateTag(CompoundTag tag) {
		load(tag);
		capacityKw = tag.getDouble("capacityKw");
		outputKw = tag.getDouble("outputKw");
		running = tag.getInt("running");
		curtailed = tag.getInt("curtailed");
		unitCount = tag.getInt("units");
	}
}
