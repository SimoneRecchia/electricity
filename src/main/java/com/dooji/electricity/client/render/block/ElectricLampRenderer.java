package com.dooji.electricity.client.render.block;

import com.dooji.electricity.block.ElectricLampBlock;
import com.dooji.electricity.block.ElectricLampBlockEntity;
import com.dooji.electricity.client.TrackedBlockEntities;
import com.dooji.electricity.client.render.obj.ObjBlockRegistry;
import com.dooji.electricity.client.render.obj.ObjModel;
import com.dooji.electricity.client.render.obj.ObjRenderUtil;
import com.dooji.electricity.client.render.obj.ObjRendererBase;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.registry.ObjBlockDefinition;
import com.dooji.electricity.main.registry.ObjDefinitions;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws the lamp, and picks which of its six lenses is the lit one.
 *
 * <h2>Why the lamp draws itself now</h2>
 *
 * It used to be a cube with a picture of a light on all six faces, because that is all a vanilla JSON
 * model can be: the format has axis-aligned boxes and rotations of a quarter of a right angle, so a round
 * column cannot be expressed in it at all - a rotated box is a *union* rather than an intersection, so
 * three of them make a twelve-pointed star and not a twelve-sided prism. The rest of this mod's machines
 * are drawn from OBJ by Java, at the turbine's own eighty sides, and the lamp is now one of them.
 *
 * <h2>How the glow state changes the texture</h2>
 *
 * Not by swapping a texture, because this pipeline binds one texture per material and caches a vertex
 * buffer per group. So the model carries the glass bowl six times over, once per state, each with its own
 * material and therefore its own texture - and this draws exactly one of them. Six copies of one box is
 * thirty-six faces, which is cheaper than any way of rebuilding a buffer when a lamp dims.
 *
 * <h2>What lights it</h2>
 *
 * The level's own light at the lamp's position, the same as every other machine here. That is not a
 * shortcut: the block emits light in proportion to its glow state - fifteen on overdrive, nothing when
 * burnt - so the lamp is lit by what it is emitting, and a brighter state really is drawn brighter
 * without anything having to be told twice.
 */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Electricity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ElectricLampRenderer extends ObjRendererBase {
	/**
	 * How far a lamp is drawn from.
	 *
	 * Further than the machines, because a lamp is the one block here whose whole purpose is to be seen
	 * from a distance: a street of them going dark at ninety-six blocks while their light stayed would be
	 * the most visible thing in the mod.
	 */
	private static final double MAX_RENDER_DISTANCE_SQ = 160 * 160;
	private static final Map<BlockPos, Map<String, GroupBuffer>> BUFFER_CACHE = new HashMap<>();

	/** The prefix the generator gives every lens group, and the whole of the contract with it. */
	private static final String LENS = "lens_";

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;

		Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
		HashSet<BlockPos> seen = new HashSet<>();

		for (ElectricLampBlockEntity lamp : TrackedBlockEntities.ofType(ElectricLampBlockEntity.class)) {
			seen.add(lamp.getBlockPos());
			// no facing: a post-top luminaire is symmetric about its own column, which is why it is the
			// shape it is - the block has no facing property to point anything with
			ObjRenderUtil.withAlignedPose(lamp, event.getPoseStack(), mc.renderBuffers().bufferSource(), cameraPos,
					MAX_RENDER_DISTANCE_SQ, state -> Direction.NORTH, facing -> 0.0f,
					(context, pose, buffers) -> renderGrouped(context.model(), pose, event.getProjectionMatrix(),
							context.texture(), context.packedLight(), lamp.getBlockPos(), BUFFER_CACHE,
							groupName -> drawn(groupName, lamp.getBlockState().getValue(ElectricLampBlock.GLOW_STATE))));
		}

		cleanupCache(BUFFER_CACHE, seen);
	}

	/**
	 * Whether one group of the lamp's model is drawn.
	 *
	 * Everything that is not a lens, and the one lens whose name carries this state. The trailing
	 * underscore matters: the group name is the object's name and its material's, so {@code lens_off} is
	 * really {@code lens_off_glass_off}, and matching without it would have {@code off} accept nothing
	 * while a state that was a prefix of another accepted both.
	 */
	private static boolean drawn(String groupName, ElectricLampBlock.LampState state) {
		if (!groupName.startsWith(LENS)) return true;

		return groupName.startsWith(LENS + state.getSerializedName() + "_");
	}

	public static void init() {
		ObjBlockDefinition definition = ObjDefinitions.get(Electricity.ELECTRIC_LAMP_BLOCK.get());
		if (definition != null) {
			ObjBlockRegistry.register(definition);
		}
	}
}
