package com.dooji.electricity.client.render.block;

import com.dooji.electricity.block.SwitchgearBlock;
import com.dooji.electricity.block.SwitchgearBlockEntity;
import com.dooji.electricity.client.render.obj.ObjBlockRegistry;
import com.dooji.electricity.client.render.obj.ObjModel;
import com.dooji.electricity.client.render.obj.ObjRenderContext;
import com.dooji.electricity.client.render.obj.ObjRendererBase;
import com.dooji.electricity.main.Electricity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Draws the two switches, and swings a disconnector's blades when it is open.
 *
 * The blade turns about the hinge its own model marks with {@code pivot_blade}, which runs along x - so one
 * marker serves all three poles, because a rotation about x does not care where along x a part is.  A
 * breaker has nothing that moves: its contacts are inside a sealed bottle, and the two flag plates are what
 * it can show instead.
 */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Electricity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class SwitchgearRenderer extends ObjRendererBase {
	private static final double MAX_RENDER_DISTANCE_SQ = 96 * 96;
	private static final Map<BlockPos, Map<String, GroupBuffer>> BUFFER_CACHE = new HashMap<>();

	/** How far a blade swings, in degrees: far enough that the gap reads as a gap from anywhere. */
	private static final float OPEN_DEGREES = 84.0f;

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		drawAll(event, SwitchgearBlockEntity.class, MAX_RENDER_DISTANCE_SQ, state -> state.getValue(SwitchgearBlock.FACING),
				SwitchgearBlock.AUTHORED, BUFFER_CACHE, SwitchgearRenderer::render);
	}

	private static void render(SwitchgearBlockEntity gear, ObjRenderContext context, PoseStack poseStack, Matrix4f projection) {
		ObjModel model = context.model();
		boolean open = SwitchgearBlock.open(gear.getBlockState());
		Vec3 hinge = pivot(model, "blade", new Vec3(0.0, 0.336, 0.155));
		Map<String, Matrix4f> poses = new HashMap<>();

		for (String groupName : model.groups.keySet()) {
			if (groupName.startsWith("pivot_") || !drawn(groupName, open)) continue;

			poseStack.pushPose();
			if (open && groupName.startsWith("rotate_")) {
				poseStack.translate(0.0, hinge.y, hinge.z);
				poseStack.mulPose(Axis.XP.rotationDegrees(OPEN_DEGREES));
				poseStack.translate(0.0, -hinge.y, -hinge.z);
			}

			poses.put(groupName, new Matrix4f(poseStack.last().pose()));
			poseStack.popPose();
		}

		renderGrouped(model, poses, projection, context.texture(), context.packedLight(), gear.getBlockPos(), BUFFER_CACHE);
	}

	/** The two flag plates are one plate: whichever the position calls for. */
	private static boolean drawn(String groupName, boolean open) {
		if (groupName.startsWith("flag_open")) return open;
		if (groupName.startsWith("flag_shut")) return !open;

		return true;
	}

	public static void init() {
		ObjBlockRegistry.registerAll(Electricity.SWITCHGEAR_BLOCKS);
	}
}
