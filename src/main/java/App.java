import model.*;
import service.*;
import util.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main application entry point for the AGV fleet scheduler.
 */
public class App {

    private static final Logger LOGGER = Logger.getLogger(App.class.getName());

    public static void main(String[] args) {
        LOGGER.info("Starting AGV Fleet Scheduler");

        String payloadFile = "payload.csv";
        String outputFile = "execution_logs.txt";
        String reportFile = "simulation_report.txt";
        String summaryFile = "summary_report.txt";

        // Parse command line arguments
        if (args.length >= 1) {
            payloadFile = args[0];
        }
        if (args.length >= 2) {
            outputFile = args[1];
        }
        if (args.length >= 3) {
            reportFile = args[2];
        }
        if (args.length >= 4) {
            summaryFile = args[3];
        }

        try {
            // Initialize data loader
            DataLoader dataLoader = new DataLoader();

            // Load payloads from file
            List<Payload> payloads = dataLoader.loadPayloads(payloadFile);

            if (payloads.isEmpty()) {
                LOGGER.severe("No payloads loaded. Exiting.");
                return;
            }

            // Initialize network graph
            NetworkGraph network = dataLoader.initializeNetwork();

            // Initialize AGVs
            List<AGV> agvs = dataLoader.initializeAGVs();

            LOGGER.info("Simulation configuration:");
            LOGGER.info("- Payloads: " + payloads.size());
            LOGGER.info("- AGVs: " + agvs.size());
            LOGGER.info("- Stations: " + network.getTotalStations());

            // Create and run simulation
            SimulationEngine engine = new SimulationEngine(agvs, payloads, network);
            engine.runSimulation();

            // Write execution logs to file
            writeExecutionLogs(engine.getExecutionLogs(), outputFile);

            // Generate and write detailed report
            String report = engine.generateReport();
            writeReportToFile(report, reportFile);

            // Generate and write summary report
            String summaryReport = engine.generateSummaryReport();
            writeReportToFile(summaryReport, summaryFile);

            // Display key metrics in the console
            displayKeyMetrics(engine);

            LOGGER.info("Simulation complete. Results written to:");
            LOGGER.info("- Execution logs: " + outputFile);
            LOGGER.info("- Detailed report: " + reportFile);
            LOGGER.info("- Summary report: " + summaryFile);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error running simulation: " + e.getMessage(), e);
        }
    }

    /**
     * Displays key metrics from the simulation in the console.
     */
    private static void displayKeyMetrics(SimulationEngine engine) {
        System.out.println("\n=== AGV Fleet Scheduling Results ===");
        System.out.println("Total execution time: " + engine.getTotalExecutionTime() + " minutes");

        System.out.println("\nAverage delivery times by priority:");
        Map<Integer, Double> avgDeliveryTimes = engine.getAverageDeliveryTimesByPriority();
        for (int i = 1; i <= 3; i++) {
            double avgTime = avgDeliveryTimes.getOrDefault(i, 0.0);
            System.out.println("  Priority " + i + ": " + String.format("%.2f", avgTime) + " minutes");
        }

        System.out.println("\nTotal AGV charges: " + engine.getTotalChargeCount());
        System.out.println("===============================\n");
    }

    /**
     * Writes execution logs to a file.
     */
    private static void writeExecutionLogs(List<String> logs, String outputFile) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            for (String log : logs) {
                writer.write(log);
                writer.newLine();
            }
            LOGGER.info("Successfully wrote " + logs.size() + " execution log entries to " + outputFile);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error writing execution logs to " + outputFile, e);
        }
    }

    /**
     * Writes the simulation report to a file.
     */
    private static void writeReportToFile(String report, String reportFile) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(reportFile))) {
            writer.write(report);
            LOGGER.info("Successfully wrote report to " + reportFile);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error writing report to " + reportFile, e);
        }
    }
}