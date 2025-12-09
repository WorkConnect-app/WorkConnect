package com.example.workconnect.models;

import java.util.Date;

public class AttendanceRecord {

    private String id;             // Unique record ID
    private String userId;         // UID of the user (employee)

    private Date date;             // The workday date
    private Date clockInTime;      // Timestamp of clock-in
    private Date clockOutTime;     // Timestamp of clock-out
    private double totalHours;     // Total hours worked for that day

    public AttendanceRecord() {
        // Required for Firebase deserialization
    }

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
