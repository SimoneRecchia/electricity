package com.dooji.electricity.api.power;

/**
 * A block's worth of photovoltaic modules, described the way a datasheet describes one.
 *
 * The same split as {@link TurbineSpec}: only figures a real datasheet prints are constants,
 * and every relation between them is a method. There is one array model rather than a
 * catalogue, because a panel differs from a bigger panel only in area, and area here is
 * blocks.
 *
 * <h2>Why 20 kW a block</h2>
 *
 * One block covers a hundred square metres at the mod's ten metres to the block. A real
 * ground-mounted array leaves gaps between its rows so they do not shade one another, and
 * covers perhaps half its land; Minecraft's sun passes through the zenith, so there is
 * nothing to shade and the whole block can be covered. A hundred square metres of modules at
 * the 200 W/m2 a modern one manages is 20 kW, and that is the figure. It also puts a panel
 * block alongside the smallest turbine in the catalogue, which makes the two comparable to
 * build with.
 */
public final class PhotovoltaicArray {
	/** Nameplate at standard test conditions, in kW. */
	public static final double RATED_POWER_KW = 20.0;
	/** Irradiance the nameplate is measured at, W/m2. */
	public static final double STC_IRRADIANCE = 1000.0;
	/** Cell temperature the nameplate is measured at, in Celsius. */
	public static final double STC_TEMPERATURE = 25.0;
	/**
	 * Nominal operating cell temperature: what the cells reach at 800 W/m2 in 20 C air with a
	 * light breeze. 45 C is the middle of the range crystalline silicon modules print.
	 */
	private static final double NOCT = 45.0;
	private static final double NOCT_IRRADIANCE = 800.0;
	private static final double NOCT_AMBIENT = 20.0;
	/**
	 * Fraction of peak power lost per degree of cell temperature above standard, for
	 * crystalline silicon. Real datasheets print -0.34 to -0.45 %/K.
	 *
	 * This is the term that decides an argument the game would otherwise get backwards. A
	 * desert panel runs near 75 C and gives up a fifth of its rating to heat - but it stands
	 * under a sky that passes 92% of the sunlight rather than 74%, and the sunlight wins. So a
	 * desert out-produces a rainforest by about a quarter, which is what the real pair do.
	 */
	private static final double TEMPERATURE_COEFFICIENT = -0.0040;
	/** Peak efficiency of the inverter, and the load at which it reaches half of that. */
	private static final double INVERTER_PEAK = 0.97;
	private static final double INVERTER_KNEE = 0.017;

	private PhotovoltaicArray() {
	}

	/**
	 * Cell temperature from the air temperature and the light falling on it.
	 *
	 * The standard NOCT model, linear in irradiance: a module in full sun sits some thirty
	 * degrees above the air around it, which is why the same array gives less at two in the
	 * afternoon than at ten in the morning under the same sun.
	 */
	public static double cellTemperature(double ambientC, double irradiance) {
		return ambientC + (NOCT - NOCT_AMBIENT) * Math.max(0.0, irradiance) / NOCT_IRRADIANCE;
	}

	/** Direct-current output before the inverter, in kW. Linear in light, derated by heat. */
	public static double dcPowerKw(double irradiance, double cellTemperatureC) {
		if (irradiance <= 0.0) return 0.0;

		double derating = 1.0 + TEMPERATURE_COEFFICIENT * (cellTemperatureC - STC_TEMPERATURE);
		return Math.max(0.0, RATED_POWER_KW * irradiance / STC_IRRADIANCE * derating);
	}

	/**
	 * Inverter efficiency at a given fraction of nameplate.
	 *
	 * Flat and high across most of the range and poor at the very bottom, which is the shape
	 * every real efficiency curve has: the inverter's own consumption is fixed, so at first
	 * light it eats most of what the modules make. It is why a panel produces nothing at all
	 * for the first few minutes after sunrise rather than a trickle.
	 */
	public static double inverterEfficiency(double loadFraction) {
		if (loadFraction <= 0.0) return 0.0;

		return INVERTER_PEAK * loadFraction / (loadFraction + INVERTER_KNEE);
	}

	/** What leaves the inverter, in kW, from the light and the air temperature. */
	public static double acPowerKw(double irradiance, double ambientC) {
		double dc = dcPowerKw(irradiance, cellTemperature(ambientC, irradiance));
		return dc * inverterEfficiency(dc / RATED_POWER_KW);
	}
}
