package model;

import model.Station;
import org.jgrapht.Graph;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents the network of stations and the routes between them.
 * Uses JGraphT library for graph operations and shortest path calculations.
 */
public class NetworkGraph {
    private final Graph<Station, DefaultWeightedEdge> graph;
    private final Map<Integer, Station> stationMap;
    private DijkstraShortestPath<Station, DefaultWeightedEdge> shortestPathAlgorithm;

    /**
     * Constructor to create a new network graph.
     */
    public NetworkGraph() {
        graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        stationMap = new HashMap<>();

        // Create stations (1-9)
        for (int i = 1; i <= 9; i++) {
            Station station = new Station(i);
            stationMap.put(i, station);
            graph.addVertex(station);
        }
    }

    /**
     * Initializes the default network topology based on the problem statement.
     * This method should be called after creating the graph and before any path calculations.
     */
    public void initializeDefaultNetwork() {
        // Initialize the route map - this is just an example
        // Replace with the actual route map from your problem statement

        // Example route map:
        addRoute(1, 2, 1.0);
        addRoute(1, 3, 1.0);
        addRoute(1, 4, 1.0);
        addRoute(2, 5, 1.0);
        addRoute(3, 5, 1.0);
        addRoute(3, 6, 1.0);
        addRoute(4, 7, 1.0);
        addRoute(5, 8, 1.0);
        addRoute(6, 8, 1.0);
        addRoute(7, 8, 1.0);
        addRoute(8, 9, 1.0);

        // Initialize the shortest path algorithm after adding all routes
        shortestPathAlgorithm = new DijkstraShortestPath<>(graph);
    }

    /**
     * Adds a route between two stations with a given distance.
     * Routes are bidirectional by default.
     */
    public void addRoute(int from, int to, double distance) {
        Station fromStation = stationMap.get(from);
        Station toStation = stationMap.get(to);

        if (fromStation != null && toStation != null) {
            DefaultWeightedEdge edge = graph.addEdge(fromStation, toStation);
            if (edge != null) {
                graph.setEdgeWeight(edge, distance);
            }
        } else {
            throw new IllegalArgumentException(
                    "Invalid station ID: " + (fromStation == null ? from : to));
        }
    }

    /**
     * Gets the distance between two stations.
     * Returns POSITIVE_INFINITY if no path exists.
     */
    public double getDistance(int from, int to) {
        if (shortestPathAlgorithm == null) {
            throw new IllegalStateException("Network not initialized. Call initializeDefaultNetwork first.");
        }

        Station fromStation = stationMap.get(from);
        Station toStation = stationMap.get(to);

        if (fromStation != null && toStation != null) {
            try {
                return shortestPathAlgorithm.getPathWeight(fromStation, toStation);
            } catch (IllegalArgumentException e) {
                // No path exists
                return Double.POSITIVE_INFINITY;
            }
        }
        return Double.POSITIVE_INFINITY;  // No path exists
    }

    /**
     * Gets the shortest path between two stations as a list of station IDs.
     * Returns null if no path exists.
     */
    public List<Integer> getShortestPath(int from, int to) {
        if (shortestPathAlgorithm == null) {
            throw new IllegalStateException("Network not initialized. Call initializeDefaultNetwork first.");
        }

        Station fromStation = stationMap.get(from);
        Station toStation = stationMap.get(to);

        if (fromStation != null && toStation != null) {
            var path = shortestPathAlgorithm.getPath(fromStation, toStation);
            if (path == null) {
                return new ArrayList<>();  // No path found
            }

            return path.getVertexList().stream()
                    .map(Station::getId)
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();  // No path exists
    }

    /**
     * Gets the station with the given ID.
     */
    public Station getStation(int id) {
        return stationMap.get(id);
    }

    /**
     * Gets all stations in the network.
     */
    public List<Station> getAllStations() {
        return new ArrayList<>(stationMap.values());
    }

    /**
     * Gets the charging station (Station 9).
     */
    public Station getChargingStation() {
        return stationMap.get(9);
    }

    /**
     * Gets the total number of stations in the network.
     */
    public int getTotalStations() {
        return stationMap.size();
    }

    /**
     * Checks if a direct connection exists between two stations
     */
    public boolean areDirectlyConnected(int from, int to) {
        Station fromStation = stationMap.get(from);
        Station toStation = stationMap.get(to);

        if (fromStation != null && toStation != null) {
            return graph.containsEdge(fromStation, toStation);
        }
        return false;
    }

    /**
     * Updates the network with a custom route configuration.
     * Clears existing routes and adds new ones.
     *
     * @param routes Map of source station ID to a map of destination station ID and distance
     */
    public void updateNetwork(Map<Integer, Map<Integer, Double>> routes) {
        // Clear existing edges
        for (DefaultWeightedEdge edge : new ArrayList<>(graph.edgeSet())) {
            graph.removeEdge(edge);
        }

        // Add new routes
        for (Map.Entry<Integer, Map<Integer, Double>> sourceEntry : routes.entrySet()) {
            int source = sourceEntry.getKey();
            for (Map.Entry<Integer, Double> destEntry : sourceEntry.getValue().entrySet()) {
                int dest = destEntry.getKey();
                double distance = destEntry.getValue();
                addRoute(source, dest, distance);
            }
        }

        // Reinitialize shortest path algorithm
        shortestPathAlgorithm = new DijkstraShortestPath<>(graph);
    }
}