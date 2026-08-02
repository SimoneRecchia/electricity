package com.dooji.electricity.client.screen;

import com.dooji.electricity.api.power.CombinerSpec;
import com.dooji.electricity.block.PvCombinerBlockEntity;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.registry.CombinerCatalog;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The combiner box's panel: how full it is, what it is sending, and what it turned away.
 *
 * Shorter than the other panels because a combiner box is switchgear rather than a machine - it makes
 * nothing, decides nothing, and the only question anybody asks of one is whether the group behind it is
 * on. Which is why the state line is the largest thing on it.
 *
 * The refusal line is the one that earns its place. A box that has left a row out has done so for one of
 * four reasons off its own datasheet, and three of them are invisible from outside: a player who has run
 * the cable correctly and got nothing needs to be told it is the fuses and not the wiring.
 */
@OnlyIn(Dist.CLIENT)
public class PvCombinerScreen extends PlantScreen {
	private static final ResourceLocation PANEL = new ResourceLocation(Electricity.MOD_ID, "textures/gui/pv_combiner.png");

	private static final int WIDTH = 288;
	private static final int HEIGHT = 136;
	private static final int WAYS_BAR_Y = 62;
	private static final int SEPARATOR_Y = 40;
	private static final int SECOND_SEPARATOR_Y = 78;

	public PvCombinerScreen(BlockPos pos) {
		super(Component.translatable("screen.electricity.pv_combiner.title"), PANEL, WIDTH, HEIGHT, pos);
	}

	@Override
	protected BlockEntity blockEntity() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;

		return mc.level.getBlockEntity(targetPos) instanceof PvCombinerBlockEntity combiner ? combiner : null;
	}

	@Override
	protected void drawPanel(GuiGraphics graphics) {
		if (!(blockEntity() instanceof PvCombinerBlockEntity combiner)) return;

		CombinerSpec spec = combiner.spec();
		if (spec == null) return;

		graphics.drawString(font, CombinerCatalog.fullName(spec), leftPos + MARGIN, topPos + 12, VALUE_COLOUR, false);
		faintValue(graphics, Component.translatable("screen.electricity.pv_combiner.rating",
				fmt("%s", spec.ingressProtection()), fmt("%.0f", spec.maxSystemVolts()),
				fmt("%.0f", spec.fuseAmps())).getString(), 12);
		separator(graphics, SEPARATOR_Y);

		drawState(graphics, combiner);
		drawWays(graphics, combiner, spec);

		separator(graphics, SECOND_SEPARATOR_Y);
		faint(graphics, Component.translatable("screen.electricity.pv_combiner.bus",
				fmt("%.0f V", combiner.busVoltage()), fmt("%.0f A", combiner.busCurrent()),
				power(combiner.offeredDcKw())), 84);
		faint(graphics, Component.translatable("screen.electricity.pv_combiner.trunk",
				fmt("%.0f m", combiner.runMetres()), fmt("%.0f%%", combiner.runBuriedFraction() * 100.0),
				fmt("%.2f%%", combiner.trunkLossFraction() * 100.0)), 94);
		faint(graphics, Component.translatable(combiner.wired()
				? "screen.electricity.pv_combiner.wired"
				: "screen.electricity.pv_combiner.unwired"), 104);
	}

	/**
	 * The one line worth reading at a glance, in the order the answers matter.
	 *
	 * Isolated first, because that is a thing a player did on purpose and every other reading follows
	 * from it. Then no inverter, then the refusal, then plain running.
	 */
	private void drawState(GuiGraphics graphics, PvCombinerBlockEntity combiner) {
		if (combiner.isolated()) {
			state(graphics, Component.translatable("screen.electricity.pv_combiner.state.isolated"), AMBER, 48);
			return;
		}

		if (!combiner.wired()) {
			state(graphics, Component.translatable("screen.electricity.pv_combiner.state.no_inverter"), RED, 48);
			return;
		}

		if (combiner.stringsConnected() == 0) {
			state(graphics, Component.translatable("screen.electricity.pv_combiner.state.empty"), RED, 48);
			return;
		}

		if (combiner.refusal() != CombinerSpec.Refusal.NONE) {
			state(graphics, Component.translatable("screen.electricity.pv_combiner.refusal."
					+ combiner.refusal().name().toLowerCase(Locale.ROOT)), AMBER, 48);
			return;
		}

		state(graphics, Component.translatable("screen.electricity.pv_combiner.state.closed"), GREEN, 48);
	}

	/** How much of the box is used, in ways and in amps, on one bar with the two marked apart. */
	private void drawWays(GuiGraphics graphics, PvCombinerBlockEntity combiner, CombinerSpec spec) {
		double ways = spec.fusedInputs() <= 0 ? 0.0 : (double) combiner.stringsConnected() / spec.fusedInputs();
		double amps = spec.outputAmps() <= 0.0 ? 0.0
				: 1.0 - Math.max(0.0, combiner.stringCurrentHeadroom()) / spec.outputAmps();

		label(graphics, Component.translatable("screen.electricity.pv_combiner.ways"), WAYS_BAR_Y - 14);
		value(graphics, fmt("%d of %d  ·  %s free", combiner.stringsConnected(), spec.fusedInputs(),
				fmt("%.0f A", Math.max(0.0, combiner.stringCurrentHeadroom()))), WAYS_BAR_Y - 14);

		fillBar(graphics, WAYS_BAR_Y, barWidth(Math.min(1.0, ways)), BLUE);
		// the copper marked over the ways, because which of the two is nearly full is the whole question
		needle(graphics, WAYS_BAR_Y, barWidth(Math.min(1.0, amps)), 0xFFF0F0F0);
	}
}
