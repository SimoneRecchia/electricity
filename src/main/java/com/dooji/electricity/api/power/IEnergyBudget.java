package com.dooji.electricity.api.power;

/** A per-tick pool of energy (in Joules) that a generator offers to the energy systems of other mods. */
public interface IEnergyBudget {
	/** @return the Joules still unclaimed this tick. */
	double getAvailableJoules();

	/** @return the largest budget this source can offer within a single tick. */
	double getMaxJoulesPerTick();

	/** Claims up to {@code joules} from this tick's budget. */
	double claimJoules(double joules, boolean simulate);
}
