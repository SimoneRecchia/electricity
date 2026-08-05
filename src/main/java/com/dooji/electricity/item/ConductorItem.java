package com.dooji.electricity.item;

import com.dooji.electricity.api.power.ConductorSpec;
import com.dooji.electricity.client.hooks.WireClientHooks;
import com.dooji.electricity.main.Electricity;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** A reel of overhead line conductor: click one fitting, then another, and the span is strung between. */
public class ConductorItem extends Item {
	private final ConductorSpec spec;

	public ConductorItem(ConductorSpec spec) {
		super(new Properties().stacksTo(64));
		this.spec = spec;
	}

	public ConductorSpec spec() {
		return spec;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getLevel().isClientSide) {
			return DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> WireClientHooks.handleUseOn(context));
		}

		return Electricity.wireManager.handleWireUse(context);
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("tooltip.electricity." + spec.id().getPath())
				.withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.electricity.conductor.reach", spec.blocksPerItem(),
				(int) spec.maxSpan()).withStyle(ChatFormatting.DARK_GRAY));
	}
}
