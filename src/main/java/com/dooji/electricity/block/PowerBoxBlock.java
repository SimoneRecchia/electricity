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

	/**
	 * Hung on a wall rather than stood on the ground.
	 *
	 * Set by clicking the side of a block, which is how every wall-mounted thing in the game is placed.
	 * A pad-mounted kiosk and a wall enclosure are the same cabinet with two ways of fixing it, and the
	 * mounting channels on its back are drawn either way because a real one is railed at the factory -
	 * so the only visible difference is that a mounted box loses its plinth and sits back against the
	 * wall instead of in the middle of its block.
	 */
	public static final BooleanProperty MOUNTED = BooleanProperty.create("mounted");

	/**
	 * How far back a mounted box is pushed, so its own back lands on the block's.
	 *
	 * The model is centred and reaches z 0.220 at the rear, so this is what is left to the boundary.
	 * The renderer translates the model by it and {@link #WALL_CELLS} moves the collision by the same
	 * figure, in the model's own frame, before the facing turns either.
	 */
	public static final double BACKSET = 0.280;

	/**
	 * The facing power_box.obj is modelled at.
	 *
	 * North now, like every model the mod generates for itself: the doors are on the north face and the
	 * kiosk is centred in its block. The inherited model faced east and hugged the west edge, hanging a
	 * sixteenth of a block outside it - so a kiosk placed against a wall was half inside the wall, and its
	 * collision was four hand-written boxes each a quarter turn from where the cabinet was drawn.
	 */
	public static final Direction AUTHORED = Direction.NORTH;

	/**
	 * The kiosk, where it actually is: cut from power_box.obj, part by part.
	 *
	 * The plinth, the body, the doors, the hood over them and the bushing on the roof, each as the box it
	 * is drawn as - so a player walking round it collides with the cabinet and not with the air over the
	 * plinth, and can stand on the hood's ledge because there is one.
	 *
	 * In the model's own frame, turned onto the facing by the same arithmetic the renderer poses by.
	 * Written by {@code tools/check_hitboxes.py --java}, which reads both and fails if they have drifted.
	 */
	private static final List<Cell> CELLS = List.of(
			new Cell(0, 0, 0, Shapes.or(Block.box(2.53, 10.94, 4.77, 13.47, 11.81, 11.23),
					Block.box(2.56, 0.00, 4.80, 13.44, 0.88, 11.20),
					Block.box(2.91, 0.88, 4.90, 13.09, 11.20, 11.20),
					Block.box(3.44, 1.36, 4.61, 12.56, 10.72, 5.28),
					Block.box(6.67, 12.19, 6.67, 9.33, 14.26, 9.33),
					Block.box(6.80, 11.23, 6.80, 9.20, 12.18, 9.20),
					Block.box(10.08, 0.00, 10.56, 11.04, 1.92, 11.52))));

	/** The plinth, which a mounted box has not got. One of the boxes of {@link #CELLS}. */
	private static final VoxelShape PLINTH = Block.box(2.56, 0.00, 4.80, 13.44, 0.88, 11.20);

	/** The same cabinet with no plinth under it, moved back against the wall it hangs on. */
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

	/**
	 * Clicked on a wall it hangs on that wall; clicked on the ground it stands on the ground.
	 *
	 * The facing is the clicked face when mounted, so the doors look out of the wall rather than into it.
	 */
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

	@Override
	public List<Cell> shellCells() {
		return CELLS;
	}

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
