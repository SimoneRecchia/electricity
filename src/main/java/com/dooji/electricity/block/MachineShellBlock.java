package com.dooji.electricity.block;

import com.dooji.electricity.item.ItemWire;
import com.dooji.electricity.main.Electricity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A cell of a machine that is bigger than the block it was placed in.
 *
 * <h2>Why this has to exist</h2>
 *
 * Several of these machines are drawn far past their own block - an electric cabin is two and a half
 * blocks tall and two and a bit deep, a utility pole is six tall with four-block arms - and a block's
 * collision was one cube at the bottom of it. So the machine you could see was a machine you walked
 * through, and stood on the air above.
 *
 * An oversized {@link VoxelShape} is the obvious fix and it does not work past one block. Collisions
 * are gathered from the block positions overlapping the entity's own box grown by one, so a shape three
 * blocks tall hanging off a block three below a player is never consulted: they fall through the roof.
 * The only thing the game will reliably collide with at a position is a block at that position, which
 * is what this is - invisible, drawn by nothing, and shaped exactly like the part of the machine that
 * reaches into it.
 *
 * <h2>Where its shape comes from</h2>
 *
 * From the machine, every time it is asked. A cell holds no shape of its own: it holds the way back to
 * its machine, and the machine's table says what that cell contains
 * ({@link MachineShell#shellCells()}). That is what makes {@link #hasDynamicShape()} true, and it is
 * worth the cost - the alternative is a fixed catalogue of slabs and corners in here, which is how the
 * collision came to disagree with the models in the first place.
 *
 * <h2>What it does not do</h2>
 *
 * It does not occlude: the machine above it is drawn by a renderer rather than by the block, so a shell
 * that culled its neighbours' faces would leave holes in the world. It blocks no light for the same
 * reason. It is never an item. And it never handles a click itself - mining it, picking it and using it
 * all reach past it to the machine, because a player pointing at the middle of a cabin means the cabin.
 */
public class MachineShellBlock extends Block {
	/**
	 * The way back to the machine, as the offset from it to this cell.
	 *
	 * Carried in the state rather than searched for, because the shape is needed on every collision test
	 * and a search of the box a machine could occupy is sixty-odd block reads. Carried rather than
	 * trusted: {@link MachineShell#hostOf} checks that the machine at the far end still claims this
	 * cell, so a machine that has been broken or turned leaves orphans that take themselves away.
	 *
	 * A property cannot hold a negative number, so the two horizontal offsets are stored shifted by
	 * {@link MachineShell#REACH_SIDE} and read back the same way.
	 */
	public static final IntegerProperty HOST_X = IntegerProperty.create("host_x", 0, 2 * MachineShell.REACH_SIDE);
	public static final IntegerProperty HOST_Y = IntegerProperty.create("host_y", 0, MachineShell.REACH_UP);
	public static final IntegerProperty HOST_Z = IntegerProperty.create("host_z", 0, 2 * MachineShell.REACH_SIDE);

	public MachineShellBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(HOST_X, MachineShell.REACH_SIDE).setValue(HOST_Y, 0).setValue(HOST_Z, MachineShell.REACH_SIDE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HOST_X, HOST_Y, HOST_Z);
	}

	/** The state a cell at {@code cell} takes for the machine at {@code host}. */
	public static BlockState pointingAt(BlockPos host, BlockPos cell) {
		BlockPos offset = cell.subtract(host);
		return Electricity.MACHINE_SHELL_BLOCK.get().defaultBlockState()
				.setValue(HOST_X, offset.getX() + MachineShell.REACH_SIDE)
				.setValue(HOST_Y, offset.getY())
				.setValue(HOST_Z, offset.getZ() + MachineShell.REACH_SIDE);
	}

	/** Where the machine is, according to the cell itself. Whether it is still there is another question. */
	public static BlockPos pointsAt(BlockPos pos, BlockState state) {
		return pos.offset(MachineShell.REACH_SIDE - state.getValue(HOST_X),
				-state.getValue(HOST_Y),
				MachineShell.REACH_SIDE - state.getValue(HOST_Z));
	}

	/**
	 * The part of the machine that reaches into this cell.
	 *
	 * A whole block if the machine cannot be found, which happens for a heartbeat while a chunk loads
	 * and for good once a machine has been broken. Solid is the safe answer to give in the meantime: a
	 * player standing on a cabin's roof keeps standing on it, and an orphan is gone on the next update.
	 */
	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		BlockPos host = pointsAt(pos, state);
		BlockState hostState = level.getBlockState(host);
		if (!(hostState.getBlock() instanceof MachineShell machine)) return Shapes.block();

		int quarters = machine.shellTurns(hostState);
		BlockPos offset = pos.subtract(host);
		for (MachineShell.Cell cell : machine.shellCells()) {
			if (cell.at(quarters).equals(offset)) return cell.shape(quarters);
		}

		return Shapes.block();
	}

	/** Because the shape is the machine's, and the machine is at another position. */
	@Override
	public boolean hasDynamicShape() {
		return true;
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

	/** Whatever the machine is, so middle-clicking any part of it picks the machine. */
	@Override
	public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
		BlockPos host = MachineShell.hostOf(level, pos);
		return host == null ? ItemStack.EMPTY : level.getBlockState(host).getBlock().asItem().getDefaultInstance();
	}

	/**
	 * Using any part of a machine uses the machine.
	 *
	 * Which is what a player means by it: they are right-clicking the pole, and the fact that the block
	 * their cursor is on is a cell rather than the machine's own base is an implementation detail they
	 * should never have to know. Without this, a pole opened its panel from the bottom metre only.
	 *
	 * Except for a wire, which is aimed at an insulator rather than at the machine. A block's use runs
	 * before the item in hand gets a look, so forwarding here would mean a pole answering every wire
	 * click with its own panel - and its insulators stand on the crossarms, which is to say behind these
	 * cells. So the cell stands aside and lets {@code ItemWire} have the click.
	 */
	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		BlockPos host = MachineShell.hostOf(level, pos);
		if (host == null || player.getItemInHand(hand).getItem() instanceof ItemWire) return InteractionResult.PASS;

		return level.getBlockState(host).use(level, player, hand,
				new BlockHitResult(hit.getLocation(), hit.getDirection(), host, hit.isInside()));
	}

	/** And mining any part of a machine mines the machine, for the same reason. */
	@Override
	public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
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
