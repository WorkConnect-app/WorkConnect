package com.example.workconnect.models;
import com.example.workconnect.models.enums.VacationStatus;

import java.util.Date;

public class VacationRequest {

    private String id;
    private String employeeId;
    private String managerId;
    private Date startDate;
    private Date endDate;
    private String reason;
    private VacationStatus status;
    private int daysRequested;
    private Date createdAt;        // when the request was created
    private Date decisionAt;       // when the manager approved/rejected
    private String managerComment; // optional comment from manager

    public VacationRequest() {
        // Required for Firebase deserialization
    }

    public VacationRequest(String id,
                           String employeeId,
                           String managerId,
                           Date startDate,
                           Date endDate,
                           String reason,
                           VacationStatus status,
                           int daysRequested,
                           Date createdAt) {

        this.id = id;
        this.employeeId = employeeId;
        this.managerId = managerId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.status = status;
        this.daysRequested = daysRequested;
        this.createdAt = createdAt;
        this.decisionAt = null;
        this.managerComment = null;
    }

    // ===== Getters & Setters =====

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getManagerId() {
        return managerId;
    }

    public void setManagerId(String managerId) {
        this.managerId = managerId;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public VacationStatus getStatus() {
        return status;
    }

    public void setStatus(VacationStatus status) {
        this.status = status;
    }

    public int getDaysRequested() {
        return daysRequested;
    }

    public void setDaysRequested(int daysRequested) {
        this.daysRequested = daysRequested;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getDecisionAt() {
        return decisionAt;
    }

    public void setDecisionAt(Date decisionAt) {
        this.decisionAt = decisionAt;
    }

    public String getManagerComment() {
        return managerComment;
    }

    public void setManagerComment(String managerComment) {
        this.managerComment = managerComment;
    }
}
