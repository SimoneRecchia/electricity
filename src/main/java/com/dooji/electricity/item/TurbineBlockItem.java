package com.dooji.electricity.item;

import com.dooji.electricity.api.Nameplate;
import com.dooji.electricity.api.power.TurbineSpec;
import com.dooji.electricity.block.TurbineTowerBlock;
import com.dooji.electricity.main.registry.TurbineCatalog;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** A turbine in the hand, showing its nameplate the way a datasheet header reads. */
public class TurbineBlockItem extends BlockItem {
	private final TurbineSpec spec;

	public TurbineBlockItem(Block block, Properties properties, TurbineSpec spec) {
		super(block, properties);
		this.spec = spec;
	}

	public TurbineSpec spec() {
		return spec;
	}

	/** Refuses to mount, and says why. */
	@Override
	public InteractionResult place(BlockPlaceContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		int segments = TurbineTowerBlock.countBelow(level, pos);

		if (!spec.acceptsTowerHeight(segments)) {
			Player player = context.getPlayer();
			if (player != null) {
				player.displayClientMessage(Component.translatable("message.electricity.turbine.tower_height",
						spec.displayName(), spec.minTowerSegments(), spec.maxTowerSegments(), segments).withStyle(ChatFormatting.RED), true);
			}

			return InteractionResult.FAIL;
		}

		return super.place(context);
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.literal(TurbineCatalog.fullName(spec)).withStyle(ChatFormatting.WHITE));

		if (!spec.iecClass().isEmpty()) {
			tooltip.add(Component.translatable("tooltip.electricity.turbine.class", spec.iecClass()).withStyle(ChatFormatting.DARK_GRAY));
		}

		tooltip.add(Component.translatable("tooltip.electricity.turbine.nameplate",
				Nameplate.rating(spec.ratedPowerKw()),
				Nameplate.fmt("%.0f", spec.rotorDiameterM())).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.electricity.turbine.wind",
				Nameplate.fmt("%.1f", spec.cutInSpeed()),
				Nameplate.fmt("%.1f", spec.ratedSpeed()),
				Nameplate.fmt("%.1f", spec.cutOutSpeed())).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.electricity.turbine.tower",
				spec.minTowerSegments(),
				spec.maxTowerSegments(),
				Nameplate.fmt("%.0f", spec.hubHeightM(spec.minTowerSegments())),
				Nameplate.fmt("%.0f", spec.hubHeightM(spec.maxTowerSegments()))).withStyle(ChatFormatting.DARK_GRAY));
	}


}
