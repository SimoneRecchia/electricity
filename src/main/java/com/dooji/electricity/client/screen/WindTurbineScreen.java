package com.dooji.electricity.client.screen;

import com.dooji.electricity.api.power.TurbineSpec;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import com.dooji.electricity.item.TurbineBlockItem;
import com.dooji.electricity.main.network.ElectricityNetworking;
import com.dooji.electricity.main.network.payloads.TurbineControlPayload;
import com.dooji.electricity.main.registry.TurbineCatalog;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** The control panel at the foot of a wind turbine. */
public class WindTurbineScreen extends PlantScreen<WindTurbineBlockEntity> {
	private static final ResourceLocation TEXTURE = new ResourceLocation("electricity", "textures/gui/wind_turbine.png");
	// 240 wide rather than the vanilla 176: the nameplate lines are the longest thing
	// here and at 200 the cut-out speed ran off the right edge
	private static final int IMAGE_WIDTH = 240;
	private static final int IMAGE_HEIGHT = 190;

	// mirrors the wells cut into the texture by the panel generator
	private static final int POWER_BAR_Y = 69;
	private static final int WIND_BAR_Y = 97;

	private Button stopButton;
	private Button redstoneButton;
	private LimitSlider limitSlider;

	public WindTurbineScreen(BlockPos targetPos) {
		super(Component.translatable("screen.electricity.wind_turbine.title"), TEXTURE, IMAGE_WIDTH, IMAGE_HEIGHT, targetPos, WindTurbineBlockEntity.class);
	}

	@Override
	protected void init() {
		super.init();

		WindTurbineBlockEntity turbine = machine();
		double limitFraction = turbine == null ? 1.0 : turbine.getActivePowerLimit() / turbine.spec().ratedPowerKw();
		limitSlider = addRenderableWidget(new LimitSlider(leftPos + 11, topPos + 139, 218, 20, limitFraction));

		stopButton = addRenderableWidget(Button.builder(Component.empty(), b -> send(TurbineControlPayload.Action.TOGGLE_RUNNING))
				.bounds(leftPos + 11, topPos + 163, 105, 20).build());
		redstoneButton = addRenderableWidget(Button.builder(Component.empty(), b -> send(TurbineControlPayload.Action.CYCLE_REDSTONE_MODE))
				.bounds(leftPos + 124, topPos + 163, 105, 20).build());

		refreshWidgets();
	}

	@Override
	public void tick() {
		super.tick();
		if (machine() != null) {
			refreshWidgets();
		}
	}

	/** Keeps the buttons telling the truth. */
	private void refreshWidgets() {
		WindTurbineBlockEntity turbine = machine();
		if (turbine == null) return;

		TurbineSpec spec = turbine.spec();
		stopButton.setMessage(Component.translatable(turbine.isStoppedByPlayer() ? "screen.electricity.wind_turbine.start" : "screen.electricity.wind_turbine.stop"));
		// the label is one word so it fits the button
		// tooltip rather than being squeezed in beside it
		String mode = turbine.getRedstoneMode().name().toLowerCase(Locale.ROOT);
		redstoneButton.setMessage(Component.translatable("screen.electricity.wind_turbine.redstone",
				Component.translatable("screen.electricity.wind_turbine.redstone." + mode)));
		redstoneButton.setTooltip(Tooltip.create(Component.translatable("screen.electricity.wind_turbine.redstone.tip." + mode)));

		// while a drag is in progress the handle is the player's, not the machine's:
		// overwriting it from the synced setpoint would fight the mouse
		limitSlider.syncTo(turbine.getActivePowerLimit() / spec.ratedPowerKw());
	}

	@Override
	protected void drawPanel(GuiGraphics graphics) {
		WindTurbineBlockEntity turbine = machine();
		if (turbine == null) return;

		drawNameplate(graphics, turbine);
		drawStatus(graphics, turbine);
		drawPower(graphics, turbine);
		drawWind(graphics, turbine);
		drawTower(graphics, turbine);
	}

	private void drawNameplate(GuiGraphics graphics, WindTurbineBlockEntity turbine) {
		TurbineSpec spec = turbine.spec();

		graphics.drawString(font, TurbineCatalog.fullName(spec), leftPos + 8, topPos + 6, VALUE_COLOUR, false);
		if (!spec.iecClass().isEmpty()) {
			String iec = Component.translatable("screen.electricity.wind_turbine.iec", spec.iecClass()).getString();
			faintValue(graphics, iec, 6);
		}

		graphics.drawString(font, Component.translatable("screen.electricity.wind_turbine.geometry",
				fmt("%.0f", spec.rotorDiameterM()), fmt("%.0f", turbine.getHubHeightM())), leftPos + 8, topPos + 17, LABEL_COLOUR, false);
		graphics.drawString(font, Component.translatable("screen.electricity.wind_turbine.curve",
				fmt("%.1f", spec.cutInSpeed()), fmt("%.1f", spec.ratedSpeed()), fmt("%.0f", spec.cutOutSpeed())), leftPos + 8, topPos + 27, LABEL_COLOUR, false);

		separator(graphics, 40);
	}

	/** Why the machine is or is not running. */
	private void drawStatus(GuiGraphics graphics, WindTurbineBlockEntity turbine) {
		String key;
		int colour;
		if (turbine.isWindCutOut()) {
			key = "screen.electricity.wind_turbine.state.cut_out";
			colour = RED;
		} else if (turbine.isStoppedByPlayer()) {
			key = "screen.electricity.wind_turbine.state.stopped_by_player";
			colour = AMBER;
		} else if (turbine.isStoppedByComputer()) {
			key = "screen.electricity.wind_turbine.state.stopped_by_computer";
			colour = AMBER;
		} else if (turbine.isStoppedByRedstone()) {
			key = "screen.electricity.wind_turbine.state.stopped_by_redstone";
			colour = AMBER;
		} else if (turbine.getUncappedPower() > turbine.getReportedPowerKw() + 0.01) {
			key = "screen.electricity.wind_turbine.state.curtailed";
			colour = BLUE;
		} else {
			key = "screen.electricity.wind_turbine.state.running";
			colour = GREEN;
		}

		state(graphics, Component.translatable(key), colour, 46);
	}

	/** Produced, and what the wind was offering before anything got in the way. */
	private void drawPower(GuiGraphics graphics, WindTurbineBlockEntity turbine) {
		TurbineSpec spec = turbine.spec();
		double rated = spec.ratedPowerKw();
		double produced = turbine.getReportedPowerKw();
		double potential = Math.max(produced, turbine.getUncappedPower());

		graphics.drawString(font, Component.translatable("screen.electricity.wind_turbine.power"), leftPos + 8, topPos + 58, LABEL_COLOUR, false);
		value(graphics, TurbineBlockItem.formatPower(produced) + " / " + TurbineBlockItem.formatPower(rated), 58);

		// the potential goes down first so the produced bar sits on top of it: what is left
		// showing behind is exactly what the machine is giving up
		fillBar(graphics, POWER_BAR_Y, barWidth(potential / rated), GHOST);
		fillBar(graphics, POWER_BAR_Y, barWidth(produced / rated), produced >= rated * 0.999 ? GREEN : BLUE);

		// the curtailment setpoint, as a notch on the same scale it limits
		notch(graphics, POWER_BAR_Y, barWidth(turbine.getActivePowerLimit() / rated), 0xFF202020);
	}

	/** The wind, against the thresholds of this particular machine. */
	private void drawWind(GuiGraphics graphics, WindTurbineBlockEntity turbine) {
		TurbineSpec spec = turbine.spec();
		double scale = spec.cutOutSpeed();
		// the mean, not the instantaneous wind: this is the figure the machine supervises on and
		double wind = turbine.getMeanWindSpeed();

		graphics.drawString(font, Component.translatable("screen.electricity.wind_turbine.wind"), leftPos + 8, topPos + 86, LABEL_COLOUR, false);
		value(graphics, fmt("%.1f / %.1f m/s", wind, turbine.getGustWindSpeed()), 86);

		int cutIn = barWidth(spec.cutInSpeed() / scale);
		int rated = barWidth(spec.ratedSpeed() / scale);
		int storm = barWidth(spec.stormOnsetSpeed() / scale);

		zone(graphics, WIND_BAR_Y, 0, cutIn, 0xFF5A5A5A);        // nothing to be had
		zone(graphics, WIND_BAR_Y, cutIn, rated, 0xFF2F6E3A);    // climbing the curve
		zone(graphics, WIND_BAR_Y, rated, storm, GREEN);         // on the plateau
		zone(graphics, WIND_BAR_Y, storm, barSpan, RED);        // shedding output

		// the gust first, so the mean draws over it where the two coincide
		needle(graphics, WIND_BAR_Y, barWidth(turbine.getGustWindSpeed() / scale), 0x80F0F0F0);
		needle(graphics, WIND_BAR_Y, barWidth(wind / scale), 0xFFF0F0F0);

		separator(graphics, 112);
	}

	/** The tower, in segments and in the hub height they add up to. */
	private void drawTower(GuiGraphics graphics, WindTurbineBlockEntity turbine) {
		TurbineSpec spec = turbine.spec();
		// the hub height this adds up to is already on the nameplate line
		// here is what ran the string under the tower buttons
		graphics.drawString(font, Component.translatable("screen.electricity.wind_turbine.tower",
				turbine.getTowerSegments(), spec.maxTowerSegments()), leftPos + 8, topPos + 117, LABEL_COLOUR, false);

		// what one more block of tower would be worth
		int segments = turbine.getTowerSegments();
		if (segments < spec.maxTowerSegments()) {
			double here = turbine.getReportedPowerKw();
			double ratio = Math.pow(spec.hubHeightM(segments + 1) / spec.hubHeightM(segments), turbine.getShearExponent());
			double taller = spec.powerWhileRunningKw(turbine.getWindSpeed() * ratio, turbine.getAirDensity());
			if (here > 0.01 && taller > here) {
				String gain = fmt("+%.0f%%", (taller / here - 1.0) * 100.0);
				graphics.drawString(font, Component.translatable("screen.electricity.wind_turbine.tower_gain", gain), leftPos + 8, topPos + 128, FAINT_COLOUR, false);
			}
		}
	}

	private void send(TurbineControlPayload.Action action) {
		ElectricityNetworking.INSTANCE.sendToServer(TurbineControlPayload.of(targetPos, action));
	}

	/** The curtailment setpoint. */
	private class LimitSlider extends SetpointSlider {
		private LimitSlider(int x, int y, int width, int height, double fraction) {
			super(x, y, width, height, fraction);
		}

		@Override
		protected void updateMessage() {
			WindTurbineBlockEntity turbine = machine();
			double rated = turbine == null ? 1.0 : turbine.spec().ratedPowerKw();
			setMessage(Component.translatable("screen.electricity.wind_turbine.limit", TurbineBlockItem.formatPower(value * rated), fmt("%.0f", value * 100.0)));
		}

		@Override
		protected void commit() {
			WindTurbineBlockEntity turbine = machine();
			if (turbine == null) return;

			ElectricityNetworking.INSTANCE.sendToServer(new TurbineControlPayload(targetPos, TurbineControlPayload.Action.SET_POWER_LIMIT, value * turbine.spec().ratedPowerKw()));
		}
	}
}
