package com.dooji.electricity.client.screen;

import com.dooji.electricity.api.power.TurbineSpec;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import com.dooji.electricity.item.TurbineBlockItem;
import com.dooji.electricity.main.network.ElectricityNetworking;
import com.dooji.electricity.main.network.payloads.TurbineControlPayload;
import com.dooji.electricity.main.registry.TurbineCatalog;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The control panel at the foot of a wind turbine.
 *
 * Deliberately short on readings. The machine reports about fifty signals through
 * its ComputerCraft peripheral and none of them belong here: standing at a turbine
 * you want to know what it is, what it is doing right now, and why it is not doing
 * more. The gearbox oil temperature is a question for a monitor watching a whole
 * site, not for the panel on the tower.
 *
 * There is no menu behind it and no slots, because nothing goes inside a turbine -
 * the one thing a player adds is tower segments, and those are spent straight out
 * of the inventory. Everything shown is read from the client's copy of the block
 * entity, all of which arrives in its update tag already, so the panel needs no
 * traffic of its own in that direction. It only sends commands.
 */
public class WindTurbineScreen extends Screen {
	private static final ResourceLocation TEXTURE = new ResourceLocation("electricity", "textures/gui/wind_turbine.png");
	private static final int IMAGE_WIDTH = 200;
	private static final int IMAGE_HEIGHT = 186;

	// mirrors the wells cut into the texture by gen_gui.py
	private static final int BAR_X = 12;
	private static final int BAR_WIDTH = 176;
	private static final int POWER_BAR_Y = 65;
	private static final int WIND_BAR_Y = 92;
	private static final int BAR_HEIGHT = 10;

	private static final int LABEL_COLOUR = 0x404040;
	private static final int VALUE_COLOUR = 0x202020;
	private static final int FAINT_COLOUR = 0x707070;
	private static final int SEPARATOR_COLOUR = 0xFF8B8B8B;

	private static final int GREEN = 0xFF4CAF50;
	private static final int AMBER = 0xFFCE9B18;
	private static final int RED = 0xFFB33A2E;
	private static final int BLUE = 0xFF3E7CB1;
	private static final int GHOST = 0xFF6E6E6E;

	private final BlockPos targetPos;
	private int leftPos;
	private int topPos;
	private Button stopButton;
	private Button redstoneButton;
	private Button towerDownButton;
	private Button towerUpButton;
	private LimitSlider limitSlider;

	public WindTurbineScreen(BlockPos targetPos) {
		super(Component.translatable("screen.electricity.wind_turbine.title"));
		this.targetPos = targetPos.immutable();
	}

	@Override
	protected void init() {
		leftPos = (width - IMAGE_WIDTH) / 2;
		topPos = (height - IMAGE_HEIGHT) / 2;

		towerDownButton = addRenderableWidget(Button.builder(Component.literal("-"), b -> send(TurbineControlPayload.Action.REMOVE_TOWER_SEGMENT))
				.bounds(leftPos + 141, topPos + 110, 20, 20).build());
		towerUpButton = addRenderableWidget(Button.builder(Component.literal("+"), b -> send(TurbineControlPayload.Action.ADD_TOWER_SEGMENT))
				.bounds(leftPos + 165, topPos + 110, 20, 20).build());

		WindTurbineBlockEntity turbine = turbine();
		double limitFraction = turbine == null ? 1.0 : turbine.getActivePowerLimit() / turbine.spec().ratedPowerKw();
		limitSlider = addRenderableWidget(new LimitSlider(leftPos + 11, topPos + 134, 178, 20, limitFraction));

		stopButton = addRenderableWidget(Button.builder(Component.empty(), b -> send(TurbineControlPayload.Action.TOGGLE_RUNNING))
				.bounds(leftPos + 11, topPos + 158, 86, 20).build());
		redstoneButton = addRenderableWidget(Button.builder(Component.empty(), b -> send(TurbineControlPayload.Action.CYCLE_REDSTONE_MODE))
				.bounds(leftPos + 103, topPos + 158, 86, 20).build());

		refreshWidgets();
	}

	@Override
	public void tick() {
		super.tick();
		if (turbine() == null) {
			onClose();
			return;
		}

		refreshWidgets();
	}

	/**
	 * Keeps the buttons telling the truth.
	 *
	 * Their labels and enabled state come from the turbine rather than from what was
	 * clicked, so a machine stopped by a computer or by redstone while the panel is open
	 * shows it, and the tower buttons grey out at the ends of the range its model is sold
	 * on instead of sending commands the server would refuse.
	 */
	private void refreshWidgets() {
		WindTurbineBlockEntity turbine = turbine();
		if (turbine == null) return;

		TurbineSpec spec = turbine.spec();
		stopButton.setMessage(Component.translatable(turbine.isStoppedByPlayer() ? "screen.electricity.wind_turbine.start" : "screen.electricity.wind_turbine.stop"));
		redstoneButton.setMessage(Component.translatable("screen.electricity.wind_turbine.redstone",
				Component.translatable("screen.electricity.wind_turbine.redstone." + turbine.getRedstoneMode().name().toLowerCase(Locale.ROOT))));

		towerDownButton.active = turbine.getTowerSegments() > spec.minTowerSegments();
		towerUpButton.active = turbine.getTowerSegments() < spec.maxTowerSegments();

		// while a drag is in progress the handle is the player's, not the machine's:
		// overwriting it from the synced setpoint would fight the mouse
		if (!limitSlider.beingDragged) {
			limitSlider.syncTo(turbine.getActivePowerLimit() / spec.ratedPowerKw());
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

		WindTurbineBlockEntity turbine = turbine();
		if (turbine != null) {
			drawNameplate(graphics, turbine);
			drawStatus(graphics, turbine);
			drawPower(graphics, turbine);
			drawWind(graphics, turbine);
			drawTower(graphics, turbine);
		}

		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void drawNameplate(GuiGraphics graphics, WindTurbineBlockEntity turbine) {
		TurbineSpec spec = turbine.spec();

		graphics.drawString(font, TurbineCatalog.fullName(spec), leftPos + 8, topPos + 6, VALUE_COLOUR, false);
		if (!spec.iecClass().isEmpty()) {
			String iec = Component.translatable("screen.electricity.wind_turbine.iec", spec.iecClass()).getString();
			graphics.drawString(font, iec, leftPos + IMAGE_WIDTH - 8 - font.width(iec), topPos + 6, FAINT_COLOUR, false);
		}

		graphics.drawString(font, Component.translatable("screen.electricity.wind_turbine.geometry",
				fmt("%.0f", spec.rotorDiameterM()), fmt("%.0f", turbine.getHubHeightM())), leftPos + 8, topPos + 17, LABEL_COLOUR, false);
		graphics.drawString(font, Component.translatable("screen.electricity.wind_turbine.curve",
				fmt("%.1f", spec.cutInSpeed()), fmt("%.1f", spec.ratedSpeed()), fmt("%.0f", spec.cutOutSpeed())), leftPos + 8, topPos + 27, LABEL_COLOUR, false);

		separator(graphics, topPos + 38);
	}

	/**
	 * Why the machine is or is not running.
	 *
	 * The reason matters more than the fact. A turbine sitting at zero output is the one
	 * thing a player cannot diagnose from outside, and there are four different causes -
	 * a gale, a computer, a redstone signal, or their own hand on the brake - which look
	 * identical from the ground.
	 */
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

		graphics.fill(leftPos + 8, topPos + 45, leftPos + 12, topPos + 49, colour);
		graphics.drawString(font, Component.translatable(key), leftPos + 16, topPos + 44, VALUE_COLOUR, false);
	}

	/**
	 * Produced, and what the wind was offering before anything got in the way.
	 *
	 * Drawn as one bar with the potential behind the actual, so curtailment is visible as
	 * a gap rather than having to be inferred from two numbers.
	 */
	private void drawPower(GuiGraphics graphics, WindTurbineBlockEntity turbine) {
		TurbineSpec spec = turbine.spec();
		double rated = spec.ratedPowerKw();
		double produced = turbine.getReportedPowerKw();
		double potential = Math.max(produced, turbine.getUncappedPower());

		graphics.drawString(font, Component.translatable("screen.electricity.wind_turbine.power"), leftPos + 8, topPos + 54, LABEL_COLOUR, false);
		String value = TurbineBlockItem.formatPower(produced) + " / " + TurbineBlockItem.formatPower(rated);
		graphics.drawString(font, value, leftPos + IMAGE_WIDTH - 8 - font.width(value), topPos + 54, VALUE_COLOUR, false);

		int potentialWidth = barWidth(potential / rated);
		int producedWidth = barWidth(produced / rated);
		// the potential goes down first so the produced bar sits on top of it: what is left
		// showing behind is exactly what the machine is giving up
		fillBar(graphics, POWER_BAR_Y, potentialWidth, GHOST);
		fillBar(graphics, POWER_BAR_Y, producedWidth, produced >= rated * 0.999 ? GREEN : BLUE);

		// the curtailment setpoint, as a notch on the same scale it limits
		int limit = barWidth(turbine.getActivePowerLimit() / rated);
		if (limit < BAR_WIDTH) {
			graphics.fill(leftPos + BAR_X + limit, topPos + POWER_BAR_Y - 1, leftPos + BAR_X + limit + 1, topPos + POWER_BAR_Y + BAR_HEIGHT + 1, 0xFF202020);
		}
	}

	/**
	 * The wind, against the thresholds of this particular machine.
	 *
	 * The zones are the whole point: a bare number cannot tell a player that 2 m/s is
	 * below cut-in and therefore hopeless, while 23 is above the storm onset and therefore
	 * being deliberately thrown away. The scale runs to the cut-out, so the same gauge
	 * reads correctly for every model in the catalogue.
	 */
	private void drawWind(GuiGraphics graphics, WindTurbineBlockEntity turbine) {
		TurbineSpec spec = turbine.spec();
		double scale = spec.cutOutSpeed();
		double wind = turbine.getWindSpeed();

		graphics.drawString(font, Component.translatable("screen.electricity.wind_turbine.wind"), leftPos + 8, topPos + 81, LABEL_COLOUR, false);
		String value = fmt("%.1f m/s", wind);
		graphics.drawString(font, value, leftPos + IMAGE_WIDTH - 8 - font.width(value), topPos + 81, VALUE_COLOUR, false);

		int cutIn = barWidth(spec.cutInSpeed() / scale);
		int rated = barWidth(spec.ratedSpeed() / scale);
		int storm = barWidth(spec.stormOnsetSpeed() / scale);

		zone(graphics, WIND_BAR_Y, 0, cutIn, 0xFF5A5A5A);        // nothing to be had
		zone(graphics, WIND_BAR_Y, cutIn, rated, 0xFF2F6E3A);    // climbing the curve
		zone(graphics, WIND_BAR_Y, rated, storm, GREEN);         // on the plateau
		zone(graphics, WIND_BAR_Y, storm, BAR_WIDTH, RED);       // shedding output

		int needle = barWidth(wind / scale);
		graphics.fill(leftPos + BAR_X + Mth.clamp(needle - 1, 0, BAR_WIDTH - 2), topPos + WIND_BAR_Y - 2,
				leftPos + BAR_X + Mth.clamp(needle + 1, 2, BAR_WIDTH), topPos + WIND_BAR_Y + BAR_HEIGHT + 2, 0xFFF0F0F0);

		separator(graphics, topPos + 106);
	}

	/**
	 * The tower, in segments and in the hub height they add up to.
	 *
	 * Both numbers are shown because they are the same fact in the two units the player
	 * needs: segments are what it costs, metres are what it earns.
	 */
	private void drawTower(GuiGraphics graphics, WindTurbineBlockEntity turbine) {
		TurbineSpec spec = turbine.spec();
		graphics.drawString(font, Component.translatable("screen.electricity.wind_turbine.tower",
				turbine.getTowerSegments(), spec.maxTowerSegments(), fmt("%.0f", turbine.getHubHeightM())), leftPos + 8, topPos + 111, LABEL_COLOUR, false);

		// what one more segment is worth, so the cost is a decision rather than a gamble.
		// Asked as a ratio between the two hub heights, which needs no detour back to the
		// reference height and holds whatever the wind happens to be doing right now.
		int segments = turbine.getTowerSegments();
		if (segments < spec.maxTowerSegments()) {
			double here = turbine.getReportedPowerKw();
			double tallerWind = turbine.getWindSpeed() * TurbineSpec.windRatio(spec.hubHeightM(segments), spec.hubHeightM(segments + 1));
			double taller = spec.powerAtKw(tallerWind, turbine.getAirDensity());
			if (here > 0.01 && taller > here) {
				String gain = fmt("+%.0f%%", (taller / here - 1.0) * 100.0);
				graphics.drawString(font, Component.translatable("screen.electricity.wind_turbine.tower_gain", gain), leftPos + 8, topPos + 122, FAINT_COLOUR, false);
			}
		}
	}

	private void separator(GuiGraphics graphics, int y) {
		graphics.fill(leftPos + 11, y, leftPos + IMAGE_WIDTH - 11, y + 1, SEPARATOR_COLOUR);
	}

	private int barWidth(double fraction) {
		return (int) Math.round(Mth.clamp(fraction, 0.0, 1.0) * BAR_WIDTH);
	}

	private void fillBar(GuiGraphics graphics, int y, int width, int colour) {
		if (width <= 0) return;
		graphics.fill(leftPos + BAR_X, topPos + y, leftPos + BAR_X + width, topPos + y + BAR_HEIGHT, colour);
	}

	private void zone(GuiGraphics graphics, int y, int from, int to, int colour) {
		if (to <= from) return;
		graphics.fill(leftPos + BAR_X + from, topPos + y, leftPos + BAR_X + to, topPos + y + BAR_HEIGHT, colour);
	}

	private WindTurbineBlockEntity turbine() {
		if (minecraft == null || minecraft.level == null) return null;
		if (minecraft.level.getBlockEntity(targetPos) instanceof WindTurbineBlockEntity turbine) return turbine;

		return null;
	}

	private void send(TurbineControlPayload.Action action) {
		ElectricityNetworking.INSTANCE.sendToServer(TurbineControlPayload.of(targetPos, action));
	}

	private static String fmt(String pattern, Object... values) {
		return String.format(Locale.ROOT, pattern, values);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/**
	 * The curtailment setpoint.
	 *
	 * Sends on release rather than on every pixel of travel, because dragging across the
	 * whole range would otherwise be a hundred packets and a hundred block updates for one
	 * decision.
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
			WindTurbineBlockEntity turbine = turbine();
			double rated = turbine == null ? 1.0 : turbine.spec().ratedPowerKw();
			setMessage(Component.translatable("screen.electricity.wind_turbine.limit", TurbineBlockItem.formatPower(value * rated), fmt("%.0f", value * 100.0)));
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
			WindTurbineBlockEntity turbine = turbine();
			if (turbine == null) return;

			ElectricityNetworking.INSTANCE.sendToServer(new TurbineControlPayload(targetPos, TurbineControlPayload.Action.SET_POWER_LIMIT, value * turbine.spec().ratedPowerKw()));
		}
	}
}
