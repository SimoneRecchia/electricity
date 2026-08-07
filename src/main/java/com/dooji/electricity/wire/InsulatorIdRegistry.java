package com.dooji.electricity.wire;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class InsulatorIdRegistry {
	private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
	private static final ConcurrentHashMap.KeySetView<Integer, Boolean> CLAIMED_IDS = ConcurrentHashMap.newKeySet();

	private InsulatorIdRegistry() {
	}

	public static int claimId() {
		int candidate;
		do {
			candidate = NEXT_ID.getAndIncrement();
		} while (!CLAIMED_IDS.add(candidate));

		return candidate;
	}

	public static void registerExistingId(int id) {
		if (id <= 0) return;

		CLAIMED_IDS.add(id);
		NEXT_ID.accumulateAndGet(id + 1, Math::max);
	}

	public static void releaseId(int id) {
		if (id > 0) {
			CLAIMED_IDS.remove(id);
		}
	}

	/** Fills in whatever ids a device has not got yet, leaving the ones it has. */
	public static void claimMissing(int[] ids) {
		for (int i = 0; i < ids.length; i++) {
			if (ids[i] == 0) {
				ids[i] = claimId();
			}
		}
	}

	public static void releaseIds(int[] ids) {
		if (ids == null) return;

		for (int id : ids) {
			releaseId(id);
		}
	}
}
