package com.dooji.electricity.api.power;

/** An interface that allows blocks/entities to request power from the Electricity network, all values are in kW. */
public interface IElectricPowerConsumer {
	/** @return the amount of power (in kW) the device would like to receive while operating at full efficiency. */
	double getRequiredPower();

	/** @return the minimum amount of power (in kW) required for the device to operate safely. */
	default double getMinimumOperationalPower() {
		return getRequiredPower();
	}

	/** Called once per tick for every consumer that sits inside an active power field. */
	void onPowerSupplied(double deliveredPower, boolean meetsRequirement, PowerDeliveryEvent event);
}
