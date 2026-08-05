package com.dooji.electricity.main.registry;

import com.dooji.electricity.api.power.ConductorSpec;
import com.dooji.electricity.api.power.SwitchgearSpec;
import com.dooji.electricity.api.power.SwitchgearSpec.Duty;
import com.dooji.electricity.main.Electricity;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * The two switches, both at 24 kV, because that is the voltage a player's own network runs at.
 *
 * They are a pair rather than two choices: the breaker is what you open under load and the disconnector is
 * what you open after it to make the gap you can see. Ratings are the real ones for a 630 A feeder unit and
 * for the three-phase line it is in - root three times volts times amps - so the figure a breaker trips
 * above is the figure its nameplate would carry.
 */
public final class SwitchgearCatalog {
	private static final Map<ResourceLocation, SwitchgearSpec> BY_ID = new LinkedHashMap<>();

	/** A 630 A three-pole air-break disconnector: porcelain posts, a copper blade, a hand lever. */
	public static final SwitchgearSpec MV_DISCONNECTOR = register(new SwitchgearSpec(
			id("mv_disconnector"), "AB-24/630", Duty.DISCONNECTOR,
			ConductorSpec.VoltageClass.MEDIUM, 24_000.0, 3, 26_000.0, "mv_disconnector"));

	/** A 630 A three-pole vacuum circuit breaker: cast epoxy poles over a spring mechanism. */
	public static final SwitchgearSpec MV_BREAKER = register(new SwitchgearSpec(
			id("mv_breaker"), "VB-24/630", Duty.BREAKER,
			ConductorSpec.VoltageClass.MEDIUM, 24_000.0, 3, 26_000.0, "mv_breaker"));

	private SwitchgearCatalog() {
	}

	private static ResourceLocation id(String path) {
		return new ResourceLocation(Electricity.MOD_ID, path);
	}

	private static SwitchgearSpec register(SwitchgearSpec spec) {
		BY_ID.put(spec.id(), spec);
		return spec;
	}

	public static Collection<SwitchgearSpec> all() {
		return List.copyOf(BY_ID.values());
	}

	@Nullable
	public static SwitchgearSpec get(ResourceLocation id) {
		return BY_ID.get(id);
	}
}
