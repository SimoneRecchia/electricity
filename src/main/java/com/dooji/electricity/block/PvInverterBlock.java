package com.dooji.electricity.block;

import com.dooji.electricity.api.power.CombinerSpec;
import com.dooji.electricity.api.power.DcCableSpec;
import com.dooji.electricity.api.power.InverterSpec;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.registry.CombinerCatalog;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
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
public class PvInverterBlock extends HorizontalDirectionalBlock implements EntityBlock, DcTerminal {
	/**
	 * A combiner box fitted inside the cabinet, giving it fused string terminals it did not have.
	 *
	 * A real option on a real product: a central inverter's direct-current section is a factory-fitted
	 * combiner, and the *virtual central* arrangement - boxes spread through the field, machines together
	 * at one end - is the other way of solving the same problem. So both are here, and this is the first.
	 *
	 * A block state rather than block entity data because a cable has to know whether the machine takes
	 * strings while a chunk is being meshed. Which box it is lives in the block entity, since that only
	 * decides how many strings, not whether any.
	 */
	public static final BooleanProperty COMBINER = BooleanProperty.create("combiner");

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
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(COMBINER, false));
	}

	public InverterSpec spec() {
		return spec;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, COMBINER);
	}

	/**
	 * Which gauge lands on this machine, off its own datasheet.
	 *
	 * The distinction a real catalogue makes and this one now makes too: a residential or commercial
	 * machine has plug connectors, so strings go straight in and a combiner's trunk has nowhere to go. A
	 * utility string machine has both. A central machine has bare busbars, so it takes a trunk and cannot
	 * take a string at all - which is why it needs combiner boxes rather than merely liking them.
	 */
	@Override
	public boolean acceptsCable(BlockState state, DcCableSpec cable) {
		if (cable.trunk()) return spec.trunkTerminals();

		return spec.stringTerminals() || state.getValue(COMBINER);
	}

	/**
	 * Whether a combiner box can be worked into this cabinet, and the state it takes when it is.
	 *
	 * Only a machine that has no fused string terminals of its own, which is the central one. Refusing it
	 * on the others is not pedantry: a string inverter's terminals *are* its fuses, so a box inside one
	 * would be a second set of fuses in series with the first, and no vendor sells that.
	 */
	@Nullable
	public BlockState withCombinerFitted(BlockState state) {
		if (spec.stringTerminals() || state.getValue(COMBINER)) return null;

		return state.setValue(COMBINER, true);
	}

	/** Whether a cabinet has a direct-current section in it, asked of the state so a cable can ask too. */
	public static boolean hasCombiner(BlockState state) {
		return state.getBlock() instanceof PvInverterBlock && state.getValue(COMBINER);
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

	/**
	 * Takes the cabinet out of the plant when it is actually broken.
	 *
	 * This and not the block entity's own removal, which also happens every time the chunk unloads: the
	 * plant reaches into other chunks here, and reaching out of an unloading chunk is what stops a world
	 * from ever finishing its save.
	 */
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && !level.isClientSide && level instanceof ServerLevel serverLevel) {
			if (level.getBlockEntity(pos) instanceof PvInverterBlockEntity inverter) {
				// the arrays, or they would go on believing they were wired to a cabinet that is no longer
				// there and would wait for an operating point that never comes
				inverter.releaseArrays();
				Electricity.wireManager.removeConnectionsForInsulators(serverLevel, inverter.getInsulatorIds());
				// and the box out of the cabinet, so rearranging a plant is not a tax on the parts
				CombinerSpec fitted = inverter.integratedCombiner();
				if (fitted != null) {
					popResource(level, pos, new ItemStack(Electricity.PV_COMBINER_ITEMS.get(fitted.id()).get()));
				}
			}
		}

		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
