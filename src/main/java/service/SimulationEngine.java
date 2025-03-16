package service;

import model.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Manages the execution of the simulation and provides reporting.
 */
public class SimulationEngine {
    private static final Logger LOGGER = Logger.getLogger(SimulationEngine.class.getName());
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");

    private final List<AGV> agvs;
    private final List<Payload> payloads;
    private final NetworkGraph network;
    private final SchedulingService scheduler;
    private List<String> executionLogs;

    /**
     * Constructor for the simulation engine.
     */
    public SimulationEngine(List<AGV> agvs, List<Payload> payloads, NetworkGraph network) {
        this.agvs = agvs;
        this.payloads = payloads;
        this.network = network;
        this.scheduler = new SchedulingService(agvs, payloads, network);
    }

    /**
     * Runs the simulation and collects results.
     */
    public void runSimulation() {
        LOGGER.info("Starting simulation with " + agvs.size() + " AGVs and " + payloads.size() + " payloads");

        LocalTime startTime = LocalTime.parse("08:00");

        // Run the scheduler
        executionLogs = scheduler.runSimulation();

        LocalTime endTime = scheduler.getCurrentTime();

        LOGGER.info("Simulation complete");
        LOGGER.info("Start time: " + startTime.format(TIME_FORMATTER));
        LOGGER.info("End time: " + endTime.format(TIME_FORMATTER));
        LOGGER.info("Total execution time: " + scheduler.getTotalExecutionTime() + " minutes");
        LOGGER.info("Total charge count: " + scheduler.getTotalChargeCount());

        // Log delivery times by priority
        Map<Integer, Double> avgDeliveryTimes = scheduler.getAverageDeliveryTimesByPriority();
        for (Map.Entry<Integer, Double> entry : avgDeliveryTimes.entrySet()) {
            LOGGER.info("Average delivery time for priority " + entry.getKey() + ": " +
                    String.format("%.2f", entry.getValue()) + " minutes");
        }
    }

    /**
     * Calculates the difference between two LocalTime objects in minutes.
     */
    private int calculateTimeDifference(LocalTime startTime, LocalTime endTime) {
        int minutesDiff = endTime.getHour() * 60 + endTime.getMinute() - (startTime.getHour() * 60 + startTime.getMinute());
        if (minutesDiff < 0) {
            minutesDiff += 24 * 60; // Handle wrap-around if end time is on the next day
        }
        return minutesDiff;
    }

    /**
     * Generates a report of the simulation results.
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("AGV Fleet Scheduling Simulation Report\n");
        report.append("=====================================\n\n");

        // Summary statistics
        int deliveredCount = (int) payloads.stream().filter(Payload::isDelivered).count();
        int undeliveredCount = payloads.size() - deliveredCount;

        report.append("1. Summary Statistics\n");
        report.append("---------------------\n");
        report.append("Total execution time: ").append(scheduler.getTotalExecutionTime()).append(" minutes\n");
        report.append("Total payloads: ").append(payloads.size()).append("\n");
        report.append("Delivered payloads: ").append(deliveredCount).append("\n");
        if (undeliveredCount > 0) {
            report.append("Undelivered payloads: ").append(undeliveredCount).append("\n");
        }
        report.append("Delivery rate: ").append(String.format("%.1f%%", (double) deliveredCount / payloads.size() * 100)).append("\n\n");

        // Average delivery times by priority
        report.append("2. Average Delivery Time by Priority\n");
        report.append("----------------------------------\n");
        Map<Integer, Double> avgDeliveryTimes = scheduler.getAverageDeliveryTimesByPriority();
        for (int i = 1; i <= 3; i++) {
            double avgTime = avgDeliveryTimes.getOrDefault(i, 0.0);
            report.append("Priority ").append(i).append(": ").append(String.format("%.2f", avgTime)).append(" minutes\n");
        }
        report.append("\n");

        // AGV status
        report.append("3. AGV Status\n");
        report.append("------------\n");
        for (AGV agv : agvs) {
            report.append("- ").append(agv.getId()).append(":\n");
            report.append("  - Final location: Station ").append(agv.getCurrentStation()).append("\n");
            report.append("  - Final battery level: ").append(String.format("%.1f%%", agv.getBatteryLevel())).append("\n");
            report.append("  - Charge count: ").append(agv.getChargeCount()).append("\n");
            if (!agv.getPayloads().isEmpty()) {
                report.append("  - Carrying payloads: ").append(
                        agv.getPayloads().stream()
                                .map(Payload::getId)
                                .collect(Collectors.joining(", "))
                ).append("\n");
            }
        }
        report.append("\n");

        // Undelivered payloads (if any)
        List<Payload> undeliveredPayloads = payloads.stream()
                .filter(p -> !p.isDelivered())
                .collect(Collectors.toList());

        if (!undeliveredPayloads.isEmpty()) {
            report.append("4. Undelivered Payloads\n");
            report.append("----------------------\n");
            for (Payload payload : undeliveredPayloads) {
                report.append("- ").append(payload.getId())
                        .append(" (Priority: ").append(payload.getPriority())
                        .append(", Source: Station ").append(payload.getSourceStation())
                        .append(", Destination: Station ").append(payload.getDestinationStation())
                        .append(", Weight: ").append(payload.getWeight())
                        .append(")\n");
            }
            report.append("\n");
        }

        // Execution logs summary
        report.append("5. Execution Logs Summary\n");
        report.append("-----------------------\n");
        report.append("Total log entries: ").append(executionLogs.size()).append("\n\n");

        // Full execution logs
        report.append("6. Full Execution Logs\n");
        report.append("--------------------\n");
        for (String log : executionLogs) {
            report.append("- ").append(log).append("\n");
        }

        return report.toString();
    }

    /**
     * Generates a summary report focusing on the key metrics.
     */
    public String generateSummaryReport() {
        StringBuilder report = new StringBuilder();
        report.append("AGV Fleet Scheduling - Summary Report\n");
        report.append("====================================\n\n");

        // Core metrics as per the requirements
        report.append("1. Total Execution Time: ").append(scheduler.getTotalExecutionTime()).append(" minutes\n\n");

        report.append("2. Average Delivery Time by Priority:\n");
        Map<Integer, Double> avgDeliveryTimes = scheduler.getAverageDeliveryTimesByPriority();
        for (int i = 1; i <= 3; i++) {
            double avgTime = avgDeliveryTimes.getOrDefault(i, 0.0);
            report.append("   Priority ").append(i).append(": ").append(String.format("%.2f", avgTime)).append(" minutes\n");
        }
        report.append("\n");

        report.append("3. AGV Charge Count:\n");
        for (AGV agv : agvs) {
            report.append("   ").append(agv.getId()).append(": ").append(agv.getChargeCount()).append(" charges\n");
        }
        report.append("   Total charges: ").append(scheduler.getTotalChargeCount()).append("\n\n");

        // Delivery statistics
        int deliveredCount = (int) payloads.stream().filter(Payload::isDelivered).count();
        report.append("4. Delivery Statistics:\n");
        report.append("   Payloads delivered: ").append(deliveredCount).append(" / ").append(payloads.size())
                .append(" (").append(String.format("%.1f%%", (double) deliveredCount / payloads.size() * 100)).append(")\n");

        return report.toString();
    }

    /**
     * Gets the list of execution logs.
     */
    public List<String> getExecutionLogs() {
        return new ArrayList<>(executionLogs);
    }

    /**
     * Gets the average delivery time for each priority class.
     */
    public Map<Integer, Double> getAverageDeliveryTimesByPriority() {
        return scheduler.getAverageDeliveryTimesByPriority();
    }

    /**
     * Gets the total execution time in minutes.
     */
    public int getTotalExecutionTime() {
        return scheduler.getTotalExecutionTime();
    }

    /**
     * Gets the total number of times AGVs were charged.
     */
    public int getTotalChargeCount() {
        return scheduler.getTotalChargeCount();
    }
}