package com.dooji.electricity.api.power;

/**
 * A machine a control system can dispatch: it can be stopped, told how to read redstone, and held to a
 * setpoint, and it publishes a per-tick pool of energy while it runs.
 *
 * The turbine and the inverter both answered to all of it already - identically, method for method - and the
 * Lua peripherals had two copies of the sixteen wrappers because nothing said so.
 */
public interface Dispatchable extends IEnergyBudget {
	/** Whether the machine is turning and allowed to generate, for any reason. */
	boolean isRunning();

	/** Joules produced in the last tick, before anything was claimed. */
	double getGrossJoulesPerTick();

	// ---- what a computer may do to it ----

	void setStoppedByComputer(boolean stopped);

	boolean isStoppedByComputer();

	/** Whether the current redstone mode and signal are holding it down. */
	boolean isStoppedByRedstone();

	RedstoneMode getRedstoneMode();

	void setRedstoneMode(RedstoneMode mode);

	/** The curtailment setpoint, in kW. */
	double getActivePowerLimit();

	void setActivePowerLimit(double limitKw);
}
