package service;

import model.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;

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
        LOGGER.info("Total execution time: " + calculateTimeDifference(startTime, endTime) + " minutes");
        LOGGER.info("Total charge count: " + scheduler.getTotalChargeCount());
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
        int deliveredCount = 0;
        for (Payload payload : payloads) {
            if (payload.isDelivered()) {
                deliveredCount++;
            }
        }

        report.append("Summary:\n");
        report.append("- Total payloads: ").append(payloads.size()).append("\n");
        report.append("- Delivered payloads: ").append(deliveredCount).append("\n");
        report.append("- Delivery rate: ").append(String.format("%.1f%%", (double) deliveredCount / payloads.size() * 100)).append("\n");
        report.append("- Total AGV charges: ").append(scheduler.getTotalChargeCount()).append("\n");
        report.append("- Simulation time: ").append(calculateTimeDifference(LocalTime.parse("08:00"), scheduler.getCurrentTime())).append(" minutes\n\n");

        // AGV status
        report.append("AGV Status:\n");
        for (AGV agv : agvs) {
            report.append("- ").append(agv.getId()).append(":\n");
            report.append("  - Final location: Station ").append(agv.getCurrentStation()).append("\n");
            report.append("  - Final battery level: ").append(String.format("%.1f%%", agv.getBatteryLevel())).append("\n");
            report.append("  - Charge count: ").append(agv.getChargeCount()).append("\n");
        }
        report.append("\n");

        // Execution logs
        report.append("Execution Logs:\n");
        for (String log : executionLogs) {
            report.append("- ").append(log).append("\n");
        }

        return report.toString();
    }

    /**
     * Gets the list of execution logs.
     */
    public List<String> getExecutionLogs() {
        return executionLogs;
    }
}