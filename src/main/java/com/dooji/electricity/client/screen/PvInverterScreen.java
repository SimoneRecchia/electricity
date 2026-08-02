package com.dooji.electricity.client.screen;

import com.dooji.electricity.api.power.InverterSpec;
import com.dooji.electricity.block.PvInverterBlockEntity;
import com.dooji.electricity.main.network.ElectricityNetworking;
import com.dooji.electricity.main.network.payloads.SolarControlPayload;
import com.dooji.electricity.main.registry.InverterCatalog;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The inverter's panel: the plant, as its own control room would show it.
 *
 * <h2>What it is trying to answer</h2>
 *
 * One question, and it is the question every photovoltaic plant operator asks first: why is it not making
 * more? There are five answers and they look identical from outside - the sun is not out, the array is
 * offering more than the machine can pass, the air is too hot, somebody set a limit, or somebody stopped
 * it. So the two bars are drawn with what was *available* behind what was *delivered*, which turns every
 * one of those into a visible gap rather than a number to compare against another number.
 *
 * The DC bar behind the AC bar is the same idea one layer down. When the machine is clipping, the DC bar
 * shows the array's offer running past the point the operating point was pulled back to - which is what
 * clipping physically is, and it is the only place in the game a player can see it happen.
 */
public class PvInverterScreen extends PlantScreen {
	private static final ResourceLocation TEXTURE = new ResourceLocation("electricity", "textures/gui/pv_inverter.png");
	private static final int WIDTH = 288;
	private static final int HEIGHT = 232;

	// the wells cut into the texture by tools/gen_pv_textures.py
	private static final int AC_BAR_Y = 70;
	private static final int DC_BAR_Y = 98;
	private static final int TEMP_BAR_Y = 126;
	private static final int FIRST_SEPARATOR_Y = 40;
	private static final int SECOND_SEPARATOR_Y = 142;

	private Button stopButton;
	private Button redstoneButton;
	private Button powerFactorButton;
	private LimitSlider limitSlider;

	/** The power factors the button steps through: unity, and the two ends real grid codes ask for. */
	private static final double[] POWER_FACTORS = {1.0, 0.95, 0.90, 0.85, 0.80};

	public PvInverterScreen(BlockPos targetPos) {
		super(Component.translatable("screen.electricity.pv_inverter.title"), TEXTURE, WIDTH, HEIGHT, targetPos);
	}

	@Override
	protected void init() {
		super.init();

		PvInverterBlockEntity inverter = inverter();
		double limitFraction = inverter == null ? 1.0 : inverter.getActivePowerLimit() / inverter.spec().acPowerKw();
		limitSlider = addRenderableWidget(new LimitSlider(leftPos + 11, topPos + 180, WIDTH - 22, 20, limitFraction));

		int buttonWidth = (WIDTH - 24 - 8) / 3;
		stopButton = addRenderableWidget(Button.builder(Component.empty(), b -> send(SolarControlPayload.Action.INVERTER_TOGGLE_RUNNING))
				.bounds(leftPos + 12, topPos + 204, buttonWidth, 20).build());
		redstoneButton = addRenderableWidget(Button.builder(Component.empty(), b -> send(SolarControlPayload.Action.INVERTER_CYCLE_REDSTONE_MODE))
				.bounds(leftPos + 12 + buttonWidth + 4, topPos + 204, buttonWidth, 20).build());
		powerFactorButton = addRenderableWidget(Button.builder(Component.empty(), b -> cyclePowerFactor())
				.bounds(leftPos + 12 + 2 * (buttonWidth + 4), topPos + 204, buttonWidth, 20).build());

		refreshWidgets();
	}

	@Override
	public void tick() {
		super.tick();
		if (inverter() != null) {
			refreshWidgets();
		}
	}

	/**
	 * Keeps the buttons telling the truth.
	 *
	 * Their labels come from the machine rather than from what was last clicked, so a plant stopped by a
	 * computer or by redstone while the panel is open shows it.
	 */
	private void refreshWidgets() {
		PvInverterBlockEntity inverter = inverter();
		if (inverter == null) return;

		stopButton.setMessage(Component.translatable(inverter.isStoppedByPlayer()
				? "screen.electricity.pv.start" : "screen.electricity.pv.stop"));

		String mode = inverter.getRedstoneMode().name().toLowerCase(Locale.ROOT);
		redstoneButton.setMessage(Component.translatable("screen.electricity.pv.redstone",
				Component.translatable("screen.electricity.wind_turbine.redstone." + mode)));
		redstoneButton.setTooltip(Tooltip.create(Component.translatable("screen.electricity.wind_turbine.redstone.tip." + mode)));

		powerFactorButton.setMessage(Component.translatable("screen.electricity.pv_inverter.pf", fmt("%.2f", inverter.powerFactor())));
		powerFactorButton.setTooltip(Tooltip.create(Component.translatable("screen.electricity.pv_inverter.pf.tip")));

		// while a drag is in progress the handle is the player's, not the machine's
		if (!limitSlider.beingDragged) {
			limitSlider.syncTo(inverter.getActivePowerLimit() / inverter.spec().acPowerKw());
		}
	}

	@Override
	protected void drawPanel(GuiGraphics graphics) {
		PvInverterBlockEntity inverter = inverter();
		if (inverter == null) return;

		drawNameplate(graphics, inverter);
		drawState(graphics, inverter);
		drawPower(graphics, inverter);
		drawDc(graphics, inverter);
		drawThermal(graphics, inverter);
		drawPlant(graphics, inverter);
	}

	private void drawNameplate(GuiGraphics graphics, PvInverterBlockEntity inverter) {
		InverterSpec spec = inverter.spec();

		graphics.drawString(font, InverterCatalog.fullName(spec), leftPos + MARGIN, topPos + 6, VALUE_COLOUR, false);
		String mppt = Component.translatable("screen.electricity.pv_inverter.mppt_count", spec.mpptCount()).getString();
		graphics.drawString(font, mppt, leftPos + WIDTH - MARGIN - font.width(mppt), topPos + 6, FAINT_COLOUR, false);

		label(graphics, Component.translatable("screen.electricity.pv_inverter.ratings",
				power(spec.acPowerKw()), power(spec.maxDcPowerKw()), fmt("%.1f%%", spec.peakEfficiency() * 100.0)), 17);
		label(graphics, Component.translatable("screen.electricity.pv_inverter.window",
				fmt("%.0f", spec.mpptMinVolts()), fmt("%.0f", spec.mpptMaxVolts()), fmt("%.0f", spec.startupVolts())), 27);

		separator(graphics, FIRST_SEPARATOR_Y);
	}

	/**
	 * Why the plant is or is not making what it could.
	 *
	 * The order is the order an operator would want to be told: a stop first, because that is somebody's
	 * decision and cancels everything below it; then the heat, because that is the one a bigger inverter
	 * would not have fixed; then clipping, which is expected; then a setpoint; then simply night.
	 */
	private void drawState(GuiGraphics graphics, PvInverterBlockEntity inverter) {
		String key;
		int colour;
		if (inverter.isStoppedByPlayer()) {
			key = "screen.electricity.pv.state.stopped_by_player";
			colour = AMBER;
		} else if (inverter.isStoppedByComputer()) {
			key = "screen.electricity.pv.state.stopped_by_computer";
			colour = AMBER;
		} else if (inverter.isStoppedByRedstone()) {
			key = "screen.electricity.pv.state.stopped_by_redstone";
			colour = AMBER;
		} else if (inverter.arraysConnected() == 0) {
			key = "screen.electricity.pv_inverter.state.no_arrays";
			colour = RED;
		} else if (inverter.stringsOutOfWindow()) {
			// named rather than left at standby, because it is the one mismatch a player cannot see
			graphics.fill(leftPos + MARGIN, topPos + 47, leftPos + MARGIN + 4, topPos + 51, RED);
			graphics.drawString(font, Component.translatable("screen.electricity.pv_inverter.state.out_of_window",
					fmt("%.0f", inverter.dcVoltage()), fmt("%.0f", inverter.spec().mpptMinVolts()),
					fmt("%.0f", inverter.spec().mpptMaxVolts())), leftPos + MARGIN + 8, topPos + 46, VALUE_COLOUR, false);
			return;
		} else if (inverter.derating()) {
			key = "screen.electricity.pv_inverter.state.derating";
			colour = RED;
		} else if (inverter.clipping()) {
			key = "screen.electricity.pv_inverter.state.clipping";
			colour = BLUE;
		} else if (inverter.getActivePowerLimit() < inverter.spec().acPowerKw() - 0.01 && inverter.acPowerKw() >= inverter.getActivePowerLimit() - 0.01) {
			key = "screen.electricity.pv_inverter.state.curtailed";
			colour = BLUE;
		} else if (inverter.acPowerKw() <= 0.0) {
			key = "screen.electricity.pv_inverter.state.standby";
			colour = FAINT_COLOUR | 0xFF000000;
		} else {
			key = "screen.electricity.pv_inverter.state.mppt";
			colour = GREEN;
		}

		state(graphics, Component.translatable(key), colour, 46);
	}

	/**
	 * Alternating current out, against what the machine could have passed.
	 *
	 * The ghost behind is the array's offer converted at full efficiency, so the gap between the two bars
	 * is exactly what is being thrown away - to clipping, to heat, or to a setpoint. The notch is the
	 * setpoint itself, drawn on the scale it limits.
	 */
	private void drawPower(GuiGraphics graphics, PvInverterBlockEntity inverter) {
		InverterSpec spec = inverter.spec();
		double rated = spec.acPowerKw();
		double produced = Math.max(0.0, inverter.acPowerKw());
		double potential = Math.max(produced, Math.min(rated, spec.unclippedAcKw(inverter.availableDcKw())));

		label(graphics, Component.translatable("screen.electricity.pv.active_power"), 56);
		value(graphics, power(inverter.acPowerKw()) + " / " + power(rated), 56);

		fillBar(graphics, AC_BAR_Y, barWidth(potential / rated), GHOST);
		fillBar(graphics, AC_BAR_Y, barWidth(produced / rated), produced >= rated * 0.999 ? GREEN : BLUE);
		notch(graphics, AC_BAR_Y, barWidth(inverter.getActivePowerLimit() / rated), 0xFF202020);
	}

	/**
	 * The direct current side, on the machine's own input rating.
	 *
	 * Two bars again, and the gap here is the one that says clipping rather than anything else: the ghost
	 * is what the modules would give at their maximum power point and the filled bar is what the inverter
	 * has actually pulled them down to.
	 */
	private void drawDc(GuiGraphics graphics, PvInverterBlockEntity inverter) {
		InverterSpec spec = inverter.spec();
		double scale = spec.maxDcPowerKw();

		label(graphics, Component.translatable("screen.electricity.pv_inverter.dc_input"), 84);
		value(graphics, power(inverter.dcPowerKw()) + " / " + power(scale), 84);

		fillBar(graphics, DC_BAR_Y, barWidth(inverter.availableDcKw() / scale), GHOST);
		fillBar(graphics, DC_BAR_Y, barWidth(inverter.dcPowerKw() / scale), inverter.clipping() ? AMBER : BLUE);
		// the AC nameplate on the DC scale: everything past it is what an oversized array buys and pays for
		notch(graphics, DC_BAR_Y, barWidth(spec.acPowerKw() / scale), 0xFF202020);
	}

	/**
	 * The cabinet, against the two temperatures that matter to it.
	 *
	 * Zones rather than a bare number, because a bare number cannot say that 44 degrees is fine and 46 is
	 * costing output. The scale runs to the machine's own maximum, so the same gauge reads correctly for a
	 * wall-mounted 10 kW machine and a container-sized central one.
	 */
	private void drawThermal(GuiGraphics graphics, PvInverterBlockEntity inverter) {
		InverterSpec spec = inverter.spec();
		double scale = spec.maxAmbientC();

		label(graphics, Component.translatable("screen.electricity.pv_inverter.cabinet"), 112);
		value(graphics, fmt("%.0f °C  /  air %.0f °C", inverter.cabinetTempC(), inverter.ambientTempC()), 112);

		int onset = barWidth(spec.derateOnsetC() / scale);
		zone(graphics, TEMP_BAR_Y, 0, onset, 0xFF2F6E3A);
		zone(graphics, TEMP_BAR_Y, onset, barSpan, RED);
		// the air first, then the cabinet over it: the gap between them is what the losses are worth
		needle(graphics, TEMP_BAR_Y, barWidth(inverter.ambientTempC() / scale), 0x80F0F0F0);
		needle(graphics, TEMP_BAR_Y, barWidth(inverter.cabinetTempC() / scale), 0xFFF0F0F0);

		separator(graphics, SECOND_SEPARATOR_Y);
	}

	/** The plant behind the machine: what it has made, what is wired to it, and what the grid sees. */
	private void drawPlant(GuiGraphics graphics, PvInverterBlockEntity inverter) {
		faint(graphics, Component.translatable("screen.electricity.pv_inverter.energy",
				energy(inverter.energyTodayKwh()), energy(inverter.energyLifetimeKwh())), 146);
		faint(graphics, Component.translatable("screen.electricity.pv_inverter.plant",
				inverter.arraysConnected(), inverter.stringsConnected(), inverter.stringCapacity(),
				fmt("%.2f", inverter.dcAcRatio())), 156);
		faint(graphics, Component.translatable("screen.electricity.pv_inverter.grid",
				fmt("%.0f", inverter.spec().nominalAcVolts()), fmt("%.1f", inverter.phaseCurrentA()),
				fmt("%.0f", inverter.spec().frequencyHz()), fmt("%.2f", inverter.powerFactor()),
				fmt("%.0f", inverter.reactiveKvar())), 166);
	}

	private void cyclePowerFactor() {
		PvInverterBlockEntity inverter = inverter();
		if (inverter == null) return;

		double current = inverter.powerFactor();
		int next = 0;
		for (int i = 0; i < POWER_FACTORS.length; i++) {
			if (Math.abs(POWER_FACTORS[i] - current) < 0.01) {
				next = (i + 1) % POWER_FACTORS.length;
				break;
			}
		}

		// clamped by the machine on arrival, so a setpoint deeper than it can hold comes back as its limit
		ElectricityNetworking.INSTANCE.sendToServer(
				new SolarControlPayload(targetPos, SolarControlPayload.Action.INVERTER_SET_POWER_FACTOR, POWER_FACTORS[next]));
	}

	private void send(SolarControlPayload.Action action) {
		ElectricityNetworking.INSTANCE.sendToServer(SolarControlPayload.of(targetPos, action));
	}

	private PvInverterBlockEntity inverter() {
		if (minecraft == null || minecraft.level == null) return null;
		if (minecraft.level.getBlockEntity(targetPos) instanceof PvInverterBlockEntity inverter) return inverter;

		return null;
	}

	@Override
	protected BlockEntity blockEntity() {
		return inverter();
	}

	/**
	 * The curtailment setpoint.
	 *
	 * Sends on release rather than on every pixel of travel, because dragging across the whole range would
	 * otherwise be a hundred packets and a hundred block updates for one decision.
	 */
	private class LimitSlider extends AbstractSliderButton {
		private boolean beingDragged;

		private LimitSlider(int x, int y, int width, int height, double fraction) {
			super(x, y, width, height, Component.empty(), Mth.clamp(fraction, 0.0, 1.0));
			updateMessage();
		}

		private void syncTo(double fraction) {
			double clamped = Mth.clamp(fraction, 0.0, 1.0);
			if (Math.abs(clamped - value) < 1.0e-4) return;

			value = clamped;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			PvInverterBlockEntity inverter = inverter();
			double rated = inverter == null ? 1.0 : inverter.spec().acPowerKw();
			setMessage(Component.translatable("screen.electricity.pv.limit", power(value * rated), fmt("%.0f", value * 100.0)));
		}

		@Override
		protected void applyValue() {
			// nothing until the drag ends, see onRelease
		}

		@Override
		public void onClick(double mouseX, double mouseY) {
			beingDragged = true;
			super.onClick(mouseX, mouseY);
		}

		@Override
		public void onRelease(double mouseX, double mouseY) {
			super.onRelease(mouseX, mouseY);
			beingDragged = false;

			PvInverterBlockEntity inverter = inverter();
			if (inverter == null) return;

			ElectricityNetworking.INSTANCE.sendToServer(new SolarControlPayload(targetPos,
					SolarControlPayload.Action.INVERTER_SET_POWER_LIMIT, value * inverter.spec().acPowerKw()));
		}
	}
}
