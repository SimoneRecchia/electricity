package com.dooji.electricity.block;

import com.dooji.electricity.main.Electricity;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PowerBoxBlock extends Block implements EntityBlock, MachineShell {
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

	/** Hung on a wall rather than stood on the ground. */
	public static final BooleanProperty MOUNTED = BooleanProperty.create("mounted");

	/** How far back a mounted box is pushed, so its own back lands on the block's. */
	public static final double BACKSET = 0.280;

	/** The facing power_box.obj is modelled at. */
	public static final Direction AUTHORED = Direction.NORTH;

	/** The kiosk, where it actually is: cut from power_box.obj, part by part. */
	private static final List<Cell> CELLS = List.of(
			new Cell(0, 0, 0, Shapes.or(Block.box(2.53, 0.00, 4.61, 13.47, 11.81, 11.52),
					Block.box(6.67, 12.19, 6.67, 9.33, 14.26, 9.33),
					Block.box(6.80, 11.23, 6.80, 9.20, 12.18, 9.20))));

	/**
	 * The plinth, which a mounted box has not got.
	  *
	 * One of the boxes of {@link #CELLS}.
	 */
	private static final VoxelShape PLINTH = Block.box(2.56, 0.00, 4.80, 13.44, 0.88, 11.20);

	/** The same cabinet with no plinth under it */
	private static final List<Cell> WALL_CELLS = wallCells();

	private static List<Cell> wallCells() {
		// one cell, and it is the machine's own: a kiosk fits inside its block
		VoxelShape shape = Shapes.join(CELLS.get(0).shape(0), PLINTH, BooleanOp.ONLY_FIRST);
		return List.of(new Cell(0, 0, 0, shape.move(0.0, 0.0, BACKSET)));
	}

	public PowerBoxBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MOUNTED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, MOUNTED);
	}

	/** Clicked on a wall it hangs on that wall; clicked on the ground it stands on the ground. */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		if (face.getAxis().isHorizontal()) {
			return this.defaultBlockState().setValue(FACING, face).setValue(MOUNTED, true);
		}

		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(MOUNTED, false);
	}

	/** A mounted box needs the wall it is bolted to. */
	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		if (!state.getValue(MOUNTED)) return true;

		BlockPos wall = pos.relative(state.getValue(FACING).getOpposite());
		return level.getBlockState(wall).isFaceSturdy(level, wall, state.getValue(FACING));
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
		if (state.getValue(MOUNTED) && direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}

		return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shellShape(state);
	}

	/** A kiosk on the ground is one shape and one bolted to a wall is another. */
	@Override
	public List<Cell> shellCells(BlockState state) {
		return state.getValue(MOUNTED) ? WALL_CELLS : CELLS;
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

	@Nullable @Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PowerBoxBlockEntity(pos, state);
	}

	@Nullable @Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
		return (lvl, pos, blockState, blockEntity) -> {
			if (blockEntity instanceof PowerBoxBlockEntity powerBox) {
				powerBox.tick();
			}
		};
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock())) {
			if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
				BlockEntity blockEntity = level.getBlockEntity(pos);
				if (blockEntity instanceof PowerBoxBlockEntity powerBox) {
					Electricity.wireManager.removeConnectionsForInsulators(serverLevel, powerBox.getInsulatorIds());
				}
			}
		}

		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
