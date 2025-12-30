package com.example.workconnect.models;

import java.util.Date;

/**
 * Model class representing a single attendance record for an employee.
 * Each record stores clock-in / clock-out times and the total hours worked for a day.
 */
public class AttendanceRecord {

    // Unique identifier of the attendance record (Firestore document ID)
    private String id;

    // ID of the employee this record belongs to
    private String userId;

    // The date of the workday
    private Date date;

    // Time when the employee clocked in
    private Date clockInTime;

    // Time when the employee clocked out
    private Date clockOutTime;

    // Total number of hours worked during the day
    private double totalHours;

    /**
     * Empty constructor required for Firebase / Firestore deserialization.
     */
    public AttendanceRecord() {
    }

    /**
     * Creates a fully initialized attendance record.
     */
    public AttendanceRecord(String id,
                            String userId,
                            Date date,
                            Date clockInTime,
                            Date clockOutTime,
                            double totalHours) {

        this.id = id;
        this.userId = userId;
        this.date = date;
        this.clockInTime = clockInTime;
        this.clockOutTime = clockOutTime;
        this.totalHours = totalHours;
    }

    // ===== Getters & Setters =====

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Date getClockInTime() {
        return clockInTime;
    }

    public void setClockInTime(Date clockInTime) {
        this.clockInTime = clockInTime;
    }

    public Date getClockOutTime() {
        return clockOutTime;
    }

    public void setClockOutTime(Date clockOutTime) {
        this.clockOutTime = clockOutTime;
    }

    public double getTotalHours() {
        return totalHours;
    }

    public void setTotalHours(double totalHours) {
        this.totalHours = totalHours;
    }
}
