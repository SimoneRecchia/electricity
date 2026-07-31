package com.dooji.electricity.item;

import com.dooji.electricity.api.power.TurbineSpec;
import com.dooji.electricity.main.registry.TurbineCatalog;
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

/**
 * A turbine in the hand, showing its nameplate the way a datasheet header reads.
 *
 * The figures come off the spec rather than out of a translation file, so a machine
 * added by a datapack describes itself without anyone writing lines for it, and the
 * numbers in the tooltip cannot drift away from the ones the turbine will actually
 * run at.
 */
public class TurbineBlockItem extends BlockItem {
	private final TurbineSpec spec;

	public TurbineBlockItem(Block block, Properties properties, TurbineSpec spec) {
		super(block, properties);
		this.spec = spec;
	}

	public TurbineSpec spec() {
		return spec;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.literal(TurbineCatalog.fullName(spec)).withStyle(ChatFormatting.WHITE));

		if (!spec.iecClass().isEmpty()) {
			tooltip.add(Component.translatable("tooltip.electricity.turbine.class", spec.iecClass()).withStyle(ChatFormatting.DARK_GRAY));
		}

		tooltip.add(Component.translatable("tooltip.electricity.turbine.nameplate",
				formatPower(spec.ratedPowerKw()),
				format("%.0f", spec.rotorDiameterM())).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.electricity.turbine.wind",
				format("%.1f", spec.cutInSpeed()),
				format("%.1f", spec.ratedSpeed()),
				format("%.1f", spec.cutOutSpeed())).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.electricity.turbine.tower",
				spec.minTowerSegments(),
				spec.maxTowerSegments(),
				format("%.0f", spec.hubHeightM(spec.minTowerSegments())),
				format("%.0f", spec.hubHeightM(spec.maxTowerSegments()))).withStyle(ChatFormatting.DARK_GRAY));
	}

	/**
	 * kW below a megawatt and MW above it, because a catalogue spanning 10 kW to 4 MW
	 * reads badly in either unit alone: "4000 kW" and "0.01 MW" are both harder to place
	 * at a glance than the unit an engineer would have used.
	 */
	public static String formatPower(double kw) {
		if (kw >= 1000.0) return format("%.1f MW", kw / 1000.0);

		return format("%.0f kW", kw);
	}

	private static String format(String pattern, Object... values) {
		return String.format(Locale.ROOT, pattern, values);
	}
}
