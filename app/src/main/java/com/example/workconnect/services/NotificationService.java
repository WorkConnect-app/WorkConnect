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

    // ===============================
// Shifts (assignment)
// ===============================
    public static void addShiftAssigned(@NonNull WriteBatch batch,
                                        @NonNull String employeeUid,
                                        @NonNull String companyId,
                                        @NonNull String teamId,
                                        @NonNull String dateKey,
                                        @NonNull String templateTitle) {

        Map<String, Object> data = new HashMap<>();
        data.put("companyId", companyId);
        data.put("teamId", teamId);
        data.put("dateKey", dateKey);

        AppNotification n = new AppNotification(
                "SHIFT_ASSIGNED",
                "Shift assigned",
                "You were assigned to \"" + templateTitle + "\" on " + dateKey + ".",
                data
        );

        batch.set(newNotifRef(employeeUid), n);
    }

    public static void addShiftChanged(@NonNull WriteBatch batch,
                                       @NonNull String employeeUid,
                                       @NonNull String companyId,
                                       @NonNull String teamId,
                                       @NonNull String dateKey,
                                       @NonNull String fromTitle,
                                       @NonNull String toTitle) {

        Map<String, Object> data = new HashMap<>();
        data.put("companyId", companyId);
        data.put("teamId", teamId);
        data.put("dateKey", dateKey);

        AppNotification n = new AppNotification(
                "SHIFT_CHANGED",
                "Shift changed",
                "Your shift on " + dateKey + " changed from \"" + fromTitle + "\" to \"" + toTitle + "\".",
                data
        );

        batch.set(newNotifRef(employeeUid), n);
    }

    public static void addShiftRemoved(@NonNull WriteBatch batch,
                                       @NonNull String employeeUid,
                                       @NonNull String companyId,
                                       @NonNull String teamId,
                                       @NonNull String dateKey,
                                       @NonNull String templateTitle) {

        Map<String, Object> data = new HashMap<>();
        data.put("companyId", companyId);
        data.put("teamId", teamId);
        data.put("dateKey", dateKey);

        AppNotification n = new AppNotification(
                "SHIFT_REMOVED",
                "Shift removed",
                "Your shift \"" + templateTitle + "\" on " + dateKey + " was removed.",
                data
        );

        batch.set(newNotifRef(employeeUid), n);
    }

    // ===============================
// Shift replacement
// ===============================
    public static void addSwapOfferReceived(@NonNull WriteBatch batch,
                                            @NonNull String requesterUid,
                                            @NonNull String companyId,
                                            @NonNull String teamId,
                                            @NonNull String requestId) {

        Map<String, Object> data = new HashMap<>();
        data.put("companyId", companyId);
        data.put("teamId", teamId);
        data.put("requestId", requestId);

        AppNotification n = new AppNotification(
                "SWAP_OFFER_RECEIVED",
                "New offer received",
                "Someone submitted an offer on your shift replacement request.",
                data
        );

        batch.set(newNotifRef(requesterUid), n);
    }

    public static void addSwapSentForApproval(@NonNull WriteBatch batch,
                                              @NonNull String managerUid,
                                              @NonNull String companyId,
                                              @NonNull String teamId,
                                              @NonNull String requestId) {

        Map<String, Object> data = new HashMap<>();
        data.put("companyId", companyId);
        data.put("teamId", teamId);
        data.put("requestId", requestId);

        AppNotification n = new AppNotification(
                "SWAP_SENT_FOR_APPROVAL",
                "Swap approval needed",
                "A shift replacement request is pending your approval.",
                data
        );

        batch.set(newNotifRef(managerUid), n);
    }

    public static void addSwapApproved(@NonNull Transaction tx,
                                       @NonNull String userUid,
                                       @NonNull String companyId,
                                       @NonNull String teamId,
                                       @NonNull String requestId) {

        Map<String, Object> data = new HashMap<>();
        data.put("companyId", companyId);
        data.put("teamId", teamId);
        data.put("requestId", requestId);

        AppNotification n = new AppNotification(
                "SWAP_APPROVED",
                "Swap approved",
                "Your shift replacement was approved.",
                data
        );

        tx.set(newNotifRef(userUid), n);
    }

    public static void addSwapRejected(@NonNull WriteBatch batch,
                                       @NonNull String requesterUid,
                                       @NonNull String companyId,
                                       @NonNull String teamId,
                                       @NonNull String requestId) {

        Map<String, Object> data = new HashMap<>();
        data.put("companyId", companyId);
        data.put("teamId", teamId);
        data.put("requestId", requestId);

        AppNotification n = new AppNotification(
                "SWAP_REJECTED",
                "Swap rejected",
                "Your shift replacement request was rejected (returned to open).",
                data
        );

        batch.set(newNotifRef(requesterUid), n);
    }

    // ===============================
// Attendance (auto-end)
// ===============================
    public static void addAttendanceAutoEnded(@NonNull Transaction tx,
                                              @NonNull String userUid,
                                              @NonNull String companyId,
                                              @NonNull String dateKey) {

        Map<String, Object> data = new HashMap<>();
        data.put("companyId", companyId);
        data.put("dateKey", dateKey);

        AppNotification n = new AppNotification(
                "ATTENDANCE_AUTO_ENDED",
                "Shift auto-ended",
                "Your shift was automatically ended after reaching 13 hours (" + dateKey + ").",
                data
        );

        tx.set(newNotifRef(userUid), n);
    }

    // ===============================
// Payslips
// ===============================
    public static void addPayslipUploaded(@NonNull Transaction tx,
                                          @NonNull String employeeUid,
                                          @NonNull String companyId,
                                          @NonNull String periodKey) {

        Map<String, Object> data = new HashMap<>();
        data.put("companyId", companyId);
        data.put("periodKey", periodKey);

        AppNotification n = new AppNotification(
                "PAYSLIP_UPLOADED",
                "New salary slip",
                "A new salary slip was uploaded for " + periodKey + ".",
                data
        );

        tx.set(newNotifRef(employeeUid), n);
    }

    public static void addPayslipDeleted(@NonNull WriteBatch batch,
                                         @NonNull String employeeUid,
                                         @NonNull String companyId,
                                         @NonNull String periodKey) {

        Map<String, Object> data = new HashMap<>();
        data.put("companyId", companyId);
        data.put("periodKey", periodKey);

        AppNotification n = new AppNotification(
                "PAYSLIP_DELETED",
                "Salary slip removed",
                "A salary slip was removed for " + periodKey + ".",
                data
        );

        batch.set(newNotifRef(employeeUid), n);
    }
}