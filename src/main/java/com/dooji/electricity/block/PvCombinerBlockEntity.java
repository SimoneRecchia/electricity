package com.dooji.electricity.block;

import com.dooji.electricity.api.power.CombinerSpec;
import com.dooji.electricity.api.power.DcCableSpec;
import com.dooji.electricity.api.power.PvArraySpec;
import com.dooji.electricity.api.power.PvModuleSpec;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.ElectricityServerConfig;
import com.dooji.electricity.main.registry.CableCatalog;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The box that turns many strings into one pair.
 *
 * It sits between the arrays and the cabinet and behaves like both: it claims arrays down a run of
 * string cable exactly as an inverter does, and it is itself claimed by an inverter down a run of trunk
 * cable exactly as an array is. The operating point comes back the other way - the inverter tells the
 * box what fraction of the maximum power point it can take, and the box tells its arrays - so clipping
 * still shows up in the current reading of every module behind it.
 *
 * <h2>What it refuses, and why each refusal is real</h2>
 *
 * Four things off its own datasheet, and the interesting one is the fuses. IEC 62548 wants a string fuse
 * at 1.4 times the string's short-circuit current, so a twenty-amp fuse cannot protect an eighteen-amp
 * string - and the six-way box in this catalogue is a twenty-amp box. It is not a toy: it was a
 * perfectly ordinary specification when a module made nine amps, and it is the wrong box now for exactly
 * the reason its label says.
 *
 * The other three are the insulation, the output switch, and the ways.
 *
 * <h2>The trunk is the fifth limit, and it is not on the box's datasheet</h2>
 *
 * It is on the cable's. Thirty-two high-current strings is five hundred and sixty-six amps, which one
 * 240 mm² pair carries in free air and does not carry buried - so filling a thirty-two way box and then
 * digging its trunk in costs a string, and the box says which limit took it.
 */
public class PvCombinerBlockEntity extends BlockEntity {
	/** How often the box looks for arrays, in ticks. The same two seconds an inverter uses. */
	private static final int RESCAN_TICKS = 40;
	/**
	 * How long a claim outlives the last word from the inverter that made it, in ticks.
	 *
	 * The same lease an array holds, and for the same reason: an inverter that is broken, stopped or in a
	 * chunk that is no longer loaded simply stops renewing, and the box releases itself rather than
	 * waiting to be told.
	 */
	private static final int CLAIM_LEASE_TICKS = 100;

	private final ClientSync clientSync = new ClientSync();
	private final List<BlockPos> arrays = new ArrayList<>();
	private int rescanCountdown = 0;

	private int stringsConnected = 0;
	private double stringCurrentHeadroom = 0.0;
	/** Nameplate of everything wired into the box, kW: what an inverter sizes itself against. */
	private double nameplateDcKw = 0.0;
	/** Why the last string was turned away, for the panel to name. */
	private CombinerSpec.Refusal refusal = CombinerSpec.Refusal.NONE;

	private double availableDcKw = 0.0;
	private double busVoltage = 0.0;
	private double busCurrent = 0.0;
	private double mpptFraction = 0.0;

	/** The inverter collecting this box's output, and the trunk run to it. */
	private BlockPos inverterPos = null;
	private DcCableSpec trunk = null;
	private double runMetres = 0.0;
	private double runBuriedFraction = 0.0;
	private int claimAge = 0;

	public PvCombinerBlockEntity(BlockPos pos, BlockState state) {
		super(Electricity.PV_COMBINER_BLOCK_ENTITY.get(), pos, state);
	}

	public CombinerSpec spec() {
		return getBlockState().getBlock() instanceof PvCombinerBlock combiner ? combiner.spec() : null;
	}

	@Override
	public void onLoad() {
		super.onLoad();
		ClientTracking.track(this);
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		ClientTracking.untrack(this);
	}

	// ---- the tick ----

	public void serverTick() {
		if (!(level instanceof ServerLevel serverLevel)) return;

		CombinerSpec spec = spec();
		if (spec == null) return;

		if (--rescanCountdown <= 0) {
			rescan(serverLevel, spec);
			rescanCountdown = RESCAN_TICKS;
		}

		expireClaim();
		gather(serverLevel, spec);
		clientSync.throttled(this);
	}

	/**
	 * Finds the arrays wired to this box, shortest run first, and takes what it can protect.
	 *
	 * The order is what makes the refusal worth reporting: the nearest strings are the ones a designer
	 * would have brought here, so a box that runs out of ways runs out of them on the far rows.
	 */
	private void rescan(ServerLevel serverLevel, CombinerSpec spec) {
		releaseArrays();
		arrays.clear();
		stringsConnected = 0;
		nameplateDcKw = 0.0;
		refusal = CombinerSpec.Refusal.NONE;

		double currentCeiling = outputCeiling(spec);
		double currentConnected = 0.0;

		for (DcNetwork.Reach reach : PvStrings.reachable(serverLevel, worldPosition, CableCatalog.STRING_6,
				ElectricityServerConfig.maxCableRun())) {
			PvArrayBlockEntity array = LoadedBlockEntities.find(serverLevel, reach.pos(), PvArrayBlockEntity.class);
			if (array == null) continue;

			PvArraySpec wanted = array.spec();
			CombinerSpec.Refusal why = spec.accepts(wanted, stringsConnected, currentConnected, currentCeiling);
			if (why != CombinerSpec.Refusal.NONE) {
				refusal = worse(refusal, why);
				continue;
			}

			if (!array.claim(worldPosition, reach, CableCatalog.STRING_6)) continue;

			arrays.add(array.getBlockPos().immutable());
			stringsConnected += wanted.strings();
			nameplateDcKw += wanted.dcPowerKw();
			currentConnected += wanted.stringCurrent(PvModuleSpec.STC_IRRADIANCE, PvModuleSpec.STC_TEMPERATURE) * wanted.strings();
		}

		stringCurrentHeadroom = currentCeiling - currentConnected;
	}

	/**
	 * What the output can actually carry, in amps: the switch, or the trunk behind it.
	 *
	 * The trunk's rating is not on the box's datasheet and is still the box's problem, which is why it is
	 * folded in here. Before an inverter has claimed the box there is no trunk to measure, so the switch
	 * stands alone until there is - and burying the trunk afterwards drops a string at the next rescan,
	 * which is the right way round: the cable derates and the box notices.
	 */
	private double outputCeiling(CombinerSpec spec) {
		if (trunk == null) return spec.outputAmps();

		return Math.min(spec.outputAmps(), trunk.ampacity(runBuriedFraction));
	}

	/**
	 * The more informative of two refusals.
	 *
	 * Lowest wins, because {@link CombinerSpec.Refusal} is ordered worst-first: a wrong-product answer is
	 * worth saying over a this-box-is-full one, since only one of the two has an answer a player can act
	 * on by buying a different box.
	 */
	private static CombinerSpec.Refusal worse(CombinerSpec.Refusal current, CombinerSpec.Refusal candidate) {
		if (current == CombinerSpec.Refusal.NONE) return candidate;

		return current.ordinal() <= candidate.ordinal() ? current : candidate;
	}

	/**
	 * Adds up what the strings are offering and pushes the operating point back to them.
	 *
	 * The box adds nothing to the power and takes two things off it: the drop down its own trunk, and
	 * whatever its monitoring draws. Both are small and both are real, and the second is the reason a
	 * combiner box has a self-consumption figure on its datasheet at all.
	 */
	private void gather(ServerLevel serverLevel, CombinerSpec spec) {
		availableDcKw = 0.0;
		busCurrent = 0.0;
		double highest = 0.0;
		List<PvArrayBlockEntity> live = new ArrayList<>();

		for (BlockPos pos : arrays) {
			PvArrayBlockEntity array = LoadedBlockEntities.find(serverLevel, pos, PvArrayBlockEntity.class);
			if (array == null) continue;
			if (!worldPosition.equals(array.collectorPos())) continue;

			live.add(array);
			availableDcKw += array.offeredDcKw();
			highest = Math.max(highest, array.stringVoltageAtCollector());
		}

		busVoltage = highest;
		// the switch open means the group is off the inverter, and the arrays behind it know it: the
		// operating point goes to zero, which is what isolating a combiner box actually does
		double fraction = isolated() ? 0.0 : mpptFraction;
		for (PvArrayBlockEntity array : live) {
			array.setMpptFraction(fraction);
		}

		busCurrent = busVoltage <= 0.0 ? 0.0 : availableDcKw * fraction * 1000.0 / busVoltage;
	}

	// ---- what the inverter talks to ----

	/** Whether the output switch is open, which takes the whole group off the cabinet. */
	public boolean isolated() {
		return getBlockState().getValue(PvCombinerBlock.ISOLATED);
	}

	/**
	 * Offers this box's output to an inverter down a measured trunk.
	 *
	 * The same rule an array follows: shorter copper wins, and a claim that stops being renewed lapses on
	 * its own.
	 */
	public boolean claim(BlockPos candidate, DcNetwork.Reach run, DcCableSpec through) {
		if (level == null) return false;
		if (inverterPos != null && !inverterPos.equals(candidate) && runMetres <= run.metres()) return false;

		inverterPos = candidate.immutable();
		trunk = through;
		runMetres = run.metres();
		runBuriedFraction = run.buriedFraction();
		claimAge = 0;
		return true;
	}

	public void releaseClaim(BlockPos claimant) {
		if (claimant.equals(inverterPos)) forgetInverter();
	}

	private void expireClaim() {
		if (inverterPos == null) return;
		if (++claimAge <= CLAIM_LEASE_TICKS) return;

		forgetInverter();
	}

	private void forgetInverter() {
		inverterPos = null;
		trunk = null;
		runMetres = 0.0;
		runBuriedFraction = 0.0;
		mpptFraction = 0.0;
	}

	/** Tells the box where the inverter has put its operating point, 0 to 1. Forwarded to every string. */
	public void setMpptFraction(double fraction) {
		mpptFraction = Mth.clamp(fraction, 0.0, 1.0);
		claimAge = 0;
	}

	/**
	 * What arrives at the cabinet, in kW.
	 *
	 * Everything the strings offer at the box, less the drop down the trunk and less what the monitoring
	 * is eating. Nothing at all with the switch open, which is the whole purpose of the switch.
	 */
	public double offeredDcKw() {
		if (isolated()) return 0.0;

		CombinerSpec spec = spec();
		double atBox = Math.max(0.0, availableDcKw - (spec == null ? 0.0 : spec.selfConsumptionKw()));
		return atBox * (1.0 - trunkLossFraction());
	}

	/** The bus voltage as the cabinet sees it, which is lower by the drop down the trunk. */
	public double busVoltageAtInverter() {
		if (isolated() || trunk == null || runMetres <= 0.0) return isolated() ? 0.0 : busVoltage;

		return Math.max(0.0, busVoltage - trunk.voltageDrop(designAmps(), runMetres));
	}

	/**
	 * What the trunk burns, as a fraction of what is going down it.
	 *
	 * Worked out at the current the box was filled to rather than at the moment's current, because that
	 * is the condition the cable was chosen for and it is the figure a designer would quote. A box at a
	 * quarter load loses a sixteenth as much, and reporting that as the cable's performance would flatter
	 * a run that is too long.
	 */
	public double trunkLossFraction() {
		if (trunk == null || runMetres <= 0.0 || busVoltage <= 0.0) return 0.0;

		return trunk.lossFraction(designAmps(), busVoltage, runMetres);
	}

	/** The current the box is wired for, in amps: everything in it at standard conditions. */
	public double designAmps() {
		CombinerSpec spec = spec();
		if (spec == null) return 0.0;

		return Math.max(0.0, outputCeiling(spec) - stringCurrentHeadroom);
	}

	/** Nameplate of everything behind the box, kW. What an inverter counts against its DC input rating. */
	public double nameplateDcKw() {
		return nameplateDcKw;
	}

	/**
	 * Tells the arrays this box is letting them go.
	 *
	 * Called when the box is broken and before every rescan, and deliberately not when its chunk unloads:
	 * an unloading block entity must not reach into another chunk, and an array does not need telling
	 * anyway, because a claim it stops hearing about lapses on its own.
	 */
	void releaseArrays() {
		for (BlockPos pos : arrays) {
			PvArrayBlockEntity array = LoadedBlockEntities.find(level, pos, PvArrayBlockEntity.class);
			if (array != null) array.releaseClaim(worldPosition);
		}
	}

	// ---- readings ----

	public int stringsConnected() {
		return stringsConnected;
	}

	public int arraysConnected() {
		return arrays.size();
	}

	public double stringCurrentHeadroom() {
		return stringCurrentHeadroom;
	}

	public CombinerSpec.Refusal refusal() {
		return refusal;
	}

	public double availableDcKw() {
		return availableDcKw;
	}

	public double busVoltage() {
		return busVoltage;
	}

	public double busCurrent() {
		return busCurrent;
	}

	public double runMetres() {
		return runMetres;
	}

	public double runBuriedFraction() {
		return runBuriedFraction;
	}

	public boolean wired() {
		return inverterPos != null;
	}

	@Nullable
	public BlockPos inverterPos() {
		return inverterPos;
	}

	// ---- persistence ----

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putInt("stringsConnected", stringsConnected);
		tag.putDouble("nameplateDc", nameplateDcKw);
		tag.putDouble("headroom", stringCurrentHeadroom);
		tag.putString("refusal", refusal.name());
		tag.putDouble("availableDc", availableDcKw);
		tag.putDouble("busVoltage", busVoltage);
		tag.putDouble("busCurrent", busCurrent);
		tag.putDouble("mpptFraction", mpptFraction);
		tag.putDouble("runMetres", runMetres);
		tag.putDouble("runBuried", runBuriedFraction);
		if (trunk != null) tag.putString("trunk", trunk.id().toString());
		if (inverterPos != null) tag.putLong("inverter", inverterPos.asLong());

		long[] wired = new long[arrays.size()];
		for (int i = 0; i < wired.length; i++) {
			wired[i] = arrays.get(i).asLong();
		}

		tag.putLongArray("arrays", wired);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		stringsConnected = tag.getInt("stringsConnected");
		nameplateDcKw = tag.getDouble("nameplateDc");
		stringCurrentHeadroom = tag.getDouble("headroom");
		refusal = readRefusal(tag.getString("refusal"));
		availableDcKw = tag.getDouble("availableDc");
		busVoltage = tag.getDouble("busVoltage");
		busCurrent = tag.getDouble("busCurrent");
		mpptFraction = tag.getDouble("mpptFraction");
		runMetres = tag.getDouble("runMetres");
		runBuriedFraction = tag.getDouble("runBuried");
		trunk = tag.contains("trunk") ? CableCatalog.byId(new net.minecraft.resources.ResourceLocation(tag.getString("trunk"))) : null;
		inverterPos = tag.contains("inverter") ? BlockPos.of(tag.getLong("inverter")) : null;

		arrays.clear();
		for (long packed : tag.getLongArray("arrays")) {
			arrays.add(BlockPos.of(packed));
		}
	}

	private static CombinerSpec.Refusal readRefusal(String name) {
		for (CombinerSpec.Refusal candidate : CombinerSpec.Refusal.values()) {
			if (candidate.name().equals(name)) return candidate;
		}

		return CombinerSpec.Refusal.NONE;
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = super.getUpdateTag();
		saveAdditional(tag);
		return tag;
	}

	@Nullable
	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void handleUpdateTag(@Nonnull CompoundTag tag) {
		load(tag);
	}
}
