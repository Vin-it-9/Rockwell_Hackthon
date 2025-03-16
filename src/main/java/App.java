
import model.*;
import service.*;
import util.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
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

            // Create and run simulation
            SimulationEngine engine = new SimulationEngine(agvs, payloads, network);
            engine.runSimulation();

            // Write execution logs to file
            writeExecutionLogs(engine.getExecutionLogs(), outputFile);

            // Generate and write report
            String report = engine.generateReport();
            writeReportToFile(report, reportFile);

            LOGGER.info("Simulation complete. Results written to " + outputFile + " and " + reportFile);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error running simulation: " + e.getMessage(), e);
        }
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
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error writing report to " + reportFile, e);
        }
    }
}