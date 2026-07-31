package com.dooji.electricity.compat.energy;

import com.dooji.electricity.api.power.IEnergyBudget;
import com.dooji.electricity.compat.mekanism.MekanismEnergyBridge;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.ModList;

/**
 * Exposes an {@link IEnergyBudget} to the energy systems of other mods and pushes
 * the budget out to neighbouring blocks once per tick.
 *
 * Everything that names a Mekanism class lives in {@link MekanismEnergyBridge},
 * which is only ever reached from behind {@link #mekanismLoaded()}. That keeps
 * this class loadable when Mekanism is absent, since the JVM only resolves a
 * class the first time an instruction referencing it actually runs.
 */
public final class EnergyBridge {
	/**
	 * Mekanism converts Forge Energy at 2.5 J per FE by default, and the Power Box
	 * has always run at 50 FE per kW, so one kW is worth 125 J. Deriving both
	 * numbers from what the mod already did keeps the wire network, the Power Box
	 * and the turbines in agreement instead of inventing a third scale.
	 */
	public static final double JOULES_PER_FE = 2.5;
	public static final double JOULES_PER_KW = 125.0;

	private static Boolean mekanismLoaded;

	private EnergyBridge() {
	}

	public static boolean mekanismLoaded() {
		if (mekanismLoaded == null) {
			mekanismLoaded = ModList.get().isLoaded("mekanism");
		}

		return mekanismLoaded;
	}

	/**
	 * Builds the Mekanism-side handler for a budget, or an empty optional when
	 * Mekanism is not installed. The result is deliberately wildcarded so callers
	 * can hold it in a field without naming a Mekanism type.
	 */
	public static LazyOptional<?> createMekanismHandler(IEnergyBudget budget) {
		if (!mekanismLoaded()) return LazyOptional.empty();

		return MekanismEnergyBridge.createHandler(budget);
	}

	public static boolean isMekanismEnergyCapability(Capability<?> cap) {
		return mekanismLoaded() && MekanismEnergyBridge.isEnergyCapability(cap);
	}

	/**
	 * A Forge Energy view of a budget, for the mods that do not speak Joules. Push
	 * only: energy can be extracted but never inserted, which is how a Mekanism
	 * generator behaves.
	 */
	public static IEnergyStorage forgeEnergyView(IEnergyBudget budget) {
		return new IEnergyStorage() {
			@Override
			public int receiveEnergy(int maxReceive, boolean simulate) {
				return 0;
			}

			@Override
			public int extractEnergy(int maxExtract, boolean simulate) {
				// claim exactly the Joules matching the whole FE handed over, so the
				// rounding down to integer FE does not quietly burn the remainder
				int available = (int) Math.floor(budget.getAvailableJoules() / JOULES_PER_FE);
				int extracted = Math.min(Math.max(0, maxExtract), available);
				if (extracted <= 0) return 0;

				budget.claimJoules(extracted * JOULES_PER_FE, simulate);
				return extracted;
			}

			@Override
			public int getEnergyStored() {
				return (int) Math.floor(budget.getAvailableJoules() / JOULES_PER_FE);
			}

			@Override
			public int getMaxEnergyStored() {
				return (int) Math.floor(budget.getMaxJoulesPerTick() / JOULES_PER_FE);
			}

			@Override
			public boolean canExtract() {
				return true;
			}

			@Override
			public boolean canReceive() {
				return false;
			}
		};
	}

	/**
	 * Pushes this tick's budget to the acceptors adjacent to {@code source}, the
	 * way a Mekanism generator emits from its energy sides every tick rather than
	 * waiting to be pulled from.
	 */
	public static void emit(BlockEntity source, IEnergyBudget budget, BlockPos origin, Iterable<Direction> faces) {
		Level level = source.getLevel();
		if (level == null || level.isClientSide()) return;
		if (budget.getAvailableJoules() <= 0.0) return;

		List<EnergyAcceptor> acceptors = new ArrayList<>();
		for (Direction face : faces) {
			BlockEntity neighbour = level.getBlockEntity(origin.relative(face));
			if (neighbour == null || neighbour.isRemoved()) continue;

			EnergyAcceptor acceptor = findAcceptor(neighbour, face.getOpposite());
			if (acceptor != null) acceptors.add(acceptor);
		}

		if (acceptors.isEmpty()) return;

		// the same fair split Mekanism uses: each acceptor is offered an equal share
		// of what is left, so whatever one turns down is re-offered to the rest
		int remainingTargets = acceptors.size();
		for (EnergyAcceptor acceptor : acceptors) {
			double available = budget.getAvailableJoules();
			if (available <= 0.0) break;

			double accepted = acceptor.accept(available / remainingTargets, false);
			if (accepted > 0.0) budget.claimJoules(accepted, false);
			remainingTargets--;
		}
	}

	/**
	 * Mekanism's own handler is preferred over Forge Energy, matching the priority
	 * order in Mekanism's EnergyCompatUtils. Going through FE would round every
	 * transfer to whole FE for no reason.
	 */
	@Nullable
	private static EnergyAcceptor findAcceptor(BlockEntity neighbour, Direction side) {
		if (mekanismLoaded()) {
			EnergyAcceptor mekanism = MekanismEnergyBridge.findAcceptor(neighbour, side);
			if (mekanism != null) return mekanism;
		}

		IEnergyStorage storage = neighbour.getCapability(ForgeCapabilities.ENERGY, side).orElse(null);
		if (storage == null || !storage.canReceive()) return null;

		return (joules, simulate) -> {
			int offered = (int) Math.floor(joules / JOULES_PER_FE);
			if (offered <= 0) return 0.0;

			return storage.receiveEnergy(offered, simulate) * JOULES_PER_FE;
		};
	}
}
