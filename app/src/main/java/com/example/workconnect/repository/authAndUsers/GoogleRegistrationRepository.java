package com.example.workconnect.repository.authAndUsers;

import androidx.annotation.NonNull;

import com.example.workconnect.services.NotificationService;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

public class GoogleRegistrationRepository {

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface CompleteCallback {
        void onEmployeePending();
        void onManagerApproved(String companyId, String companyCode);
        void onError(String message);
    }

    public interface CompleteProfileCallback {
        void onSuccessEmployeePending();
        void onError(String message);
    }

    // =========================================================
    // EMPLOYEE
    // =========================================================
    public void completeAsEmployee(@NonNull String fullName,
                                   @NonNull String companyCode,
                                   @NonNull CompleteCallback callback) {

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError("Not authenticated with Google");
            return;
        }

        String uid = user.getUid();
        String email = user.getEmail() == null ? "" : user.getEmail();

        db.collection("companies")
                .whereEqualTo("code", companyCode.trim().toUpperCase())
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (qs == null || qs.isEmpty()) {
                        callback.onError("Company code not found");
                        return;
                    }

                    String companyId = qs.getDocuments().get(0).getId();

                    Map<String, Object> userData = new HashMap<>();
                    userData.put("uid", uid);
                    userData.put("fullName", fullName.trim());
                    userData.put("email", email);
                    userData.put("role", "EMPLOYEE");
                    userData.put("companyId", companyId);
                    userData.put("status", "PENDING");
                    userData.put("createdAt", Timestamp.now());

                    db.collection("users").document(uid)
                            .set(userData)
                            .addOnSuccessListener(v -> {
                                // ✅ notify managers that a new employee is pending
                                notifyManagersEmployeePending(companyId, uid, fullName.trim());

                                // ✅ continue flow
                                callback.onEmployeePending();
                            })
                            .addOnFailureListener(e -> callback.onError(msg(e, "Failed to save user profile")));
                })
                .addOnFailureListener(e -> callback.onError(msg(e, "Failed to validate company code")));
    }

    // =========================================================
    // MANAGER
    // =========================================================
    public void completeAsManagerCreateCompany(@NonNull String fullName,
                                               @NonNull String companyName,
                                               @NonNull CompleteCallback callback) {

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError("Not authenticated with Google");
            return;
        }

        String uid = user.getUid();
        String email = user.getEmail() == null ? "" : user.getEmail();

        String companyId = db.collection("companies").document().getId();
        String companyCode = companyId.substring(0, 6).toUpperCase();

        Map<String, Object> companyData = new HashMap<>();
        companyData.put("id", companyId);
        companyData.put("name", companyName.trim());
        companyData.put("managerId", uid);
        companyData.put("code", companyCode);
        companyData.put("createdAt", Timestamp.now());

        db.collection("companies").document(companyId)
                .set(companyData)
                .addOnSuccessListener(v -> {

                    Map<String, Object> managerData = new HashMap<>();
                    managerData.put("uid", uid);
                    managerData.put("fullName", fullName.trim());
                    managerData.put("email", email);
                    managerData.put("role", "MANAGER");
                    managerData.put("companyId", companyId);
                    managerData.put("status", "APPROVED");
                    managerData.put("createdAt", Timestamp.now());

                    db.collection("users").document(uid)
                            .set(managerData)
                            .addOnSuccessListener(v2 -> callback.onManagerApproved(companyId, companyCode))
                            .addOnFailureListener(e -> callback.onError(msg(e, "Failed to save manager profile")));

                })
                .addOnFailureListener(e -> callback.onError(msg(e, "Failed to create company")));
    }

    // =========================================================
    // תאימות: המתודה שה-ViewModel שלך קורא לה כרגע
    // completeEmployeeProfile(firstName, lastName, companyCode, callback)
    // =========================================================
    public void completeEmployeeProfile(@NonNull String firstName,
                                        @NonNull String lastName,
                                        @NonNull String companyCode,
                                        @NonNull CompleteProfileCallback callback) {

        String fullName = (firstName + " " + lastName).trim();

        // נשתמש במימוש הקיים completeAsEmployee
        completeAsEmployee(fullName, companyCode, new CompleteCallback() {
            @Override
            public void onEmployeePending() {
                callback.onSuccessEmployeePending();
            }

            @Override
            public void onManagerApproved(String companyId, String companyCode) {
                // לא רלוונטי לעובד
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private String msg(Exception e, String fallback) {
        return (e == null || e.getMessage() == null) ? fallback : e.getMessage();
    }

    private void notifyManagersEmployeePending(@NonNull String companyId,
                                               @NonNull String employeeUid,
                                               @NonNull String employeeName) {

        db.collection("users")
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("role", "MANAGER")
                .get()
                .addOnSuccessListener(qs -> {
                    if (qs == null || qs.isEmpty()) return;

                    WriteBatch batch = db.batch();

                    for (DocumentSnapshot m : qs.getDocuments()) {
                        String managerId = m.getId();
                        NotificationService.addEmployeePendingApprovalForManager(
                                batch,
                                managerId,
                                employeeUid,
                                employeeName,
                                companyId
                        );
                    }

                    batch.commit().addOnFailureListener(e ->
                            android.util.Log.e("Notif", "❌ notifyManagersEmployeePending failed", e)
                    );
                })
                .addOnFailureListener(e ->
                        android.util.Log.e("Notif", "❌ failed to find managers", e)
                );
    }
}
