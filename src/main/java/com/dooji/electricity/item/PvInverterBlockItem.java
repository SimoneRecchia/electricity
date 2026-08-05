package com.dooji.electricity.item;

import com.dooji.electricity.api.power.InverterSpec;
import com.dooji.electricity.main.registry.InverterCatalog;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** An inverter in the hand, reading like the front page of its datasheet. */
public class PvInverterBlockItem extends BlockItem {
	private final InverterSpec spec;

	public PvInverterBlockItem(Block block, Properties properties, InverterSpec spec) {
		super(block, properties);
		this.spec = spec;
	}

	public InverterSpec spec() {
		return spec;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.literal(InverterCatalog.fullName(spec)).withStyle(ChatFormatting.WHITE));

		tooltip.add(Component.translatable("tooltip.electricity.inverter.nameplate",
				TurbineBlockItem.formatPower(spec.acPowerKw()),
				format("%.0f kVA", spec.apparentPowerKva())).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.electricity.inverter.dc",
				TurbineBlockItem.formatPower(spec.maxDcPowerKw()),
				format("%.2f", spec.nominalDcAcRatio())).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.electricity.inverter.mppt",
				spec.mpptCount(),
				format("%.0f", spec.mpptMinVolts()),
				format("%.0f", spec.mpptMaxVolts()),
				format("%.0f", spec.startupVolts())).withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("tooltip.electricity.inverter.efficiency",
				format("%.1f%%", spec.peakEfficiency() * 100.0),
				format("%.1f%%", spec.europeanEfficiency() * 100.0)).withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("tooltip.electricity.inverter.thermal",
				format("%.0f", spec.derateOnsetC()),
				Component.translatable("cooling.electricity." + spec.cooling().key()),
				format("%.0f W", spec.nightWatts())).withStyle(ChatFormatting.DARK_GRAY));
	}

	private static String format(String pattern, Object... values) {
		return String.format(Locale.ROOT, pattern, values);
	}
}
