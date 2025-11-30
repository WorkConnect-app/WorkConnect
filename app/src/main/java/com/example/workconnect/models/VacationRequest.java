package com.example.workconnect.models;

import java.util.Date;

public class VacationRequest {

    private String id;               // Unique vacation request ID
    private String employeeId;       // UID of the employee who submitted the request

    private Date startDate;          // Vacation start date
    private Date endDate;            // Vacation end date
    private String type;             // Type of request: "vacation", "sick", "other"

    private String status;           // Request status: "pending", "approved", "rejected"
    private String managerId;        // UID of the manager who approved/rejected the request
    private Date createdAt;          // When the request was created
    private Date decisionAt;         // When a manager made the decision
    private String managerComment;   // Optional comment from the manager

    public VacationRequest() {
        // Required for Firebase deserialization
    }

    public VacationRequest(String id,
                           String employeeId,
                           Date startDate,
                           Date endDate,
                           String type,
                           String status,
                           String managerId,
                           Date createdAt,
                           Date decisionAt,
                           String managerComment) {

        this.id = id;
        this.employeeId = employeeId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.type = type;
        this.status = status;
        this.managerId = managerId;
        this.createdAt = createdAt;
        this.decisionAt = decisionAt;
        this.managerComment = managerComment;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getManagerId() {
        return managerId;
    }

    public void setManagerId(String managerId) {
        this.managerId = managerId;
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
