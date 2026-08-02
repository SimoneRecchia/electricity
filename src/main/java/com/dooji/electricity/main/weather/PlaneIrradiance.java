package com.dooji.electricity.main.weather;

/**
 * What actually falls on a module plane, broken into where it came from.
 *
 * The transposition: the sky's three components projected onto a plane at some tilt and bearing. It is
 * where a tracker's advantage appears as arithmetic rather than as an assertion - the beam term is the
 * one that carries a cosine, and a plane held square to the sun has a cosine of one all day where a
 * flat one has the sine of the elevation.
 *
 * Kept apart from {@link SkyConditions} because the two belong to different things. The sky is a
 * property of the place and the moment, so a met mast can measure it and every array on a site shares
 * it. The plane is a property of one array's mounting and, if it tracks, of where its drive has
 * actually got to - so it is worked out per array and cannot be measured centrally at all.
 *
 * @param incidenceAngleDeg angle between the sun and the plane's normal. 90 or more means the sun is
 *                          behind the plane and the beam term is zero.
 * @param beam              direct irradiance on the plane, reflection at the glass already taken off
 * @param skyDiffuse        sky irradiance on the plane: less than a flat plane sees, because a tilted
 *                          one is looking at less sky
 * @param groundReflected   light bounced up off the ground into the front of the plane
 * @param rear              light reaching the back of the modules, for a bifacial array. Zero
 *                          otherwise, and the module's own bifaciality decides what it is worth.
 */
public record PlaneIrradiance(
		double incidenceAngleDeg,
		double beam,
		double skyDiffuse,
		double groundReflected,
		double rear
) {
	public static final PlaneIrradiance DARK = new PlaneIrradiance(90.0, 0.0, 0.0, 0.0, 0.0);

	/** Everything arriving on the front, W/m2. What a reference cell in the plane of the array reads. */
	public double front() {
		return beam + skyDiffuse + groundReflected;
	}

	/** Whether the sun is on the front face at all. */
	public boolean sunlit() {
		return incidenceAngleDeg < 90.0 && beam > 0.0;
	}

	/**
	 * Scales the beam and leaves everything else, which is what an obstruction between the sun and
	 * the plane does.
	 *
	 * The reason the components are carried separately: something standing over a panel blocks the
	 * one direction the beam arrives from and barely touches the rest of the dome. Under a clear sky
	 * that costs nearly everything and under an overcast one almost nothing, which is exactly what
	 * happens outside and exactly what a single lumped irradiance figure cannot say.
	 */
	public PlaneIrradiance withBeamFraction(double fraction) {
		double kept = Math.max(0.0, Math.min(1.0, fraction));
		return new PlaneIrradiance(incidenceAngleDeg, beam * kept, skyDiffuse, groundReflected, rear);
	}

	/**
	 * Scales the sky and the ground terms, which is what having less of the dome in view does.
	 *
	 * A roof over a panel takes away sky whether the sun is behind it or not, and the ground-reflected
	 * term goes with it because the light bouncing up came down through the same sky.
	 */
	public PlaneIrradiance withSkyFraction(double fraction) {
		double kept = Math.max(0.0, Math.min(1.0, fraction));
		return new PlaneIrradiance(incidenceAngleDeg, beam, skyDiffuse * kept, groundReflected * kept, rear * kept);
	}

	/** Everything at once, for the case where a module is buried under snow or a solid block. */
	public PlaneIrradiance scaled(double fraction) {
		double kept = Math.max(0.0, Math.min(1.0, fraction));
		return new PlaneIrradiance(incidenceAngleDeg, beam * kept, skyDiffuse * kept, groundReflected * kept, rear * kept);
	}
}
