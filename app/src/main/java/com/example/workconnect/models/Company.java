package com.example.workconnect.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.GeoPoint;

import java.io.Serializable;

/**
 * Model representing a company inside the system.
 * Stored in Firestore under "companies" collection.
 */
public class Company implements Serializable {

    // Basic company info
    private String id;
    private String name;
    private String managerId;
    private Timestamp createdAt;
    private String code;

    // Optional GPS configuration for attendance (null = disabled)
    private AttendanceLocation attendanceLocation;

    // Optional timezone setting (default: Israel)
    private String timeZoneId;

    // Firestore requires a public empty constructor
    public Company() {
        this.timeZoneId = "Asia/Jerusalem";
        // attendanceLocation stays null by default => GPS disabled
    }

    public Company(String id, String name, String managerId,
                   Timestamp createdAt, String code) {
        this.id = id;
        this.name = name;
        this.managerId = managerId;
        this.createdAt = createdAt;
        this.code = code;
        this.timeZoneId = "Asia/Jerusalem";
    }

    // ---------------- Getters / Setters ----------------

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getManagerId() { return managerId; }
    public void setManagerId(String managerId) { this.managerId = managerId; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public AttendanceLocation getAttendanceLocation() { return attendanceLocation; }
    public void setAttendanceLocation(AttendanceLocation attendanceLocation) {
        this.attendanceLocation = attendanceLocation;
    }

    public String getTimeZoneId() { return timeZoneId; }
    public void setTimeZoneId(String timeZoneId) { this.timeZoneId = timeZoneId; }

    /**
     * Convenience method:
     * Returns true only if GPS attendance is configured and enabled.
     */
    public boolean isAttendanceGpsEnabled() {
        return attendanceLocation != null && attendanceLocation.isEnabled();
    }

    // ---------------- Nested model ----------------

    /**
     * Represents GPS configuration for attendance validation.
     * Stored as a nested object inside Company document.
     */
    public static class AttendanceLocation implements Serializable {

        // Whether GPS restriction is enabled
        private boolean enabled;

        // Center point of allowed attendance area (Firestore GeoPoint)
        private GeoPoint center;

        // Allowed radius in meters (e.g., 100m)
        private double radiusMeters;

        // Required empty constructor for Firestore
        public AttendanceLocation() {}

        public AttendanceLocation(boolean enabled,
                                  GeoPoint center,
                                  double radiusMeters) {
            this.enabled = enabled;
            this.center = center;
            this.radiusMeters = radiusMeters;
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public GeoPoint getCenter() { return center; }
        public void setCenter(GeoPoint center) { this.center = center; }

        public double getRadiusMeters() { return radiusMeters; }
        public void setRadiusMeters(double radiusMeters) {
            this.radiusMeters = radiusMeters;
        }
    }
}