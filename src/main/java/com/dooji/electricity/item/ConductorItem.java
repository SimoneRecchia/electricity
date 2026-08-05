package com.dooji.electricity.item;

import com.dooji.electricity.api.power.ConductorSpec;
import com.dooji.electricity.client.hooks.WireClientHooks;
import com.dooji.electricity.main.Electricity;
import java.util.List;
import javax.annotation.Nullable;
import com.dooji.electricity.block.MachineShell;
import com.dooji.electricity.wire.InsulatorHost;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** A reel of overhead line conductor: click one fitting, then another, and the span is strung between. */
public class ConductorItem extends Item {
	private final ConductorSpec spec;

	public ConductorItem(ConductorSpec spec) {
		super(new Properties().stacksTo(64));
		this.spec = spec;
	}

	public ConductorSpec spec() {
		return spec;
	}

	/**
	 * Clicked on a fitting it strings a span; clicked on anything else it lays a length on the ground.
	 *
	 * The reel does both because that is what a reel is - the same conductor whether it goes up a tower or
	 * along a yard - and because there is nothing else for the player to be holding. A fitting is decided
	 * by the block that is really there, {@link MachineShell#hostOr} first, so a click on a machine's shell
	 * cell reaches the machine.
	 */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos host = MachineShell.hostOr(level, context.getClickedPos());
		if (level.getBlockEntity(host) instanceof InsulatorHost) {
			if (level.isClientSide) {
				return DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> WireClientHooks.handleUseOn(context));
			}

			return Electricity.wireManager.handleWireUse(context);
		}

		return layRun(context);
	}

	/**
	 * Lays one block of conductor where the player clicked.
	 *
	 * Through the block's own placement, so the run connects to whatever it lands next to and refuses a
	 * spot with nothing under it. One item a block, and in creative none.
	 */
	private InteractionResult layRun(UseOnContext context) {
		Block block = Electricity.GROUND_CONDUCTOR_BLOCKS.get(spec.id()).get();
		BlockPlaceContext placement = new BlockPlaceContext(context);
		if (!placement.canPlace()) return InteractionResult.PASS;

		BlockPos pos = placement.getClickedPos();
		Level level = context.getLevel();
		BlockState state = block.getStateForPlacement(placement);
		if (state == null || !state.canSurvive(level, pos)) return InteractionResult.PASS;

		if (level.isClientSide) return InteractionResult.SUCCESS;

		level.setBlock(pos, state, Block.UPDATE_ALL);
		level.playSound(null, pos, block.getSoundType(state).getPlaceSound(), SoundSource.BLOCKS, 0.8f, 1.1f);
		Player player = context.getPlayer();
		if (player != null && !player.isCreative()) {
			context.getItemInHand().shrink(1);
		}

		return InteractionResult.CONSUME;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("tooltip.electricity." + spec.id().getPath())
				.withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.electricity.conductor.reach", spec.blocksPerItem(),
				(int) spec.maxSpan()).withStyle(ChatFormatting.DARK_GRAY));
	}
}
