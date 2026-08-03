package com.dooji.electricity.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A cell of a machine that is bigger than the block it was placed in.
 *
 * <h2>Why this has to exist</h2>
 *
 * Several of these machines are drawn far past their own block - an electric cabin is three blocks tall
 * and two and a bit deep, a utility pole is six tall - and a block's collision was one cube at the
 * bottom of it. So the machine you could see was a machine you walked through, and stood on the air
 * above.
 *
 * An oversized {@link VoxelShape} is the obvious fix and it does not work past one block. Collisions
 * are gathered from the block positions overlapping the entity's own box grown by one, so a shape three
 * blocks tall hanging off a block three below a player is never consulted: they fall through the roof.
 * The only thing the game will reliably collide with at a position is a block at that position, which
 * is what this is - invisible, drawn by nothing, and solid.
 *
 * <h2>What it does not do</h2>
 *
 * It does not occlude: the machine above it is drawn by a renderer rather than by the block, so a shell
 * that culled its neighbours' faces would leave holes in the world. It blocks no light for the same
 * reason. And it is never an item: it is placed by its machine and it goes when the machine goes.
 *
 * <h2>Finding the machine it belongs to</h2>
 *
 * By searching, rather than by remembering. A shell could carry the offset to its machine in its state,
 * and then a machine moved by anything at all would leave shells pointing at nothing. So a shell asks
 * the small box of positions around it which of them holds a machine that claims this cell -
 * {@link MachineShell#hostOf} - and if none does, it stops being able to survive and the ordinary
 * neighbour update takes it away. Self-healing, and no state to get out of step.
 */
public class MachineShellBlock extends Block {
	public static final EnumProperty<Fill> FILL = EnumProperty.create("fill", Fill.class);

	/**
	 * How much of the cell a machine's body actually fills.
	 *
	 * A whole cell for anything solid; a ten-pixel slab against one face for the cells a body only
	 * reaches part way into, which is what the top of a cabin and the ends of its roof are; and a post
	 * for a pole, where the mast is half a block across and standing a pixel off it would be wrong.
	 *
	 * Ten pixels rather than eight, and that is measured rather than chosen: the cabin's roof is 0.627
	 * of a block into the cell above it and its ends reach 0.657 into the cells beside it.
	 */
	public enum Fill implements StringRepresentable {
		FULL("full", Shapes.block()),
		POST("post", Block.box(4.0, 0.0, 4.0, 12.0, 16.0, 12.0)),
		SLAB_DOWN("slab_down", Block.box(0.0, 0.0, 0.0, 16.0, 10.0, 16.0)),
		SLAB_UP("slab_up", Block.box(0.0, 6.0, 0.0, 16.0, 16.0, 16.0)),
		SLAB_NORTH("slab_north", Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 10.0)),
		SLAB_SOUTH("slab_south", Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 16.0)),
		SLAB_WEST("slab_west", Block.box(0.0, 0.0, 0.0, 10.0, 16.0, 16.0)),
		SLAB_EAST("slab_east", Block.box(6.0, 0.0, 0.0, 16.0, 16.0, 16.0));

		private final String name;
		private final VoxelShape shape;

		Fill(String name, VoxelShape shape) {
			this.name = name;
			this.shape = shape;
		}

		public VoxelShape shape() {
			return shape;
		}

		/** The same fill on a machine turned to face another way. Only the sideways slabs move. */
		public Fill turned(Direction facing) {
			return switch (this) {
				case SLAB_NORTH -> of(MachineShell.turned(Direction.NORTH, facing));
				case SLAB_SOUTH -> of(MachineShell.turned(Direction.SOUTH, facing));
				case SLAB_WEST -> of(MachineShell.turned(Direction.WEST, facing));
				case SLAB_EAST -> of(MachineShell.turned(Direction.EAST, facing));
				default -> this;
			};
		}

		private static Fill of(Direction side) {
			return switch (side) {
				case NORTH -> SLAB_NORTH;
				case SOUTH -> SLAB_SOUTH;
				case WEST -> SLAB_WEST;
				case EAST -> SLAB_EAST;
				case DOWN -> SLAB_DOWN;
				case UP -> SLAB_UP;
			};
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public MachineShellBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FILL, Fill.FULL));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FILL);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(FILL).shape();
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
		return 0;
	}

	@Override
	public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
		return false;
	}

	/** Whatever the machine is, so middle-clicking its upper half picks the machine. */
	@Override
	public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
		BlockPos host = MachineShell.hostOf(level, pos);
		return host == null ? ItemStack.EMPTY : level.getBlockState(host).getBlock().asItem().getDefaultInstance();
	}

	/**
	 * Mining any part of a machine mines the machine.
	 *
	 * Which is what a player means by it: they are hitting the cabin, and the fact that the block their
	 * cursor is on is a shell rather than the machine's own base is an implementation detail they should
	 * never have to know.
	 */
	@Override
	public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, net.minecraft.world.level.material.FluidState fluid) {
		BlockPos host = MachineShell.hostOf(level, pos);
		if (host != null) {
			level.destroyBlock(host, !player.isCreative(), player);
			return true;
		}

		return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
	}

	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return MachineShell.hostOf(level, pos) != null;
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
		return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
	}
}
