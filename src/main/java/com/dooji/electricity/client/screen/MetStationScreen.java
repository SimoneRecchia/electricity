package com.dooji.electricity.client.screen;

import com.dooji.electricity.api.power.SensorSpec;
import com.dooji.electricity.block.MetStationBlockEntity;
import com.dooji.electricity.main.registry.SensorCatalog;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The met mast's panel: nine instrument readings and what is reading them.
 *
 * <h2>Why it is laid out as a list rather than as gauges</h2>
 *
 * Because a met display is a list. There is nothing to control here and nothing with a nameplate to be
 * measured against - a pyranometer reading 640 W/m2 is not 58% of anything - so bars would be inventing a
 * scale to put a number on. What an operator does with a mast is read two numbers next to each other and
 * notice that they disagree, which wants columns.
 *
 * The pairs are deliberate and each one is a check on something:
 *
 * <ul>
 * <li><b>Global against plane-of-array</b> - the same sky through two mountings. Equal when the reference
 *     array is lying flat, and the gap is what its tilt or its tracker is worth.</li>
 * <li><b>Plane-of-array against the reference cell</b> - the same plane through two instruments. The cell
 *     reads lower whenever the light is red, because it only sees what the modules see.</li>
 * <li><b>Diffuse fraction against clearness index</b> - two ways of saying how much sky there is. One near
 *     1 and the other near 0.2 is a heavy overcast; the reverse is impossible and would mean a fault.</li>
 * <li><b>Snow distance against snow height</b> - what the gauge measures and what was worked out from it.
 *     A distance that stops matching the height is how a real plant finds out the transducer has iced up.</li>
 * </ul>
 */
public class MetStationScreen extends PlantScreen {
	private static final ResourceLocation TEXTURE = new ResourceLocation("electricity", "textures/gui/met_station.png");
	private static final int WIDTH = 288;
	private static final int HEIGHT = 208;

	private static final int FIRST_SEPARATOR_Y = 20;
	private static final int SECOND_SEPARATOR_Y = 176;

	/**
	 * The two columns, and where each one's value is right-aligned to.
	 *
	 * A hundred and thirty each, which is not a round number chosen for looks: it is what the widest
	 * label and the widest value in each column actually measure, plus a gap. At ninety-eight the labels
	 * drew straight through their own values - "Plane of array" and a four-digit irradiance came out as
	 * "Plane of arr@yW/m²" - and nothing reported it, because a collision between two strings is a
	 * property of the font rather than of the code. {@code tools/check_gui_fits.py} measures the real
	 * glyphs against these numbers now.
	 */
	private static final int LEFT_LABEL_X = 10;
	private static final int LEFT_VALUE_X = 140;
	private static final int RIGHT_LABEL_X = 148;
	private static final int RIGHT_VALUE_X = 278;
	private static final int FIRST_ROW_Y = 26;
	private static final int ROW_HEIGHT = 11;

	public MetStationScreen(BlockPos targetPos) {
		super(Component.translatable("screen.electricity.met_station.title"), TEXTURE, WIDTH, HEIGHT, targetPos);
	}

	@Override
	protected void drawPanel(GuiGraphics graphics) {
		MetStationBlockEntity station = station();
		if (station == null) return;

		graphics.drawString(font, Component.translatable("screen.electricity.met_station.title"), leftPos + MARGIN, topPos + 6, VALUE_COLOUR, false);
		String count = Component.translatable("screen.electricity.met_station.instruments", SensorCatalog.all().size()).getString();
		graphics.drawString(font, count, leftPos + WIDTH - MARGIN - font.width(count), topPos + 6, FAINT_COLOUR, false);
		separator(graphics, FIRST_SEPARATOR_Y);

		int row = 0;
		pair(graphics, row++, "global", fmt("%.0f W/m²", station.globalIrradiance()), "plane", fmt("%.0f W/m²", station.planeIrradiance()));
		pair(graphics, row++, "diffuse", fmt("%.0f W/m²", station.diffuseIrradiance()), "reference_cell", fmt("%.0f W/m²", station.referenceCell()));
		pair(graphics, row++, "diffuse_fraction", fmt("%.2f", station.diffuseFraction()), "clearness",
				fmt("%.2f", station.getTelemetry().number("clearnessIndex")));
		pair(graphics, row++, "albedo", fmt("%.2f", station.albedo()), "sun",
				fmt("%.0f° at %.0f°", station.getTelemetry().number("sunElevation"), station.getTelemetry().number("sunAzimuth")));
		pair(graphics, row++, "air_temp", fmt("%.1f °C", station.ambientTempC()), "module_temp", fmt("%.1f °C", station.moduleTempC()));
		pair(graphics, row++, "wind_speed", fmt("%.1f m/s", station.windSpeed()), "wind_dir", fmt("%.0f°", station.windDirection()));
		pair(graphics, row++, "snow_distance", fmt("%.2f m", station.snowDistanceM()), "snow_height", fmt("%.2f m", station.snowHeightM()));

		drawInstrumentList(graphics, row);
		drawReference(graphics, station);
	}

	/** One row: a label and a value in each column. */
	private void pair(GuiGraphics graphics, int row, String leftKey, String leftValue, String rightKey, String rightValue) {
		int y = topPos + FIRST_ROW_Y + row * ROW_HEIGHT;
		Component left = Component.translatable("screen.electricity.met_station." + leftKey);
		Component right = Component.translatable("screen.electricity.met_station." + rightKey);

		graphics.drawString(font, left, leftPos + LEFT_LABEL_X, y, LABEL_COLOUR, false);
		graphics.drawString(font, leftValue, leftPos + LEFT_VALUE_X - font.width(leftValue), y, VALUE_COLOUR, false);
		graphics.drawString(font, right, leftPos + RIGHT_LABEL_X, y, LABEL_COLOUR, false);
		graphics.drawString(font, rightValue, leftPos + RIGHT_VALUE_X - font.width(rightValue), y, VALUE_COLOUR, false);
	}

	/**
	 * What is doing the reading, with each instrument's class beside it.
	 *
	 * The class is not decoration: a Class A pyranometer is what a utility power purchase agreement demands
	 * and a Class C one is what a rooftop gets, and a performance test written against the wrong one is not
	 * worth having. Showing it here is the same instinct as printing the IEC class on a turbine's panel.
	 */
	private void drawInstrumentList(GuiGraphics graphics, int firstRow) {
		int y = topPos + FIRST_ROW_Y + firstRow * ROW_HEIGHT + 4;
		graphics.drawString(font, Component.translatable("screen.electricity.met_station.fitted"), leftPos + LEFT_LABEL_X, y, LABEL_COLOUR, false);

		int row = 0;
		for (SensorSpec spec : SensorCatalog.all()) {
			int columnX = row % 2 == 0 ? LEFT_LABEL_X : RIGHT_LABEL_X;
			int rowY = y + 11 + (row / 2) * 10;
			String text = SensorCatalog.fullName(spec) + (spec.instrument().onArray() ? " *" : "");
			graphics.drawString(font, text, leftPos + columnX, rowY, FAINT_COLOUR, false);
			row++;
		}
	}

	/** Which array the two on-array instruments are fitted to, and the footnote saying which those are. */
	private void drawReference(GuiGraphics graphics, MetStationBlockEntity station) {
		separator(graphics, SECOND_SEPARATOR_Y);

		BlockPos reference = station.referenceArray();
		Component text = reference == null
				? Component.translatable("screen.electricity.met_station.no_reference")
				: Component.translatable("screen.electricity.met_station.reference", reference.getX(), reference.getY(), reference.getZ());

		// wrapped rather than trusted to fit: one of these two is a sentence and the other is three
		// coordinates, and neither has any business knowing how wide the panel is
		int y = topPos + SECOND_SEPARATOR_Y + 5;
		for (var line : font.split(text, WIDTH - 2 * MARGIN)) {
			graphics.drawString(font, line, leftPos + MARGIN, y, FAINT_COLOUR, false);
			y += 10;
		}
	}

	private MetStationBlockEntity station() {
		if (minecraft == null || minecraft.level == null) return null;
		if (minecraft.level.getBlockEntity(targetPos) instanceof MetStationBlockEntity station) return station;

		return null;
	}

	@Override
	protected BlockEntity blockEntity() {
		return station();
	}
}
