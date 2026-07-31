package com.dooji.electricity.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A block of photovoltaic modules, laid flat.
 *
 * Flat, and with no way to tilt it, because Minecraft's sun passes through the zenith: there
 * is no latitude and no season here, so horizontal is the optimum rather than a compromise.
 * The same fact is why the whole block is covered - a real array spaces its rows out so they
 * do not shade each other, and there is nothing to shade when the sun comes from overhead.
 *
 * It is three pixels tall, so it reads as a panel from across a field and can be walked over
 * without jumping, which is what a player laying out a farm of them wants.
 */
public class SolarPanelBlock extends Block implements EntityBlock {
	private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0);

	public SolarPanelBlock(Properties properties) {
		super(properties.sound(SoundType.GLASS));
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SolarPanelBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide ? null : (lvl, pos, blockState, blockEntity) -> {
			if (blockEntity instanceof SolarPanelBlockEntity panel) {
				panel.serverTick();
			}
		};
	}
}
