package model;

import model.Payload;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an Automated Guided Vehicle (AGV).
 * Each AGV has a current location, battery level, and can carry multiple payloads
 * up to its maximum capacity.
 */
public class AGV {
    private final String id;               // AGV identifier (e.g., "agv_1")
    private int currentStation;      // Current station where AGV is located
    private double batteryLevel;     // Battery level (0-100%)
    private double currentLoad;      // Current weight being carried (0-10)
    private boolean charging;        // Whether AGV is currently charging
    private boolean moving;          // Whether AGV is currently moving
    private LocalTime busyUntil;     // Time until which AGV is busy
    private final List<Payload> payloads;  // Payloads currently being carried
    private int chargeCount;         // Number of times the AGV has been charged
    private int destinationStation;  // Track the destination during movement

    // Constants from the problem statement
    public static final double MAX_CAPACITY = 10.0;  // Maximum weight an AGV can carry
    public static final int EMPTY_TRAVEL_TIME = 5;   // Minutes to travel 1 unit with no load
    public static final int FULL_TRAVEL_TIME = 10;   // Minutes to travel 1 unit with full load
    public static final int CHARGE_TIME = 15;        // Minutes to fully charge
    public static final int DISCHARGE_TIME = 45;     // Minutes to fully discharge
    public static final double BATTERY_THRESHOLD = 20.0; // Battery threshold for charging

    /**
     * Constructor for creating an AGV.
     */
    public AGV(String id, int initialStation, double initialBattery) {
        this.id = id;
        this.currentStation = initialStation;
        this.batteryLevel = initialBattery;
        this.currentLoad = 0.0;
        this.charging = false;
        this.moving = false;
        this.busyUntil = LocalTime.parse("08:00"); // Start of the day
        this.payloads = new ArrayList<>();
        this.chargeCount = 0;
        this.destinationStation = initialStation;
    }

    /**
     * Checks if the AGV can take on a new payload.
     */
    public boolean canAddPayload(Payload payload) {
        return currentLoad + payload.getWeight() <= MAX_CAPACITY;
    }

    /**
     * Adds a payload to the AGV.
     */
    public void addPayload(Payload payload) {
        if (canAddPayload(payload)) {
            payloads.add(payload);
            currentLoad += payload.getWeight();
        } else {
            throw new IllegalStateException("Cannot add payload - exceeds capacity");
        }
    }

    /**
     * Removes a payload from the AGV.
     */
    public void removePayload(Payload payload) {
        if (payloads.remove(payload)) {
            currentLoad -= payload.getWeight();
        }
    }

    /**
     * Calculates travel time based on current load.
     * Linear interpolation between empty and full travel times.
     */
    public int calculateTravelTime(int distance) {
        double loadRatio = currentLoad / MAX_CAPACITY;
        double timePerUnit = EMPTY_TRAVEL_TIME + loadRatio * (FULL_TRAVEL_TIME - EMPTY_TRAVEL_TIME);
        return (int) Math.ceil(timePerUnit * distance);
    }

    /**
     * Starts charging the AGV.
     */
    public void startCharging(LocalTime currentTime) {
        charging = true;
        moving = false; // Cannot be moving and charging
        busyUntil = currentTime.plusMinutes(CHARGE_TIME);
        chargeCount++;
    }

    /**
     * Completes charging and resets the battery level.
     */
    public void completeCharging() {
        charging = false;
        batteryLevel = 100.0;
    }

    /**
     * Starts moving the AGV to a new station.
     */
    public void startMoving(int destination, int distance, LocalTime currentTime) {
        moving = true;
        charging = false; // Cannot be charging and moving
        destinationStation = destination; // Store destination

        // Calculate travel time
        int travelTime = calculateTravelTime(distance);
        busyUntil = currentTime.plusMinutes(travelTime);

        // Calculate battery consumption based on distance and load
        // Battery drains over DISCHARGE_TIME minutes of full travel
        double consumptionPerUnit = 100.0 / DISCHARGE_TIME;

        // More load = more battery usage, max 2x for full load
        double loadFactor = 1.0 + (currentLoad / MAX_CAPACITY);

        // Calculate battery used for this movement - scale by distance and time
        double batteryUsed = consumptionPerUnit * distance * loadFactor *
                (travelTime / (double)(distance * FULL_TRAVEL_TIME));

        // Cap battery usage to reasonable values
        batteryUsed = Math.min(batteryUsed, 30.0); // Max 30% per movement

        batteryLevel = Math.max(0.0, batteryLevel - batteryUsed);
    }

    /**
     * Completes moving the AGV and updates its location.
     */
    public void completeMoving() {
        moving = false;
        currentStation = destinationStation; // Update station to the destination
    }

    /**
     * Checks if the AGV is idle (not moving or charging).
     */
    public boolean isIdle(LocalTime currentTime) {
        return !moving && !charging && (busyUntil.equals(currentTime) || busyUntil.isBefore(currentTime));
    }

    /**
     * Checks if the AGV needs charging (battery below threshold).
     */
    public boolean needsCharging() {
        return batteryLevel < BATTERY_THRESHOLD;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public int getCurrentStation() {
        return currentStation;
    }

    public int getDestinationStation() {
        return destinationStation;
    }

    public double getBatteryLevel() {
        return batteryLevel;
    }

    public double getCurrentLoad() {
        return currentLoad;
    }

    public boolean isCharging() {
        return charging;
    }

    public boolean isMoving() {
        return moving;
    }

    public LocalTime getBusyUntil() {
        return busyUntil;
    }

    public List<Payload> getPayloads() {
        return new ArrayList<>(payloads);
    }

    public int getChargeCount() {
        return chargeCount;
    }

    @Override
    public String toString() {
        return "AGV{" +
                "id='" + id + '\'' +
                ", currentStation=" + currentStation +
                ", batteryLevel=" + batteryLevel +
                ", currentLoad=" + currentLoad +
                ", charging=" + charging +
                ", moving=" + moving +
                ", busyUntil=" + busyUntil +
                ", payloads=" + payloads +
                ", chargeCount=" + chargeCount +
                '}';
    }
}