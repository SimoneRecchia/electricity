package com.dooji.electricity.client.screen;

import com.dooji.electricity.api.Nameplate;
import com.dooji.electricity.block.PowerBoxBlockEntity;
import com.dooji.electricity.block.UtilityPoleBlockEntity;
import com.dooji.electricity.wire.InsulatorHost;
import java.util.ArrayList;
import java.util.List;
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

		lines.add(blockEntity.getBlockState().getBlock().getName());
		if (blockEntity instanceof InsulatorHost host) {
			lines.add(Component.translatable("tooltip.electricity.power.amount", Nameplate.fmt("%.1f", host.getCurrentPower())));
		} else {
			lines.add(Component.translatable("screen.electricity.power_info.no_power_data"));
		}

		// the kiosk is the one machine that also speaks the mods' own energy unit
		if (blockEntity instanceof PowerBoxBlockEntity powerBox) {
			lines.add(Component.translatable("tooltip.electricity.power.fe",
					powerBox.getForgeEnergyStored(), powerBox.getForgeTransferRate()));
		}

		if (blockEntity instanceof UtilityPoleBlockEntity pole) {
			lines.add(Component.translatable("screen.electricity.power_info.offsets",
					Nameplate.fmt("%.2f", pole.getOffsetX()),
					Nameplate.fmt("%.2f", pole.getOffsetY()),
					Nameplate.fmt("%.2f", pole.getOffsetZ())));
			lines.add(Component.translatable("screen.electricity.power_info.yaw_pitch",
					Nameplate.fmt("%.1f", Math.toDegrees(pole.getYaw())),
					Nameplate.fmt("%.1f", Math.toDegrees(pole.getPitch()))));
		}

		return lines;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
