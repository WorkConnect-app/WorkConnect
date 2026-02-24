package com.example.workconnect.services;

import androidx.annotation.NonNull;

import com.example.workconnect.models.AppNotification;
import com.example.workconnect.models.enums.VacationStatus;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized service responsible for creating notification documents
 * under users/{uid}/notifications.
 *
 * All methods are static and are designed to be used inside
 * Firestore transactions or batch writes.
 */
public class NotificationService {

    /**
     * Creates a new notification document reference
     * under users/{uid}/notifications with an auto-generated ID.
     */
    private static DocumentReference newNotifRef(@NonNull String uid) {
        return FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("notifications").document();
    }

    /**
     * Adds a "Vacation Approved" notification to an employee.
     * Must be called inside an existing Firestore transaction.
     */
    public static void addVacationApproved(@NonNull Transaction tx,
                                           @NonNull String employeeId,
                                           @NonNull String requestId,
                                           int daysRequested) {

        Map<String, Object> data = new HashMap<>();
        data.put("requestId", requestId);
        data.put("status", VacationStatus.APPROVED.name());
        data.put("daysRequested", daysRequested);

        AppNotification n = new AppNotification(
                "VACATION_APPROVED",
                "Vacation approved",
                "Your vacation request was approved",
                data
        );

        tx.set(newNotifRef(employeeId), n);
    }

    /**
     * Adds a "Vacation Rejected" notification to an employee.
     * Must be called inside an existing Firestore transaction.
     */
    public static void addVacationRejected(@NonNull Transaction tx,
                                           @NonNull String employeeId,
                                           @NonNull String requestId) {

        Map<String, Object> data = new HashMap<>();
        data.put("requestId", requestId);
        data.put("status", VacationStatus.REJECTED.name());

        AppNotification n = new AppNotification(
                "VACATION_REJECTED",
                "Vacation rejected",
                "Your vacation request was rejected",
                data
        );

        tx.set(newNotifRef(employeeId), n);
    }

    /**
     * Adds a notification for a manager when a new vacation request is created.
     * Must be used inside a Firestore WriteBatch.
     */
    public static void addVacationNewRequestForManager(@NonNull WriteBatch batch,
                                                       @NonNull String managerId,
                                                       @NonNull String requestId,
                                                       @NonNull String employeeId) {

        Map<String, Object> data = new HashMap<>();
        data.put("requestId", requestId);
        data.put("employeeId", employeeId);
        data.put("status", VacationStatus.PENDING.name());

        AppNotification n = new AppNotification(
                "VACATION_NEW_REQUEST",
                "New vacation request",
                "A new vacation request is waiting for approval",
                data
        );

        batch.set(newNotifRef(managerId), n);
    }

    /**
     * Adds a notification for managers when a new employee registers
     * and is pending approval.
     * Must be used inside a Firestore WriteBatch.
     */
    public static void addEmployeePendingApprovalForManager(@NonNull WriteBatch batch,
                                                            @NonNull String managerId,
                                                            @NonNull String employeeId,
                                                            @NonNull String employeeName,
                                                            @NonNull String companyId) {

        Map<String, Object> data = new HashMap<>();
        data.put("employeeId", employeeId);
        data.put("companyId", companyId);

        AppNotification n = new AppNotification(
                "EMPLOYEE_PENDING_APPROVAL",
                "New employee pending approval",
                employeeName + " is waiting for approval",
                data
        );

        batch.set(newNotifRef(managerId), n);
    }

    /**
     * Additional notification types (e.g., shift assigned, swap approved)
     * can be implemented here in the future.
     */
}