package com.dooji.electricity.client.preview;

import com.dooji.electricity.main.Electricity;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Renders a machine with the game's own renderer, from the angles worth looking at, straight to PNG. */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Electricity.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PreviewStage {
	/** Where the stage is built, well away from anything a player has made. */
	private static final int STAGE_X = 512;
	private static final int STAGE_Z = 512;
	private static final int STAGE_Y = 96;
	/** Half-width of the floor. */
	private static final int FLOOR = 10;

	/** The views, and why these ones. */
	private record View(String name, float yaw, float pitch, double distance, double height) {
	}

	private static final List<View> VIEWS = List.of(
			new View("front", 0.0f, 8.0f, 7.0, 1.6),
			new View("side", 90.0f, 8.0f, 7.0, 1.6),
			new View("iso", 45.0f, 30.0f, 9.0, 5.0),
			new View("plan", 45.0f, 88.0f, 3.0, 13.0),
			new View("low", 20.0f, -4.0f, 5.0, 0.4));

	/** What is left to do */
	private static final Deque<Runnable> STEPS = new ArrayDeque<>();
	private static boolean armed = System.getProperty("electricity.preview") != null;

	private PreviewStage() {
	}

	@SubscribeEvent
	public static void onRegisterCommands(RegisterClientCommandsEvent event) {
		// a resource location argument rather than a string one, because brigadier's unquoted strings do
		// not allow a colon: /preview electricity:pv_tilt_530 would not have parsed at all
		LiteralArgumentBuilder<CommandSourceStack> command = LiteralArgumentBuilder.<CommandSourceStack>literal("preview")
				.then(Commands.argument("block", ResourceLocationArgument.id())
						.executes(context -> {
							queue(List.of(ResourceLocationArgument.getId(context, "block").toString()), false);
							return 1;
						})
						.then(Commands.literal("pair")
								.executes(context -> {
									queue(List.of(ResourceLocationArgument.getId(context, "block").toString()), true);
									return 1;
								})));

		event.getDispatcher().register(command);
	}

	/** Fires the unattended run, once */
	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;

		if (armed) {
			armed = false;
			String wanted = System.getProperty("electricity.preview", "");
			queue(subjects(wanted), true);
		}

		if (!STEPS.isEmpty()) STEPS.poll().run();
	}

	/** Which machines a property value names. */
	private static List<String> subjects(String wanted) {
		if (!"all".equalsIgnoreCase(wanted)) {
			return List.of(wanted.split(","));
		}

		List<String> found = new ArrayList<>();
		for (var entry : Electricity.BLOCKS.getEntries()) {
			String path = entry.getId().getPath();
			// the shell is invisible by design, and a tower on its own is a tube
			if (path.equals("machine_shell")) continue;

			found.add("electricity:" + path);
		}

		return found;
	}

	private static void queue(List<String> blocks, boolean pair) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;

		say("preview: %d machine(s), %d views each".formatted(blocks.size(), VIEWS.size()));
		STEPS.add(() -> {
			// one light, one sky, every time - a render that changes with the weather is not a comparison
			run("gamerule doDaylightCycle false");
			run("time set noon");
			run("weather clear");
			run("gamemode creative");
			Minecraft.getInstance().options.hideGui = true;
		});

		for (String block : blocks) {
			String id = block.trim();
			if (id.isEmpty()) continue;

			STEPS.add(() -> stage(id, pair));
			// two ticks for the chunk to relight and the block entity renderers to pick the block up
			STEPS.add(PreviewStage::nothing);
			STEPS.add(PreviewStage::nothing);
			for (View view : VIEWS) {
				STEPS.add(() -> look(view));
				STEPS.add(PreviewStage::nothing);
				STEPS.add(() -> shoot(id, view));
			}
		}

		STEPS.add(() -> {
			Minecraft.getInstance().options.hideGui = false;
			say("preview: done, in run/screenshots");
		});
	}

	/** Clears the stage, lays a floor */
	private static void stage(String block, boolean pair) {
		int x = STAGE_X;
		int y = STAGE_Y;
		int z = STAGE_Z;
		run("fill %d %d %d %d %d %d air".formatted(x - FLOOR, y, z - FLOOR, x + FLOOR, y + 24, z + FLOOR));
		run("fill %d %d %d %d %d %d minecraft:smooth_stone".formatted(x - FLOOR, y - 1, z - FLOOR, x + FLOOR, y - 1, z + FLOOR));

		if (needsTower(block)) {
			// a machine that needs a tower gets one
			run("fill %d %d %d %d %d %d electricity:turbine_tower".formatted(x, y, z, x, y + 5, z));
			run("setblock %d %d %d %s".formatted(x, y + 6, z, block));
		} else {
			run("setblock %d %d %d %s".formatted(x, y, z, block));
			if (pair) {
				// the row behind, and a run of cable in front: the two questions that are about the join
				run("setblock %d %d %d %s".formatted(x, y, z - 1, block));
				run("setblock %d %d %d electricity:dc_string_cable".formatted(x, y, z + 1));
				run("setblock %d %d %d electricity:dc_string_cable".formatted(x, y, z + 2));
			}
		}
	}

	/** Puts the camera where it can see the stage from one view. */
	/** Whether this block is a machine that will not seat without a tower under it. */
	private static boolean needsTower(String block) {
		for (com.dooji.electricity.api.power.TurbineSpec spec : com.dooji.electricity.main.registry.TurbineCatalog.all()) {
			if (block.endsWith(":" + spec.id().getPath())) return true;
		}

		// the C130 keeps the original registry name, which is not a catalogue path
		return block.endsWith(":wind_turbine");
	}

	private static void look(View view) {
		double radians = Math.toRadians(view.yaw());
		double x = STAGE_X + 0.5 + Math.sin(radians) * view.distance();
		double z = STAGE_Z + 0.5 - Math.cos(radians) * view.distance();
		run("tp @s %.2f %.2f %.2f %.1f %.1f".formatted(x, STAGE_Y + view.height(), z, view.yaw(), view.pitch()));
	}

	private static void shoot(String block, View view) {
		Minecraft mc = Minecraft.getInstance();
		String name = "preview_%s_%s.png".formatted(block.replace(':', '_'), view.name());
		// the game directory, not the screenshots directory: grab appends "screenshots" itself
		Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {
		});
	}

	private static void nothing() {
	}

	private static void run(String command) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.connection.sendCommand(command);
	}

	private static void say(String message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.displayClientMessage(Component.literal(message), false);
	}
}
