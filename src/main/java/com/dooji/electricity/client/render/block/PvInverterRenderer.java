package com.dooji.electricity.client.render.block;

import com.dooji.electricity.api.power.InverterSpec;
import com.dooji.electricity.block.PvInverterBlock;
import com.dooji.electricity.block.PvInverterBlockEntity;
import com.dooji.electricity.client.TrackedBlockEntities;
import com.dooji.electricity.client.render.obj.ObjBlockRegistry;
import com.dooji.electricity.client.render.obj.ObjBoundingBoxRegistry;
import com.dooji.electricity.client.render.obj.ObjLoader;
import com.dooji.electricity.client.render.obj.ObjModel;
import com.dooji.electricity.client.render.obj.ObjRenderUtil;
import com.dooji.electricity.client.render.obj.ObjRendererBase;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.registry.InverterCatalog;
import com.dooji.electricity.main.registry.ObjBlockDefinition;
import com.dooji.electricity.main.registry.ObjDefinitions;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Draws the inverter cabinets, and spins the fan that is keeping them alive.
 *
 * <h2>The fan is a reading</h2>
 *
 * Its speed comes off the cabinet temperature rather than the output, which is the useful way round and
 * also the real one: a temperature-controlled fan runs to hold a setpoint, so it idles on a cool
 * morning at full output and roars on a hot afternoon at half. A player who learns to read it has a
 * derating warning they can hear across a field, and a cabinet whose fan has stopped while the
 * temperature climbs is the failure this is worth drawing at all for.
 *
 * A convection-cooled machine has no fan group to turn, and the geometry simply has a blank grille -
 * which is what the small one looks like.
 *
 * <h2>One asset, four machines</h2>
 *
 * The same trick the turbines use: the catalogue spans a factor of two hundred and fifty in nameplate
 * and the difference on screen is a scale rather than four models. The three sizes match the three
 * collision shapes {@link PvInverterBlock} chooses between, so what a player can walk into is what they
 * can see.
 */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Electricity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PvInverterRenderer extends ObjRendererBase {
	private static final double MAX_RENDER_DISTANCE_SQ = 96 * 96;
	private static final Map<BlockPos, Map<String, GroupBuffer>> BUFFER_CACHE = new HashMap<>();
	/** Where each fan has got to, so it turns continuously rather than restarting every frame. */
	private static final Map<BlockPos, Float> FAN_ANGLE = new HashMap<>();

	/** Cabinet temperature at which the fan is at a standstill, and the one at which it is flat out. */
	private static final double FAN_IDLE_C = 25.0;
	private static final double FAN_FULL_C = 60.0;
	/** Degrees a frame at full speed. Fast enough to blur, slow enough not to strobe. */
	private static final float FAN_MAX_STEP = 22.0f;

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;

		Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
		HashSet<BlockPos> seen = new HashSet<>();

		for (PvInverterBlockEntity inverter : TrackedBlockEntities.ofType(PvInverterBlockEntity.class)) {
			seen.add(inverter.getBlockPos());
			ObjRenderUtil.withAlignedPose(inverter, event.getPoseStack(), mc.renderBuffers().bufferSource(), cameraPos, MAX_RENDER_DISTANCE_SQ,
					state -> state.getValue(PvInverterBlock.FACING), turnedFrom(PvInverterBlock.AUTHORED),
					(context, pose, buffers) -> render(context.model(), pose, event.getProjectionMatrix(), context.texture(), context.packedLight(), inverter));
		}

		cleanupCache(BUFFER_CACHE, seen);
		if (!FAN_ANGLE.isEmpty() && !seen.isEmpty()) {
			FAN_ANGLE.keySet().removeIf(pos -> !seen.contains(pos));
		}
	}

	private static void render(ObjModel model, PoseStack poseStack, Matrix4f projectionMatrix, ResourceLocation texture, int packedLight,
			PvInverterBlockEntity inverter) {
		InverterSpec spec = inverter.spec();
		float scale = (float) renderScale(spec);
		float fanAngle = advanceFan(inverter.getBlockPos(), inverter.cabinetTempC(), spec.cooling());

		Vec3 hub = pivot(model, "fan", new Vec3(0.46, 0.68, 0.0));
		Direction facing = inverter.getBlockState().getValue(PvInverterBlock.FACING);
		Set<String> entries = inverter.getLevel() == null ? Set.of()
				: cableEntries(inverter.getLevel(), inverter.getBlockPos(), PvInverterBlock.AUTHORED, facing, "entry");
		boolean section = PvInverterBlock.hasCombiner(inverter.getBlockState());
		Map<String, Matrix4f> poses = new HashMap<>();

		for (String groupName : model.groups.keySet()) {
			if (groupName.startsWith("pivot_")) continue;
			if (!entryVisible(groupName, "entry", entries)) continue;
			// the direct-current section is only there when a combiner box has been fitted, and it is the
			// whole visible difference between a cabinet that can take a string and one that cannot
			if (!section && groupName.startsWith("section")) continue;

			poseStack.pushPose();
			// the whole machine scales about the middle of its own footprint, so a small one sits on
			// the ground rather than hovering over it - but not the cable entry, which has to reach the
			// edge of the block whatever size the cabinet is, because that is where the cable is
			if (!groupName.startsWith("entry")) poseStack.scale(scale, scale, scale);

			if (groupName.startsWith("rotate_fan")) {
				poseStack.translate(hub.x, hub.y, hub.z);
				poseStack.mulPose(Axis.XP.rotationDegrees(fanAngle));
				poseStack.translate(-hub.x, -hub.y, -hub.z);
			}

			poses.put(groupName, new Matrix4f(poseStack.last().pose()));
			poseStack.popPose();
		}

		renderGrouped(model, poses, projectionMatrix, texture, packedLight, inverter.getBlockPos(), BUFFER_CACHE);
	}

	/**
	 * How large to draw a machine, from its nameplate.
	 *
	 * Three sizes rather than a continuous scale, and they are the same three
	 * {@link PvInverterBlock} picks its collision shape from - so the thing a player bumps into is the
	 * thing they can see. The authored model is the commercial cabinet, which is why that one is 1:1.
	 */
	private static double renderScale(InverterSpec spec) {
		if (spec.acPowerKw() >= 1000.0) return 1.0;
		if (spec.acPowerKw() <= 30.0) return 0.62;

		return 0.92;
	}

	/**
	 * Turns the fan on by the cabinet temperature, and keeps the angle between frames.
	 *
	 * Held still while the game is paused, so a paused world does not have a fan creeping round in a
	 * screenshot.
	 */
	private static float advanceFan(BlockPos pos, double cabinetTempC, InverterSpec.Cooling cooling) {
		float current = FAN_ANGLE.getOrDefault(pos, 0.0f);
		if (cooling == InverterSpec.Cooling.NATURAL || Minecraft.getInstance().isPaused()) return current;

		double duty = Math.max(0.0, Math.min(1.0, (cabinetTempC - FAN_IDLE_C) / (FAN_FULL_C - FAN_IDLE_C)));
		float next = (current + (float) (duty * FAN_MAX_STEP)) % 360.0f;
		FAN_ANGLE.put(pos, next);
		return next;
	}

	public static void init() {
		for (InverterSpec spec : InverterCatalog.all()) {
			ObjBlockDefinition definition = ObjDefinitions.get(Electricity.PV_INVERTER_BLOCKS.get(spec.id()).get());
			if (definition == null) continue;

			ObjBlockRegistry.register(definition);
			registerInsulators(definition);
		}
	}

	/** The wire fitting on top of the cabinet, so a wire can be attached to it and seen where it attaches. */
	private static void registerInsulators(ObjBlockDefinition definition) {
		ObjModel model = ObjLoader.getModel(definition.model());
		if (model == null) return;

		Map<String, ObjModel.BoundingBox> boxes = new HashMap<>();
		for (String groupName : definition.insulators()) {
			ObjModel.BoundingBox box = model.getBoundingBox(groupName);
			if (box != null) {
				boxes.put(groupName, box);
			}
		}

		ObjBoundingBoxRegistry.registerBoundingBoxes(definition.block(), boxes);
	}

}
