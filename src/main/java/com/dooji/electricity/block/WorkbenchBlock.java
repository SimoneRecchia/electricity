package com.dooji.electricity.block;

import com.dooji.electricity.menu.WorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

public class WorkbenchBlock extends Block {
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

	/**
	 * The bench as collision, cut from the same list of parts the model is drawn from.
	 *
	 * It was a whole cube, because the model was a cube with a picture of a bench on it. It is a bench now
	 * - four legs, a shelf, a drawer stack, a plate top, a tool board and a vice - and the collision is
	 * those parts where they are drawn, so a player can stand on the top, walk their feet under the frame,
	 * and put something in the gap between the legs.
	 *
	 * Printed by {@code tools/gen_json_models.py}, which writes the model from the same figures: if a
	 * drawer moves there, this moves with it.
	 */
	private static final VoxelShape BENCH = Shapes.or(Block.box(0.5, 0.0, 0.5, 2.5, 12.0, 2.5),
			Block.box(13.5, 0.0, 0.5, 15.5, 12.0, 2.5),
			Block.box(0.5, 0.0, 13.5, 2.5, 12.0, 15.5),
			Block.box(13.5, 0.0, 13.5, 15.5, 12.0, 15.5),
			Block.box(2.5, 1.5, 2.5, 13.5, 3.0, 13.5),
			Block.box(2.5, 3.0, 0.5, 8.0, 12.0, 13.5),
			Block.box(8.0, 7.5, 2.5, 13.5, 9.0, 13.5),
			Block.box(0.0, 12.0, 0.0, 16.0, 14.0, 16.0),
			Block.box(0.5, 14.0, 13.5, 15.5, 16.0, 15.5),
			Block.box(2.0, 14.0, 2.0, 6.0, 15.6, 5.0),
			Block.box(3.4, 15.6, 2.6, 4.6, 16.0, 4.4),
			Block.box(9.0, 14.0, 2.5, 14.0, 14.8, 7.5));

	/**
	 * The same shape at each facing, turned once when the class is read rather than per collision test.
	 *
	 * The model is authored with its front at north and the blockstate turns it by whole quarters, so the
	 * shape has to be turned by the same quarters - {@link MachineShell#turned} is that arithmetic, and it
	 * exists once for the whole mod.
	 */
	private static final VoxelShape[] SHAPES = shapes();

	private static VoxelShape[] shapes() {
		VoxelShape[] turned = new VoxelShape[4];
		for (int quarters = 0; quarters < 4; quarters++) {
			turned[quarters] = MachineShell.turned(BENCH, quarters);
		}

		return turned;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[ModelFacing.quarters(Direction.NORTH, state.getValue(FACING))];
	}

	public WorkbenchBlock(Properties properties) {
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
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
			NetworkHooks.openScreen(serverPlayer, new MenuProvider() {
				@Override
				public Component getDisplayName() {
					return Component.translatable("block.electricity.workbench");
				}

				@Override
				public WorkbenchMenu createMenu(int id, Inventory inventory, Player player) {
					return new WorkbenchMenu(id, inventory, ContainerLevelAccess.create(level, pos));
				}
			}, pos);
			serverPlayer.awardStat(Stats.INTERACT_WITH_CRAFTING_TABLE);
			level.gameEvent(GameEvent.BLOCK_ACTIVATE, pos, GameEvent.Context.of(player));
		}

		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
