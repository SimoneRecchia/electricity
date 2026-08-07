package com.dooji.electricity.item;

import com.dooji.electricity.api.power.DcCableSpec;
import com.dooji.electricity.block.DcCableBlock;
import com.dooji.electricity.block.DcTerminal;
import com.dooji.electricity.main.registry.CableCatalog;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/** A reel of direct-current cable. */
public class DcCableItem extends BlockItem {
	private final DcCableSpec spec;

	public DcCableItem(DcCableBlock block, Properties properties) {
		super(block, properties);
		this.spec = block.spec();
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos clicked = context.getClickedPos();
		BlockState target = level.getBlockState(clicked);

		if (target.getBlock() instanceof DcTerminal terminal) {
			BlockState fitted = terminal.withCableFitted(target, spec);
			if (fitted != null) return fit(context, clicked, fitted);
		}

		if (trenchable(context, target)) return trench(context, clicked, target);

		return super.useOn(context);
	}

	/** Works the leads into the machine that was clicked. */
	private InteractionResult fit(UseOnContext context, BlockPos pos, BlockState fitted) {
		Level level = context.getLevel();
		if (!level.isClientSide) {
			level.setBlock(pos, fitted, Block.UPDATE_ALL);
			level.playSound(null, pos, SoundType.WOOL.getPlaceSound(), SoundSource.BLOCKS, 0.8f, 1.1f);
			consume(context.getPlayer(), context.getItemInHand());
		}

		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	/** Whether this click is a trench rather than a surface run. */
	private boolean trenchable(UseOnContext context, BlockState target) {
		Player player = context.getPlayer();
		return player != null && player.isSecondaryUseActive()
				&& context.getClickedFace() == Direction.UP
				&& target.is(BlockTags.MINEABLE_WITH_SHOVEL);
	}

	/** Digs the cable in where the ground was, and hands the spoil back. */
	private InteractionResult trench(UseOnContext context, BlockPos pos, BlockState ground) {
		Level level = context.getLevel();
		if (!level.isClientSide) {
			Player player = context.getPlayer();
			// the spoil, because a trench is dug rather than conjured
			// carried either way
			if (player == null || !player.isCreative()) Block.dropResources(ground, level, pos);

			DcCableBlock cable = (DcCableBlock) getBlock();
			BlockState laid = cable.connected(cable.defaultBlockState().setValue(DcCableBlock.BURIED, true), level, pos);
			level.setBlock(pos, laid, Block.UPDATE_ALL);
			level.playSound(null, pos, SoundType.GRAVEL.getPlaceSound(), SoundSource.BLOCKS, 0.9f, 0.9f);
			consume(player, context.getItemInHand());
		}

		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	private static void consume(@Nullable Player player, ItemStack stack) {
		if (player == null || !player.isCreative()) stack.shrink(1);
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.literal(CableCatalog.fullName(spec)).withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.translatable("tooltip.electricity.dc_cable.rating",
				fmt(spec.crossSectionMm2()), fmt(spec.freeAirAmps()), fmt(spec.maxSystemVolts()))
				.withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.electricity.dc_cable.trench",
				fmt(spec.ampacity(1.0))).withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable(spec.trunk()
				? "tooltip.electricity.dc_cable.trunk"
				: "tooltip.electricity.dc_cable.string").withStyle(ChatFormatting.DARK_GRAY));
	}

	private static String fmt(double value) {
		return value == Math.floor(value) ? String.valueOf((long) value) : String.format("%.1f", value);
	}
}
