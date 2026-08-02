package com.dooji.electricity.client.screen;

import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * What every machine's control panel in this mod has in common.
 *
 * Four panels now - a turbine, an array, an inverter and a met mast - and they have to read alike or a
 * player has to learn each one separately. A shared base is how that is guaranteed rather than
 * remembered: the same bevel, the same bar geometry, the same six colours meaning the same six things,
 * the same left-label-right-value rhythm, and the same behaviour when the machine the panel is open on
 * is broken while somebody is looking at it.
 *
 * The bar coordinates are the ones cut into the background textures by
 * {@code tools/gen_pv_textures.py}, and the well positions are that script's declared layout. If a bar
 * moves in one place it has to move in the other, which is what the comment at the top of that file is
 * there to say.
 *
 * <h2>What a panel is for</h2>
 *
 * Deliberately not everything. These machines report between fifteen and ninety-six signals through
 * their peripherals and almost none of them belong here: standing at a machine you want to know what it
 * is, what it is doing right now, and why it is not doing more. An insulation resistance is a question
 * for a monitor watching a whole site.
 */
public abstract class PlantScreen extends Screen {
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
	protected final int imageWidth;
	protected final int imageHeight;
	/** Total pixels a bar spans. Named apart from {@link #barWidth(double)} so the two cannot be misread. */
	protected final int barSpan;
	protected final BlockPos targetPos;

	protected int leftPos;
	protected int topPos;

	protected PlantScreen(Component title, ResourceLocation texture, int imageWidth, int imageHeight, BlockPos targetPos) {
		super(title);
		this.texture = texture;
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

	/**
	 * Closes the panel when the machine it belongs to stops existing.
	 *
	 * A player can break the block they are standing at, and a panel left open on a hole would draw
	 * whatever it last read for ever.
	 */
	@Override
	public void tick() {
		super.tick();
		if (blockEntity() == null) {
			onClose();
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);
		graphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, imageHeight);

		if (blockEntity() != null) {
			drawPanel(graphics);
		}

		super.render(graphics, mouseX, mouseY, partialTick);
	}

	/** Everything inside the panel. Only called when the machine is still there. */
	protected abstract void drawPanel(GuiGraphics graphics);

	/** The machine this panel is open on, or null if it has gone. */
	protected abstract BlockEntity blockEntity();

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

	/**
	 * The state of the machine: a coloured square and a line saying why.
	 *
	 * The reason matters more than the fact, on every machine in this mod. A plant sitting at zero output
	 * is the one thing a player cannot diagnose from outside, and there are half a dozen different causes
	 * which look identical from the ground.
	 */
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

	/**
	 * A bar built from parts that add up, drawn left to right.
	 *
	 * What the plane-of-array gauge wants: the beam, the sky and the ground stacked in one bar, because
	 * the interesting thing about them is their proportion and reading three numbers to work that out is
	 * exactly what a chart is for.
	 */
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

	protected static String fmt(String pattern, Object... values) {
		return String.format(Locale.ROOT, pattern, values);
	}

	/**
	 * Power in the unit an engineer would have used: kW under a megawatt and MW above it.
	 *
	 * A catalogue spanning 10 kW to 4 MW reads badly in either unit alone - "4000 kW" and "0.01 MW" are
	 * both harder to place at a glance.
	 */
	protected static String power(double kw) {
		if (Math.abs(kw) >= 1000.0) return fmt("%.2f MW", kw / 1000.0);

		return fmt("%.1f kW", kw);
	}

	/** Energy the same way: kWh below a megawatt hour and MWh above. */
	protected static String energy(double kwh) {
		if (kwh >= 1000.0) return fmt("%.2f MWh", kwh / 1000.0);

		return fmt("%.1f kWh", kwh);
	}
}
