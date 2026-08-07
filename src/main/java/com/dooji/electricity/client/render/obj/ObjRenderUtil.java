package com.dooji.electricity.client.render.obj;

import com.dooji.electricity.block.ModelFacing;
import com.dooji.electricity.client.render.obj.ObjTransforms.Transform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

// OBJ pipeline code will be migrated to Renderix
@OnlyIn(Dist.CLIENT)
public final class ObjRenderUtil {
	private ObjRenderUtil() {
	}

	/** Puts one machine's own frame on the stack - turned onto its facing, then by whatever it carries - and draws it. */
	public static void withAlignedPose(BlockEntity entity, PoseStack poseStack, Vec3 cameraPos, double maxDistanceSq,
			Function<BlockState, Direction> facingExtractor, Direction authored, BiConsumer<ObjRenderContext, PoseStack> action) {
		var level = entity.getLevel();
		if (level == null) return;

		BlockPos pos = entity.getBlockPos();
		if (cameraPos.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > maxDistanceSq) return;

		BlockState blockState = entity.getBlockState();
		var block = blockState.getBlock();
		if (!ObjBlockRegistry.hasModel(block)) return;

		ResourceLocation modelLocation = ObjBlockRegistry.getModelLocation(block);
		ObjModel model = ObjLoader.getModel(modelLocation);
		if (model == null) return;

		ResourceLocation texture = ObjBlockRegistry.getTextureLocation(block);
		Direction facing = facingExtractor != null ? facingExtractor.apply(blockState) : null;
		float facingRotation = facing == null || authored == null ? 0.0f : ModelFacing.degrees(authored, facing);
		Transform transform = ObjTransforms.resolve(entity);
		int packedLight = LevelRenderer.getLightColor(level, pos);

		Vec3 viewPos = new Vec3(pos.getX(), pos.getY(), pos.getZ()).subtract(cameraPos);

		poseStack.pushPose();
		poseStack.translate(viewPos.x, viewPos.y, viewPos.z);
		poseStack.translate(0.5, 0, 0.5);
		if (facingRotation != 0) {
			poseStack.mulPose(Axis.YP.rotationDegrees(facingRotation));
		}
		poseStack.translate(transform.offsetX(), transform.offsetY(), transform.offsetZ());
		if (transform.yaw() != 0) {
			poseStack.mulPose(Axis.YP.rotationDegrees(transform.yaw()));
		}
		if (transform.pitch() != 0) {
			poseStack.mulPose(Axis.XP.rotationDegrees(transform.pitch()));
		}

		action.accept(new ObjRenderContext(model, texture, packedLight), poseStack);
		poseStack.popPose();
	}
}
