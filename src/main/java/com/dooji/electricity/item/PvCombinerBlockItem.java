package com.dooji.electricity.item;

import com.dooji.electricity.api.power.CombinerSpec;
import com.dooji.electricity.block.PvCombinerBlock;
import com.dooji.electricity.block.PvInverterBlock;
import com.dooji.electricity.block.PvInverterBlockEntity;
import com.dooji.electricity.main.registry.CombinerCatalog;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A combiner box, which goes on a post in the field or inside a cabinet. */
public class PvCombinerBlockItem extends BlockItem {
	private final CombinerSpec spec;

	public PvCombinerBlockItem(PvCombinerBlock block, Properties properties) {
		super(block, properties);
		this.spec = block.spec();
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos clicked = context.getClickedPos();
		BlockState target = level.getBlockState(clicked);

		if (target.getBlock() instanceof PvInverterBlock cabinet) {
			BlockState fitted = cabinet.withCombinerFitted(target);
			if (fitted != null) return fit(context, clicked, fitted);

			// said out loud, because a click that does nothing is the worst answer available
			return refuse(context, cabinet.spec().stringTerminals()
					? "message.electricity.combiner.already_fused"
					: "message.electricity.combiner.already_fitted");
		}

		return super.useOn(context);
	}

	private InteractionResult fit(UseOnContext context, BlockPos pos, BlockState fitted) {
		Level level = context.getLevel();
		if (!level.isClientSide) {
			level.setBlock(pos, fitted, Block.UPDATE_ALL);
			// the block entity survives a state change on the same block
			// recorded straight after: the state says there is one and this says which
			if (level.getBlockEntity(pos) instanceof PvInverterBlockEntity inverter) {
				inverter.fitCombiner(spec);
			}

			level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.4f);
			Player player = context.getPlayer();
			if (player == null || !player.isCreative()) context.getItemInHand().shrink(1);
		}

		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	private static InteractionResult refuse(UseOnContext context, String key) {
		Player player = context.getPlayer();
		if (player != null && !context.getLevel().isClientSide) {
			player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
		}

		return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.literal(CombinerCatalog.fullName(spec)).withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.translatable("tooltip.electricity.combiner.ways",
				spec.fusedInputs(), fmt(spec.fuseAmps()), fmt(spec.maxSystemVolts())).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.electricity.combiner.output",
				fmt(spec.outputAmps()), spec.ingressProtection()).withStyle(ChatFormatting.GRAY));
		if (spec.stringMonitoring()) {
			tooltip.add(Component.translatable("tooltip.electricity.combiner.monitoring").withStyle(ChatFormatting.DARK_GRAY));
		}

		tooltip.add(Component.translatable("tooltip.electricity.combiner.use").withStyle(ChatFormatting.DARK_GRAY));
	}

	private static String fmt(double value) {
		return value == Math.floor(value) ? String.valueOf((long) value) : String.format("%.1f", value);
	}
}
