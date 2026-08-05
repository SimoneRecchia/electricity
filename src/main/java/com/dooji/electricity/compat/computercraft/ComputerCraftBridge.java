package com.dooji.electricity.compat.computercraft;

import net.minecraftforge.fml.ModList;

/** Guarded entry point for the ComputerCraft integration. */
public final class ComputerCraftBridge {
	private static Boolean loaded;

	private ComputerCraftBridge() {
	}

	public static boolean isLoaded() {
		if (loaded == null) {
			loaded = ModList.get().isLoaded("computercraft");
		}

		return loaded;
	}

	/** Registers the turbine as a ComputerCraft peripheral. */
	public static void register() {
		if (!isLoaded()) return;

		CCTweakedPeripherals.register();
	}
}
