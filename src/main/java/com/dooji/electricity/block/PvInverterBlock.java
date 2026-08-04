package com.dooji.electricity.block;

import com.dooji.electricity.api.power.CombinerSpec;
import com.dooji.electricity.api.power.DcCableSpec;
import com.dooji.electricity.api.power.InverterSpec;
import com.dooji.electricity.main.Electricity;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The cabinet a photovoltaic plant actually is.
 *
 * A field of modules is not a power station until something turns their direct current into
 * alternating current at grid voltage, holds a power factor, and reports what it is doing. That is
 * this block: the generator as far as the rest of the mod is concerned, the node a computer talks to,
 * and the thing that clips.
 *
 * <h2>Size</h2>
 *
 * Four products spanning a factor of two hundred and fifty in nameplate, drawn at three sizes,
 * because that is roughly how the real ones scale: a residential machine hangs on a wall, a
 * commercial one stands about as tall as a person, and a central inverter is a shipping container
 * with a transformer next to it. The door faces the way it was placed, because a technician has to be
 * able to open it.
 */
public class PvInverterBlock extends HorizontalDirectionalBlock implements EntityBlock, DcTerminal, MachineShell {
	/**
	 * The facing pv_inverter.obj was modelled at: one of the mod's own models, so it faces north like the rest of them.
	 *
	 * Declared here because more than one thing has to agree about it - the renderer turns the model
	 * by it, and whatever else reads the geometry turns with it. See {@link ModelFacing}.
	 */
	public static final Direction AUTHORED = Direction.NORTH;

	/**
	 * A combiner box fitted inside the cabinet, giving it fused string terminals it did not have.
	 *
	 * A real option on a real product: a central inverter's direct-current section is a factory-fitted
	 * combiner, and the *virtual central* arrangement - boxes spread through the field, machines together
	 * at one end - is the other way of solving the same problem. So both are here, and this is the first.
	 *
	 * A block state rather than block entity data because a cable has to know whether the machine takes
	 * strings while a chunk is being meshed. Which box it is lives in the block entity, since that only
	 * decides how many strings, not whether any.
	 */
	public static final BooleanProperty COMBINER = BooleanProperty.create("combiner");

	/**
	 * The machine as collision, cut from pv_inverter.obj at the size it is drawn.
	 *
	 * The plinth, the cabinet, the hood, the doors and the direct-current compartment, each where the model
	 * puts it - and it turns with the block, which three hand-written boxes did not. Written by
	 * {@code tools/check_hitboxes.py --java}, and it is the 1:1 table: the machine is drawn at the size of
	 * the commercial cabinet and scaled per product, so the other two sizes come from this one.
	 */
	private static final List<Cell> CELLS = List.of(
			new Cell(0, 0, 0, Shapes.or(Block.box(0.48, 15.39, 2.72, 15.52, 16.00, 12.96),
					Block.box(0.56, 0.00, 2.80, 15.44, 0.88, 12.88),
					Block.box(0.80, 0.88, 2.62, 15.20, 15.68, 12.64),
					Block.box(1.52, 1.52, 2.21, 14.48, 15.04, 3.20),
					Block.box(3.20, 0.85, 2.00, 12.80, 6.72, 2.64))));

	/**
	 * The same table at the other two sizes the renderer draws.
	 *
	 * Scaled rather than written out, because the renderer scales the model rather than swapping it, and a
	 * second table would be a second thing to keep in step. The scale is about the middle of the block's
	 * footprint and its floor, which is where {@code PvInverterRenderer} applies it - so a small machine
	 * sits on the ground in the middle of its block rather than hovering in a corner of it.
	 */
	private static final List<Cell> CABINET_CELLS = scaledCells(0.92);
	private static final List<Cell> WALL_CELLS = scaledCells(0.62);

	private static List<Cell> scaledCells(double factor) {
		List<Cell> out = new ArrayList<>();
		for (Cell cell : CELLS) {
			VoxelShape scaled = Shapes.empty();
			for (AABB box : cell.shape(0).toAabbs()) {
				scaled = Shapes.or(scaled, Shapes.box(
						0.5 + (box.minX - 0.5) * factor, box.minY * factor, 0.5 + (box.minZ - 0.5) * factor,
						0.5 + (box.maxX - 0.5) * factor, box.maxY * factor, 0.5 + (box.maxZ - 0.5) * factor));
			}

			// every cell of this machine is its own, so the offset is the one it was declared with
			out.add(new Cell(0, 0, 0, scaled));
		}

		return List.copyOf(out);
	}

	/** Nameplate above which a machine is drawn as a container rather than a cabinet, in kW. */
	private static final double CONTAINER_KW = 1000.0;
	/** Nameplate below which it hangs on a wall, in kW. */
	private static final double WALL_KW = 30.0;

	private final InverterSpec spec;

	public PvInverterBlock(Properties properties, InverterSpec spec) {
		super(properties.sound(SoundType.METAL));
		this.spec = spec;
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(COMBINER, false));
	}

	public InverterSpec spec() {
		return spec;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, COMBINER);
	}

	/**
	 * Which gauge lands on this machine, off its own datasheet.
	 *
	 * The distinction a real catalogue makes and this one now makes too: a residential or commercial
	 * machine has plug connectors, so strings go straight in and a combiner's trunk has nowhere to go. A
	 * utility string machine has both. A central machine has bare busbars, so it takes a trunk and cannot
	 * take a string at all - which is why it needs combiner boxes rather than merely liking them.
	 */
	@Override
	public boolean acceptsCable(BlockState state, DcCableSpec cable, Direction side) {
		if (cable.trunk()) return spec.trunkTerminals();

		return spec.stringTerminals() || state.getValue(COMBINER);
	}

	/**
	 * Whether a combiner box can be worked into this cabinet, and the state it takes when it is.
	 *
	 * Only a machine that has no fused string terminals of its own, which is the central one. Refusing it
	 * on the others is not pedantry: a string inverter's terminals *are* its fuses, so a box inside one
	 * would be a second set of fuses in series with the first, and no vendor sells that.
	 */
	@Nullable
	public BlockState withCombinerFitted(BlockState state) {
		if (spec.stringTerminals() || state.getValue(COMBINER)) return null;

		return state.setValue(COMBINER, true);
	}

	/** Whether a cabinet has a direct-current section in it, asked of the state so a cable can ask too. */
	public static boolean hasCombiner(BlockState state) {
		return state.getBlock() instanceof PvInverterBlock && state.getValue(COMBINER);
	}

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shellShape(state);
	}

	@Override
	public List<Cell> shellCells() {
		if (spec.acPowerKw() >= CONTAINER_KW) return CELLS;
		if (spec.acPowerKw() <= WALL_KW) return WALL_CELLS;

		return CABINET_CELLS;
	}

	@Override
	public Direction shellFacing(BlockState state) {
		return state.getValue(FACING);
	}

	@Override
	public Direction shellAuthored() {
		return AUTHORED;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PvInverterBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide ? null : (lvl, pos, blockState, blockEntity) -> {
			if (blockEntity instanceof PvInverterBlockEntity inverter) {
				inverter.serverTick();
			}
		};
	}

	/**
	 * Takes the cabinet out of the plant when it is actually broken.
	 *
	 * This and not the block entity's own removal, which also happens every time the chunk unloads: the
	 * plant reaches into other chunks here, and reaching out of an unloading chunk is what stops a world
	 * from ever finishing its save.
	 */
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && !level.isClientSide && level instanceof ServerLevel serverLevel) {
			if (level.getBlockEntity(pos) instanceof PvInverterBlockEntity inverter) {
				// the arrays, or they would go on believing they were wired to a cabinet that is no longer
				// there and would wait for an operating point that never comes
				inverter.releaseArrays();
				Electricity.wireManager.removeConnectionsForInsulators(serverLevel, inverter.getInsulatorIds());
				// and the box out of the cabinet, so rearranging a plant is not a tax on the parts
				CombinerSpec fitted = inverter.integratedCombiner();
				if (fitted != null) {
					popResource(level, pos, new ItemStack(Electricity.PV_COMBINER_ITEMS.get(fitted.id()).get()));
				}
			}
		}

		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
