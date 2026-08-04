package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.SensorSpec;
import com.dooji.electricity.main.Electricity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * The instruments a photovoltaic plant is monitored with.
 *
 * Instrument makers name their products by class and series - a CMP11, an SMP10, an SR30, an SR50A -
 * so these do too, and the maker is {@link #MANUFACTURER}. The numbers are the real ones: spectral
 * ranges in nanometres, response times in seconds, uncertainties as the daily-total percentages a
 * calibration certificate actually quotes.
 *
 * <h2>Two of them belong to the array and seven to the mast</h2>
 *
 * That split answers the question of whether a plant's sensors are built into the panels or stand
 * apart, and the answer is both, for a reason. Module temperature has to be measured on a module, and
 * a module a hundred metres away on different racking is not representative of this one - so a
 * resistance thermometer is taped to the back sheet of every array and there is one per array. A
 * reference cell is the same argument: it only means anything in the plane of the modules it is
 * standing in for.
 *
 * Everything else measures the sky rather than the plant, and the sky is the same across a site, so
 * IEC 61724 asks for one or two mast installations for a whole plant rather than one per array. Seven
 * instruments on one bus is what that looks like, and it is what the mod's met station carries.
 *
 * <h2>Why the response times are here</h2>
 *
 * Because they are the reason a real plant's irradiance trend is smoother than the power trend beside
 * it. A thermopile is a black disc that has to change temperature before it can change its output, so
 * it takes five seconds to answer a cloud edge that the modules answered instantly. Reading the two
 * trends together and noticing that the power moved first is how an operator knows the sensor is
 * telling the truth slowly rather than the array telling lies.
 */
public final class SensorCatalog {
	/** The fictional instrument maker. */
	public static final String MANUFACTURER = "Kelvinsen";

	private static final Map<ResourceLocation, SensorSpec> BY_ID = new LinkedHashMap<>();

	/**
	 * Class A pyranometer: global irradiance, 285 to 2800 nanometres.
	 *
	 * The instrument the whole plant is judged against, because performance ratio is production over
	 * what this says was available. A utility power purchase agreement will name the class in the
	 * contract; a rooftop gets whatever was cheap.
	 */
	public static final SensorSpec SP_11 = register(new SensorSpec(
			id("sensor_pyranometer"), "SP-11", SensorSpec.Instrument.PYRANOMETER,
			"ISO 9060 Class A", 285.0, 2800.0,
			5.0, 2.0, 0.0, 4000.0, "W/m2"));

	/**
	 * Albedometer: two Class A pyranometers back to back, one up and one down.
	 *
	 * Only worth fitting on a bifacial plant, where it is worth a great deal - the rear-side gain is
	 * proportional to what this reads, so it is how a developer finds out whether the grass under the
	 * array should have been gravel.
	 */
	public static final SensorSpec SA_11 = register(new SensorSpec(
			id("sensor_albedometer"), "SA-11", SensorSpec.Instrument.ALBEDOMETER,
			"ISO 9060 Class A", 285.0, 2800.0,
			5.0, 3.0, 0.0, 1.0, ""));

	/**
	 * Class A pyranometer under a shadow ring: the diffuse component alone.
	 *
	 * The instrument most often quietly wrong on a real plant, because the ring has to be moved as the
	 * sun's declination does and nobody remembers. Here the sun has no declination to move with, which
	 * is one maintenance job this world does not have.
	 */
	public static final SensorSpec SD_11 = register(new SensorSpec(
			id("sensor_diffuse"), "SD-11", SensorSpec.Instrument.DIFFUSE_PYRANOMETER,
			"ISO 9060 Class A", 285.0, 2800.0,
			5.0, 3.5, 0.0, 2000.0, "W/m2"));

	/**
	 * Reference cell: a single crystalline cell short-circuited across a shunt.
	 *
	 * Not a cheaper pyranometer but a different measurement. It responds only to the wavelengths the
	 * array responds to and it answers in microseconds rather than seconds, so it tracks the array's
	 * current far more closely - and it reads *lower* than the pyranometer beside it whenever the light
	 * is red, which is first thing, last thing and under heavy cloud.
	 */
	public static final SensorSpec RC_1 = register(new SensorSpec(
			id("sensor_reference_cell"), "RC-1", SensorSpec.Instrument.REFERENCE_CELL,
			"IEC 60904-2 secondary", 350.0, 1150.0,
			0.05, 3.0, 0.0, 1500.0, "W/m2"));

	/**
	 * Platinum resistance thermometer, Class A, on the back sheet of a representative module.
	 *
	 * A tenth of a degree of accuracy on a quantity that varies by twenty degrees across an array, so
	 * the accuracy is not the interesting part - where it is stuck is. Real plants argue about this:
	 * mid-cell on a module in the middle of a string is representative, and the edge of an end module
	 * runs several degrees cooler.
	 */
	public static final SensorSpec MT_1000 = register(new SensorSpec(
			id("sensor_module_temp"), "MT-1000", SensorSpec.Instrument.MODULE_TEMPERATURE,
			"IEC 60751 Class A", 0.0, 0.0,
			20.0, 0.5, -40.0, 110.0, "C"));

	/**
	 * Air temperature in a naturally aspirated radiation shield.
	 *
	 * The shield is the instrument, near enough: a thermometer in the sun reads the sun. Naturally
	 * aspirated rather than fan-driven, which costs a degree on a still hot afternoon and needs no
	 * power.
	 */
	public static final SensorSpec AT_7 = register(new SensorSpec(
			id("sensor_ambient_temp"), "AT-7", SensorSpec.Instrument.AMBIENT_TEMPERATURE,
			"IEC 60751 Class A", 0.0, 0.0,
			30.0, 0.3, -50.0, 60.0, "C"));

	/**
	 * Ultrasonic snow gauge: distance to the surface below, and the depth worked out from it.
	 *
	 * Publishes both numbers, which is not redundancy - the distance is the measurement and the depth
	 * is a subtraction from a surveyed clear-ground reference. The two disagreeing is how a plant finds
	 * out the transducer has iced over or somebody has parked under it. Its range starts half a metre
	 * out, so it genuinely cannot see the first snowfall of the season.
	 */
	public static final SensorSpec SN_50 = register(new SensorSpec(
			id("sensor_snow"), "SN-50", SensorSpec.Instrument.SNOW_DEPTH,
			"ultrasonic", 0.0, 0.0,
			1.0, 1.0, 0.5, 10.0, "m"));

	/** Cup anemometer. The wind that cools the modules and stows the trackers. */
	public static final SensorSpec WS_3 = register(new SensorSpec(
			id("sensor_anemometer"), "WS-3", SensorSpec.Instrument.ANEMOMETER,
			"IEC 61400-12 class 1", 0.0, 0.0,
			2.0, 1.0, 0.0, 75.0, "m/s"));

	/** Wind vane. Direction, which decides which way a stowed row is loaded and therefore whether flat is safe. */
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


	/** Full designation as a calibration certificate prints it, e.g. {@code Kelvinsen SP-11}. */
	public static String fullName(SensorSpec spec) {
		return MANUFACTURER + " " + spec.displayName();
	}
}
