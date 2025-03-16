package service;

import model.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Core service that implements the payload scheduling algorithm.
 */
public class SchedulingService {
    private static final Logger LOGGER = Logger.getLogger(SchedulingService.class.getName());
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");

    // Battery threshold below which an AGV should consider charging
    private static final double LOW_BATTERY_THRESHOLD = 30.0;
    // Minimum battery level required for an AGV to pick up a new payload
    private static final double MIN_BATTERY_FOR_PICKUP = 20.0;

    private final List<AGV> agvs;
    private final List<Payload> payloads;
    private final NetworkGraph network;
    private LocalTime currentTime;
    private final List<String> executionLogs;

    // Track metrics for reporting
    private final Map<Integer, List<Long>> deliveryTimesByPriority = new HashMap<>();
    private final Map<String, Payload> deliveryStartTimes = new HashMap<>();

    /**
     * Constructor for the scheduling service.
     */
    public SchedulingService(List<AGV> agvs, List<Payload> payloads, NetworkGraph network) {
        this.agvs = new ArrayList<>(agvs);
        this.payloads = new ArrayList<>(payloads);
        this.payloads.sort(Comparator
                .comparing(Payload::getPriority)
                .thenComparing(Payload::getSchedulingTime));
        this.network = network;
        this.currentTime = LocalTime.parse("08:00"); // Start time of simulation
        this.executionLogs = new ArrayList<>();

        // Initialize delivery time tracking for each priority
        for (int i = 1; i <= 3; i++) {
            deliveryTimesByPriority.put(i, new ArrayList<>());
        }

        // For any initial payloads that AGVs already have
        for (AGV agv : agvs) {
            for (Payload payload : agv.getPayloads()) {
                deliveryStartTimes.put(payload.getId(), payload);
            }
        }
    }

    /**
     * Runs the simulation until all payloads are delivered or no more progress can be made.
     */

    public List<String> runSimulation() {
        LOGGER.info("Starting simulation at " + currentTime);

        // Continue until all payloads are delivered or a true deadlock occurs
        int maxStuckCounter = 5;
        int stuckCounter = 0;

        while (true) {
            // Check if all payloads are delivered
            boolean allDelivered = payloads.stream().allMatch(Payload::isDelivered);
            if (allDelivered) {
                LOGGER.info("All payloads delivered. Ending simulation at " + currentTime);
                break;
            }

            // Process completed tasks and assign new ones
            boolean madeProgress = processCompletedTasks();
            boolean assignedTasks = assignNewTasks();

            // Also check if any AGVs are currently busy - this is also progress!
            boolean agvsBusy = agvs.stream().anyMatch(agv -> !agv.isIdle(currentTime));

            // If we made progress or AGVs are busy, we're still making progress
            if (madeProgress || assignedTasks || agvsBusy) {
                stuckCounter = 0; // Reset stuck counter

                // Advance time to next event if no immediate progress was made
                if (!madeProgress && !assignedTasks) {
                    LocalTime nextEventTime = getNextEventTime();
                    if (nextEventTime != null && nextEventTime.isAfter(currentTime)) {
                        LOGGER.info("AGVs busy. Advancing time to: " + nextEventTime);
                        currentTime = nextEventTime;
                    }
                }
            } else {
                // If there's truly no progress and nothing scheduled
                stuckCounter++;

                if (stuckCounter >= maxStuckCounter) {
                    LOGGER.warning("System appears deadlocked. Ending simulation at " + currentTime);
                    break;
                }

                // Try to advance time to find more work
                LocalTime nextEventTime = getNextEventTime();
                if (nextEventTime != null && nextEventTime.isAfter(currentTime)) {
                    currentTime = nextEventTime;
                    LOGGER.info("Advancing time to next event: " + currentTime);
                } else {
                    // If no next event, advance by a fixed amount
                    currentTime = currentTime.plusMinutes(5);
                    LOGGER.info("No scheduled events. Advancing time to: " + currentTime);
                }
            }
        }

        return executionLogs;
    }

    /**
     * Process any AGVs that have completed their tasks
     */
    private boolean processCompletedTasks() {
        boolean progress = false;

        for (AGV agv : agvs) {
            if (!agv.isIdle(currentTime) &&
                    (currentTime.equals(agv.getBusyUntil()) || currentTime.isAfter(agv.getBusyUntil()))) {

                if (agv.isMoving()) {
                    // AGV has completed its movement
                    agv.completeMoving();
                    LOGGER.info(agv.getId() + " arrived at station " + agv.getCurrentStation());

                    // Check for payloads to deliver at the current station
                    List<Payload> deliveredPayloads = new ArrayList<>();
                    for (Payload payload : agv.getPayloads()) {
                        if (payload.getDestinationStation() == agv.getCurrentStation()) {
                            deliveredPayloads.add(payload);

                            // Record delivery time for metrics
                            String payloadId = payload.getId();
                            if (deliveryStartTimes.containsKey(payloadId)) {
                                int minutesTaken = calculateMinutesBetween(
                                        deliveryStartTimes.get(payloadId).getSchedulingTime(),
                                        currentTime
                                );
                                deliveryTimesByPriority.get(payload.getPriority()).add((long)minutesTaken);
                                deliveryStartTimes.remove(payloadId);
                            }
                        }
                    }

                    // Remove delivered payloads
                    for (Payload delivered : deliveredPayloads) {
                        agv.removePayload(delivered);
                        delivered.setDelivered(true);
                        LOGGER.info(agv.getId() + " delivered " + delivered.getId() +
                                " at station " + agv.getCurrentStation());
                    }

                    progress = true;
                } else if (agv.isCharging()) {
                    // AGV has completed charging
                    agv.completeCharging();
                    LOGGER.info(agv.getId() + " completed charging at station 9");
                    progress = true;
                }
            }
        }

        return progress;
    }

    /**
     * Assign new tasks to idle AGVs
     */
    private boolean assignNewTasks() {
        boolean progress = false;

        for (AGV agv : agvs) {
            if (agv.isIdle(currentTime)) {
                // Handle different tasks in priority order

                // 1. Emergency: If battery critically low, go charge immediately
                if (agv.getBatteryLevel() < 10.0) {
                    if (moveTowardChargingStation(agv)) {
                        progress = true;
                        continue;
                    }
                }

                // 2. Charge if at charging station with depleted battery
                if (agv.getCurrentStation() == 9 && agv.getBatteryLevel() < 100.0) {
                    agv.startCharging(currentTime);
                    LOGGER.info(agv.getId() + " started charging at station 9");
                    progress = true;
                    continue;
                }

                // 3. Deliver current payloads
                if (!agv.getPayloads().isEmpty()) {
                    if (moveTowardDestination(agv)) {
                        progress = true;
                        continue;
                    }
                }

                // 4. Go charge if battery is low and no payloads
                if (agv.getBatteryLevel() < LOW_BATTERY_THRESHOLD && agv.getPayloads().isEmpty()) {
                    if (moveTowardChargingStation(agv)) {
                        progress = true;
                        continue;
                    }
                }

                // 5. Pickup new payloads if battery sufficient
                if (agv.getBatteryLevel() >= MIN_BATTERY_FOR_PICKUP) {
                    if (pickupNewPayload(agv)) {
                        progress = true;
                        continue;
                    }
                }
            }
        }

        return progress;
    }

    /**
     * Move AGV toward a destination for its current payload(s)
     */
    private boolean moveTowardDestination(AGV agv) {
        // Group payloads by destination to find the best one to deliver first
        Map<Integer, List<Payload>> destinationGroups = agv.getPayloads().stream()
                .collect(Collectors.groupingBy(Payload::getDestinationStation));

        // Find the closest destination
        int bestDestination = -1;
        double shortestDistance = Double.MAX_VALUE;

        for (Map.Entry<Integer, List<Payload>> entry : destinationGroups.entrySet()) {
            int destination = entry.getKey();
            double distance = network.getDistance(agv.getCurrentStation(), destination);

            if (distance < shortestDistance) {
                shortestDistance = distance;
                bestDestination = destination;
            }
        }

        if (bestDestination != -1) {
            List<Integer> path = network.getShortestPath(agv.getCurrentStation(), bestDestination);
            if (path != null && path.size() > 1) {
                // If we're already at the destination
                if (bestDestination == agv.getCurrentStation()) {
                    return false; // Let processCompletedTasks handle delivery
                }

                int nextStation = path.get(1); // First step toward destination

                // Get payload IDs for logging
                String payloadIds = agv.getPayloads().stream()
                        .map(Payload::getId)
                        .collect(Collectors.joining(","));

                moveAgv(agv, nextStation, payloadIds);
                return true;
            }
        }

        return false;
    }

    /**
     * Move AGV toward charging station
     */
    private boolean moveTowardChargingStation(AGV agv) {
        if (agv.getCurrentStation() != 9) {
            List<Integer> path = network.getShortestPath(agv.getCurrentStation(), 9);
            if (path != null && path.size() > 1) {
                int nextStation = path.get(1); // First step toward charging station
                moveAgv(agv, nextStation, "empty");
                return true;
            }
        }
        return false;
    }

    /**
     * Handle payload pickup for an AGV
     */
    private boolean pickupNewPayload(AGV agv) {
        // Find available payloads scheduled at or before the current time
        List<Payload> availablePayloads = payloads.stream()
                .filter(p -> !p.isDelivered() &&
                        (p.getSchedulingTime().isBefore(currentTime) ||
                                p.getSchedulingTime().equals(currentTime)))
                .collect(Collectors.toList());

        if (availablePayloads.isEmpty()) {
            return false;
        }

        // Group by source station for better batching
        Map<Integer, List<Payload>> sourceGroups = availablePayloads.stream()
                .collect(Collectors.groupingBy(Payload::getSourceStation));

        // Variables to track best pickup choice
        int bestStation = -1;
        List<Payload> bestPayloads = null;
        double shortestDistance = Double.MAX_VALUE;
        int highestPriority = 4; // Higher than any actual priority

        // Check each source station
        for (Map.Entry<Integer, List<Payload>> entry : sourceGroups.entrySet()) {
            int station = entry.getKey();
            List<Payload> stationPayloads = entry.getValue();

            // Sort by priority to get highest priority first
            stationPayloads.sort(Comparator.comparing(Payload::getPriority));

            // Find what payloads can fit together
            List<Payload> compatiblePayloads = new ArrayList<>();
            double totalWeight = agv.getCurrentLoad(); // Start with current load
            for (Payload payload : stationPayloads) {
                if (totalWeight + payload.getWeight() <= AGV.MAX_CAPACITY) {
                    compatiblePayloads.add(payload);
                    totalWeight += payload.getWeight();
                }
            }

            if (!compatiblePayloads.isEmpty()) {
                double distance = network.getDistance(agv.getCurrentStation(), station);
                int priority = compatiblePayloads.get(0).getPriority();

                // Prefer higher priority or closer station
                if (priority < highestPriority ||
                        (priority == highestPriority && distance < shortestDistance)) {
                    bestStation = station;
                    bestPayloads = compatiblePayloads;
                    shortestDistance = distance;
                    highestPriority = priority;
                }
            }
        }

        if (bestStation != -1 && bestPayloads != null) {
            if (agv.getCurrentStation() == bestStation) {
                // Already at the station - pick up payloads
                // Double check capacity before adding
                double totalWeight = agv.getCurrentLoad();
                List<Payload> payloadsToAdd = new ArrayList<>();

                for (Payload payload : bestPayloads) {
                    if (totalWeight + payload.getWeight() <= AGV.MAX_CAPACITY) {
                        payloadsToAdd.add(payload);
                        totalWeight += payload.getWeight();
                    } else {
                        LOGGER.warning("Cannot add " + payload.getId() + " - would exceed capacity");
                    }
                }

                for (Payload payload : payloadsToAdd) {
                    try {
                        agv.addPayload(payload);
                        deliveryStartTimes.put(payload.getId(), payload);
                        LOGGER.info(agv.getId() + " picked up " + payload.getId() +
                                " at station " + bestStation);
                    } catch (Exception e) {
                        LOGGER.warning("Failed to add payload " + payload.getId() + ": " + e.getMessage());
                    }
                }
                return !payloadsToAdd.isEmpty();
            } else {
                // Move toward the station
                List<Integer> path = network.getShortestPath(agv.getCurrentStation(), bestStation);
                if (path != null && path.size() > 1) {
                    int nextStation = path.get(1); // First step toward source
                    moveAgv(agv, nextStation, "empty");
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Move AGV to the next station with proper logging
     */
    private void moveAgv(AGV agv, int nextStation, String payloadInfo) {
        int currentStation = agv.getCurrentStation();
        double distance = network.getDistance(currentStation, nextStation);

        if (distance <= 0) {
            LOGGER.warning("Invalid distance from " + currentStation + " to " + nextStation);
            return;
        }

        // Create log entry
        String logEntry = String.format("%s-%d-%d-%s-%.1f-%s",
                agv.getId(),
                currentStation,
                nextStation,
                currentTime.format(TIME_FORMATTER),
                agv.getCurrentLoad(),
                payloadInfo);
        executionLogs.add(logEntry);

        // Calculate travel time based on distance and load
        int travelTime = agv.calculateTravelTime((int)distance);
        LocalTime arrivalTime = currentTime.plusMinutes(travelTime);

        // Start the movement
        agv.startMoving(nextStation, (int)distance, currentTime);

        LOGGER.info(agv.getId() + " moving from station " + currentStation +
                " to station " + nextStation + " with load " + agv.getCurrentLoad() +
                ", arriving at " + arrivalTime.format(TIME_FORMATTER));
    }

    /**
     * Gets the time of the next event
     */
    private LocalTime getNextEventTime() {
        LocalTime nextTime = null;

        // Check when AGVs will become idle
        for (AGV agv : agvs) {
            if (!agv.isIdle(currentTime)) {
                if (nextTime == null || agv.getBusyUntil().isBefore(nextTime)) {
                    nextTime = agv.getBusyUntil();
                }
            }
        }

        // Check when new payloads will become available
        for (Payload payload : payloads) {
            if (!payload.isDelivered() && payload.getSchedulingTime().isAfter(currentTime)) {
                if (nextTime == null || payload.getSchedulingTime().isBefore(nextTime)) {
                    nextTime = payload.getSchedulingTime();
                }
            }
        }

        return nextTime;
    }

    /**
     * Calculate minutes between two LocalTime objects.
     */
    private int calculateMinutesBetween(LocalTime start, LocalTime end) {
        int startMinutes = start.getHour() * 60 + start.getMinute();
        int endMinutes = end.getHour() * 60 + end.getMinute();
        return endMinutes - startMinutes;
    }

    // Other methods unchanged
    public LocalTime getCurrentTime() {
        return currentTime;
    }

    public List<String> getExecutionLogs() {
        return new ArrayList<>(executionLogs);
    }

    public int getTotalChargeCount() {
        return agvs.stream().mapToInt(AGV::getChargeCount).sum();
    }

    public Map<Integer, Double> getAverageDeliveryTimesByPriority() {
        Map<Integer, Double> averageTimes = new HashMap<>();

        for (Map.Entry<Integer, List<Long>> entry : deliveryTimesByPriority.entrySet()) {
            int priority = entry.getKey();
            List<Long> times = entry.getValue();

            if (!times.isEmpty()) {
                double average = times.stream().mapToLong(Long::longValue).average().orElse(0);
                averageTimes.put(priority, average);
            } else {
                averageTimes.put(priority, 0.0);
            }
        }

        return averageTimes;
    }




    public int getTotalExecutionTime() {
        LocalTime startTime = LocalTime.parse("08:00");
        return calculateMinutesBetween(startTime, currentTime);
    }
}