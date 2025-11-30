package com.example.workconnect.models;

import java.util.Date;
import java.util.List;

public class Manager extends User {

    // List of employee UIDs that this manager is responsible for
    private List<String> managedUserIds;

    public Manager() {
        // Required by Firebase for deserialization
        super();
    }

    public Manager(String uid,
                   String firstName,
                   String lastName,
                   String email,
                   String phoneNumber,
                   Date dateOfStartWork,
                   int totalVacationDaysPerYear,
                   int remainingVacationDays,
                   int usedVacationDays,
                   List<String> managedUserIds) {

        // Initialize all User fields via the superclass constructor
        super(uid, firstName, lastName, email, phoneNumber,
                dateOfStartWork, totalVacationDaysPerYear,
                remainingVacationDays, usedVacationDays);

        this.managedUserIds = managedUserIds;
    }

    public List<String> getManagedUserIds() {
        return managedUserIds;
    }

    public void setManagedUserIds(List<String> managedUserIds) {
        this.managedUserIds = managedUserIds;
    }

    // Logical permissions that distinguish a manager from a regular user
    public boolean canApproveVacation() {
        return true;  // Managers can approve vacation requests
    }

    public boolean canAssignTasks() {
        return true;  // Managers can assign tasks to employees
    }
}
