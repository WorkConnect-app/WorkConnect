package com.example.workconnect.models;

import java.util.Date;

public class Task {

    private String id;                   // Unique task ID
    private String title;                // Task title
    private String description;          // Detailed task description

    private String assignedToUserId;     // UID of the employee the task is assigned to
    private String createdByManagerId;   // UID of the manager who created/assigned the task

    private String status;               // Task status: "open", "in_progress", "done"
    private int priority;                // Priority level (1–5)
    private Date createdAt;              // Timestamp when the task was created
    private Date dueDate;                // Optional deadline for the task

    public Task() {
        // Required for Firebase deserialization
    }

    public Task(String id,
                String title,
                String description,
                String assignedToUserId,
                String createdByManagerId,
                String status,
                int priority,
                Date createdAt,
                Date dueDate) {

        this.id = id;
        this.title = title;
        this.description = description;
        this.assignedToUserId = assignedToUserId;
        this.createdByManagerId = createdByManagerId;
        this.status = status;
        this.priority = priority;
        this.createdAt = createdAt;
        this.dueDate = dueDate;
    }

    // ===== Getters & Setters =====

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAssignedToUserId() {
        return assignedToUserId;
    }

    public void setAssignedToUserId(String assignedToUserId) {
        this.assignedToUserId = assignedToUserId;
    }

    public String getCreatedByManagerId() {
        return createdByManagerId;
    }

    public void setCreatedByManagerId(String createdByManagerId) {
        this.createdByManagerId = createdByManagerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getDueDate() {
        return dueDate;
    }

    public void setDueDate(Date dueDate) {
        this.dueDate = dueDate;
    }
}
