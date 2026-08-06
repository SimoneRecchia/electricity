package com.dooji.electricity.client.render.block;

import com.dooji.electricity.api.power.InverterSpec;
import com.dooji.electricity.block.PvInverterBlock;
import com.dooji.electricity.block.PvInverterBlockEntity;
import com.dooji.electricity.client.render.obj.ObjBlockRegistry;
import com.dooji.electricity.client.render.obj.ObjModel;
import com.dooji.electricity.client.render.obj.ObjRenderContext;
import com.dooji.electricity.client.render.obj.ObjRendererBase;
import com.dooji.electricity.main.Electricity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Draws the inverter cabinets, and spins the fan that is keeping them alive. */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Electricity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PvInverterRenderer extends ObjRendererBase {
	private static final double MAX_RENDER_DISTANCE_SQ = 96 * 96;
	private static final Map<BlockPos, Map<String, GroupBuffer>> BUFFER_CACHE = new HashMap<>();
	/** Where each fan has got to, so it turns continuously rather than restarting every frame. */
	private static final Map<BlockPos, Float> FAN_ANGLE = new HashMap<>();

	/** Cabinet temperature at which the fan is at a standstill */
	private static final double FAN_IDLE_C = 25.0;
	private static final double FAN_FULL_C = 60.0;
	/** Degrees a frame at full speed. */
	private static final float FAN_MAX_STEP = 22.0f;

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		cleanupAngles(FAN_ANGLE, drawAll(event, PvInverterBlockEntity.class, MAX_RENDER_DISTANCE_SQ,
				state -> state.getValue(PvInverterBlock.FACING), PvInverterBlock.AUTHORED, BUFFER_CACHE, PvInverterRenderer::render));
	}

	private static void render(PvInverterBlockEntity inverter, ObjRenderContext context, PoseStack poseStack, Matrix4f projection) {
		ObjModel model = context.model();
		InverterSpec spec = inverter.spec();
		float scale = (float) renderScale(spec);
		float fanAngle = advanceFan(inverter.getBlockPos(), inverter.cabinetTempC(), spec.cooling());

		Direction facing = inverter.getBlockState().getValue(PvInverterBlock.FACING);
		Set<String> entries = inverter.getLevel() == null ? Set.of()
				: cableEntries(inverter.getLevel(), inverter.getBlockPos(), PvInverterBlock.AUTHORED, facing, "entry");
		boolean section = PvInverterBlock.hasCombiner(inverter.getBlockState());
		Map<String, Matrix4f> poses = new HashMap<>();

		for (String groupName : model.groups.keySet()) {
			if (groupName.startsWith("pivot_")) continue;
			if (!entryVisible(groupName, "entry", entries)) continue;
			// the lower front is the DC compartment or the plate that blanks its aperture off
			if (groupName.startsWith("section") && !section) continue;
			if (groupName.startsWith("blank") && section) continue;

			poseStack.pushPose();
			// the whole machine scales about the middle of its own footprint
			if (!groupName.startsWith("entry")) poseStack.scale(scale, scale, scale);

			// Each roof fan turns about its own hub, and about the vertical.
			if (groupName.startsWith("rotate_fan")) {
				Vec3 hub = pivot(model, "fan_" + groupName.substring("rotate_fan_".length()).split("_")[0],
						new Vec3(0.0, 1.0, 0.0));
				poseStack.translate(hub.x, hub.y, hub.z);
				poseStack.mulPose(Axis.YP.rotationDegrees(fanAngle));
				poseStack.translate(-hub.x, -hub.y, -hub.z);
			}

			poses.put(groupName, new Matrix4f(poseStack.last().pose()));
			poseStack.popPose();
		}

		renderGrouped(model, poses, projection, context.texture(), context.packedLight(), inverter.getBlockPos(), BUFFER_CACHE);
	}

	/** How large to draw a machine, from its nameplate. */
	private static double renderScale(InverterSpec spec) {
		if (spec.acPowerKw() >= 1000.0) return 1.0;
		if (spec.acPowerKw() <= 30.0) return 0.62;

		return 0.92;
	}

	/** Turns the fan on by the cabinet temperature, and keeps the angle between frames. */
	private static float advanceFan(BlockPos pos, double cabinetTempC, InverterSpec.Cooling cooling) {
		float current = FAN_ANGLE.getOrDefault(pos, 0.0f);
		if (cooling == InverterSpec.Cooling.NATURAL || Minecraft.getInstance().isPaused()) return current;

		double duty = Math.max(0.0, Math.min(1.0, (cabinetTempC - FAN_IDLE_C) / (FAN_FULL_C - FAN_IDLE_C)));
		float next = (current + (float) (duty * FAN_MAX_STEP)) % 360.0f;
		FAN_ANGLE.put(pos, next);
		return next;
	}

	public static void init() {
		ObjBlockRegistry.registerAll(Electricity.PV_INVERTER_BLOCKS);
	}
}
