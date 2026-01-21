package com.example.workconnect.models;

public class AvailabilityPref {
    private String userId;
    private String teamId;
    private String shiftId;
    private String dateKey;  // yyyy-MM-dd
    private String status;   // CAN / PREFER_NOT / CANT

    public AvailabilityPref() {}

    public AvailabilityPref(String userId, String teamId, String shiftId, String dateKey, String status) {
        this.userId = userId;
        this.teamId = teamId;
        this.shiftId = shiftId;
        this.dateKey = dateKey;
        this.status = status;
    }

    public String getUserId() { return userId; }
    public String getTeamId() { return teamId; }
    public String getShiftId() { return shiftId; }
    public String getDateKey() { return dateKey; }
    public String getStatus() { return status; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
    public void setShiftId(String shiftId) { this.shiftId = shiftId; }
    public void setDateKey(String dateKey) { this.dateKey = dateKey; }
    public void setStatus(String status) { this.status = status; }
}
