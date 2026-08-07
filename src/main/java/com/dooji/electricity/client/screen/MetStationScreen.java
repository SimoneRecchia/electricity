package com.dooji.electricity.client.screen;

import com.dooji.electricity.api.power.SensorSpec;
import com.dooji.electricity.block.MetStationBlockEntity;
import com.dooji.electricity.main.registry.SensorCatalog;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** The met mast's panel: nine instrument readings and what is reading them. */
public class MetStationScreen extends PlantScreen<MetStationBlockEntity> {
	private static final ResourceLocation TEXTURE = new ResourceLocation("electricity", "textures/gui/met_station.png");
	private static final int WIDTH = 288;
	private static final int HEIGHT = 208;

	private static final int FIRST_SEPARATOR_Y = 20;
	private static final int SECOND_SEPARATOR_Y = 176;

	/** The two columns, and where each one's value is right-aligned to. */
	private static final int LEFT_LABEL_X = 10;
	private static final int LEFT_VALUE_X = 140;
	private static final int RIGHT_LABEL_X = 148;
	private static final int RIGHT_VALUE_X = 278;
	private static final int FIRST_ROW_Y = 26;
	private static final int ROW_HEIGHT = 11;

	public MetStationScreen(BlockPos targetPos) {
		super(Component.translatable("screen.electricity.met_station.title"), TEXTURE, WIDTH, HEIGHT, targetPos, MetStationBlockEntity.class);
	}

	@Override
	protected void drawPanel(GuiGraphics graphics) {
		MetStationBlockEntity station = machine();
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
	 * Showing it here is the same instinct as printing the IEC class on a turbine's panel.
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

	/** Which array the two on-array instruments are fitted to */
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
}
