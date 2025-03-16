package model;

import java.util.Objects;

/**
 * Represents a station in the AGV network.
 * Each station has a unique ID and can be connected to other stations.
 * Station 9 is specially designated as a charging station.
 */
public class Station {
    private final int id;                  // Station ID (1-9)
    private final boolean isChargingStation; // Whether this is a charging station

    /**
     * Constructor for creating a station.
     */
    public Station(int id) {
        this.id = id;
        // According to the problem statement, station 9 is the charging station
        this.isChargingStation = (id == 9);
    }

    /**
     * Gets the station ID.
     */
    public int getId() {
        return id;
    }

    /**
     * Checks if this station is a charging station.
     */
    public boolean isChargingStation() {
        return isChargingStation;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Station station = (Station) obj;
        return id == station.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Station{" +
                "id=" + id +
                ", isChargingStation=" + isChargingStation +
                '}';
    }
}