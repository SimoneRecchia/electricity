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

/** The cabinet a photovoltaic plant actually is. */
public class PvInverterBlock extends HorizontalDirectionalBlock implements EntityBlock, DcTerminal, MachineShell {
	/**
	 * The facing pv_inverter.obj was modelled at: one of the mod's own models, so it faces north like the rest of them.
	  *
	 * See {@link ModelFacing}.
	 */
	public static final Direction AUTHORED = Direction.NORTH;

	/** A combiner box fitted inside the cabinet, giving it fused string terminals it did not have. */
	public static final BooleanProperty COMBINER = BooleanProperty.create("combiner");

	/** The machine as collision, cut from pv_inverter.obj at the size it is drawn. */
	private static final List<Cell> CELLS = List.of(
			new Cell(0, 0, 0, Block.box(0.48, 0.00, 2.05, 15.52, 16.00, 12.96)));

	/** The same table at the other two sizes the renderer draws. */
	private static final List<Cell> CABINET_CELLS = scaledCells(0.92);
	private static final List<Cell> WALL_CELLS = scaledCells(0.62);

	// The direct-current compartment used to be subtracted from each table, so an inverter without one had
	// its collision notched. Against a cabinet drawn as one rectangle that notch is 0.6 px of the front
	// face, which no player can feel - so the shape no longer depends on whether the compartment is fitted.

	private static List<Cell> scaledCells(double factor) {
		List<Cell> out = new ArrayList<>();
		for (Cell cell : CELLS) {
			VoxelShape scaled = Shapes.empty();
			for (AABB box : cell.shape(0).toAabbs()) {
				scaled = Shapes.or(scaled, Shapes.box(
						0.5 + (box.minX - 0.5) * factor, box.minY * factor, 0.5 + (box.minZ - 0.5) * factor,
						0.5 + (box.maxX - 0.5) * factor, box.maxY * factor, 0.5 + (box.maxZ - 0.5) * factor));
			}

			// every cell of this machine is its own
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

	/** Which gauge lands on this machine, off its own datasheet. */
	@Override
	public boolean acceptsCable(BlockState state, DcCableSpec cable, Direction side) {
		if (cable.trunk()) return spec.trunkTerminals();

		return spec.stringTerminals() || state.getValue(COMBINER);
	}

	/** Whether a combiner box can be worked into this cabinet */
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
		return shellCells(defaultBlockState().setValue(COMBINER, true));
	}

	@Override
	public List<Cell> shellCells(BlockState state) {
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

	/** Takes the cabinet out of the plant when it is actually broken. */
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
