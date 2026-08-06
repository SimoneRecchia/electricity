package com.dooji.electricity.client.screen;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A slider a player sets a machine with: it follows the machine while nobody is holding it, and sends only
 * when the drag ends.
 *
 * Sending per pixel would be a packet a frame, and a setpoint written back from the server while a hand is on
 * the slider fights the hand - so {@link #syncTo} refuses during a drag rather than every caller remembering
 * to ask first.
 */
public abstract class SetpointSlider extends AbstractSliderButton {
	private boolean beingDragged;

	protected SetpointSlider(int x, int y, int width, int height, double fraction) {
		super(x, y, width, height, Component.empty(), Mth.clamp(fraction, 0.0, 1.0));
		updateMessage();
	}

	/** Puts the slider where the machine says it is. */
	public void syncTo(double fraction) {
		double clamped = Mth.clamp(fraction, 0.0, 1.0);
		if (beingDragged || Math.abs(clamped - value) < 1.0e-4) return;

		value = clamped;
		updateMessage();
	}

	@Override
	protected final void applyValue() {
		// nothing until the drag ends: see onRelease
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
		commit();
	}

	/** Sends the setpoint the player has left the slider at. */
	protected abstract void commit();
}
