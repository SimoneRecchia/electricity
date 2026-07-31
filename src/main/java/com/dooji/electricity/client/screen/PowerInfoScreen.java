package com.dooji.electricity.client.screen;

import com.dooji.electricity.block.UtilityPoleBlockEntity;
import com.dooji.electricity.client.render.obj.ObjRaycaster;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class PowerInfoScreen extends Screen {
	private final BlockPos targetPos;

	public PowerInfoScreen(BlockPos targetPos) {
		super(Component.translatable("screen.electricity.power_info.title"));
		this.targetPos = targetPos.immutable();
	}

	@Override
	public void tick() {
		super.tick();
		if (minecraft == null || minecraft.level == null || minecraft.level.getBlockEntity(targetPos) == null) {
			onClose();
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(guiGraphics);
		super.render(guiGraphics, mouseX, mouseY, partialTick);

		Font font = this.font;
		int centerX = this.width / 2;
		int y = this.height / 4;

		guiGraphics.drawCenteredString(font, title, centerX, y, 0xFFFFFF);
		y += font.lineHeight + 8;

		for (Component line : collectInfoLines()) {
			guiGraphics.drawCenteredString(font, line, centerX, y, 0xC6C6C6);
			y += font.lineHeight + 2;
		}
	}

	private List<Component> collectInfoLines() {
		List<Component> lines = new ArrayList<>();
		if (minecraft == null || minecraft.level == null) {
			lines.add(Component.translatable("screen.electricity.power_info.world_unavailable"));
			return lines;
		}

		var blockEntity = minecraft.level.getBlockEntity(targetPos);
		if (blockEntity == null) {
			lines.add(Component.translatable("screen.electricity.power_info.block_removed"));
			return lines;
		}

		List<Component> powerLines = ObjRaycaster.getPowerDisplayText(blockEntity);
		if (powerLines != null) {
			lines.addAll(powerLines);
		} else {
			lines.add(blockEntity.getBlockState().getBlock().getName());
			lines.add(Component.translatable("screen.electricity.power_info.no_power_data"));
		}

		if (blockEntity instanceof UtilityPoleBlockEntity pole) {
			lines.add(Component.translatable("screen.electricity.power_info.offsets",
					formatNumber("%.2f", pole.getOffsetX()),
					formatNumber("%.2f", pole.getOffsetY()),
					formatNumber("%.2f", pole.getOffsetZ())));
			lines.add(Component.translatable("screen.electricity.power_info.yaw_pitch",
					formatNumber("%.1f", Math.toDegrees(pole.getYaw())),
					formatNumber("%.1f", Math.toDegrees(pole.getPitch()))));
		}

		return lines;
	}

	private static String formatNumber(String pattern, Object... values) {
		return String.format(Locale.ROOT, pattern, values);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
