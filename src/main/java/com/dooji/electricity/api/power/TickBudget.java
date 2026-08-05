package com.dooji.electricity.api.power;

/** One tick's worth of energy a generator has offered, and how much of it has been taken. */
public final class TickBudget {
	private double offered;
	private double claimed;

	/** Starts a fresh tick with this much on the table */
	public void open(double joules) {
		offered = Math.max(0.0, joules);
		claimed = 0.0;
	}

	public double available() {
		return Math.max(0.0, offered - claimed);
	}

	/** What has already been taken this tick. */
	public double claimed() {
		return claimed;
	}

	/** @return how much was actually taken, which is never more than was asked or than remains */
	public double claim(double joules, boolean simulate) {
		if (!(joules > 0.0)) return 0.0;

		double claimable = Math.min(joules, available());
		if (claimable <= 0.0) return 0.0;
		if (!simulate) claimed += claimable;

		return claimable;
	}
}
