package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.ConductorSpec;
import com.dooji.electricity.api.power.SwitchgearSpec;
import com.dooji.electricity.api.power.SwitchgearSpec.Duty;
import static com.dooji.electricity.main.registry.Catalogue.id;
import java.util.List;

/**
 * The two switches, both at 24 kV, because that is the voltage a player's own network runs at.
 *
 * They are a pair rather than two choices: the breaker is what you open under load and the disconnector is
 * what you open after it to make the gap you can see. Ratings are the real ones for a 630 A feeder unit and
 * for the three-phase line it is in - root three times volts times amps - so the figure a breaker trips
 * above is the figure its nameplate would carry.
 */
public final class SwitchgearCatalog {
	private static final Catalogue<SwitchgearSpec> SWITCHES = new Catalogue<>(SwitchgearSpec::id);

	/** A 630 A three-pole air-break disconnector: porcelain posts, a copper blade, a hand lever. */
	public static final SwitchgearSpec MV_DISCONNECTOR = SWITCHES.register(new SwitchgearSpec(
			id("mv_disconnector"), "AB-24/630", Duty.DISCONNECTOR,
			ConductorSpec.VoltageClass.MEDIUM, 24_000.0, 3, 26_000.0, "mv_disconnector"));

	/** A 630 A three-pole vacuum circuit breaker: cast epoxy poles over a spring mechanism. */
	public static final SwitchgearSpec MV_BREAKER = SWITCHES.register(new SwitchgearSpec(
			id("mv_breaker"), "VB-24/630", Duty.BREAKER,
			ConductorSpec.VoltageClass.MEDIUM, 24_000.0, 3, 26_000.0, "mv_breaker"));

	private SwitchgearCatalog() {
	}

	public static List<SwitchgearSpec> all() {
		return SWITCHES.all();
	}

}
