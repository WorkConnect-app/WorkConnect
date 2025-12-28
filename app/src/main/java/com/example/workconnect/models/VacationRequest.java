package com.example.workconnect.models;

import com.example.workconnect.models.enums.VacationStatus;

import java.util.Date;

/**
 * Model class representing a vacation request document in Firestore.
 *
 * Notes about IDs:
 * - Firestore already has a document ID (the document path).
 * - This model also stores the same value in the "id" field for convenience,
 *   so the UI can easily reference request.getId() without extra mapping.
 */
public class VacationRequest {

    // Firestore document ID (also stored as a field for convenience)
    private String id;

    // ===== Employee info =====
    // We store employeeId for linking, and a snapshot of name/email for easy display (no join needed).
    private String employeeId;
    private String employeeName;
    private String employeeEmail;

    // ===== Manager info =====
    private String managerId;

    // ===== Vacation details =====
    private Date startDate;
    private Date endDate;
    private String reason;

    // Request status (PENDING / APPROVED / REJECTED)
    private VacationStatus status;

    // Total requested days (calculated when creating the request)
    private int daysRequested;

    // ===== Metadata =====
    private Date createdAt;       // when the request was created
    private Date decisionAt;      // when the manager approved/rejected
    private String managerComment; // optional manager note

    // Prevents double-deduction of balance in case the request is processed twice.
    // null/false = not deducted yet, true = deducted.
    private Boolean balanceDeducted;

    /**
     * Empty constructor required for Firebase / Firestore deserialization.
     */
    public VacationRequest() {
    }

    /**
     * Constructor for creating a new vacation request.
     * New requests should usually start with status = PENDING and balanceDeducted = false.
     */
    public VacationRequest(String id,
                           String employeeId,
                           String employeeName,
                           String employeeEmail,
                           String managerId,
                           Date startDate,
                           Date endDate,
                           String reason,
                           VacationStatus status,
                           int daysRequested,
                           Date createdAt) {

        this.id = id;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.employeeEmail = employeeEmail;
        this.managerId = managerId;

        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;

        this.status = status;
        this.daysRequested = daysRequested;

        this.createdAt = createdAt;

        // These will be filled by the manager action
        this.decisionAt = null;
        this.managerComment = null;

        // New request is not deducted yet
        this.balanceDeducted = false;
    }

    // ===== Getters & Setters =====

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getEmployeeEmail() { return employeeEmail; }
    public void setEmployeeEmail(String employeeEmail) { this.employeeEmail = employeeEmail; }

    public String getManagerId() { return managerId; }
    public void setManagerId(String managerId) { this.managerId = managerId; }

    public Date getStartDate() { return startDate; }
    public void setStartDate(Date startDate) { this.startDate = startDate; }

    public Date getEndDate() { return endDate; }
    public void setEndDate(Date endDate) { this.endDate = endDate; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public VacationStatus getStatus() { return status; }
    public void setStatus(VacationStatus status) { this.status = status; }

    public int getDaysRequested() { return daysRequested; }
    public void setDaysRequested(int daysRequested) { this.daysRequested = daysRequested; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getDecisionAt() { return decisionAt; }
    public void setDecisionAt(Date decisionAt) { this.decisionAt = decisionAt; }

    public String getManagerComment() { return managerComment; }
    public void setManagerComment(String managerComment) { this.managerComment = managerComment; }

    public Boolean getBalanceDeducted() { return balanceDeducted; }
    public void setBalanceDeducted(Boolean balanceDeducted) { this.balanceDeducted = balanceDeducted; }
}
