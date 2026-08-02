package com.dooji.electricity.block;

import com.dooji.electricity.api.power.DcCableSpec;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block a direct-current cable can be landed on.
 *
 * The one question a cable has to be able to ask of its neighbour, and the reason it is asked of the
 * {@link BlockState} rather than of a block entity: the answer decides what gets *drawn*, so it is
 * wanted while a chunk is being meshed, on the client, with no guarantee that a block entity exists
 * yet. Anything a terminal needs to remember about its cabling therefore lives in its block state.
 *
 * That is also what keeps the picture honest. A cable that draws itself running into a machine and a
 * plant that does not count it would be the worst of both, so the walk in {@link DcNetwork} asks this
 * same question and gets the same answer.
 */
public interface DcTerminal {
	/**
	 * Whether a cable of this kind connects to this block as it stands.
	 *
	 * Gauge matters because the termination does: string cable ends in a plug and goes into an array's
	 * leads or a tracker's terminals, and trunk cable is lugged onto a busbar. A machine has one or the
	 * other, or both, and saying which is what makes a catalogue of inverters a catalogue of choices.
	 *
	 * The face matters for an array and for nothing else. A string has two ends and they are at the two
	 * ends of the row, so a run of cable joins a row where the next row would - not wherever it happens to
	 * arrive. A cabinet and a combiner box take cable on any side, which is what a gland plate is for.
	 */
	boolean acceptsCable(BlockState state, DcCableSpec cable, Direction side);

	/**
	 * The state this block takes when a length of that cable is worked into it, or null if it will not
	 * take one.
	 *
	 * Only an array does, and it has to: its strings need leads before anything can be plugged into
	 * them, which is the one place in a plant where the cable is part of the machine rather than a run
	 * between two of them. An inverter's terminals and a combiner's glands come with the machine, so
	 * they answer null and a click on them falls through to laying cable on the ground.
	 */
	@Nullable
	default BlockState withCableFitted(BlockState state, DcCableSpec cable) {
		return null;
	}
}
