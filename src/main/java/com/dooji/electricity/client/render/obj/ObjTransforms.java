package com.dooji.electricity.client.render.obj;

import com.dooji.electricity.block.UtilityPoleBlockEntity;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

// OBJ pipeline code will be migrated to Renderix
@OnlyIn(Dist.CLIENT)
public final class ObjTransforms {
	private ObjTransforms() {
	}

	public record Transform(float offsetX, float offsetY, float offsetZ, float yaw, float pitch) {
	}

	public static Transform resolve(BlockEntity blockEntity) {
		if (blockEntity instanceof WindTurbineBlockEntity turbine) {
			// The model is authored from the foot of the tower up, and the machine is the top
			// block of the structure, so everything it draws belongs one whole tower lower than
			// the block it lives in.
			//
			// It lives here rather than in the renderer because ObjRaycaster resolves the same
			// transform to place an interactive part's hitbox. With the offset applied only while
			// drawing, the wire fitting was visible at the tower's foot and clickable up beside
			// the nacelle - a wire could be seen but not grabbed.
			return new Transform(0f, -turbine.getTowerSegments(), 0f, 0f, 0f);
		}

		if (blockEntity instanceof UtilityPoleBlockEntity entity) {
			return new Transform(entity.getOffsetX(), entity.getOffsetY(), entity.getOffsetZ(), (float) Math.toDegrees(entity.getYaw()), (float) Math.toDegrees(entity.getPitch()));
		}

		return new Transform(0f, 0f, 0f, 0f, 0f);
	}
}
