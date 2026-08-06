package com.dooji.electricity.client.screen;

import com.dooji.electricity.block.PlantControllerBlockEntity;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.network.ElectricityNetworking;
import com.dooji.electricity.main.network.payloads.PlantControlPayload;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The plant controller's panel: what the site is making, what it is being held to, and by what. */
@OnlyIn(Dist.CLIENT)
public class PlantControllerScreen extends PlantScreen<PlantControllerBlockEntity> {
	private static final ResourceLocation PANEL = new ResourceLocation(Electricity.MOD_ID, "textures/gui/plant_controller.png");

	private static final int WIDTH = 288;
	private static final int HEIGHT = 178;
	private static final int LOAD_BAR_Y = 58;
	private static final int SEPARATOR_Y = 40;
	private static final int SECOND_SEPARATOR_Y = 78;
	/** Baseline of each line of text, and they are constants for one reason. */
	private static final int STATE_Y = 46;
	private static final int OUTPUT_Y = 66;
	private static final int UNITS_Y = 84;
	private static final int SETPOINT_Y = 94;
	private static final int COMMS_Y = 104;
	private static final int MODE_BUTTON_Y = 118;
	private static final int SLIDER_Y = 142;

	private Button modeButton;
	private LimitSlider limitSlider;

	public PlantControllerScreen(BlockPos pos) {
		super(Component.translatable("screen.electricity.plant_controller.title"), PANEL, WIDTH, HEIGHT, pos,
				PlantControllerBlockEntity.class);
	}

	@Override
	protected void init() {
		super.init();
		PlantControllerBlockEntity controller = machine();
		double capacity = controller == null ? 0.0 : controller.getCapacityKw();
		double fraction = capacity <= 0.0 ? 1.0 : controller.getSetpointKw() / capacity;

		modeButton = addRenderableWidget(Button.builder(modeLabel(),
						button -> send(PlantControlPayload.Action.CYCLE_MODE, 0.0))
				.bounds(leftPos + 12, topPos + MODE_BUTTON_Y, WIDTH - 24, 20).build());
		limitSlider = addRenderableWidget(new LimitSlider(leftPos + 12, topPos + SLIDER_Y, WIDTH - 24, 20, fraction));
	}

	@Override
	protected void drawPanel(GuiGraphics graphics) {
		PlantControllerBlockEntity controller = machine();
		if (controller == null) return;

		graphics.drawString(font, Component.translatable("screen.electricity.plant_controller.subtitle").getString(),
				leftPos + MARGIN, topPos + 12, VALUE_COLOUR, false);
		faintValue(graphics, Component.translatable("screen.electricity.plant_controller.reach",
				fmt("%d", PlantControllerBlockEntity.RANGE)).getString(), 12);
		separator(graphics, SEPARATOR_Y);

		drawState(graphics, controller);
		drawLoading(graphics, controller);

		separator(graphics, SECOND_SEPARATOR_Y);
		faint(graphics, Component.translatable("screen.electricity.plant_controller.units",
				fmt("%d", controller.getUnitCount()), fmt("%d", controller.getRunningCount()),
				fmt("%d", controller.getCurtailedCount())), UNITS_Y);
		faint(graphics, Component.translatable("screen.electricity.plant_controller.setpoint",
				power(controller.getTargetKw()), power(controller.getCapacityKw())), SETPOINT_Y);
		faint(graphics, Component.translatable("screen.electricity.plant_controller.comms",
				fmt("%d", controller.getComparatorOutput())), COMMS_Y);

		modeButton.setMessage(modeLabel());
		limitSlider.follow(controller);
	}

	/** The one line worth reading at a glance: whether anything is being held, and by what. */
	private void drawState(GuiGraphics graphics, PlantControllerBlockEntity controller) {
		if (controller.getUnitCount() == 0) {
			state(graphics, Component.translatable("screen.electricity.plant_controller.state.no_units"), RED, STATE_Y);
			return;
		}

		if (controller.getMode() == PlantControllerBlockEntity.Mode.OFF) {
			state(graphics, Component.translatable("screen.electricity.plant_controller.state.watching"), BLUE, STATE_Y);
			return;
		}

		if (controller.getTargetKw() < controller.getCapacityKw() - 0.01) {
			state(graphics, Component.translatable("screen.electricity.plant_controller.state.limiting"), AMBER, STATE_Y);
			return;
		}

		state(graphics, Component.translatable("screen.electricity.plant_controller.state.free"), GREEN, STATE_Y);
	}

	/** Output over capacity on one bar, with the setpoint marked across it. */
	private void drawLoading(GuiGraphics graphics, PlantControllerBlockEntity controller) {
		double capacity = controller.getCapacityKw();
		double loading = capacity <= 0.0 ? 0.0 : controller.getOutputKw() / capacity;
		double target = capacity <= 0.0 ? 1.0 : controller.getTargetKw() / capacity;

		label(graphics, Component.translatable("screen.electricity.plant_controller.output"), OUTPUT_Y);
		value(graphics, fmt("%s of %s", power(controller.getOutputKw()), power(capacity)), OUTPUT_Y);

		fillBar(graphics, LOAD_BAR_Y, barSpan, GHOST);
		fillBar(graphics, LOAD_BAR_Y, barWidth(Math.min(1.0, loading)),
				controller.getMode() == PlantControllerBlockEntity.Mode.OFF ? BLUE : AMBER);
		// the setpoint over the output, because whether the plant is *at* its limit is the whole question
		needle(graphics, LOAD_BAR_Y, barWidth(Math.min(1.0, target)), 0xFFF0F0F0);
	}

	private Component modeLabel() {
		PlantControllerBlockEntity controller = machine();
		String mode = (controller == null ? PlantControllerBlockEntity.Mode.OFF : controller.getMode())
				.name().toLowerCase(Locale.ROOT);
		return Component.translatable("screen.electricity.plant_controller.mode",
				Component.translatable("screen.electricity.plant_controller.mode." + mode));
	}

	private void send(PlantControlPayload.Action action, double value) {
		ElectricityNetworking.INSTANCE.sendToServer(new PlantControlPayload(targetPos, action, value));
	}

	/** The plant setpoint, as a fraction of what the units in range add up to. */
	private class LimitSlider extends SetpointSlider {
		private LimitSlider(int x, int y, int width, int height, double fraction) {
			super(x, y, width, height, fraction);
		}

		private void follow(PlantControllerBlockEntity controller) {
			double capacity = controller.getCapacityKw();
			syncTo(capacity <= 0.0 ? 1.0 : controller.getSetpointKw() / capacity);
			active = controller.getMode() == PlantControllerBlockEntity.Mode.LIMIT;
		}

		@Override
		protected void updateMessage() {
			PlantControllerBlockEntity controller = machine();
			double capacity = controller == null ? 0.0 : controller.getCapacityKw();
			setMessage(Component.translatable("screen.electricity.plant_controller.limit",
					power(value * capacity), fmt("%.0f%%", value * 100.0)));
		}

		@Override
		protected void commit() {
			PlantControllerBlockEntity controller = machine();
			if (controller != null) send(PlantControlPayload.Action.SET_SETPOINT, value * controller.getCapacityKw());
		}
	}
}
