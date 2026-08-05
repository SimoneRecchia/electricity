package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.SensorSpec;
import com.dooji.electricity.main.Electricity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** The instruments a photovoltaic plant is monitored with. */
public final class SensorCatalog {
	/** The fictional instrument maker. */
	public static final String MANUFACTURER = "Kelvinsen";

	private static final Map<ResourceLocation, SensorSpec> BY_ID = new LinkedHashMap<>();

	/** Class A pyranometer: global irradiance, 285 to 2800 nanometres. */
	public static final SensorSpec SP_11 = register(new SensorSpec(
			id("sensor_pyranometer"), "SP-11", SensorSpec.Instrument.PYRANOMETER,
			"ISO 9060 Class A", 285.0, 2800.0,
			5.0, 2.0, 0.0, 4000.0, "W/m2"));

	/** Albedometer: two Class A pyranometers back to back */
	public static final SensorSpec SA_11 = register(new SensorSpec(
			id("sensor_albedometer"), "SA-11", SensorSpec.Instrument.ALBEDOMETER,
			"ISO 9060 Class A", 285.0, 2800.0,
			5.0, 3.0, 0.0, 1.0, ""));

	/** Class A pyranometer under a shadow ring: the diffuse component alone. */
	public static final SensorSpec SD_11 = register(new SensorSpec(
			id("sensor_diffuse"), "SD-11", SensorSpec.Instrument.DIFFUSE_PYRANOMETER,
			"ISO 9060 Class A", 285.0, 2800.0,
			5.0, 3.5, 0.0, 2000.0, "W/m2"));

	/** Reference cell: a single crystalline cell short-circuited across a shunt. */
	public static final SensorSpec RC_1 = register(new SensorSpec(
			id("sensor_reference_cell"), "RC-1", SensorSpec.Instrument.REFERENCE_CELL,
			"IEC 60904-2 secondary", 350.0, 1150.0,
			0.05, 3.0, 0.0, 1500.0, "W/m2"));

	/** Platinum resistance thermometer, Class A, on the back sheet of a representative module. */
	public static final SensorSpec MT_1000 = register(new SensorSpec(
			id("sensor_module_temp"), "MT-1000", SensorSpec.Instrument.MODULE_TEMPERATURE,
			"IEC 60751 Class A", 0.0, 0.0,
			20.0, 0.5, -40.0, 110.0, "C"));

	/** Air temperature in a naturally aspirated radiation shield. */
	public static final SensorSpec AT_7 = register(new SensorSpec(
			id("sensor_ambient_temp"), "AT-7", SensorSpec.Instrument.AMBIENT_TEMPERATURE,
			"IEC 60751 Class A", 0.0, 0.0,
			30.0, 0.3, -50.0, 60.0, "C"));

	/** Ultrasonic snow gauge: distance to the surface below */
	public static final SensorSpec SN_50 = register(new SensorSpec(
			id("sensor_snow"), "SN-50", SensorSpec.Instrument.SNOW_DEPTH,
			"ultrasonic", 0.0, 0.0,
			1.0, 1.0, 0.5, 10.0, "m"));

	/** Cup anemometer. */
	public static final SensorSpec WS_3 = register(new SensorSpec(
			id("sensor_anemometer"), "WS-3", SensorSpec.Instrument.ANEMOMETER,
			"IEC 61400-12 class 1", 0.0, 0.0,
			2.0, 1.0, 0.0, 75.0, "m/s"));

	/** Wind vane. */
	public static final SensorSpec WV_3 = register(new SensorSpec(
			id("sensor_vane"), "WV-3", SensorSpec.Instrument.WIND_VANE,
			"IEC 61400-12 class 1", 0.0, 0.0,
			2.0, 3.0, 0.0, 360.0, "deg"));

	private SensorCatalog() {
	}

	private static ResourceLocation id(String path) {
		return new ResourceLocation(Electricity.MOD_ID, path);
	}

	private static SensorSpec register(SensorSpec spec) {
		BY_ID.put(spec.id(), spec);
		return spec;
	}

	public static List<SensorSpec> all() {
		return List.copyOf(BY_ID.values());
	}


	/** Full designation as a calibration certificate prints it. */
	public static String fullName(SensorSpec spec) {
		return MANUFACTURER + " " + spec.displayName();
	}
}
