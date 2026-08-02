package com.dooji.electricity.block;

import com.dooji.electricity.api.power.PvArraySpec;
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
 * One block of photovoltaic array, on whichever mounting its product came with.
 *
 * The block carries the {@link PvArraySpec} the way {@link WindTurbineBlock} carries a turbine's:
 * one block per catalogue entry, so a spec cannot exist without something that places it, and a
 * placed array cannot disagree with the item that placed it because there is nothing saved to drift.
 *
 * <h2>Why the height differs per mounting</h2>
 *
 * Because the mountings really are different heights, and a player has to be able to tell them apart
 * from across a field. A flat table is ankle high and walked over. A tilted rack stands about half a
 * block. A tracker's torque tube is carried on piers, so it is higher again and there is room to walk
 * underneath - which is the real reason trackers are built that way, since a row that has to rotate
 * through sixty degrees needs the clearance. A dual-axis pedestal is the tallest of the four.
 *
 * The collision is a single conservative box in every case, including the tracked ones. A rotating
 * collision shape would be correct and would also mean a player standing on a tracker at dawn is
 * inside it by mid-morning, which is worse than slightly wrong.
 *
 * <h2>Orientation</h2>
 *
 * A tilted rack faces the way it was placed, and that decides which way it tips - so it is a real
 * choice, unlike on Earth where a fixed rack faces the equator and there is nothing to decide. A
 * tracker's axis has to run north-south, because that is the only orientation from which it can
 * follow a sun that travels east to west, so a tracker snaps to that whatever direction the player
 * was looking.
 */
public class PvArrayBlock extends HorizontalDirectionalBlock implements EntityBlock {
	/** Ankle high: a flat table is walked over rather than round. */
	private static final VoxelShape FLAT_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0);
	/** A tilted rack, low edge on the ground and high edge about half a block up. */
	private static final VoxelShape TILT_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0);
	/** A torque tube on piers, with the rotation envelope above it. */
	private static final VoxelShape TRACKER_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 12.0, 16.0);
	/** A pedestal frame, which is the tallest thing in the catalogue that is not a turbine. */
	private static final VoxelShape PEDESTAL_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 14.0, 16.0);

	private final PvArraySpec spec;

	public PvArrayBlock(Properties properties, PvArraySpec spec) {
		super(properties.sound(SoundType.GLASS));
		this.spec = spec;
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	public PvArraySpec spec() {
		return spec;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// a tracker's axis is north-south or it cannot track at all, so it takes that orientation
		// however the player was standing. Everything else faces them, which for a tilted rack is
		// the direction it tips
		Direction facing = spec.tracked() ? Direction.NORTH : context.getHorizontalDirection().getOpposite();
		return defaultBlockState().setValue(FACING, facing);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (spec.mounting()) {
			case FLAT -> FLAT_SHAPE;
			case FIXED_TILT -> TILT_SHAPE;
			case SINGLE_AXIS -> TRACKER_SHAPE;
			case DUAL_AXIS -> PEDESTAL_SHAPE;
		};
	}

	/**
	 * Which way the module plane faces, on the mod's compass: 0 east, 90 south, 180 west, 270 north.
	 *
	 * The same convention the sun and the wind use, so an incidence angle can be worked out without
	 * converting between two ideas of north. A flat plane's bearing means nothing - it is looking
	 * straight up - and it is answered anyway, because the transposition arithmetic multiplies it by
	 * the sine of a zero tilt and does not care.
	 */
	public static double planeAzimuthDeg(Direction facing) {
		return switch (facing) {
			case EAST -> 0.0;
			case SOUTH -> 90.0;
			case WEST -> 180.0;
			default -> 270.0;
		};
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PvArrayBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide ? (lvl, pos, blockState, blockEntity) -> {
			if (blockEntity instanceof PvArrayBlockEntity array) {
				array.clientTick();
			}
		} : (lvl, pos, blockState, blockEntity) -> {
			if (blockEntity instanceof PvArrayBlockEntity array) {
				array.serverTick();
			}
		};
	}
}
