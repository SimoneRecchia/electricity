package com.dooji.electricity.main.registry;

import java.util.List;

/**
 * The parts a machine is made of, in the order they are made.
  *
 * {@link Tier#RAW} is what a player can make straight out of the ground: steel, copper bar, wafers.
 */
public final class PartCatalog {
	public enum Tier {
		/** Made out of ore and stock: the way into the tree. */
		RAW,
		/** Made from raw parts: the pieces a machine is assembled from. */
		COMPONENT,
		/** Made from components: an assembly that goes straight into a machine. */
		ASSEMBLY
	}

	/** One part. */
	public record Part(String id, Tier tier, String name, String tooltip) {
	}

	private static final List<Part> ALL = List.of(
			// ---------------------------------------------------------------- raw stock
			new Part("steel_plate", Tier.RAW, "Steel Plate",
					"Rolled sheet. The body of every cabinet and the web of every frame."),
			new Part("steel_section", Tier.RAW, "Steel Section",
					"Structural angle and tube. What a rack, a mast and a spar are built from."),
			new Part("tempered_glass", Tier.RAW, "Tempered Glass",
					"Heat-treated low-iron glass. A module's front sheet, and the fibre in a blade."),
			new Part("resin", Tier.RAW, "Encapsulant Resin",
					"Polymer film and epoxy. Laminates a module and binds a blade's fibre."),
			new Part("copper_coil", Tier.RAW, "Copper Coil",
					"Enamelled magnet wire, wound. Every winding in the mod starts here."),
			new Part("silicon_ingot", Tier.RAW, "Silicon Ingot",
					"Quartz reduced and cast. One step from sand to a solar cell."),
			new Part("silicon_wafer", Tier.RAW, "Silicon Wafer",
					"Sawn from an ingot. The blank a cell is made on and a die is cut from."),
			new Part("busbar", Tier.RAW, "Copper Busbar",
					"Solid drawn bar. Carries the current a cable could not."),

			// -------------------------------------------------------------- components
			new Part("solar_cell", Tier.COMPONENT, "Solar Cell",
					"A doped wafer with its fingers printed. Sixty of these make a module."),
			new Part("bypass_diode", Tier.COMPONENT, "Bypass Diode",
					"Carries a shaded substring's current so it does not become a heater."),
			new Part("mc4_connector", Tier.COMPONENT, "DC Connector",
					"The touch-proof plug and socket every string is joined with."),
			new Part("junction_box", Tier.COMPONENT, "Junction Box",
					"Bonded to a module's back: three bypass diodes and the leads out."),
			new Part("power_module", Tier.COMPONENT, "Power Module",
					"Switching dies on a substrate. What actually turns direct current into alternating."),
			new Part("capacitor_bank", Tier.COMPONENT, "Capacitor Bank",
					"The DC link. Holds the voltage steady between switching and the grid."),
			new Part("magnetic_core", Tier.COMPONENT, "Magnetic Core",
					"Laminated iron and a winding: the choke that smooths what switching leaves behind."),
			new Part("control_board", Tier.COMPONENT, "Control Board",
					"The processor that tracks the maximum power point and answers the panel."),
			new Part("bearing", Tier.COMPONENT, "Bearing",
					"Races and rollers. Everything in this mod that turns, turns on one."),
			new Part("gear_set", Tier.COMPONENT, "Gear Set",
					"Cut gears and a pinion, for a gearbox or a slew drive."),
			new Part("gpv_fuse", Tier.COMPONENT, "gPV Fuse",
					"A photovoltaic fuse: one per pole per string, and it breaks direct current."),
			new Part("load_break_switch", Tier.COMPONENT, "DC Load-Break Switch",
					"Opens a live direct-current circuit without leaving an arc behind."),
			new Part("sensor_head", Tier.COMPONENT, "Sensor Head",
					"A glass-domed element on a machined body: what a met station measures with."),

			// --------------------------------------------------------------- assemblies
			new Part("pv_laminate", Tier.ASSEMBLY, "PV Laminate",
					"Glass, film, cells and frame, cured into one piece. A module, ready to mount."),
			new Part("enclosure", Tier.ASSEMBLY, "Weatherproof Enclosure",
					"A sealed sheet-steel cabinet. What keeps the rain off the electronics."),
			new Part("dc_section", Tier.ASSEMBLY, "DC Section",
					"Fuse ways, busbar and a load-break switch: the front end of a big machine."),
			new Part("inverter_bridge", Tier.ASSEMBLY, "Inverter Bridge",
					"Power modules, DC link and magnetics on one busbar. One MPPT channel's worth."),
			new Part("mounting_rack", Tier.ASSEMBLY, "Mounting Rack",
					"Purlins, rails and clamps. What a fixed array is bolted to."),
			new Part("torque_tube", Tier.ASSEMBLY, "Torque Tube",
					"The shaft a tracked row turns on, in its bearings."),
			new Part("slew_drive", Tier.ASSEMBLY, "Slew Drive",
					"A geared bearing with a motor on it: what turns a tracker, a yaw deck or a pitch hub."),
			new Part("generator_set", Tier.ASSEMBLY, "Generator Set",
					"Stator windings round a rotor. Turns a shaft into three-phase current."),
			new Part("gearbox", Tier.ASSEMBLY, "Gearbox",
					"Three stages of gearing: takes a rotor's fifteen revolutions to a generator's fifteen hundred."),
			new Part("turbine_blade", Tier.ASSEMBLY, "Turbine Blade",
					"Glass fibre in resin over a steel spar. One of the three."),
			new Part("long_blade", Tier.ASSEMBLY, "Long Turbine Blade",
					"A blade extended: more fibre, a stronger spar, and the rotor that reaches low wind."));

	private PartCatalog() {
	}

	public static List<Part> all() {
		return ALL;
	}
}
