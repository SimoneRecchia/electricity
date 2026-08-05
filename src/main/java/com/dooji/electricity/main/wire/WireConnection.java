package com.dooji.electricity.main.wire;

import net.minecraft.core.BlockPos;

public class WireConnection {
	private final int startInsulatorId;
	private final int endInsulatorId;
	private final String wireType;
	private final BlockPos startBlockPos;
	private final BlockPos endBlockPos;
	private final String startBlockType;
	private final String endBlockType;
	private final String startPowerType;
	private final String endPowerType;
	/** How many items the span was charged for */
	private final int chargedItems;

	public WireConnection(int startInsulatorId, int endInsulatorId) {
		this(startInsulatorId, endInsulatorId, "default", BlockPos.ZERO, BlockPos.ZERO, "unknown", "unknown", "unknown", "unknown");
	}

	public WireConnection(int startInsulatorId, int endInsulatorId, String wireType) {
		this(startInsulatorId, endInsulatorId, wireType, BlockPos.ZERO, BlockPos.ZERO, "unknown", "unknown", "unknown", "unknown");
	}

	public WireConnection(int startInsulatorId, int endInsulatorId, String wireType, BlockPos startBlockPos, BlockPos endBlockPos, String startBlockType, String endBlockType) {
		this(startInsulatorId, endInsulatorId, wireType, startBlockPos, endBlockPos, startBlockType, endBlockType, "unknown", "unknown");
	}

	public WireConnection(int startInsulatorId, int endInsulatorId, String wireType, BlockPos startBlockPos, BlockPos endBlockPos, String startBlockType, String endBlockType, String startPowerType,
			String endPowerType) {
		this(startInsulatorId, endInsulatorId, wireType, startBlockPos, endBlockPos, startBlockType, endBlockType, startPowerType, endPowerType, 0);
	}

	public WireConnection(int startInsulatorId, int endInsulatorId, String wireType, BlockPos startBlockPos, BlockPos endBlockPos, String startBlockType, String endBlockType, String startPowerType,
			String endPowerType, int chargedItems) {
		this.startInsulatorId = startInsulatorId;
		this.endInsulatorId = endInsulatorId;
		this.wireType = wireType;
		this.startBlockPos = startBlockPos;
		this.endBlockPos = endBlockPos;
		this.startBlockType = startBlockType;
		this.endBlockType = endBlockType;
		this.startPowerType = startPowerType;
		this.endPowerType = endPowerType;
		this.chargedItems = Math.max(0, chargedItems);
	}

	public int getChargedItems() {
		return chargedItems;
	}

	public int getStartInsulatorId() {
		return startInsulatorId;
	}

	public int getEndInsulatorId() {
		return endInsulatorId;
	}

	public String getWireType() {
		return wireType;
	}

	public BlockPos getStartBlockPos() {
		return startBlockPos;
	}

	public BlockPos getEndBlockPos() {
		return endBlockPos;
	}

	public String getStartBlockType() {
		return startBlockType;
	}

	public String getEndBlockType() {
		return endBlockType;
	}

	public String getStartPowerType() {
		return startPowerType;
	}

	public String getEndPowerType() {
		return endPowerType;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		WireConnection that = (WireConnection) obj;
		return startInsulatorId == that.startInsulatorId && endInsulatorId == that.endInsulatorId && wireType.equals(that.wireType) && startBlockPos.equals(that.startBlockPos)
				&& endBlockPos.equals(that.endBlockPos);
	}

	@Override
	public int hashCode() {
		int result = Integer.hashCode(startInsulatorId);
		result = 31 * result + Integer.hashCode(endInsulatorId);
		result = 31 * result + wireType.hashCode();
		result = 31 * result + startBlockPos.hashCode();
		result = 31 * result + endBlockPos.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return startInsulatorId + " -> " + endInsulatorId + " (" + wireType + ")";
	}
}
