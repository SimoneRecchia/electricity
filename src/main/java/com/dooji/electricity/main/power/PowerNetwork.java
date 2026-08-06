package com.dooji.electricity.main.power;

import com.dooji.electricity.block.PowerBoxBlockEntity;
import com.dooji.electricity.wire.InsulatorHost;
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
	private final Map<Bus, List<PowerNode>> nodesByBus = new HashMap<>();
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
		nodesByBus.clear();
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
		int startId = wireConnection.startInsulatorId();
		int endId = wireConnection.endInsulatorId();

		PowerNode startNode = getOrCreateNode(startId, wireConnection.startBlockPos(), wireConnection.startBlockType());
		PowerNode endNode = getOrCreateNode(endId, wireConnection.endBlockPos(), wireConnection.endBlockType());

		if (startNode != null && endNode != null) {
			double distance = calculateDistance(startNode.position, endNode.position);
			String connectionKey = Math.min(startId, endId) + "_" + Math.max(startId, endId);

			PowerConnection connection = new PowerConnection(startNode, endNode, distance, wireConnection.startPowerType(), wireConnection.endPowerType());
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

		// what the connection was saved against has to still be what is standing there
		boolean typeMatches = blockEntity instanceof InsulatorHost host && host.fittingType().equals(blockType);

		if (!typeMatches) {
			LOGGER.warn("Block type mismatch at {} - client reported {} but server found {}", blockPos, blockType, blockEntity.getClass().getSimpleName());
			removeStaleConnections(insulatorId);
			return null;
		}

		// Which bus the fitting is on, not just which block it is on: a switch says its two sides are two
		// buses when it is open, and a cluster is what power crosses for free.
		int bus = blockEntity instanceof InsulatorHost host ? host.busOf(indexOf(host, insulatorId)) : 0;
		PowerNode node = new PowerNode(insulatorId, immutablePos, new Bus(immutablePos, bus), blockEntity);
		powerNodes.put(insulatorId, node);
		nodesByBus.computeIfAbsent(node.bus, key -> new ArrayList<>()).add(node);
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

		List<PowerNode> clusterNodes = nodesByBus.getOrDefault(startNode.bus, Collections.singletonList(startNode));
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

		Map<Bus, TargetGroup> targetGroups = groupConnectionsByTarget(externalConnections);
		if (targetGroups.isEmpty()) return distribution;
		List<TargetGroup> viableGroups = new ArrayList<>();
		for (TargetGroup group : targetGroups.values()) {
			if (!isClusterVisited(group.targetNode.bus, visited)) {
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

	private Map<Bus, TargetGroup> groupConnectionsByTarget(List<ClusterConnection> connections) {
		Map<Bus, TargetGroup> groups = new HashMap<>();

		for (ClusterConnection clusterConnection : connections) {
			PowerNode targetNode = clusterConnection.connection.getOtherNode(clusterConnection.sourceNode);
			TargetGroup group = groups.computeIfAbsent(targetNode.bus, bus -> new TargetGroup(targetNode));
			group.addConnection(clusterConnection.connection);
		}

		return groups;
	}

	/**
	 * Every span power may leave this fitting by.
	 *
	 * A span has no direction of its own, so the same three rules were written out twice with start and end
	 * swapped - and the two copies had drifted: the third rule read "input to output" one way round and
	 * "output to input" the other, which is the same rule said backwards.
	 */
	private List<PowerConnection> getOutgoingConnections(int fromNodeId) {
		List<PowerConnection> connections = new ArrayList<>();
		for (PowerConnection connection : powerConnections.values()) {
			PowerNode from = connection.startNode.insulatorId == fromNodeId ? connection.startNode
					: connection.endNode.insulatorId == fromNodeId ? connection.endNode : null;
			if (from == null) continue;

			PowerNode to = connection.getOtherNode(from);
			if (!canTransfer(from, to)) continue;
			if (leaves(connection.typeOf(from), connection.typeOf(to), from.position.equals(to.position))) {
				connections.add(connection);
			}
		}

		return connections;
	}

	/**
	 * Whether power leaves a fitting of this type for one of that type.
	 *
	 * An output or a bidirectional fitting feeds an input or a bidirectional one. The third rule is for two
	 * fittings of one machine wired to each other, where an input may feed an output - that is a jumper across
	 * the machine, and it is the only case where the direction runs the other way.
	 */
	private static boolean leaves(String from, String to, boolean sameBlock) {
		boolean takes = "input".equals(to) || "bidirectional".equals(to);
		if (("output".equals(from) || "bidirectional".equals(from)) && takes) return true;

		return sameBlock && "input".equals(from) && "output".equals(to);
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

	private boolean isClusterVisited(Bus bus, Set<Integer> visitedIds) {
		return isClusterVisited(nodesByBus.get(bus), visitedIds);
	}

	/** Which fitting of its machine an id is, so the machine can be asked which bus that one is on. */
	private static int indexOf(InsulatorHost host, int insulatorId) {
		int[] ids = host.getInsulatorIds();
		for (int index = 0; index < ids.length; index++) {
			if (ids[index] == insulatorId) return index;
		}

		return 0;
	}

	/** One bus of one machine: what power crosses without a wire. */
	private record Bus(BlockPos position, int index) {
	}

	private boolean isClusterVisited(List<PowerNode> clusterNodes, Set<Integer> visitedIds) {
		if (clusterNodes == null || clusterNodes.isEmpty()) return false;

		for (PowerNode node : clusterNodes) {
			if (!visitedIds.contains(node.insulatorId)) return false;
		}
		return true;
	}

	private static void applyPower(BlockEntity blockEntity, double power, PowerDeliveryEvent event) {
		if (!(blockEntity instanceof InsulatorHost host)) return;

		host.deliverPower(power);
		// the kiosk is the one that needs the event as well as the figure: it bridges to Forge Energy
		if (blockEntity instanceof PowerBoxBlockEntity powerBox) {
			powerBox.setCurrentPower(power);
			powerBox.setIncomingEvent(event);
		}
	}

	private static class PowerNode {
		final int insulatorId;
		final BlockPos position;
		final Bus bus;
		final BlockEntity blockEntity;
		double power = 0.0;
		private PowerDeliveryEvent event = PowerDeliveryEvent.none();

		PowerNode(int insulatorId, BlockPos position, Bus bus, BlockEntity blockEntity) {
			this.insulatorId = insulatorId;
			this.position = position;
			this.bus = bus;
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

		/** Whether this end of the span is an input, an output, or either. */
		String typeOf(PowerNode node) {
			return node == startNode ? startPowerType : endPowerType;
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

	/** Whether power may flow along a wire, which each machine says for itself. */
	private boolean canTransfer(PowerNode from, PowerNode to) {
		return from.blockEntity instanceof InsulatorHost a && to.blockEntity instanceof InsulatorHost b
				&& (a.feeds(b) || b.passesThrough());
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
