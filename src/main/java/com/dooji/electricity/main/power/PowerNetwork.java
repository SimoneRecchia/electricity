package com.dooji.electricity.main.power;

import com.dooji.electricity.block.ElectricCabinBlockEntity;
import com.dooji.electricity.block.PowerBoxBlockEntity;
import com.dooji.electricity.block.UtilityPoleBlockEntity;
import com.dooji.electricity.block.PvInverterBlockEntity;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import com.dooji.electricity.api.power.PowerDeliveryEvent;
import com.dooji.electricity.main.network.ElectricityNetworking;
import com.dooji.electricity.main.wire.WireConnection;
import com.dooji.electricity.main.wire.WireManager;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PowerNetwork {
	private static final Logger LOGGER = LoggerFactory.getLogger("electricity");
	private final ServerLevel level;
	private final Map<Integer, PowerNode> powerNodes = new HashMap<>();
	private final Map<String, PowerConnection> powerConnections = new HashMap<>();
	private final Map<BlockPos, List<PowerNode>> nodesByPosition = new HashMap<>();
	private final WireManager wireManager;
	private final Map<BlockPos, Double> lastSyncedPower = new HashMap<>();
	private final Set<Integer> surgeImpactedNodes = new HashSet<>();
	private final Map<Integer, PowerDeliveryEvent> nodeEvents = new HashMap<>();
	private final Map<Integer, GeneratorEvent> generatorEvents = new HashMap<>();

	public PowerNetwork(ServerLevel level, WireManager wireManager) {
		this.level = level;
		this.wireManager = wireManager;
	}

	public void updatePowerNetwork() {
		clearNetwork();
		buildNetworkFromWires();
		calculatePowerFlow();
		syncToClients();
	}

	private void clearNetwork() {
		powerNodes.clear();
		powerConnections.clear();
		nodesByPosition.clear();
		surgeImpactedNodes.clear();
		generatorEvents.clear();
	}

	private void buildNetworkFromWires() {
		var savedData = wireManager.getSavedData(level);
		if (savedData == null) return;

		for (WireConnection wireConnection : savedData.getAllWireConnections()) {
			addWireConnection(wireConnection);
		}
	}

	private void addWireConnection(WireConnection wireConnection) {
		int startId = wireConnection.getStartInsulatorId();
		int endId = wireConnection.getEndInsulatorId();

		PowerNode startNode = getOrCreateNode(startId, wireConnection.getStartBlockPos(), wireConnection.getStartBlockType());
		PowerNode endNode = getOrCreateNode(endId, wireConnection.getEndBlockPos(), wireConnection.getEndBlockType());

		if (startNode != null && endNode != null) {
			double distance = calculateDistance(startNode.position, endNode.position);
			String connectionKey = Math.min(startId, endId) + "_" + Math.max(startId, endId);

			PowerConnection connection = new PowerConnection(startNode, endNode, distance, wireConnection.getStartPowerType(), wireConnection.getEndPowerType());
			powerConnections.put(connectionKey, connection);
		}
	}

	private PowerNode getOrCreateNode(int insulatorId, BlockPos blockPos, String blockType) {
		if (powerNodes.containsKey(insulatorId)) return powerNodes.get(insulatorId);

		BlockPos immutablePos = blockPos.immutable();
		BlockEntity blockEntity = level.getBlockEntity(immutablePos);
		if (blockEntity == null) {
			LOGGER.warn("Block entity not found at {} for insulator {}", blockPos, insulatorId);
			removeStaleConnections(insulatorId);
			return null;
		}

		boolean typeMatches = false;
		if ("wind_turbine".equals(blockType) && blockEntity instanceof WindTurbineBlockEntity) {
			typeMatches = true;
		} else if ("electric_cabin".equals(blockType) && blockEntity instanceof ElectricCabinBlockEntity) {
			typeMatches = true;
		} else if ("utility_pole".equals(blockType) && blockEntity instanceof UtilityPoleBlockEntity) {
			typeMatches = true;
		} else if ("power_box".equals(blockType) && blockEntity instanceof PowerBoxBlockEntity) {
			typeMatches = true;
		} else if ("pv_inverter".equals(blockType) && blockEntity instanceof PvInverterBlockEntity) {
			typeMatches = true;
		}

		if (!typeMatches) {
			LOGGER.warn("Block type mismatch at {} - client reported {} but server found {}", blockPos, blockType, blockEntity.getClass().getSimpleName());
			removeStaleConnections(insulatorId);
			return null;
		}

		PowerNode node = new PowerNode(insulatorId, immutablePos, blockEntity);
		powerNodes.put(insulatorId, node);
		nodesByPosition.computeIfAbsent(immutablePos, pos -> new ArrayList<>()).add(node);
		return node;
	}

	private void removeStaleConnections(int insulatorId) {
		if (insulatorId <= 0) return;
		wireManager.removeConnectionsForInsulators(level, new int[]{insulatorId});
	}

	private double calculateDistance(BlockPos pos1, BlockPos pos2) {
		return Math.sqrt(pos1.distSqr(pos2));
	}

	private void calculatePowerFlow() {
		Map<Integer, Double> nodePower = new HashMap<>();
		nodeEvents.clear();

		for (PowerNode node : powerNodes.values()) {
			if (!node.isGenerator()) continue;

			double generatedPower = node.getOutputPower();
			if (generatedPower <= 0) continue;

			// only a turbine can put a disturbance on the network: an inverter has no rotor for a gust to
			PowerDeliveryEvent generatorEvent = node.blockEntity instanceof WindTurbineBlockEntity turbine
					? createGeneratorEvent(turbine)
					: PowerDeliveryEvent.none();
			Map<Integer, Double> powerDistribution = distributePower(node.insulatorId, generatedPower, new HashSet<>(), true, node.hasLocalSurge(), generatorEvent, nodeEvents);
			for (Map.Entry<Integer, Double> entry : powerDistribution.entrySet()) {
				nodePower.merge(entry.getKey(), entry.getValue(), Double::sum);
			}
		}

		for (PowerNode node : powerNodes.values()) {
			double power = nodePower.getOrDefault(node.insulatorId, 0.0);
			node.setPower(power);
			node.setEvent(nodeEvents.getOrDefault(node.insulatorId, PowerDeliveryEvent.none()));
		}
	}

	private Map<Integer, Double> distributePower(int fromNodeId, double availablePower, Set<Integer> visited, boolean generationAlreadyIncluded, boolean surgeActive, PowerDeliveryEvent incomingEvent, Map<Integer, PowerDeliveryEvent> eventOut) {
		Map<Integer, Double> distribution = new HashMap<>();
		if (availablePower <= 0) return distribution;

		PowerNode startNode = powerNodes.get(fromNodeId);
		if (startNode == null) return distribution;

		double totalPower = availablePower;
		if (!generationAlreadyIncluded && startNode.isGenerator()) {
			totalPower += Math.max(0.0, startNode.getOutputPower());
			surgeActive = surgeActive || startNode.hasLocalSurge();
		}

		boolean localSurge = surgeActive || startNode.hasLocalSurge();
		PowerDeliveryEvent currentEvent = incomingEvent;

		List<PowerNode> clusterNodes = nodesByPosition.getOrDefault(startNode.position, Collections.singletonList(startNode));
		if (isClusterVisited(clusterNodes, visited)) return distribution;

		Set<Integer> clusterIds = new HashSet<>();
		for (PowerNode node : clusterNodes) {
			visited.add(node.insulatorId);
			clusterIds.add(node.insulatorId);
			distribution.put(node.insulatorId, totalPower);
			if (localSurge) {
				surgeImpactedNodes.add(node.insulatorId);
			}

			eventOut.merge(node.insulatorId, currentEvent, this::mergeEvents);
		}

		List<ClusterConnection> externalConnections = collectExternalConnections(clusterNodes, clusterIds);
		if (externalConnections.isEmpty()) return distribution;

		Map<BlockPos, TargetGroup> targetGroups = groupConnectionsByTarget(externalConnections);
		if (targetGroups.isEmpty()) return distribution;
		List<TargetGroup> viableGroups = new ArrayList<>();
		for (TargetGroup group : targetGroups.values()) {
			if (!isClusterVisited(group.targetNode.position, visited)) {
				viableGroups.add(group);
			}
		}

		if (viableGroups.isEmpty()) return distribution;

		double powerPerGroup = totalPower / viableGroups.size();

		for (TargetGroup group : viableGroups) {
			double deliveredPower = powerPerGroup * group.computeEfficiency();
			if (deliveredPower <= 0) continue;

			PowerNode target = group.targetNode;
			distribution.merge(target.insulatorId, deliveredPower, Double::max);
			PowerDeliveryEvent propagatedEvent = attenuateEvent(currentEvent, group.computeEfficiency());
			eventOut.merge(target.insulatorId, propagatedEvent, this::mergeEvents);

			Map<Integer, Double> subDistribution = distributePower(target.insulatorId, deliveredPower, new HashSet<>(visited), false, localSurge || target.hasLocalSurge(), propagatedEvent, eventOut);
			for (Map.Entry<Integer, Double> entry : subDistribution.entrySet()) {
				if (!clusterIds.contains(entry.getKey())) {
					double finalPower = Math.min(entry.getValue(), deliveredPower);
					distribution.merge(entry.getKey(), finalPower, Double::max);
				}
			}
		}

		return distribution;
	}

	private PowerDeliveryEvent createGeneratorEvent(WindTurbineBlockEntity turbine) {
		int id = turbine.getInsulatorId(0);
		GeneratorEvent state = generatorEvents.get(id);
		if (state != null && state.remaining > 0) {
			int nextRemaining = state.remaining - 1;
			generatorEvents.put(id, new GeneratorEvent(state.severity, nextRemaining, state.disconnect, state.brownout));
			return new PowerDeliveryEvent(state.severity, nextRemaining, state.disconnect, state.brownout);
		}

		// asked of the machine rather than of the weather.
		double turbulence = turbine.getTurbulenceIntensity();
		double windSpeed = turbine.getMeanWindSpeed();
		var random = level.getRandom();

		// both thresholds moved onto real turbulence intensity. 0.14 is where a site stops being
		double gustFactor = Mth.clamp((windSpeed - 8.0) / 12.0, 0.0, 1.0);
		double baseSeverity = Math.max(0.0, (turbulence - 0.14) * 1.7 + gustFactor * 0.4);
		double severity = Math.max(0.0, baseSeverity + random.nextDouble() * 0.05);
		int duration = severity > 0.25 ? 4 + random.nextInt(5) : 0;

		boolean disconnect = false;
		double brownout = 1.0;
		if (windSpeed > 18.0 || turbulence > 0.24) {
			double faultChance = Mth.clamp((windSpeed - 18.0) * 0.04 + (turbulence - 0.24) * 0.7, 0.0, 0.5);
			if (random.nextDouble() < faultChance) {
				disconnect = true;
				duration = Math.max(duration, 6 + random.nextInt(5));
				brownout = 0.4 + random.nextDouble() * 0.2;
			}
		}

		if (duration > 0 || disconnect || brownout < 1.0) {
			generatorEvents.put(id, new GeneratorEvent(severity, duration, disconnect, brownout));
		} else {
			generatorEvents.remove(id);
		}

		return new PowerDeliveryEvent(severity, duration, disconnect, brownout);
	}

	private PowerDeliveryEvent attenuateEvent(PowerDeliveryEvent event, double efficiency) {
		if (event == null) return PowerDeliveryEvent.none();
		double severity = event.surgeSeverity() * Mth.clamp(efficiency, 0.0, 1.0) * 0.95;
		int duration = event.surgeDuration() > 0 ? Math.max(0, event.surgeDuration() - 1) : 0;
		boolean ifDisconnect = event.disconnectActive() && duration > 0;
		double brownout = duration > 0 ? 1.0 - (1.0 - event.brownoutFactor()) * Mth.clamp(efficiency, 0.0, 1.0) : 1.0;
		return new PowerDeliveryEvent(severity, duration, ifDisconnect, brownout);
	}

	private PowerDeliveryEvent mergeEvents(PowerDeliveryEvent a, PowerDeliveryEvent b) {
		if (a == null) return b == null ? PowerDeliveryEvent.none() : b;
		if (b == null) return a;
		double severity = Math.max(a.surgeSeverity(), b.surgeSeverity());
		int duration = Math.max(a.surgeDuration(), b.surgeDuration());
		boolean ifDisconnect = a.disconnectActive() || b.disconnectActive();
		double brownout = Math.min(a.brownoutFactor(), b.brownoutFactor());
		return new PowerDeliveryEvent(severity, duration, ifDisconnect, brownout);
	}

	private record GeneratorEvent(double severity, int remaining, boolean disconnect, double brownout) {
	}

	private List<ClusterConnection> collectExternalConnections(List<PowerNode> clusterNodes, Set<Integer> clusterIds) {
		List<ClusterConnection> connections = new ArrayList<>();
		Set<PowerConnection> seenConnections = Collections.newSetFromMap(new IdentityHashMap<>());

		for (PowerNode node : clusterNodes) {
			List<PowerConnection> outgoing = getOutgoingConnections(node.insulatorId);
			for (PowerConnection connection : outgoing) {
				if (!seenConnections.add(connection)) continue;

				PowerNode other = connection.getOtherNode(node);
				if (clusterIds.contains(other.insulatorId)) continue;

				connections.add(new ClusterConnection(connection, node));
			}
		}

		return connections;
	}

	private Map<BlockPos, TargetGroup> groupConnectionsByTarget(List<ClusterConnection> connections) {
		Map<BlockPos, TargetGroup> groups = new HashMap<>();

		for (ClusterConnection clusterConnection : connections) {
			PowerNode targetNode = clusterConnection.connection.getOtherNode(clusterConnection.sourceNode);
			BlockPos targetPos = targetNode.position;

			TargetGroup group = groups.computeIfAbsent(targetPos, pos -> new TargetGroup(targetNode));
			group.addConnection(clusterConnection.connection);
		}

		return groups;
	}

	private List<PowerConnection> getOutgoingConnections(int fromNodeId) {
		List<PowerConnection> connections = new ArrayList<>();
		for (PowerConnection connection : powerConnections.values()) {
			if (connection.startNode.insulatorId == fromNodeId) {
				boolean sameBlockPos = connection.startNode.position.equals(connection.endNode.position);
				if (!canTransfer(connection.startNode, connection.endNode)) continue;

				if ("output".equals(connection.startPowerType) && ("input".equals(connection.endPowerType) || "bidirectional".equals(connection.endPowerType))) {
					connections.add(connection);
				} else if ("bidirectional".equals(connection.startPowerType) && ("input".equals(connection.endPowerType) || "bidirectional".equals(connection.endPowerType))) {
					connections.add(connection);
				} else if (sameBlockPos && "input".equals(connection.startPowerType) && "output".equals(connection.endPowerType)) {
					connections.add(connection);
				}
			} else if (connection.endNode.insulatorId == fromNodeId) {
				boolean sameBlockPos = connection.startNode.position.equals(connection.endNode.position);
				if (!canTransfer(connection.endNode, connection.startNode)) continue;

				if ("output".equals(connection.endPowerType) && ("input".equals(connection.startPowerType) || "bidirectional".equals(connection.startPowerType))) {
					connections.add(connection);
				} else if ("bidirectional".equals(connection.endPowerType) && ("input".equals(connection.startPowerType) || "bidirectional".equals(connection.startPowerType))) {
					connections.add(connection);
				} else if (sameBlockPos && "output".equals(connection.endPowerType) && "input".equals(connection.startPowerType)) {
					connections.add(connection);
				}
			}
		}

		return connections;
	}
	public void syncToClients() {
		Map<BlockPos, Double> blockPower = new HashMap<>();
		Map<BlockPos, PowerNode> representatives = new HashMap<>();
		Map<BlockPos, PowerDeliveryEvent> blockEvents = new HashMap<>();
		Set<BlockPos> updatedPositions = new HashSet<>();

		for (PowerNode node : powerNodes.values()) {
			blockPower.merge(node.position, node.power, Double::max);
			representatives.putIfAbsent(node.position, node);
			blockEvents.merge(node.position, node.event, this::mergeEvents);
		}

		for (Map.Entry<BlockPos, Double> entry : blockPower.entrySet()) {
			double power = entry.getValue();
			BlockPos position = entry.getKey();
			PowerNode representative = representatives.get(position);
			PowerDeliveryEvent event = blockEvents.getOrDefault(position, PowerDeliveryEvent.none());

			if (representative != null) {
				representative.syncToClient(power, event);
			} else {
				applyPower(level.getBlockEntity(position), power, event);
			}

			ElectricityNetworking.sendPowerUpdate(level, position, power);
			updatedPositions.add(position);
		}

		Set<BlockPos> stalePositions = new HashSet<>(lastSyncedPower.keySet());
		stalePositions.removeAll(updatedPositions);

		for (BlockPos stalePos : stalePositions) {
			applyPower(level.getBlockEntity(stalePos), 0.0, PowerDeliveryEvent.none());
			ElectricityNetworking.sendPowerUpdate(level, stalePos, 0.0);
		}

		lastSyncedPower.clear();
		lastSyncedPower.putAll(blockPower);
	}

	private boolean isClusterVisited(BlockPos position, Set<Integer> visitedIds) {
		return isClusterVisited(nodesByPosition.get(position), visitedIds);
	}

	private boolean isClusterVisited(List<PowerNode> clusterNodes, Set<Integer> visitedIds) {
		if (clusterNodes == null || clusterNodes.isEmpty()) return false;

		for (PowerNode node : clusterNodes) {
			if (!visitedIds.contains(node.insulatorId)) return false;
		}
		return true;
	}

	private static void applyPower(BlockEntity blockEntity, double power, PowerDeliveryEvent event) {
		if (blockEntity == null) return;
		if (blockEntity instanceof WindTurbineBlockEntity turbine) {
			turbine.setCurrentPower(power);
		} else if (blockEntity instanceof PvInverterBlockEntity) {
			// nothing to tell it: an inverter's output is what it makes rather than what reaches it
			// the figure a panel shows comes off its own conversion instead of off the network
		} else if (blockEntity instanceof ElectricCabinBlockEntity cabin) {
			cabin.setCurrentPower(power);
		} else if (blockEntity instanceof UtilityPoleBlockEntity pole) {
			pole.setCurrentPower(power);
		} else if (blockEntity instanceof PowerBoxBlockEntity powerBox) {
			powerBox.setCurrentPower(power);
			powerBox.setIncomingEvent(event);
		}
	}

	private static class PowerNode {
		final int insulatorId;
		final BlockPos position;
		final BlockEntity blockEntity;
		double power = 0.0;
		private PowerDeliveryEvent event = PowerDeliveryEvent.none();

		PowerNode(int insulatorId, BlockPos position, BlockEntity blockEntity) {
			this.insulatorId = insulatorId;
			this.position = position;
			this.blockEntity = blockEntity;
		}

		/** Whether this node is a source rather than something the power passes through. */
		boolean isGenerator() {
			return blockEntity instanceof WindTurbineBlockEntity || blockEntity instanceof PvInverterBlockEntity;
		}

		double getOutputPower() {
			if (blockEntity instanceof WindTurbineBlockEntity turbine) return turbine.getGeneratedPower();
			if (blockEntity instanceof PvInverterBlockEntity inverter) return inverter.getGeneratedPower();
			return power;
		}

		void setPower(double power) {
			this.power = power;
		}

		boolean hasLocalSurge() {
			return blockEntity instanceof WindTurbineBlockEntity turbine && turbine.isSurging();
		}


		void setEvent(PowerDeliveryEvent event) {
			this.event = event != null ? event : PowerDeliveryEvent.none();
		}

		void syncToClient(double syncedPower, PowerDeliveryEvent event) {
			applyPower(blockEntity, syncedPower, event);
		}
	}

	private static class PowerConnection {
		final PowerNode startNode;
		final PowerNode endNode;
		final double distance;
		final String startPowerType;
		final String endPowerType;

		PowerConnection(PowerNode startNode, PowerNode endNode, double distance, String startPowerType, String endPowerType) {
			this.startNode = startNode;
			this.endNode = endNode;
			this.distance = distance;
			this.startPowerType = startPowerType;
			this.endPowerType = endPowerType;
		}

		PowerNode getOtherNode(PowerNode node) {
			return node == startNode ? endNode : startNode;
		}
	}

	private static class ClusterConnection {
		final PowerConnection connection;
		final PowerNode sourceNode;

		ClusterConnection(PowerConnection connection, PowerNode sourceNode) {
			this.connection = connection;
			this.sourceNode = sourceNode;
		}
	}

	private boolean canTransfer(PowerNode from, PowerNode to) {
		BlockEntity a = from.blockEntity;
		BlockEntity b = to.blockEntity;

		// both generators feed a cabin, and either may be daisy-chained through another of its own kind -
		// which is how a row of turbines or a bank of inverters shares one run of wire back to the cabin
		if (a instanceof WindTurbineBlockEntity) {
			return b instanceof ElectricCabinBlockEntity || b instanceof WindTurbineBlockEntity;
		}

		if (a instanceof PvInverterBlockEntity) {
			return b instanceof ElectricCabinBlockEntity || b instanceof PvInverterBlockEntity;
		}

		if (a instanceof ElectricCabinBlockEntity) {
			return b instanceof UtilityPoleBlockEntity;
		}

		if (a instanceof UtilityPoleBlockEntity) {
			return b instanceof UtilityPoleBlockEntity || b instanceof PowerBoxBlockEntity;
		}

		return false;
	}

	private static class TargetGroup {
		final PowerNode targetNode;
		private double totalDistance = 0.0;
		private int wireCount = 0;

		TargetGroup(PowerNode targetNode) {
			this.targetNode = targetNode;
		}

		void addConnection(PowerConnection connection) {
			wireCount++;
			totalDistance += connection.distance;
		}

		double computeEfficiency() {
			if (wireCount == 0) return 0.0;

			double averageDistance = totalDistance / wireCount;
			double normalizedDistance = averageDistance / 100.0;

			double parallelBoost = 1.0 + 0.35 * (wireCount - 1);
			double distancePenalty = normalizedDistance / parallelBoost;

			return Math.max(0.1, 1.0 - distancePenalty);
		}
	}
}
