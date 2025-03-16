package util;

import model.AGV;
import model.NetworkGraph;
import model.Payload;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for loading data from files and initializing the system.
 * Handles loading payload data, creating the station network, and setting up AGVs.
 */
public class DataLoader {
    private static final Logger LOGGER = Logger.getLogger(DataLoader.class.getName());
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");

    /**
     * Loads payload data from a CSV file.
     * Expected format: payload_id,source_station,destination_station,weight,priority,scheduling_time
     *
     * @param filePath Path to the CSV file containing payload data
     * @return List of Payload objects
     */
    public List<Payload> loadPayloads(String filePath) {

        List<Payload> payloads = new ArrayList<>();

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(filePath);
             BufferedReader fileReader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(fileReader, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build())) {

            for (CSVRecord csvRecord : csvParser) {
                String id = csvRecord.get(0);
                int sourceStation = Integer.parseInt(csvRecord.get(1));
                int destinationStation = Integer.parseInt(csvRecord.get(2));
                double weight = Double.parseDouble(csvRecord.get(3));
                int priority = Integer.parseInt(csvRecord.get(4));
                LocalTime schedulingTime = LocalTime.parse(csvRecord.get(5), TIME_FORMATTER);

                Payload payload = new Payload(id, sourceStation, destinationStation, weight, priority, schedulingTime);
                payloads.add(payload);
            }

            LOGGER.info("Loaded " + payloads.size() + " payloads from " + filePath);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error loading payload data: " + e.getMessage(), e);
        } catch (NullPointerException e) {
            LOGGER.log(Level.SEVERE, "File not found: " + filePath, e);
        }

        return payloads;
    }

    /**
     * Initializes the station network with routes between stations.
     * For this implementation, we create a complete graph where any station can directly
     * connect to any other station with the Euclidean distance between them.
     *
     * @return Configured NetworkGraph representing the station network
     */
    public NetworkGraph initializeNetwork() {
        NetworkGraph network = new NetworkGraph();

        // Create a simple network where all stations are connected to each other
        // with distances that follow the problem statement

        // Station positions (simple grid layout)
        int[][] stationPositions = {
                {0, 0},    // Station 1
                {10, 0},   // Station 2
                {20, 0},   // Station 3
                {0, 10},   // Station 4
                {10, 10},  // Station 5
                {20, 10},  // Station 6
                {0, 20},   // Station 7
                {10, 20},  // Station 8
                {20, 20}   // Station 9 (charging station)
        };

        // Connect all stations with their Euclidean distances
        for (int i = 0; i < 9; i++) {
            for (int j = i + 1; j < 9; j++) {
                // Calculate Euclidean distance
                int x1 = stationPositions[i][0];
                int y1 = stationPositions[i][1];
                int x2 = stationPositions[j][0];
                int y2 = stationPositions[j][1];

                double distance = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));

                // Add route between stations (station IDs are 1-based, array indices are 0-based)
                network.addRoute(i + 1, j + 1, distance);
            }
        }

        // Critical: Initialize the shortestPath algorithm after adding all routes
        network.initializeDefaultNetwork();

        LOGGER.info("Network initialized with 9 stations");
        return network;
    }

    /**
     * Initializes the fleet of AGVs with their starting positions and battery levels.
     *
     * @return List of initialized AGV objects
     */
    public List<AGV> initializeAGVs() {
        List<AGV> agvs = new ArrayList<>();

        // Initialize AGVs with their starting positions according to problem statement
        agvs.add(new AGV("agv_1", 1, 100.0)); // AGV 1 starts at Station 1 with 100% battery
        agvs.add(new AGV("agv_2", 3, 100.0)); // AGV 2 starts at Station 3 with 100% battery
        agvs.add(new AGV("agv_3", 7, 100.0)); // AGV 3 starts at Station 7 with 100% battery

        LOGGER.info("Initialized " + agvs.size() + " AGVs");
        return agvs;
    }

    /**
     * Loads payload data from a string (useful for testing or direct data input).
     * Expected format: One payload per line, format same as CSV file.
     *
     * @param payloadData String containing payload data in CSV format
     * @return List of Payload objects
     */
    public List<Payload> loadPayloadsFromString(String payloadData) {
        List<Payload> payloads = new ArrayList<>();

        try (CSVParser csvParser = CSVParser.parse(payloadData, CSVFormat.DEFAULT.builder().build())) {
            for (CSVRecord csvRecord : csvParser) {
                if (csvRecord.size() < 6) continue; // Skip invalid records

                String id = csvRecord.get(0);
                int sourceStation = Integer.parseInt(csvRecord.get(1));
                int destinationStation = Integer.parseInt(csvRecord.get(2));
                double weight = Double.parseDouble(csvRecord.get(3));
                int priority = Integer.parseInt(csvRecord.get(4));
                LocalTime schedulingTime = LocalTime.parse(csvRecord.get(5), TIME_FORMATTER);

                Payload payload = new Payload(id, sourceStation, destinationStation, weight, priority, schedulingTime);
                payloads.add(payload);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error parsing payload data: " + e.getMessage(), e);
        }

        return payloads;
    }
}