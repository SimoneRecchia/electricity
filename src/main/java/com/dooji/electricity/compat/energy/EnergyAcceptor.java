package com.dooji.electricity.compat.energy;

/** A neighbouring block that will take Joules, whichever energy API it actually speaks. */
@FunctionalInterface
public interface EnergyAcceptor {
	/**
	  * @param joules the amount offered @param simulate when true, report what would be taken without transferring anything @return the amount actually accepted
	 */
	double accept(double joules, boolean simulate);
}
