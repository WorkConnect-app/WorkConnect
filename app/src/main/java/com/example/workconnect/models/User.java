package com.example.workconnect.models;

import java.util.Date;

/**
 * Main user model used across the app.
 * Represents both employees and managers.
 */
public class User {

    private String uid;                    // Unique identifier (Firebase Auth UID)
    private String firstName;              // User's first name
    private String lastName;               // User's last name
    private String email;                  // Email address
    private String phoneNumber;            // Phone number

    private Date dateOfStartWork;          // Employment start date

    // Vacation fields
    private int totalVacationDaysPerYear;  // Total vacation days allocated per year
    private int remainingVacationDays;     // Days still available to use
    private int usedVacationDays;          // Days already used

    private String companyId;   // Which company this user belongs to (FK to companies collection)
    private String role;        // "employee" or "manager"
    private String status;      // "pending", "approved", "rejected"

    public User() {
        // Required for Firebase / serialization libraries
    }

    public User(String uid,
                String firstName,
                String lastName,
                String email,
                String phoneNumber,
                Date dateOfStartWork,
                int totalVacationDaysPerYear,
                int remainingVacationDays,
                int usedVacationDays,
                String companyId,
                String role,
                String status) {

        this.uid = uid;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.dateOfStartWork = dateOfStartWork;
        this.totalVacationDaysPerYear = totalVacationDaysPerYear;
        this.remainingVacationDays = remainingVacationDays;
        this.usedVacationDays = usedVacationDays;
        this.companyId = companyId;
        this.role = role;
        this.status = status;
    }

    // ===== Getters & Setters =====

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public Date getDateOfStartWork() { return dateOfStartWork; }
    public void setDateOfStartWork(Date dateOfStartWork) { this.dateOfStartWork = dateOfStartWork; }

    public int getTotalVacationDaysPerYear() { return totalVacationDaysPerYear; }
    public void setTotalVacationDaysPerYear(int totalVacationDaysPerYear) {
        this.totalVacationDaysPerYear = totalVacationDaysPerYear;
    }

    public int getRemainingVacationDays() { return remainingVacationDays; }
    public void setRemainingVacationDays(int remainingVacationDays) {
        this.remainingVacationDays = remainingVacationDays;
    }

    public int getUsedVacationDays() { return usedVacationDays; }
    public void setUsedVacationDays(int usedVacationDays) {
        this.usedVacationDays = usedVacationDays;
    }

    public String getCompanyId() { return companyId; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // Helper: full name
    public String getFullName() {
        String fn = firstName != null ? firstName : "";
        String ln = lastName != null ? lastName : "";
        return (fn + " " + ln).trim();
    }
}
