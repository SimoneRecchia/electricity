package com.dooji.electricity.item;

import com.dooji.electricity.api.power.PvArraySpec;
import com.dooji.electricity.api.power.PvModuleSpec;
import com.dooji.electricity.api.power.TrackerSpec;
import com.dooji.electricity.main.registry.PvCatalog;
import com.dooji.electricity.main.registry.PvModuleCatalog;
import com.dooji.electricity.main.registry.TrackerCatalog;
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

/** An array in the hand, reading like the header of a datasheet. */
public class PvArrayBlockItem extends BlockItem {
	private final PvArraySpec spec;

	public PvArrayBlockItem(Block block, Properties properties, PvArraySpec spec) {
		super(block, properties);
		this.spec = spec;
	}

	public PvArraySpec spec() {
		return spec;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		PvModuleSpec module = spec.module();

		tooltip.add(Component.literal(PvCatalog.fullName(spec)).withStyle(ChatFormatting.WHITE));
		tooltip.add(Component.translatable("tooltip.electricity.pv.mounting",
				Component.translatable("mounting.electricity." + spec.mounting().key())).withStyle(ChatFormatting.DARK_GRAY));

		tooltip.add(Component.translatable("tooltip.electricity.pv.nameplate",
				format("%.1f kW", spec.dcPowerKw()),
				spec.moduleCount(),
				PvModuleCatalog.fullName(module)).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.electricity.pv.module",
				module.cell().label(),
				format("%.0f W", module.ratedPowerW()),
				format("%.1f%%", module.efficiency() * 100.0),
				format("%.2f %%/K", module.powerCoefficient() * 100.0)).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.electricity.pv.laminate",
				format("%.0f", module.widthM() * 1000.0),
				format("%.0f", module.heightM() * 1000.0),
				format("%.0f", module.depthM() * 1000.0),
				format("%.1f", module.massKg())).withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("tooltip.electricity.pv.strings",
				spec.strings(),
				spec.modulesPerString(),
				format("%.0f V", spec.stringOpenCircuitVoltage(PvModuleSpec.STC_TEMPERATURE)),
				format("%.1f A", spec.stringCurrent(PvModuleSpec.STC_IRRADIANCE, PvModuleSpec.STC_TEMPERATURE))).withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("tooltip.electricity.pv.ground",
				format("%.0f%%", spec.groundCoverRatio() * 100.0),
				format("%.0f", spec.moduleAreaM2())).withStyle(ChatFormatting.DARK_GRAY));

		if (module.bifacial()) {
			tooltip.add(Component.translatable("tooltip.electricity.pv.bifacial",
					format("%.0f%%", module.bifaciality() * 100.0)).withStyle(ChatFormatting.DARK_GRAY));
		}

		TrackerSpec tracker = spec.tracker();
		if (tracker != null) {
			tooltip.add(Component.translatable("tooltip.electricity.pv.tracker",
					TrackerCatalog.fullName(tracker),
					tracker.axes(),
					format("%.0f", tracker.rotationLimitDeg()),
					format("%.0f", tracker.lowestTrackableSunDeg())).withStyle(ChatFormatting.DARK_GRAY));
		}

		tooltip.add(Component.translatable("tooltip.electricity.pv.needs_inverter").withStyle(ChatFormatting.DARK_GRAY));
	}

	private static String format(String pattern, Object... values) {
		return String.format(Locale.ROOT, pattern, values);
	}
}
