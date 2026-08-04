package com.dooji.electricity.block;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ElectricLampBlock extends Block implements EntityBlock, MachineShell {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	public static final EnumProperty<LampState> GLOW_STATE = EnumProperty.create("glow_state", LampState.class);
	/**
	 * The luminaire as collision, cut from electric_lamp.obj.
	 *
	 * It used to be the whole block, because the lamp used to *be* the whole block - a cube with a picture
	 * of a light on all six faces. It is a post-top area light now: a base plate, a column, a glass bowl
	 * and the head over it, and the collision is those four things where they are drawn. Written by
	 * {@code tools/check_hitboxes.py --java}.
	 *
	 * A cell table rather than a plain shape only so that the checker can read it back the same way it
	 * reads every other machine's. Nothing here turns: a post-top luminaire is symmetric about its own
	 * column, which is why the block has no facing at all.
	 */
	private static final List<Cell> CELLS = List.of(
			new Cell(0, 0, 0, Shapes.or(Block.box(3.76, 10.98, 3.76, 12.24, 13.06, 12.24),
					Block.box(4.08, 10.14, 4.08, 11.92, 11.07, 11.92),
					Block.box(5.84, 0.00, 5.84, 10.16, 1.22, 10.16),
					Block.box(6.85, 1.20, 6.85, 9.15, 10.62, 9.15))));

	public ElectricLampBlock(Properties properties) {
		super(properties.lightLevel(state -> state.getValue(GLOW_STATE).getLightLevel()).sound(SoundType.GLASS));
		this.registerDefaultState(this.defaultBlockState().setValue(LIT, Boolean.FALSE).setValue(GLOW_STATE, LampState.OFF));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT, GLOW_STATE);
	}

	/**
	 * Nothing is drawn from the block model.
	 *
	 * The lamp is an OBJ machine now, drawn by {@code ElectricLampRenderer} like the rest of them, and
	 * its blockstate points at a model with no elements in it - which is what every other machine here
	 * does. The model is still loaded, because that is where the break particle's texture comes from.
	 */
	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
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
		return Direction.NORTH;
	}

	@Override
	public Direction shellAuthored() {
		return Direction.NORTH;
	}

	@Nullable @Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ElectricLampBlockEntity(pos, state);
	}

	@Nullable @Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide ? null : (lvl, pos, blockState, blockEntity) -> {
			if (blockEntity instanceof ElectricLampBlockEntity lamp) {
				lamp.serverTick();
			}
		};
	}

	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
		if (!level.isClientSide) {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (blockEntity instanceof ElectricLampBlockEntity lamp) {
				lamp.updateLampState();
			}
		}

		super.neighborChanged(state, level, pos, block, fromPos, isMoving);
	}

	@Override
	public boolean isSignalSource(BlockState state) {
		return false;
	}

	@Override
	public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return 0;
	}

	@Override
	public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return 0;
	}

	public enum LampState implements StringRepresentable {
		OFF("off", 0, false),
		DIM("dim", 5, true),
		WARM("warm", 10, true),
		BRIGHT("bright", 13, true),
		OVERDRIVE("overdrive", 15, true),
		BURNT("burnt", 0, false);

		private final String name;
		private final int lightLevel;
		private final boolean emits;

		LampState(String name, int lightLevel, boolean emits) {
			this.name = name;
			this.lightLevel = lightLevel;
			this.emits = emits;
		}

		public int getLightLevel() {
			return lightLevel;
		}

		public boolean isEmitting() {
			return emits;
		}

		@Override
		public String getSerializedName() {
			return name;
		}

		public static LampState fromName(String name) {
			for (LampState state : values()) {
				if (state.name.equals(name)) return state;
			}
			return OFF;
		}
	}
}
