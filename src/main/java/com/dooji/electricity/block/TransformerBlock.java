package com.dooji.electricity.block;

import com.dooji.electricity.api.power.TransformerSpec;
import com.dooji.electricity.main.Electricity;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A transformer, of either duty: the two steps a plant's output takes on the way to the grid.
 *
 * A pad-mount machine unit fits its own block. A substation unit is two blocks tall and two wide, so it
 * is a {@link MachineShell}; both tables are cut from the models by {@code tools/check_hitboxes.py --java}.
 */
public class TransformerBlock extends Block implements EntityBlock, MachineShell {
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

	/** The facing both tx_*.obj files are modelled at: the high-voltage bushings look north. */
	public static final Direction AUTHORED = Direction.NORTH;

	private static final List<Cell> MACHINE_CELLS = List.of(
			new Cell(0, 0, 0, Shapes.or(Block.box(1.76, 0.00, 3.36, 14.24, 1.60, 12.64),
					Block.box(2.27, 1.60, 3.87, 13.73, 9.92, 12.13),
					Block.box(2.37, 9.92, 3.88, 13.63, 10.87, 12.03),
					Block.box(2.30, 1.57, 4.64, 13.70, 3.20, 5.04),
					Block.box(3.36, 2.14, 5.92, 14.08, 10.93, 8.58),
					Block.box(4.25, 10.08, 8.89, 5.67, 14.08, 10.31),
					Block.box(5.44, 2.11, 3.28, 10.56, 6.08, 4.16),
					Block.box(7.29, 10.08, 8.89, 8.71, 14.08, 10.31),
					Block.box(10.33, 10.08, 8.89, 11.75, 14.08, 10.31))));

	private static final List<Cell> SUBSTATION_CELLS = List.of(
			new Cell(0, 0, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 16.00, 3.52, 16.00),
					Block.box(0.00, 3.20, 0.74, 16.00, 16.00, 15.26),
					Block.box(0.00, 4.77, 0.00, 16.00, 16.00, 16.00))),
			new Cell(-1, 0, 0, Shapes.or(Block.box(9.28, 0.00, 0.00, 16.00, 3.52, 16.00),
					Block.box(11.04, 3.20, 4.80, 12.80, 16.00, 11.20),
					Block.box(12.80, 3.20, 0.74, 16.00, 16.00, 15.26),
					Block.box(14.56, 4.77, 0.00, 16.00, 16.00, 16.00))),
			new Cell(0, 0, -1, Shapes.or(Block.box(0.00, 0.00, 13.44, 16.00, 3.52, 16.00),
					Block.box(0.00, 4.77, 13.92, 16.00, 16.00, 16.00))),
			new Cell(0, 0, 1, Shapes.or(Block.box(0.00, 0.00, 0.00, 16.00, 3.52, 2.56),
					Block.box(0.00, 4.77, 0.00, 16.00, 16.00, 2.08))),
			new Cell(1, 0, 0, Shapes.or(Block.box(0.00, 4.77, 0.00, 1.44, 16.00, 16.00),
					Block.box(0.00, 3.20, 0.74, 3.20, 16.00, 15.26),
					Block.box(0.00, 0.00, 0.00, 6.72, 3.52, 16.00))),
			new Cell(-1, 0, -1, Shapes.or(Block.box(9.28, 0.00, 13.44, 16.00, 3.52, 16.00),
					Block.box(14.56, 4.77, 13.92, 16.00, 16.00, 16.00))),
			new Cell(-1, 0, 1, Shapes.or(Block.box(9.28, 0.00, 0.00, 16.00, 3.52, 2.56),
					Block.box(14.56, 4.77, 0.00, 16.00, 16.00, 2.08))),
			new Cell(1, 0, -1, Shapes.or(Block.box(0.00, 4.77, 13.92, 1.44, 16.00, 16.00),
					Block.box(0.00, 0.00, 13.44, 6.72, 3.52, 16.00))),
			new Cell(1, 0, 1, Shapes.or(Block.box(0.00, 4.77, 0.00, 1.44, 16.00, 2.08),
					Block.box(0.00, 0.00, 0.00, 6.72, 3.52, 2.56))),
			new Cell(0, 1, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 16.00, 4.19, 16.00),
					Block.box(0.00, 0.00, 0.74, 16.00, 6.72, 15.26),
					Block.box(0.00, 6.72, 0.58, 16.00, 8.06, 15.33),
					Block.box(0.31, 7.01, 4.15, 2.89, 16.00, 6.73),
					Block.box(1.75, 7.01, 11.03, 3.37, 11.33, 12.65),
					Block.box(6.71, 7.01, 4.15, 9.29, 16.00, 6.73),
					Block.box(7.19, 7.01, 11.03, 8.81, 11.33, 12.65),
					Block.box(7.28, 7.05, 15.52, 8.72, 10.66, 16.00),
					Block.box(12.63, 7.01, 11.03, 14.25, 11.33, 12.65),
					Block.box(13.11, 7.01, 4.15, 15.69, 16.00, 6.73))),
			new Cell(-1, 1, 0, Shapes.or(Block.box(11.04, 0.00, 4.80, 12.80, 5.12, 11.20),
					Block.box(12.51, 6.72, 0.58, 16.00, 8.06, 15.33),
					Block.box(12.80, 0.00, 0.74, 16.00, 6.72, 15.26),
					Block.box(14.56, 0.00, 0.00, 16.00, 4.19, 16.00))),
			new Cell(0, 1, -1, Block.box(0.00, 0.00, 13.92, 16.00, 4.19, 16.00)),
			new Cell(0, 1, 1, Shapes.or(Block.box(0.00, 0.00, 0.00, 16.00, 4.19, 2.08),
					Block.box(0.00, 7.20, 0.48, 16.00, 12.32, 4.00),
					Block.box(7.28, 7.05, 0.00, 8.72, 10.66, 1.46))),
			new Cell(1, 1, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 1.44, 4.19, 16.00),
					Block.box(0.00, 0.00, 0.74, 3.20, 6.72, 15.26),
					Block.box(0.00, 6.72, 0.58, 3.49, 8.06, 15.33))));
	private final TransformerSpec spec;

	public TransformerBlock(Properties properties, TransformerSpec spec) {
		super(properties.sound(SoundType.METAL));
		this.spec = spec;
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	public TransformerSpec spec() {
		return spec;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shellShape(state);
	}

	@Override
	public List<Cell> shellCells() {
		return spec.duty() == TransformerSpec.Duty.MACHINE ? MACHINE_CELLS : SUBSTATION_CELLS;
	}

	@Override
	public Direction shellFacing(BlockState state) {
		return state.getValue(FACING);
	}

	@Override
	public Direction shellAuthored() {
		return AUTHORED;
	}

	@Override
	public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
		return 0;
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	/** An empty hand on the tank reads the nameplate, which is what a transformer has instead of a panel. */
	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;

		if (level.isClientSide && level.getBlockEntity(pos) instanceof TransformerBlockEntity transformer) {
			player.displayClientMessage(Component.translatable("tooltip.electricity.transformer.reading",
					spec.ratioText(), String.format("%.1f", transformer.getCurrentPower()),
					String.format("%.1f", spec.lossKw(transformer.getCurrentPower()))), true);
		}

		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Nullable @Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TransformerBlockEntity(pos, state);
	}

	/** Nothing of its own to tick; the substation unit's cells are worth mending. */
	@Nullable @Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return (lvl, pos, blockState, blockEntity) -> MachineShell.heal(lvl, pos, blockState);
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		MachineShell.place(level, pos, state);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock())) {
			MachineShell.clear(level, pos, state);
			if (!level.isClientSide && level instanceof ServerLevel serverLevel
					&& level.getBlockEntity(pos) instanceof TransformerBlockEntity transformer) {
				Electricity.wireManager.removeConnectionsForInsulators(serverLevel, transformer.getInsulatorIds());
			}
		}

		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
