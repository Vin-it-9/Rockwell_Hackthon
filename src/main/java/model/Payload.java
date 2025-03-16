package model;

import java.time.LocalTime;

/**
 * Represents a payload that needs to be transported by an AGV.
 * Each payload has a source station, destination station, weight, priority, and scheduled time.
 */
public class Payload {
    private final String id;               // Unique identifier (e.g., "payload_1")
    private final int sourceStation;       // Source station ID (1-9)
    private final int destinationStation;  // Destination station ID (1-9)
    private final double weight;           // Weight of the payload (max 10)
    private final int priority;            // Priority (1-3, where 1 is highest)
    private final LocalTime schedulingTime; // Time when the payload is scheduled (e.g., 08:01)
    private boolean delivered;       // Whether the payload has been delivered

    /**
     * Constructor for creating a payload.
     */
    public Payload(String id, int sourceStation, int destinationStation,
                   double weight, int priority, LocalTime schedulingTime) {
        this.id = id;
        this.sourceStation = sourceStation;
        this.destinationStation = destinationStation;
        this.weight = weight;
        this.priority = priority;
        this.schedulingTime = schedulingTime;
        this.delivered = false;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public int getSourceStation() {
        return sourceStation;
    }

    public int getDestinationStation() {
        return destinationStation;
    }

    public double getWeight() {
        return weight;
    }

    public int getPriority() {
        return priority;
    }

    public LocalTime getSchedulingTime() {
        return schedulingTime;
    }

    public boolean isDelivered() {
        return delivered;
    }

    public void setDelivered(boolean delivered) {
        this.delivered = delivered;
    }

    @Override
    public String toString() {
        return "Payload{" +
                "id='" + id + '\'' +
                ", sourceStation=" + sourceStation +
                ", destinationStation=" + destinationStation +
                ", weight=" + weight +
                ", priority=" + priority +
                ", schedulingTime=" + schedulingTime +
                ", delivered=" + delivered +
                '}';
    }
}