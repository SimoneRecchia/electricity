package com.dooji.electricity.api.power;

import net.minecraft.resources.ResourceLocation;

/** One photovoltaic module, described the way a manufacturer's datasheet describes one. */
public record PvModuleSpec(
		ResourceLocation id,
		/** Model designation. */
		String displayName,
		Cell cell,
		/** Cells in the laminate. */
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
		/** Fraction of peak power lost per degree of cell temperature above 25 C. */
		double powerCoefficient,
		double vocCoefficient,
		double iscCoefficient,
		/** Nominal operating cell temperature: what the cells reach at 800 W/m2 in 20 C air with a 1 m/s breeze. */
		double noct,
		/**
		 * Rear-side efficiency as a fraction of the front's.
		  *
		 * 0.80 to 0.90 on TOPCon and heterojunction, 0.65 to 0.75 on bifacial PERC.
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
	 * The still-air term is not shared - see {@link #heatLossU0()}.
	 */
	public static final double HEAT_LOSS_U1 = 6.84;

	/**
	 * The cell technologies in the catalogue, and the two ways they differ that a datasheet does not print but a yield report does.
	 */
	public enum Cell {
		/** Passivated emitter and rear contact: the p-type workhorse the industry ran on for a decade. */
		PERC("PERC", 9.0, 0.20),
		/**
		 * Tunnel oxide passivated contact, n-type.
		  *
		 * What replaced PERC: half a percent more efficient, appreciably better in heat, and bifacial by construction.
		 */
		TOPCON("TOPCon", 6.0, 0.20),
		/** Heterojunction: crystalline silicon with amorphous layers either side. */
		HJT("HJT", 5.0, 0.20),
		/** Interdigitated back contact: every contact behind the cell, so nothing shades the front. */
		IBC("IBC", 6.0, 0.20),
		/** Cadmium telluride thin film. */
		CDTE("CdTe", 3.0, 0.55);

		private final String label;
		/** Irradiance, in W/m2, at which this technology has lost half its shunt-limited output. */
		private final double lowLightKnee;
		/** How strongly this technology's output follows the air mass away from AM1.5. */
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

	/** Module efficiency at standard test conditions. */
	public double efficiency() {
		return ratedPowerW / (areaM2() * STC_IRRADIANCE);
	}

	public boolean bifacial() {
		return bifaciality > 0.0;
	}

	// ---- temperature ----

	/** Still-air heat loss coefficient for the Faiman model, W/(m2 K). */
	public double heatLossU0() {
		return NOCT_IRRADIANCE / (noct - NOCT_AMBIENT) - HEAT_LOSS_U1 * NOCT_WIND;
	}

	/**
	 * Cell temperature from the air temperature, the light on the plane and the wind over it.
	  *
	 * The Faiman model, which IEC 61853 adopts.
	 */
	public double cellTemperature(double ambientC, double poaIrradiance, double windSpeed) {
		if (poaIrradiance <= 0.0) return ambientC;

		return ambientC + poaIrradiance / (heatLossU0() + HEAT_LOSS_U1 * Math.max(0.0, windSpeed));
	}

	/** Fraction of nameplate power left at a given cell temperature. */
	public double temperatureDerate(double cellTemperatureC) {
		return Math.max(0.0, 1.0 + powerCoefficient * (cellTemperatureC - STC_TEMPERATURE));
	}

	// ---- the light ----

	/** Relative efficiency at an irradiance below the one the module is rated at. */
	public double lowLightEfficiency(double poaIrradiance) {
		if (poaIrradiance <= 0.0) return 0.0;

		double knee = cell.lowLightKnee();
		return poaIrradiance / (poaIrradiance + knee) * ((STC_IRRADIANCE + knee) / STC_IRRADIANCE);
	}

	/** Direct-current output of one module, in watts. */
	public double powerW(double poaIrradiance, double cellTemperatureC, double spectralFactor) {
		if (poaIrradiance <= 0.0) return 0.0;

		return ratedPowerW * poaIrradiance / STC_IRRADIANCE
				 * temperatureDerate(cellTemperatureC)
				 * lowLightEfficiency(poaIrradiance)
				 * Math.max(0.0, spectralFactor);
	}

	// ---- the string ----

	/** Open circuit voltage at a given cell temperature. */
	public double vocAt(double cellTemperatureC) {
		return voc * (1.0 + vocCoefficient * (cellTemperatureC - STC_TEMPERATURE));
	}

	/** Voltage at the maximum power point, which falls with temperature at much the same rate as Voc. */
	public double vmpAt(double cellTemperatureC) {
		return Math.max(0.0, vmp * (1.0 + vocCoefficient * (cellTemperatureC - STC_TEMPERATURE)));
	}

	/** Current at the maximum power point, at a given light and temperature. */
	public double impAt(double poaIrradiance, double cellTemperatureC) {
		if (poaIrradiance <= 0.0) return 0.0;

		return imp * poaIrradiance / STC_IRRADIANCE * (1.0 + iscCoefficient * (cellTemperatureC - STC_TEMPERATURE));
	}

	/**
	 * How many of these may be wired in series before the system voltage limit, at the coldest cell temperature the site reaches.
	 */
	public int maxSeriesModules(double coldestCellC) {
		double coldVoc = vocAt(coldestCellC);
		if (coldVoc <= 0.0) return 1;

		return Math.max(1, (int) Math.floor(maxSystemVolts / coldVoc));
	}
}
