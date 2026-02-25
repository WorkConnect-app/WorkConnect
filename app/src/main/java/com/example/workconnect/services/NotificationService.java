package com.example.workconnect.services;

import androidx.annotation.NonNull;

import com.example.workconnect.models.AppNotification;
import com.example.workconnect.models.enums.VacationStatus;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.WriteBatch;
public class NotificationService {

    private static DocumentReference newNotifRef(@NonNull String uid) {
        return FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("notifications").document();
    }

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
                "Vacation approved ✅",
                "Your vacation request was approved",
                data
        );

        tx.set(newNotifRef(employeeId), n);
    }

    public static void addVacationRejected(@NonNull Transaction tx,
                                           @NonNull String employeeId,
                                           @NonNull String requestId) {
        Map<String, Object> data = new HashMap<>();
        data.put("requestId", requestId);
        data.put("status", VacationStatus.REJECTED.name());

        AppNotification n = new AppNotification(
                "VACATION_REJECTED",
                "Vacation rejected ❌",
                "Your vacation request was rejected",
                data
        );

        tx.set(newNotifRef(employeeId), n);
    }


    public static void addVacationNewRequestForManager(@NonNull com.google.firebase.firestore.WriteBatch batch,
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

        DocumentReference notifRef = FirebaseFirestore.getInstance()
                .collection("users").document(managerId)
                .collection("notifications").document();

        batch.set(notifRef, n);
    }

    public static void addEmployeePendingApprovalForManager(@NonNull WriteBatch batch,
                                                            @NonNull String managerId,
                                                            @NonNull String employeeId,
                                                            @NonNull String employeeName,
                                                            @NonNull String companyId) {

        Map<String, Object> data = new HashMap<>();
        data.put("employeeId", employeeId);
        data.put("companyId", companyId);

        Map<String, Object> notif = new HashMap<>();
        notif.put("type", "EMPLOYEE_PENDING_APPROVAL");
        notif.put("title", "New employee pending approval ✅");
        notif.put("body", employeeName + " is waiting for approval");
        notif.put("read", false);
        notif.put("createdAt", FieldValue.serverTimestamp());
        notif.put("data", data);

        DocumentReference notifRef = FirebaseFirestore.getInstance()
                .collection("users").document(managerId)
                .collection("notifications").document();

        batch.set(notifRef, notif);
    }

    // Chat notifications 

    /** Direct message: title = sender name, body = message preview. */
    public static void addChatNewMessage(@NonNull WriteBatch batch,
                                         @NonNull String recipientId,
                                         @NonNull String senderName,
                                         @NonNull String conversationId,
                                         @NonNull String messagePreview) {
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", conversationId);

        Map<String, Object> notif = new HashMap<>();
        notif.put("type", "CHAT_NEW_MESSAGE");
        notif.put("title", senderName);
        notif.put("body", messagePreview);
        notif.put("read", false);
        notif.put("createdAt", FieldValue.serverTimestamp());
        notif.put("data", data);

        batch.set(newNotifRef(recipientId), notif);
    }

    /** Group message: title = group name, body = "SenderName: preview". */
    public static void addChatGroupMessage(@NonNull WriteBatch batch,
                                           @NonNull String recipientId,
                                           @NonNull String groupName,
                                           @NonNull String senderName,
                                           @NonNull String conversationId,
                                           @NonNull String messagePreview) {
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", conversationId);

        Map<String, Object> notif = new HashMap<>();
        notif.put("type", "CHAT_GROUP_MESSAGE");
        notif.put("title", groupName);
        notif.put("body", senderName + ": " + messagePreview);
        notif.put("read", false);
        notif.put("createdAt", FieldValue.serverTimestamp());
        notif.put("data", data);

        batch.set(newNotifRef(recipientId), notif);
    }

    //Call notifications 

    /** Group call started: sent to members who haven't yet joined. */
    public static void addGroupCallStarted(@NonNull WriteBatch batch,
                                           @NonNull String recipientId,
                                           @NonNull String callerName,
                                           @NonNull String groupName,
                                           @NonNull String conversationId,
                                           @NonNull String callType) {
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", conversationId);

        String callLabel = "video".equals(callType) ? "video" : "audio";

        Map<String, Object> notif = new HashMap<>();
        notif.put("type", "GROUP_CALL_STARTED");
        notif.put("title", groupName);
        notif.put("body", callerName + " started a " + callLabel + " call");
        notif.put("read", false);
        notif.put("createdAt", FieldValue.serverTimestamp());
        notif.put("data", data);

        batch.set(newNotifRef(recipientId), notif);
    }

    /** Missed call: sent to participants who never answered. */
    public static void addMissedCall(@NonNull WriteBatch batch,
                                     @NonNull String recipientId,
                                     @NonNull String callerName,
                                     @NonNull String conversationId,
                                     @NonNull String callType) {
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", conversationId);

        String callLabel = "video".equals(callType) ? "video" : "audio";

        Map<String, Object> notif = new HashMap<>();
        notif.put("type", "MISSED_CALL");
        notif.put("title", "Missed call");
        notif.put("body", "Missed " + callLabel + " call from " + callerName);
        notif.put("read", false);
        notif.put("createdAt", FieldValue.serverTimestamp());
        notif.put("data", data);

        batch.set(newNotifRef(recipientId), notif);
    }

    // Group membership notifications 

    /** Added to group: sent to each new member. */
    public static void addAddedToGroup(@NonNull WriteBatch batch,
                                       @NonNull String recipientId,
                                       @NonNull String adderName,
                                       @NonNull String groupName,
                                       @NonNull String conversationId) {
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", conversationId);

        Map<String, Object> notif = new HashMap<>();
        notif.put("type", "ADDED_TO_GROUP");
        notif.put("title", groupName);
        notif.put("body", adderName + " added you to the group");
        notif.put("read", false);
        notif.put("createdAt", FieldValue.serverTimestamp());
        notif.put("data", data);

        batch.set(newNotifRef(recipientId), notif);
    }

    /** Removed from group: sent to each removed member. */
    public static void addRemovedFromGroup(@NonNull WriteBatch batch,
                                           @NonNull String recipientId,
                                           @NonNull String groupName,
                                           @NonNull String conversationId) {
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", conversationId);

        Map<String, Object> notif = new HashMap<>();
        notif.put("type", "REMOVED_FROM_GROUP");
        notif.put("title", groupName);
        notif.put("body", "You were removed from the group");
        notif.put("read", false);
        notif.put("createdAt", FieldValue.serverTimestamp());
        notif.put("data", data);

        batch.set(newNotifRef(recipientId), notif);
    }
}
