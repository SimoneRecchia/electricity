package com.dooji.electricity.client.screen;

import com.dooji.electricity.api.Nameplate;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * What every machine's control panel in this mod has in common, over the machine it is open on.
 *
 * The type parameter is there so a panel says which machine it belongs to once: every one of them used to
 * carry the same three-line instanceof lookup and then hand it back through an abstract accessor.
 */
public abstract class PlantScreen<T extends BlockEntity> extends Screen {
	/** Side of the sheet the panel textures are drawn into. */
	protected static final int PANEL_SHEET = 512;

	protected static final int MARGIN = 8;
	/** Left edge of the bar wells, matching what the texture generator cuts. */
	protected static final int BAR_X = 12;
	protected static final int BAR_HEIGHT = 10;

	protected static final int LABEL_COLOUR = 0x404040;
	protected static final int VALUE_COLOUR = 0x202020;
	protected static final int FAINT_COLOUR = 0x707070;
	protected static final int SEPARATOR_COLOUR = 0xFF8B8B8B;

	/** At nameplate, or nothing wrong. */
	protected static final int GREEN = 0xFF4CAF50;
	/** Somebody or something is holding it back on purpose. */
	protected static final int AMBER = 0xFFCE9B18;
	/** Not producing, and not by choice. */
	protected static final int RED = 0xFFB33A2E;
	/** Working, below nameplate. */
	protected static final int BLUE = 0xFF3E7CB1;
	/** What it could have made: drawn behind the bar so a loss reads as a gap. */
	protected static final int GHOST = 0xFF6E6E6E;
	/** The sky's own share of the light, against the beam's. */
	protected static final int VIOLET = 0xFF7E6BA8;

	private final ResourceLocation texture;
	private final Class<T> machineType;
	protected final int imageWidth;
	protected final int imageHeight;
	/**
	 * Total pixels a bar spans.
	  *
	 * Named apart from {@link #barWidth(double)} so the two cannot be misread.
	 */
	protected final int barSpan;
	protected final BlockPos targetPos;

	protected int leftPos;
	protected int topPos;

	protected PlantScreen(Component title, ResourceLocation texture, int imageWidth, int imageHeight, BlockPos targetPos,
			Class<T> machineType) {
		super(title);
		this.texture = texture;
		this.machineType = machineType;
		this.imageWidth = imageWidth;
		this.imageHeight = imageHeight;
		// the wells are inset twelve pixels either side, so the bar's width follows the panel's rather
		// than being a second number that could disagree with it
		this.barSpan = imageWidth - 2 * BAR_X;
		this.targetPos = targetPos.immutable();
	}

	@Override
	protected void init() {
		leftPos = (width - imageWidth) / 2;
		topPos = (height - imageHeight) / 2;
	}

	/** The machine this panel is open on, or null if it has gone. */
	@Nullable
	protected final T machine() {
		if (minecraft == null || minecraft.level == null) return null;

		BlockEntity entity = minecraft.level.getBlockEntity(targetPos);
		return machineType.isInstance(entity) ? machineType.cast(entity) : null;
	}

	/** Closes the panel when the machine it belongs to stops existing. */
	@Override
	public void tick() {
		super.tick();
		if (machine() == null) {
			onClose();
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);
		graphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, imageHeight, PANEL_SHEET, PANEL_SHEET);

		if (machine() != null) {
			drawPanel(graphics);
		}

		super.render(graphics, mouseX, mouseY, partialTick);
	}

	/** Everything inside the panel. */
	protected abstract void drawPanel(GuiGraphics graphics);

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---- the shared vocabulary ----

	/** A label on the left of a row. */
	protected void label(GuiGraphics graphics, Component text, int y) {
		graphics.drawString(font, text, leftPos + MARGIN, topPos + y, LABEL_COLOUR, false);
	}

	/** A value on the right of a row, right-aligned against the panel's margin. */
	protected void value(GuiGraphics graphics, String text, int y) {
		graphics.drawString(font, text, leftPos + imageWidth - MARGIN - font.width(text), topPos + y, VALUE_COLOUR, false);
	}

	protected void faint(GuiGraphics graphics, Component text, int y) {
		graphics.drawString(font, text, leftPos + MARGIN, topPos + y, FAINT_COLOUR, false);
	}

	protected void faintValue(GuiGraphics graphics, String text, int y) {
		graphics.drawString(font, text, leftPos + imageWidth - MARGIN - font.width(text), topPos + y, FAINT_COLOUR, false);
	}

	protected void separator(GuiGraphics graphics, int y) {
		graphics.fill(leftPos + 11, topPos + y, leftPos + imageWidth - 11, topPos + y + 1, SEPARATOR_COLOUR);
	}

	/** The state of the machine: a coloured square and a line saying why. */
	protected void state(GuiGraphics graphics, Component text, int colour, int y) {
		graphics.fill(leftPos + MARGIN, topPos + y + 1, leftPos + MARGIN + 4, topPos + y + 5, colour);
		graphics.drawString(font, text, leftPos + MARGIN + 8, topPos + y, VALUE_COLOUR, false);
	}

	/** Pixels a fraction of a bar comes to. */
	protected int barWidth(double fraction) {
		return (int) Math.round(Mth.clamp(fraction, 0.0, 1.0) * barSpan);
	}

	protected void fillBar(GuiGraphics graphics, int y, int width, int colour) {
		if (width <= 0) return;

		graphics.fill(leftPos + BAR_X, topPos + y, leftPos + BAR_X + width, topPos + y + BAR_HEIGHT, colour);
	}

	/** A band across part of a bar, for a gauge whose regions mean different things. */
	protected void zone(GuiGraphics graphics, int y, int from, int to, int colour) {
		if (to <= from) return;

		graphics.fill(leftPos + BAR_X + from, topPos + y, leftPos + BAR_X + to, topPos + y + BAR_HEIGHT, colour);
	}

	/** A two-pixel mark standing across a gauge, clamped so it stays inside the bar. */
	protected void needle(GuiGraphics graphics, int y, int at, int colour) {
		graphics.fill(leftPos + BAR_X + Mth.clamp(at - 1, 0, barSpan - 2), topPos + y - 2,
				leftPos + BAR_X + Mth.clamp(at + 1, 2, barSpan), topPos + y + BAR_HEIGHT + 2, colour);
	}

	/** A one-pixel notch inside a bar: a setpoint, a threshold, a limit. */
	protected void notch(GuiGraphics graphics, int y, int at, int colour) {
		if (at >= barSpan) return;

		graphics.fill(leftPos + BAR_X + at, topPos + y - 1, leftPos + BAR_X + at + 1, topPos + y + BAR_HEIGHT + 1, colour);
	}

	/** A bar built from parts that add up, drawn left to right. */
	protected void stackedBar(GuiGraphics graphics, int y, double[] fractions, int[] colours) {
		int cursor = 0;
		for (int i = 0; i < fractions.length && i < colours.length; i++) {
			int width = barWidth(fractions[i]);
			if (width <= 0) continue;

			int end = Math.min(barSpan, cursor + width);
			zone(graphics, y, cursor, end, colours[i]);
			cursor = end;
		}
	}

	// Inherited so panel code reads as panel code; the formatting itself is Nameplate's, shared with the labels
	protected static String fmt(String pattern, Object... values) {
		return Nameplate.fmt(pattern, values);
	}

	protected static String power(double kw) {
		return Nameplate.reading(kw);
	}

	protected static String energy(double kwh) {
		return Nameplate.energy(kwh);
	}
}
