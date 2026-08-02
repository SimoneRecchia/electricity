package com.dooji.electricity.block;

import com.dooji.electricity.api.power.InverterSpec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.shapes.CollisionContext;
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
public class PvInverterBlock extends HorizontalDirectionalBlock implements EntityBlock {
	/** A wall-mounted residential machine: shallow, and not much taller than it is wide. */
	private static final VoxelShape SMALL_SHAPE = Block.box(2.0, 0.0, 4.0, 14.0, 12.0, 12.0);
	/** A commercial cabinet standing on the ground. */
	private static final VoxelShape MEDIUM_SHAPE = Block.box(1.0, 0.0, 3.0, 15.0, 16.0, 13.0);
	/** A central inverter, which is a container rather than a cabinet. */
	private static final VoxelShape LARGE_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);

	/** Nameplate above which a machine is drawn as a container rather than a cabinet, in kW. */
	private static final double CONTAINER_KW = 1000.0;
	/** Nameplate below which it hangs on a wall, in kW. */
	private static final double WALL_KW = 30.0;

	private final InverterSpec spec;

	public PvInverterBlock(Properties properties, InverterSpec spec) {
		super(properties.sound(SoundType.METAL));
		this.spec = spec;
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	public InverterSpec spec() {
		return spec;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (spec.acPowerKw() >= CONTAINER_KW) return LARGE_SHAPE;
		if (spec.acPowerKw() <= WALL_KW) return SMALL_SHAPE;

		return MEDIUM_SHAPE;
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
}
