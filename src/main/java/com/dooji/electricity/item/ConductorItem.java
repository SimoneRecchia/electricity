package com.dooji.electricity.item;

import com.dooji.electricity.api.power.ConductorSpec;
import com.dooji.electricity.client.hooks.WireClientHooks;
import com.dooji.electricity.main.Electricity;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * A reel of overhead line conductor: click one fitting, then another, and the span is strung between.
 *
 * <h2>What it costs</h2>
 *
 * A span costs conductor by its <i>length</i>, which is the whole point of the item: one reel buys a
 * number of blocks that depends on the class - four for a street bundle, sixteen for a transmission
 * bundle - and a span longer than what the player is carrying is refused rather than part-built.
 *
 * The charge is worked out and taken on the <b>server</b>, in {@code WireManager}, after every other
 * check has passed and before the connection is saved; and the number taken is <i>stored on the
 * connection</i> so that taking the line down again refunds exactly what was paid. Both of those are
 * about the same failure: a span whose cost is recomputed on removal from a span length that has since
 * changed - because a machine moved, or the world was loaded by a different build - refunds the wrong
 * number, and refunding more than was charged is a duplication bug.
 *
 * <h2>Why the item is the type</h2>
 *
 * There is no menu and no mode. Which conductor a span is made of is which reel was in the player's hand
 * when they clicked, read off the hand on the server rather than sent from the client - so a client that
 * lies about it gets the conductor it is actually holding.
 */
public class ConductorItem extends Item {
	private final ConductorSpec spec;

	public ConductorItem(ConductorSpec spec) {
		super(new Properties().stacksTo(64));
		this.spec = spec;
	}

	public ConductorSpec spec() {
		return spec;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getLevel().isClientSide) {
			return DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> WireClientHooks.handleUseOn(context));
		}

		return Electricity.wireManager.handleWireUse(context);
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("tooltip.electricity." + spec.id().getPath())
				.withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.electricity.conductor.reach", spec.blocksPerItem(),
				(int) spec.maxSpan()).withStyle(ChatFormatting.DARK_GRAY));
	}
}
