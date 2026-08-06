package com.dooji.electricity.client.screen;

import com.dooji.electricity.api.power.PvArraySpec;
import com.dooji.electricity.api.power.PvModuleSpec;
import com.dooji.electricity.api.power.TrackerMode;
import com.dooji.electricity.api.power.TrackerSpec;
import com.dooji.electricity.block.PvArrayBlockEntity;
import com.dooji.electricity.main.network.ElectricityNetworking;
import com.dooji.electricity.main.network.payloads.SolarControlPayload;
import com.dooji.electricity.main.registry.PvCatalog;
import com.dooji.electricity.main.registry.PvModuleCatalog;
import com.dooji.electricity.main.registry.TrackerCatalog;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** One array's panel: the datasheet, what the light is doing, and the tracker. */
public class PvArrayScreen extends PlantScreen<PvArrayBlockEntity> {
	private static final ResourceLocation TEXTURE = new ResourceLocation("electricity", "textures/gui/pv_array.png");
	private static final int WIDTH = 288;
	private static final int HEIGHT = 232;

	// the wells cut into the texture by tools/gen_pv_textures.py
	private static final int POA_BAR_Y = 70;
	private static final int RATIO_BAR_Y = 98;
	private static final int TRACKER_BAR_Y = 126;
	private static final int FIRST_SEPARATOR_Y = 40;
	private static final int SECOND_SEPARATOR_Y = 142;

	/** Top of the plane-of-array scale, W/m2. */
	private static final double POA_SCALE = 1100.0;

	private Button modeButton;
	private AngleSlider angleSlider;

	public PvArrayScreen(BlockPos targetPos) {
		super(Component.translatable("screen.electricity.pv_array.title"), TEXTURE, WIDTH, HEIGHT, targetPos, PvArrayBlockEntity.class);
	}

	@Override
	protected void init() {
		super.init();

		PvArrayBlockEntity array = machine();
		TrackerSpec tracker = array == null ? null : array.tracker();

		if (tracker != null) {
			angleSlider = addRenderableWidget(new AngleSlider(leftPos + 11, topPos + 180, WIDTH - 22, 20,
					fractionOf(tracker, array.manualRotationDeg())));
			modeButton = addRenderableWidget(Button.builder(Component.empty(), b -> send(SolarControlPayload.Action.ARRAY_CYCLE_TRACKER_MODE))
					.bounds(leftPos + 12, topPos + 204, WIDTH - 24, 20).build());
			refreshWidgets();
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (machine() != null && modeButton != null) {
			refreshWidgets();
		}
	}

	private void refreshWidgets() {
		PvArrayBlockEntity array = machine();
		TrackerSpec tracker = array == null ? null : array.tracker();
		if (tracker == null) return;

		String mode = array.trackerMode().name().toLowerCase(Locale.ROOT);
		modeButton.setMessage(Component.translatable("screen.electricity.pv_array.mode",
				Component.translatable("screen.electricity.pv_array.mode." + mode)));
		modeButton.setTooltip(Tooltip.create(Component.translatable("screen.electricity.pv_array.mode.tip." + mode)));

		// the slider only makes sense in hand mode
		// tooltip would: in automatic mode the controller would overwrite anything it was set to
		angleSlider.active = array.trackerMode() == TrackerMode.MANUAL;
		angleSlider.syncTo(fractionOf(tracker, array.manualRotationDeg()));
	}

	@Override
	protected void drawPanel(GuiGraphics graphics) {
		PvArrayBlockEntity array = machine();
		if (array == null) return;

		drawNameplate(graphics, array);
		drawState(graphics, array);
		drawIrradiance(graphics, array);
		drawPerformance(graphics, array);
		drawTracker(graphics, array);
		drawLosses(graphics, array);
	}

	private void drawNameplate(GuiGraphics graphics, PvArrayBlockEntity array) {
		PvArraySpec spec = array.spec();
		PvModuleSpec module = spec.module();

		graphics.drawString(font, PvCatalog.fullName(spec), leftPos + MARGIN, topPos + 6, VALUE_COLOUR, false);
		String nameplate = power(spec.dcPowerKw()) + " DC";
		graphics.drawString(font, nameplate, leftPos + WIDTH - MARGIN - font.width(nameplate), topPos + 6, FAINT_COLOUR, false);

		label(graphics, Component.translatable("screen.electricity.pv_array.module",
				spec.moduleCount(), PvModuleCatalog.fullName(module), module.cell().label(),
				fmt("%.1f%%", module.efficiency() * 100.0)), 17);
		label(graphics, Component.translatable("screen.electricity.pv_array.strings",
				spec.strings(), spec.modulesPerString(), fmt("%.0f", array.stringVoltage()),
				fmt("%.0f%%", spec.groundCoverRatio() * 100.0)), 27);

		separator(graphics, FIRST_SEPARATOR_Y);
	}

	/** Why the array is or is not making what it could. */
	private void drawState(GuiGraphics graphics, PvArrayBlockEntity array) {
		String key;
		int colour;
		if (array.snowDepthM() > 0.0 && array.effectiveIrradiance() <= 1.0) {
			key = "screen.electricity.pv_array.state.snow";
			colour = RED;
		} else if (!array.wired()) {
			// one state rather than two: a row with no harness is not a fault
			// string. What is a fault is nothing reaching this one, whatever the reason

			key = "screen.electricity.pv_array.state.no_collector";
			colour = RED;
		} else if (array.obstructionFraction() < 0.5) {
			key = "screen.electricity.pv_array.state.shaded";
			colour = AMBER;
		} else if (array.rowShadedFraction() > 0.02) {
			key = "screen.electricity.pv_array.state.row_shaded";
			colour = AMBER;
		} else if (array.stowReason() != TrackerMode.Stow.NONE) {
			key = "screen.electricity.pv_array.stow." + array.stowReason().name().toLowerCase(Locale.ROOT);
			colour = array.stowReason() == TrackerMode.Stow.NIGHT ? (FAINT_COLOUR | 0xFF000000) : AMBER;
		} else if (array.effectiveIrradiance() <= 1.0) {
			key = "screen.electricity.pv_array.state.dark";
			colour = FAINT_COLOUR | 0xFF000000;
		} else if (array.backtracking()) {
			key = "screen.electricity.pv_array.state.backtracking";
			colour = BLUE;
		} else {
			key = "screen.electricity.pv_array.state.producing";
			colour = GREEN;
		}

		state(graphics, Component.translatable(key), colour, 46);
	}

	/** The light on the plane */
	private void drawIrradiance(GuiGraphics graphics, PvArrayBlockEntity array) {
		label(graphics, Component.translatable("screen.electricity.pv_array.poa"), 56);
		value(graphics, fmt("%.0f W/m²", array.poaFront())
				+ (array.poaRear() > 1.0 ? fmt("  + %.0f rear", array.poaRear()) : ""), 56);

		// beam green, sky violet, ground amber - and the order is the order they matter in, so the stripe
		// that a shadow takes away is the one at the left where it is easiest to watch
		stackedBar(graphics, POA_BAR_Y,
				new double[]{array.poaBeam() / POA_SCALE, array.poaDiffuse() / POA_SCALE, array.poaGround() / POA_SCALE},
				new int[]{GREEN, VIOLET, AMBER});
		// where standard test conditions would put it
		// nameplate was measured at rather than against the top of an arbitrary scale
		notch(graphics, POA_BAR_Y, barWidth(1000.0 / POA_SCALE), 0xFF202020);
	}

	/** Performance ratio, and the temperature that is mostly deciding it. */
	private void drawPerformance(GuiGraphics graphics, PvArrayBlockEntity array) {
		double ratio = array.performanceRatio();

		label(graphics, Component.translatable("screen.electricity.pv_array.performance"), 84);
		value(graphics, fmt("%.2f", ratio) + "   " + power(array.deliveredDcKw()), 84);

		// a good array on a cool clear day is near 0.9 and the same array at seventy degrees is near 0.7,
		// so the zones are where those two are rather than an even split
		zone(graphics, RATIO_BAR_Y, 0, barWidth(0.60), RED);
		zone(graphics, RATIO_BAR_Y, barWidth(0.60), barWidth(0.78), AMBER);
		zone(graphics, RATIO_BAR_Y, barWidth(0.78), barSpan, 0xFF2F6E3A);
		needle(graphics, RATIO_BAR_Y, barWidth(ratio), 0xFFF0F0F0);
	}

	/** Where the plane is pointing, on a centred scale. */
	private void drawTracker(GuiGraphics graphics, PvArrayBlockEntity array) {
		TrackerSpec tracker = array.tracker();
		int centre = barSpan / 2;

		if (tracker == null) {
			label(graphics, Component.translatable("screen.electricity.pv_array.fixed"), 112);
			value(graphics, fmt("tilt %.0f°  ·  bearing %.0f°", array.tiltDeg(), array.planeAzimuthDeg()), 112);

			zone(graphics, TRACKER_BAR_Y, 0, barSpan, 0xFF5A5A5A);
			needle(graphics, TRACKER_BAR_Y, centre + (int) Math.round(array.tiltDeg() / 90.0 * centre), 0xFFF0F0F0);
			separator(graphics, SECOND_SEPARATOR_Y);
			return;
		}

		label(graphics, Component.translatable("screen.electricity.pv_array.tracker",
				TrackerCatalog.fullName(tracker)), 112);
		value(graphics, fmt("%+.1f°  →  %+.1f°", array.rotationDeg(), array.targetRotationDeg()), 112);

		// the reachable band in the middle, and the travel the drive does not have at either end
		int reach = (int) Math.round(tracker.rotationLimitDeg() / 90.0 * centre);
		zone(graphics, TRACKER_BAR_Y, 0, centre - reach, 0xFF5A5A5A);
		zone(graphics, TRACKER_BAR_Y, centre - reach, centre + reach, 0xFF2F6E3A);
		zone(graphics, TRACKER_BAR_Y, centre + reach, barSpan, 0xFF5A5A5A);
		notch(graphics, TRACKER_BAR_Y, centre, 0xFF202020);

		// the target first, then where the drive has actually got to over it: the gap is the row slewing
		needle(graphics, TRACKER_BAR_Y, centre + (int) Math.round(array.targetRotationDeg() / 90.0 * centre), 0x80F0F0F0);
		needle(graphics, TRACKER_BAR_Y, centre + (int) Math.round(array.rotationDeg() / 90.0 * centre), 0xFFF0F0F0);

		separator(graphics, SECOND_SEPARATOR_Y);
	}

	/** Everything quietly taking a few percent, and the conditions behind them. */
	private void drawLosses(GuiGraphics graphics, PvArrayBlockEntity array) {
		faint(graphics, Component.translatable("screen.electricity.pv_array.losses",
				fmt("%.1f%%", array.soiling() * 100.0),
				fmt("%.0f cm", array.snowDepthM() * 100.0),
				fmt("%.0f%%", (1.0 - array.obstructionFraction()) * 100.0),
				fmt("%.0f%%", array.rowShadedFraction() * 100.0)), 146);
		faint(graphics, Component.translatable("screen.electricity.pv_array.conditions",
				fmt("%.0f", array.moduleTempC()), fmt("%.0f", array.ambientTempC()),
				fmt("%.1f", array.windSpeed()), fmt("%.0f", array.incidenceDeg())), 156);
		faint(graphics, Component.translatable("screen.electricity.pv_array.wiring",
				fmt("%.0f V", array.stringVoltage()), fmt("%.1f A", array.arrayCurrent()),
				array.wired() ? Component.translatable("screen.electricity.pv_array.wired").getString()
						: Component.translatable("screen.electricity.pv_array.unwired").getString()), 166);
		faint(graphics, Component.translatable("screen.electricity.pv_array.run",
				fmt("%.0f m", array.runMetres()), fmt("%.0f%%", array.runBuriedFraction() * 100.0),
				fmt("%.2f%%", array.dcLossFraction() * 100.0)), 176);
	}

	/** Where an angle sits on the drive's full travel, 0 at one limit and 1 at the other. */
	private static double fractionOf(TrackerSpec tracker, double degrees) {
		return Mth.clamp((degrees + tracker.rotationLimitDeg()) / (2.0 * tracker.rotationLimitDeg()), 0.0, 1.0);
	}

	private void send(SolarControlPayload.Action action) {
		ElectricityNetworking.INSTANCE.sendToServer(SolarControlPayload.of(targetPos, action));
	}

	/** The hand position for the tracker. */
	private class AngleSlider extends SetpointSlider {
		private AngleSlider(int x, int y, int width, int height, double fraction) {
			super(x, y, width, height, fraction);
		}

		private double degrees() {
			PvArrayBlockEntity array = machine();
			TrackerSpec tracker = array == null ? null : array.tracker();
			if (tracker == null) return 0.0;

			return (value * 2.0 - 1.0) * tracker.rotationLimitDeg();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable("screen.electricity.pv_array.angle", fmt("%+.0f", degrees())));
		}

		@Override
		protected void commit() {
			ElectricityNetworking.INSTANCE.sendToServer(new SolarControlPayload(targetPos,
					SolarControlPayload.Action.ARRAY_SET_TRACKER_ANGLE, degrees()));
		}
	}
}
