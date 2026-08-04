package com.dooji.electricity.api.power;

import net.minecraft.resources.ResourceLocation;

/**
 * One photovoltaic module, described the way a manufacturer's datasheet describes one.
 *
 * The same split as {@link TurbineSpec}: only the figures a real datasheet prints are fields,
 * and every relation that would hold on a real module is a method. Efficiency is the clearest
 * case - it is not a free parameter, it is the nameplate over the area at 1000 W/m2, so
 * declaring it separately would just be a second number free to drift away from the first.
 *
 * <h2>Scale</h2>
 *
 * Dimensions are the real module's, in metres, and the array works in real square metres -
 * a block of ground is a hundred of them at {@link com.dooji.electricity.api.WorldConditions#METRES_PER_BLOCK}.
 * So the module count on a block is a consequence of the module's own size rather than a
 * figure anybody chose, which is what makes a 700 W panel and a 415 W one different products
 * rather than the same product with a different label.
 *
 * <h2>What the electrical figures are for</h2>
 *
 * They are not decoration. Voc decides how many modules go in a string before the 1500 V
 * limit, Imp decides the string current the inverter has to swallow, and the two together
 * decide which inverter this module belongs in front of. An IBC module at 80.5 V takes
 * seventeen in series where a 51 V TOPCon takes twenty-seven, so the same DC nameplate
 * arrives as a completely different pair of numbers on the MPPT input - which is exactly what
 * the telemetry publishes.
 */
public record PvModuleSpec(
		ResourceLocation id,
		/** Model designation, e.g. {@code HM-144N-580}. A proper noun: never translated. */
		String displayName,
		Cell cell,
		/** Cells in the laminate. Half-cut modules count the halves, as their datasheets do. */
		int cells,
		double widthM,
		double heightM,
		double depthM,
		double massKg,
		/** Nameplate at standard test conditions: 1000 W/m2, 25 C cell, AM1.5. */
		double ratedPowerW,
		double vmp,
		double imp,
		double voc,
		double isc,
		/**
		 * Fraction of peak power lost per degree of cell temperature above 25 C.
		 *
		 * The single most consequential number here, and the one that decides an argument the
		 * game would otherwise get backwards. A module in a desert runs near 75 C and gives up
		 * a fifth of its rating to heat - but it stands under a sky that passes 92% of the
		 * sunlight rather than 65%, and the sunlight wins.
		 *
		 * It is also where the technologies genuinely part company: heterojunction runs at
		 * -0.24 %/K and PERC at -0.34, so on a hot afternoon the HJT module keeps about three
		 * percent more of its nameplate than the PERC one, every afternoon, for twenty years.
		 */
		double powerCoefficient,
		double vocCoefficient,
		double iscCoefficient,
		/**
		 * Nominal operating cell temperature: what the cells reach at 800 W/m2 in 20 C air with
		 * a 1 m/s breeze. Crystalline modules print 43 to 45 C.
		 *
		 * Declared, because it is on the datasheet, and then used to *derive* the module's heat
		 * loss coefficient rather than sitting beside one - see {@link #heatLossU0()}.
		 */
		double noct,
		/**
		 * Rear-side efficiency as a fraction of the front's. Zero for a monofacial module.
		 *
		 * 0.80 to 0.90 on TOPCon and heterojunction, 0.65 to 0.75 on bifacial PERC. What it is
		 * worth depends entirely on what the module is standing over: bifaciality times the
		 * ground albedo times the view factor, so the same module gains a couple of percent
		 * over grass and a tenth over snow.
		 */
		double bifaciality,
		double maxSystemVolts,
		double seriesFuseA
) {
	/** Irradiance the nameplate is measured at, W/m2. */
	public static final double STC_IRRADIANCE = 1000.0;
	/** Cell temperature the nameplate is measured at, in Celsius. */
	public static final double STC_TEMPERATURE = 25.0;
	/** Irradiance the NOCT figure is measured at, W/m2. */
	public static final double NOCT_IRRADIANCE = 800.0;
	/** Air temperature the NOCT figure is measured at. */
	public static final double NOCT_AMBIENT = 20.0;
	/** Wind speed the NOCT figure is measured at, m/s. */
	public static final double NOCT_WIND = 1.0;
	/**
	 * Wind term of the Faiman module temperature model, W/(m2 K per m/s).
	 *
	 * Faiman's own figure, measured on seven silicon modules on an open rack in the Negev, and
	 * the one IEC 61853 adopts. It is shared across the catalogue because it describes how air
	 * moving over a sheet of glass carries heat away, which is not a property of what is under
	 * the glass. The still-air term is not shared - see {@link #heatLossU0()}.
	 */
	public static final double HEAT_LOSS_U1 = 6.84;

	/**
	 * The cell technologies in the catalogue, and the two ways they differ that a datasheet
	 * does not print but a yield report does.
	 *
	 * The temperature coefficient is on the module rather than here, because two modules of the
	 * same technology can differ by a few hundredths and their datasheets say so.
	 */
	public enum Cell {
		/**
		 * Passivated emitter and rear contact: the p-type workhorse the industry ran on for a
		 * decade. The worst temperature coefficient and the worst low-light behaviour of the
		 * crystalline family, and the cheapest.
		 */
		PERC("PERC", 9.0, 0.20),
		/**
		 * Tunnel oxide passivated contact, n-type. What replaced PERC: half a percent more
		 * efficient, appreciably better in heat, and bifacial by construction.
		 */
		TOPCON("TOPCon", 6.0, 0.20),
		/**
		 * Heterojunction: crystalline silicon with amorphous layers either side. The best
		 * temperature coefficient in silicon by a wide margin, which is why it wins in hot
		 * climates despite costing more.
		 */
		HJT("HJT", 5.0, 0.20),
		/**
		 * Interdigitated back contact: every contact behind the cell, so nothing shades the
		 * front. The highest efficiency here, and a high voltage at a low current because the
		 * cells are whole rather than cut.
		 */
		IBC("IBC", 6.0, 0.20),
		/**
		 * Cadmium telluride thin film. The outlier on purpose: two thirds the efficiency of the
		 * silicon modules, ten times the voltage at a tenth of the current, the best low-light
		 * response of any of them, and a spectral response shifted blue - so it does relatively
		 * better under a humid or overcast sky, which is where the silicon modules do worst.
		 */
		CDTE("CdTe", 3.0, 0.55);

		private final String label;
		/**
		 * Irradiance, in W/m2, at which this technology has lost half its shunt-limited output.
		 *
		 * The knee of the low-light curve. A module's efficiency is not flat in irradiance: at
		 * the bottom of the range the shunt resistance across the cell carries a fixed share of
		 * the current and the efficiency falls away. Six W/m2 puts a good silicon module at 98%
		 * of its rated efficiency at 200 W/m2 and 95% at 100, which is what IEC 61853
		 * measurements show; thin film is flatter and PERC is steeper.
		 */
		private final double lowLightKnee;
		/**
		 * How strongly this technology's output follows the air mass away from AM1.5.
		 *
		 * Sunlight through a long atmospheric path is reddened and depleted in blue, so a cell
		 * that collects blue loses more than one that does not. Thin film is much the most
		 * sensitive and gains, rather than loses, under the diffuse blue light of an overcast
		 * sky. A fifth for silicon and better than half for CdTe are the measured ratios.
		 */
		private final double spectralSensitivity;

		Cell(String label, double lowLightKnee, double spectralSensitivity) {
			this.label = label;
			this.lowLightKnee = lowLightKnee;
			this.spectralSensitivity = spectralSensitivity;
		}

		public String label() {
			return label;
		}

		public double lowLightKnee() {
			return lowLightKnee;
		}

		public double spectralSensitivity() {
			return spectralSensitivity;
		}
	}

	public PvModuleSpec {
		if (ratedPowerW <= 0.0) throw new IllegalArgumentException(id + ": rated power must be positive");
		if (widthM <= 0.0 || heightM <= 0.0) throw new IllegalArgumentException(id + ": module must have an area");
		if (cells <= 0) throw new IllegalArgumentException(id + ": a module has cells");
		if (voc <= vmp || isc <= imp) throw new IllegalArgumentException(id + ": open circuit must exceed maximum power point");
		if (powerCoefficient >= 0.0) throw new IllegalArgumentException(id + ": power falls with cell temperature");
		if (noct <= NOCT_AMBIENT) throw new IllegalArgumentException(id + ": NOCT is above the 20 C it is measured in");
		if (bifaciality < 0.0 || bifaciality > 1.0) throw new IllegalArgumentException(id + ": bifaciality is a fraction");
	}

	// ---- geometry ----

	public double areaM2() {
		return widthM * heightM;
	}

	/**
	 * Module efficiency at standard test conditions.
	 *
	 * Derived, never declared, so it cannot disagree with the nameplate and the area it comes
	 * from. Lands on the published figures: 22.5% for the two utility TOPCon modules, 22.8%
	 * for the back-contact one, 18.9% for the thin film.
	 */
	public double efficiency() {
		return ratedPowerW / (areaM2() * STC_IRRADIANCE);
	}

	public boolean bifacial() {
		return bifaciality > 0.0;
	}

	// ---- temperature ----

	/**
	 * Still-air heat loss coefficient for the Faiman model, W/(m2 K).
	 *
	 * Derived from this module's own NOCT rather than shared, which is the point: NOCT and the
	 * Faiman coefficients are two descriptions of the same thing, so deriving one from the
	 * other means a module that a datasheet says runs cool actually runs cool here. Solving
	 * Faiman at the conditions NOCT is defined at gives it directly, and the answer comes out
	 * at 25.2 for a 45 C module against Faiman's own measured 25.0 - which is the check that
	 * the two models really are the same model.
	 */
	public double heatLossU0() {
		return NOCT_IRRADIANCE / (noct - NOCT_AMBIENT) - HEAT_LOSS_U1 * NOCT_WIND;
	}

	/**
	 * Cell temperature from the air temperature, the light on the plane and the wind over it.
	 *
	 * The Faiman model, which IEC 61853 adopts. Wind is in it because wind is what actually
	 * cools a module: the same module in the same sun runs some fifteen degrees cooler in a
	 * stiff breeze than in still air, and on a hot calm afternoon that is worth four or five
	 * percent of the output. The NOCT model the mod used before had no wind term at all, so
	 * every module ran as though the air never moved.
	 */
	public double cellTemperature(double ambientC, double poaIrradiance, double windSpeed) {
		if (poaIrradiance <= 0.0) return ambientC;

		return ambientC + poaIrradiance / (heatLossU0() + HEAT_LOSS_U1 * Math.max(0.0, windSpeed));
	}

	/** Fraction of nameplate power left at a given cell temperature. Above 1 when the cells are cold. */
	public double temperatureDerate(double cellTemperatureC) {
		return Math.max(0.0, 1.0 + powerCoefficient * (cellTemperatureC - STC_TEMPERATURE));
	}

	// ---- the light ----

	/**
	 * Relative efficiency at an irradiance below the one the module is rated at.
	 *
	 * Equal to one at 1000 W/m2 by construction, and falling below it as the light goes: 98% at
	 * 200 W/m2 and 95% at 100 for a good silicon module. The shape is the shunt resistance
	 * across the cell carrying a fixed share of a shrinking current, which is why it is a knee
	 * rather than a slope and why it only matters at first light and under heavy cloud.
	 *
	 * Written so the two factors cancel exactly at STC rather than approximately, because a
	 * module whose rated output was 0.3% off its own nameplate would be a bug in every reading
	 * that followed.
	 */
	public double lowLightEfficiency(double poaIrradiance) {
		if (poaIrradiance <= 0.0) return 0.0;

		double knee = cell.lowLightKnee();
		return poaIrradiance / (poaIrradiance + knee) * ((STC_IRRADIANCE + knee) / STC_IRRADIANCE);
	}

	/**
	 * Direct-current output of one module, in watts.
	 *
	 * Four factors, and the order they are written in is the order a yield report lists them:
	 * the light on the plane, the loss to cell temperature, the loss to low light, and the
	 * spectral correction the caller has already worked out from the air mass and the sky. All
	 * four are multiplicative, which is how the industry accounts for them.
	 */
	public double powerW(double poaIrradiance, double cellTemperatureC, double spectralFactor) {
		if (poaIrradiance <= 0.0) return 0.0;

		return ratedPowerW * poaIrradiance / STC_IRRADIANCE
				* temperatureDerate(cellTemperatureC)
				* lowLightEfficiency(poaIrradiance)
				* Math.max(0.0, spectralFactor);
	}

	// ---- the string ----

	/**
	 * Open circuit voltage at a given cell temperature.
	 *
	 * Rises as the module cools, which is the wrong direction for safety and the whole reason
	 * string length is decided at the coldest temperature a site ever sees rather than at 25 C.
	 */
	public double vocAt(double cellTemperatureC) {
		return voc * (1.0 + vocCoefficient * (cellTemperatureC - STC_TEMPERATURE));
	}

	/** Voltage at the maximum power point, which falls with temperature at much the same rate as Voc. */
	public double vmpAt(double cellTemperatureC) {
		return Math.max(0.0, vmp * (1.0 + vocCoefficient * (cellTemperatureC - STC_TEMPERATURE)));
	}

	/**
	 * Current at the maximum power point, at a given light and temperature.
	 *
	 * Nearly linear in irradiance and nearly flat in temperature - the opposite of the voltage,
	 * which is what makes a photovoltaic module a current source in the light and a voltage
	 * source in the cold.
	 */
	public double impAt(double poaIrradiance, double cellTemperatureC) {
		if (poaIrradiance <= 0.0) return 0.0;

		return imp * poaIrradiance / STC_IRRADIANCE * (1.0 + iscCoefficient * (cellTemperatureC - STC_TEMPERATURE));
	}

	/**
	 * How many of these may be wired in series before the system voltage limit, at the coldest
	 * cell temperature the site reaches.
	 *
	 * The calculation every string design starts from, and the reason a 1500 V plant is cheaper
	 * per watt than a 1000 V one: longer strings mean fewer of them, so less copper and fewer
	 * terminations for the same array.
	 */
	public int maxSeriesModules(double coldestCellC) {
		double coldVoc = vocAt(coldestCellC);
		if (coldVoc <= 0.0) return 1;

		return Math.max(1, (int) Math.floor(maxSystemVolts / coldVoc));
	}
}
