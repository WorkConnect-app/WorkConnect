package com.example.workconnect.services;

import androidx.annotation.NonNull;

import com.example.workconnect.models.AppNotification;
import com.example.workconnect.models.enums.VacationStatus;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralized service responsible for creating notification documents
 * under users/{uid}/notifications.
 */
public class NotificationService {

    /**
     * Creates a new notification document reference
     */
    private static DocumentReference newNotifRef(@NonNull String uid) {
        return FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("notifications").document();
    }

    // ===============================
    // Vacations
    // ===============================

    /** Adds a "Vacation Approved" notification to an employee. */
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

    /** Adds a "Vacation Rejected" notification to an employee. */
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

    /** Adds a notification for a manager when a new vacation request is created. */
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

    /** Adds a notification for managers when a new employee registers and is pending approval. */
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

    // ===============================
    // Chat
    // ===============================

    /** Notifies a user about a new direct message. */
    public static void addChatNewMessage(@NonNull WriteBatch batch,
                                         @NonNull String recipientId,
                                         @NonNull String senderName,
                                         @NonNull String conversationId,
                                         @NonNull String messagePreview) {
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", conversationId);
        data.put("senderName", senderName);

        AppNotification n = new AppNotification(
                "CHAT_NEW_MESSAGE",
                senderName,
                messagePreview,
                data
        );
        batch.set(newNotifRef(recipientId), n);
    }

    /** Notifies a user about a new group message. */
    public static void addChatGroupMessage(@NonNull WriteBatch batch,
                                            @NonNull String recipientId,
                                            @NonNull String groupTitle,
                                            @NonNull String senderName,
                                            @NonNull String conversationId,
                                            @NonNull String messagePreview) {
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", conversationId);
        data.put("senderName", senderName);
        data.put("groupTitle", groupTitle);

        AppNotification n = new AppNotification(
                "CHAT_GROUP_MESSAGE",
                groupTitle,
                senderName + ": " + messagePreview,
                data
        );
        batch.set(newNotifRef(recipientId), n);
    }

    /** Notifies a user about a missed call. */
    public static void addMissedCall(@NonNull WriteBatch batch,
                                      @NonNull String recipientId,
                                      @NonNull String callerName,
                                      @NonNull String conversationId,
                                      @NonNull String callType) {
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", conversationId);
        data.put("callType", callType);
        data.put("callerName", callerName);

        AppNotification n = new AppNotification(
                "MISSED_CALL",
                "Missed call",
                "You missed a call from " + callerName,
                data
        );
        batch.set(newNotifRef(recipientId), n);
    }

    /** Notifies group members that a group call has started. */
    public static void addGroupCallStarted(@NonNull WriteBatch batch,
                                           @NonNull String recipientId,
                                           @NonNull String callerName,
                                           @NonNull String groupTitle,
                                           @NonNull String conversationId,
                                           @NonNull String callType) {
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", conversationId);
        data.put("callerName", callerName);
        data.put("groupTitle", groupTitle);
        data.put("callType", callType);

        AppNotification n = new AppNotification(
                "GROUP_CALL_STARTED",
                groupTitle,
                callerName + " started a " + ("video".equals(callType) ? "video" : "audio") + " call",
                data
        );
        batch.set(newNotifRef(recipientId), n);
    }

    /** Notifies a user that they were added to a group. */
    public static void addAddedToGroup(@NonNull WriteBatch batch,
                                       @NonNull String recipientId,
                                       @NonNull String adderName,
                                       @NonNull String groupTitle,
                                       @NonNull String conversationId) {
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", conversationId);
        data.put("groupTitle", groupTitle);
        data.put("adderName", adderName);

        AppNotification n = new AppNotification(
                "ADDED_TO_GROUP",
                "Added to group",
                adderName + " added you to \"" + groupTitle + "\"",
                data
        );
        batch.set(newNotifRef(recipientId), n);
    }

    /** Notifies a user that they were removed from a group. */
    public static void addRemovedFromGroup(@NonNull WriteBatch batch,
                                            @NonNull String recipientId,
                                            @NonNull String groupTitle,
                                            @NonNull String conversationId) {
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", conversationId);
        data.put("groupTitle", groupTitle);

        AppNotification n = new AppNotification(
                "REMOVED_FROM_GROUP",
                "Removed from group",
                "You were removed from \"" + groupTitle + "\"",
                data
        );
        batch.set(newNotifRef(recipientId), n);
    }

    // ===============================
    // Shifts
    // ===============================

    /** Notifies an employee that a shift was assigned to them. */
    public static void addShiftAssigned(@NonNull WriteBatch batch,
                                        @NonNull String recipientId,
                                        @NonNull String companyId,
                                        @NonNull String teamId,
                                        @NonNull String dateKey,
                                        @NonNull String shiftTitle) {
        Map<String, Object> data = new HashMap<>();
        data.put("companyId", companyId);
        data.put("teamId", teamId);
        data.put("dateKey", dateKey);

        AppNotification n = new AppNotification(
                "SHIFT_ASSIGNED",
                "New shift assigned",
                "You have been assigned to \"" + shiftTitle + "\" on " + dateKey,
                data
        );
        batch.set(newNotifRef(recipientId), n);
    }

    /** Notifies an employee that their shift was changed. */
    public static void addShiftChanged(@NonNull WriteBatch batch,
                                       @NonNull String recipientId,
                                       @NonNull String companyId,
                                       @NonNull String teamId,
                                       @NonNull String dateKey,
                                       @NonNull String oldTitle,
                                       @NonNull String newTitle) {
        Map<String, Object> data = new HashMap<>();
        data.put("companyId", companyId);
        data.put("teamId", teamId);
        data.put("dateKey", dateKey);

        AppNotification n = new AppNotification(
                "SHIFT_CHANGED",
                "Shift changed",
                "Your shift on " + dateKey + " changed from \"" + oldTitle + "\" to \"" + newTitle + "\"",
                data
        );
        batch.set(newNotifRef(recipientId), n);
    }

    /** Notifies an employee that their shift was removed. */
    public static void addShiftRemoved(@NonNull WriteBatch batch,
                                       @NonNull String recipientId,
                                       @NonNull String companyId,
                                       @NonNull String teamId,
                                       @NonNull String dateKey,
                                       @NonNull String shiftTitle) {
        Map<String, Object> data = new HashMap<>();
        data.put("companyId", companyId);
        data.put("teamId", teamId);
        data.put("dateKey", dateKey);

        AppNotification n = new AppNotification(
                "SHIFT_REMOVED",
                "Shift removed",
                "Your shift \"" + shiftTitle + "\" on " + dateKey + " was removed",
                data
        );
        batch.set(newNotifRef(recipientId), n);
    }

    // ===============================
    // Shift Swaps
    // ===============================

    /** Notifies a user that someone offered to swap their shift. */
    public static void addSwapOfferReceived(@NonNull WriteBatch batch,
                                             @NonNull String recipientId,
                                             @NonNull String companyId,
                                             @NonNull String teamId,
                                             @NonNull String requestId) {
        Map<String, Object> data = new HashMap<>();
        data.put("companyId", companyId);
        data.put("teamId", teamId);
        data.put("requestId", requestId);

        AppNotification n = new AppNotification(
                "SWAP_OFFER_RECEIVED",
                "Shift swap offer",
                "Someone wants to swap shifts with you",
                data
        );
        batch.set(newNotifRef(recipientId), n);
    }

    /** Notifies a manager that a swap request is waiting for approval. */
    public static void addSwapSentForApproval(@NonNull WriteBatch batch,
                                               @NonNull String recipientId,
                                               @NonNull String companyId,
                                               @NonNull String teamId,
                                               @NonNull String requestId) {
        Map<String, Object> data = new HashMap<>();
        data.put("companyId", companyId);
        data.put("teamId", teamId);
        data.put("requestId", requestId);

        AppNotification n = new AppNotification(
                "SWAP_SENT_FOR_APPROVAL",
                "Shift swap pending approval",
                "A shift swap request is waiting for your approval",
                data
        );
        batch.set(newNotifRef(recipientId), n);
    }

    /** Notifies a user that their swap request was rejected. */
    public static void addSwapRejected(@NonNull WriteBatch batch,
                                        @NonNull String recipientId,
                                        @NonNull String companyId,
                                        @NonNull String teamId,
                                        @NonNull String requestId) {
        Map<String, Object> data = new HashMap<>();
        data.put("companyId", companyId);
        data.put("teamId", teamId);
        data.put("requestId", requestId);

        AppNotification n = new AppNotification(
                "SWAP_REJECTED",
                "Shift swap rejected",
                "Your shift swap request was rejected",
                data
        );
        batch.set(newNotifRef(recipientId), n);
    }

    /** Notifies a user that their swap request was approved. */
    public static void addSwapApproved(@NonNull com.google.firebase.firestore.Transaction tx,
                                        @NonNull String recipientId,
                                        @NonNull String companyId,
                                        @NonNull String teamId,
                                        @NonNull String requestId) {
        Map<String, Object> data = new HashMap<>();
        data.put("companyId", companyId);
        data.put("teamId", teamId);
        data.put("requestId", requestId);

        AppNotification n = new AppNotification(
                "SWAP_APPROVED",
                "Shift swap approved",
                "Your shift swap request was approved",
                data
        );
        tx.set(newNotifRef(recipientId), n);
    }

    // ===============================
    // Attendance
    // ===============================

    /** Notifies a user that their attendance was automatically ended. */
    public static void addAttendanceAutoEnded(@NonNull com.google.firebase.firestore.Transaction tx,
                                               @NonNull String userId,
                                               @NonNull String companyId,
                                               @NonNull String attendanceId) {
        Map<String, Object> data = new HashMap<>();
        data.put("companyId", companyId);
        data.put("attendanceId", attendanceId);

        AppNotification n = new AppNotification(
                "ATTENDANCE_AUTO_ENDED",
                "Attendance ended",
                "Your attendance session was automatically ended",
                data
        );
        tx.set(newNotifRef(userId), n);
    }

    // ===============================
    // Payslips
    // ===============================

    /** Notifies an employee that a payslip was uploaded. */
    public static void addPayslipUploaded(@NonNull com.google.firebase.firestore.Transaction tx,
                                           @NonNull String employeeId,
                                           @NonNull String companyId,
                                           @NonNull String periodKey) {
        Map<String, Object> data = new HashMap<>();
        data.put("companyId", companyId);
        data.put("periodKey", periodKey);

        AppNotification n = new AppNotification(
                "PAYSLIP_UPLOADED",
                "New payslip available",
                "Your payslip for " + periodKey + " is now available",
                data
        );
        tx.set(newNotifRef(employeeId), n);
    }

    /** Notifies an employee that a payslip was deleted. */
    public static void addPayslipDeleted(@NonNull WriteBatch batch,
                                          @NonNull String employeeId,
                                          @NonNull String companyId,
                                          @NonNull String periodKey) {
        Map<String, Object> data = new HashMap<>();
        data.put("companyId", companyId);
        data.put("periodKey", periodKey);

        AppNotification n = new AppNotification(
                "PAYSLIP_DELETED",
                "Payslip removed",
                "Your payslip for " + periodKey + " was removed",
                data
        );
        batch.set(newNotifRef(employeeId), n);
    }
}