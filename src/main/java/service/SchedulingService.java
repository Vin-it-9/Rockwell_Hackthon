package service;


import model.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Core service that implements the payload scheduling algorithm.
 * Handles assignment of payloads to AGVs based on priority, location, and battery levels.
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

    /**
     * Constructor for the scheduling service.
     *
     * @param agvs The list of AGVs in the system
     * @param payloads The list of payloads to be delivered
     * @param network The network graph representing stations and routes
     */
    public SchedulingService(List<AGV> agvs, List<Payload> payloads, NetworkGraph network) {
        this.agvs = new ArrayList<>(agvs);
        // Sort payloads by priority (1 is highest) and then by scheduling time
        this.payloads = new ArrayList<>(payloads);
        this.payloads.sort(Comparator
                .comparing(Payload::getPriority)
                .thenComparing(Payload::getSchedulingTime));
        this.network = network;
        this.currentTime = LocalTime.parse("08:00"); // Start time of simulation
        this.executionLogs = new ArrayList<>();
    }

    /**
     * Runs the simulation until all payloads are delivered or no more progress can be made.
     *
     * @return List of execution logs in the required format
     */
    public List<String> runSimulation() {
        LOGGER.info("Starting simulation at " + currentTime);

        // Continue until all payloads are delivered or no more progress can be made
        boolean progress = true;
        while (progress) {
            progress = false;

            // Check if there are undelivered payloads
            List<Payload> undeliveredPayloads = payloads.stream()
                    .filter(p -> !p.isDelivered())
                    .collect(Collectors.toList());

            if (undeliveredPayloads.isEmpty()) {
                LOGGER.info("All payloads delivered. Ending simulation at " + currentTime);
                break;
            }

            // First, handle AGVs that have completed their tasks
            for (AGV agv : agvs) {
                if (agv.isMoving() && currentTime.equals(agv.getBusyUntil()) ||
                        currentTime.isAfter(agv.getBusyUntil())) {

                    if (agv.isMoving()) {
                        // AGV has completed moving
                        int newStation = 0;

                        // Check if AGV is delivering payload(s) to their destination
                        List<Payload> deliveredPayloads = new ArrayList<>();
                        for (Payload payload : agv.getPayloads()) {
                            if (payload.getDestinationStation() == newStation) {
                                deliveredPayloads.add(payload);
                            }
                        }

                        // Remove delivered payloads
                        for (Payload delivered : deliveredPayloads) {
                            agv.removePayload(delivered);
                            LOGGER.info(agv.getId() + " delivered " + delivered.getId() + " at station " + newStation);
                        }

                        agv.completeMoving(newStation);
                        progress = true;
                    } else if (agv.isCharging()) {
                        // AGV has completed charging
                        agv.completeCharging();
                        LOGGER.info(agv.getId() + " completed charging at station 9");
                        progress = true;
                    }
                }
            }

            // Then, assign new tasks to idle AGVs
            for (AGV agv : agvs) {
                if (agv.isIdle(currentTime)) {
                    // If AGV is at the charging station and has low battery, start charging
                    if (agv.getCurrentStation() == 9 && agv.getBatteryLevel() < 100.0) {
                        agv.startCharging(currentTime);
                        LOGGER.info(agv.getId() + " started charging at station 9");
                        progress = true;
                        continue;
                    }

                    // If AGV has low battery, send it to charge
                    if (agv.getBatteryLevel() < LOW_BATTERY_THRESHOLD && agv.getPayloads().isEmpty()) {
                        if (agv.getCurrentStation() != 9) {
                            // Calculate path to charging station
                            List<Integer> pathToCharging = network.getShortestPath(agv.getCurrentStation(), 9);
                            if (pathToCharging != null && !pathToCharging.isEmpty()) {
                                int nextStation = pathToCharging.get(1); // First step toward charging station
                                double distance = network.getDistance(agv.getCurrentStation(), nextStation);

                                // Log the movement to charging station
                                String logEntry = String.format("%s-%d-%d-%s-%.1f-empty",
                                        agv.getId(),
                                        agv.getCurrentStation(),
                                        nextStation,
                                        currentTime.format(TIME_FORMATTER),
                                        0.0);
                                executionLogs.add(logEntry);

                                agv.startMoving(nextStation, (int)distance, currentTime);
                                LOGGER.info(agv.getId() + " moving to station " + nextStation + " for charging");
                                progress = true;
                                continue;
                            }
                        }
                    }

                    // If AGV already has payloads, deliver them first
                    if (!agv.getPayloads().isEmpty()) {
                        // Find the next destination (could be optimized for multiple payloads)
                        Payload firstPayload = agv.getPayloads().get(0);
                        int destination = firstPayload.getDestinationStation();

                        // Calculate path to destination
                        List<Integer> pathToDestination = network.getShortestPath(agv.getCurrentStation(), destination);
                        if (pathToDestination != null && pathToDestination.size() > 1) {
                            int nextStation = pathToDestination.get(1); // First step toward destination
                            double distance = network.getDistance(agv.getCurrentStation(), nextStation);

                            // Log the movement with payload
                            String payloadIds = agv.getPayloads().stream()
                                    .map(Payload::getId)
                                    .collect(Collectors.joining(","));

                            String logEntry = String.format("%s-%d-%d-%s-%.1f-%s",
                                    agv.getId(),
                                    agv.getCurrentStation(),
                                    nextStation,
                                    currentTime.format(TIME_FORMATTER),
                                    agv.getCurrentLoad(),
                                    payloadIds);
                            executionLogs.add(logEntry);

                            agv.startMoving(nextStation, (int)distance, currentTime);
                            LOGGER.info(agv.getId() + " moving to station " + nextStation +
                                    " to deliver payload(s) " + payloadIds);
                            progress = true;
                            continue;
                        }
                    }

                    // If AGV has sufficient battery, look for new payloads to pick up
                    if (agv.getBatteryLevel() >= MIN_BATTERY_FOR_PICKUP) {
                        // Find available payloads scheduled at or before the current time
                        List<Payload> availablePayloads = undeliveredPayloads.stream()
                                .filter(p -> !p.isDelivered() &&
                                        (p.getSchedulingTime().isBefore(currentTime) ||
                                                p.getSchedulingTime().equals(currentTime)))
                                .collect(Collectors.toList());

                        Payload bestPayload = null;
                        double shortestDistance = Double.MAX_VALUE;

                        // Find the closest high-priority payload
                        for (Payload payload : availablePayloads) {
                            // Skip payloads that are too heavy for the AGV
                            if (!agv.canAddPayload(payload)) continue;

                            double distance = network.getDistance(agv.getCurrentStation(), payload.getSourceStation());

                            // Prefer closer payloads, but prioritize by priority class
                            if (bestPayload == null ||
                                    payload.getPriority() < bestPayload.getPriority() ||
                                    (payload.getPriority() == bestPayload.getPriority() && distance < shortestDistance)) {
                                bestPayload = payload;
                                shortestDistance = distance;
                            }
                        }

                        if (bestPayload != null) {
                            if (agv.getCurrentStation() == bestPayload.getSourceStation()) {
                                // AGV is already at the source station, pick up the payload
                                agv.addPayload(bestPayload);
                                LOGGER.info(agv.getId() + " picked up " + bestPayload.getId() +
                                        " at station " + agv.getCurrentStation());
                                progress = true;
                                continue;
                            } else {
                                // Calculate path to pickup location
                                List<Integer> pathToPickup = network.getShortestPath(
                                        agv.getCurrentStation(), bestPayload.getSourceStation());

                                if (pathToPickup != null && pathToPickup.size() > 1) {
                                    int nextStation = pathToPickup.get(1); // First step toward pickup
                                    double distance = network.getDistance(agv.getCurrentStation(), nextStation);

                                    // Log the movement to pickup
                                    String logEntry = String.format("%s-%d-%d-%s-%.1f-empty",
                                            agv.getId(),
                                            agv.getCurrentStation(),
                                            nextStation,
                                            currentTime.format(TIME_FORMATTER),
                                            0.0);
                                    executionLogs.add(logEntry);

                                    agv.startMoving(nextStation, (int)distance, currentTime);
                                    LOGGER.info(agv.getId() + " moving to station " + nextStation +
                                            " to pick up payload " + bestPayload.getId());
                                    progress = true;
                                    continue;
                                }
                            }
                        }
                    }
                }
            }

            if (!progress) {
                // If no progress was made, advance time to the next event
                LocalTime nextEventTime = getNextEventTime();
                if (nextEventTime != null) {
                    currentTime = nextEventTime;
                    LOGGER.info("Advancing time to next event: " + currentTime);
                    progress = true;
                } else {
                    LOGGER.warning("No more events scheduled. Some payloads might not be deliverable.");
                    break;
                }
            }
        }

        return executionLogs;
    }

    /**
     * Gets the time of the next event (AGV becomes idle or payload becomes available).
     *
     * @return The time of the next event, or null if no more events
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
     * Gets the current simulation time.
     *
     * @return The current simulation time
     */
    public LocalTime getCurrentTime() {
        return currentTime;
    }

    /**
     * Gets all execution logs generated during the simulation.
     *
     * @return List of execution logs
     */
    public List<String> getExecutionLogs() {
        return new ArrayList<>(executionLogs);
    }

    /**
     * Gets the total charge count across all AGVs.
     *
     * @return Total number of charges performed
     */
    public int getTotalChargeCount() {
        return agvs.stream().mapToInt(AGV::getChargeCount).sum();
    }
}