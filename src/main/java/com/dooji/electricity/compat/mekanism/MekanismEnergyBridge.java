package com.dooji.electricity.compat.mekanism;

import com.dooji.electricity.api.power.IEnergyBudget;
import com.dooji.electricity.compat.energy.EnergyAcceptor;
import javax.annotation.Nullable;
import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.math.FloatingLong;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.LazyOptional;

/** The only class in this mod that names a Mekanism type. */
public final class MekanismEnergyBridge {
	private static final Capability<IStrictEnergyHandler> STRICT_ENERGY = CapabilityManager.get(new CapabilityToken<>() {
	});

	private MekanismEnergyBridge() {
	}

	public static boolean isEnergyCapability(Capability<?> cap) {
		return cap == STRICT_ENERGY;
	}

	public static LazyOptional<IStrictEnergyHandler> createHandler(IEnergyBudget budget) {
		return LazyOptional.of(() -> new BudgetEnergyHandler(budget));
	}

	@Nullable
	public static EnergyAcceptor findAcceptor(BlockEntity neighbour, Direction side) {
		IStrictEnergyHandler handler = neighbour.getCapability(STRICT_ENERGY, side).orElse(null);
		if (handler == null) return null;

		return (joules, simulate) -> {
			FloatingLong offered = joules(joules);
			if (offered.isZero()) return 0.0;

			// insertEnergy hands back the remainder, not the amount taken
			FloatingLong remainder = handler.insertEnergy(offered, simulate ? Action.SIMULATE : Action.EXECUTE);
			return offered.subtract(remainder).doubleValue();
		};
	}

	private static FloatingLong joules(double value) {
		if (!(value > 0.0)) return FloatingLong.ZERO;

		return FloatingLong.create(value);
	}

	/**
	 * Presents a per-tick budget as a single output-only energy container, which is how Mekanism's own generators expose themselves: external handlers may extract, nothing may insert.
	 */
	private static final class BudgetEnergyHandler implements IStrictEnergyHandler {
		private final IEnergyBudget budget;

		private BudgetEnergyHandler(IEnergyBudget budget) {
			this.budget = budget;
		}

		@Override
		public int getEnergyContainerCount() {
			return 1;
		}

		@Override
		public FloatingLong getEnergy(int container) {
			if (container != 0) return FloatingLong.ZERO;

			return joules(budget.getAvailableJoules());
		}

		@Override
		public void setEnergy(int container, FloatingLong energy) {
			// output only, the budget is owned by the generator
		}

		@Override
		public FloatingLong getMaxEnergy(int container) {
			if (container != 0) return FloatingLong.ZERO;

			return joules(budget.getMaxJoulesPerTick());
		}

		@Override
		public FloatingLong getNeededEnergy(int container) {
			return FloatingLong.ZERO;
		}

		@Override
		public FloatingLong insertEnergy(int container, FloatingLong amount, Action action) {
			// nothing is accepted, so the whole amount comes back as the remainder
			return amount.copy();
		}

		@Override
		public FloatingLong extractEnergy(int container, FloatingLong amount, Action action) {
			if (container != 0 || amount.isZero()) return FloatingLong.ZERO;

			return joules(budget.claimJoules(amount.doubleValue(), action.simulate()));
		}
	}
}
