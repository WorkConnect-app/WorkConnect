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

        // נבנה Map כדי להוסיף createdAt serverTimestamp (ולא להיתקע בלי createdAt)
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

    // בהמשך: addShiftAssigned, addSwapApproved וכו'
}
