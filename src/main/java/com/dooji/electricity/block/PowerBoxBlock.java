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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PowerBoxBlock extends Block implements EntityBlock, MachineShell {
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

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
					Block.box(2.91, 0.88, 4.90, 13.09, 11.20, 10.85),
					Block.box(3.44, 1.36, 4.61, 12.56, 10.72, 5.28),
					Block.box(6.67, 11.76, 6.67, 9.33, 14.26, 9.33),
					Block.box(6.80, 10.95, 6.80, 9.20, 12.19, 9.20),
					Block.box(10.08, 0.00, 10.56, 11.04, 1.92, 11.52))));

	public PowerBoxBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
