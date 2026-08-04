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

/**
 * A reel of direct-current cable.
 *
 * Three things a player can do with it, in the order the click is tested:
 *
 * <ol>
 * <li><b>Plug it into a machine.</b> Right-clicking an array works a set of leads into it - that is
 *     what {@link DcTerminal#withCableFitted} answers - and until that is done the array's strings go
 *     nowhere, because a string with no leads on it is not connected to anything. The machine grows a
 *     junction box to show it.</li>
 * <li><b>Dig it in.</b> Sneak on the top of ground a shovel would move and the cable takes that
 *     block's place: the spoil comes back as an item, the run is drawn flush in its sand bedding, and
 *     it is walked over rather than tripped in. It also loses a fifth of its rating, because a trench
 *     is a worse place to shed heat than moving air.</li>
 * <li><b>Lay it on top.</b> Anything else, and it goes down like redstone.</li>
 * </ol>
 *
 * The order matters and this is the order it has to be. A click on an array has to be a plug rather
 * than a placement, or the one interaction a player will reach for most would put a cable on the roof
 * of the machine instead of into it.
 */
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

	/**
	 * Whether this click is a trench rather than a surface run.
	 *
	 * Ground a shovel would move, hit on its top face, while sneaking. Shovel-workable is the right
	 * test and not a list of block names: it is exactly the set of things a real crew digs a trench
	 * through with a machine rather than a breaker, and it comes with the datapack rather than being
	 * restated here.
	 */
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
			// the spoil, because a trench is dug rather than conjured - and in creative nothing is
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
