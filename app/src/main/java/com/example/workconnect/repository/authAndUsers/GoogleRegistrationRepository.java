package com.example.workconnect.repository.authAndUsers;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.workconnect.services.NotificationService;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

/**
 * Completes Google-based registration flows:
 * - Employee: join existing company by code
 * - Manager: create company + manager profile
 */
public class GoogleRegistrationRepository {

    // Auth used only to access currently signed-in Google user
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    // Firestore entry point for companies/users collections
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /** Result callback for Google registration completion flows. */
    public interface CompleteCallback {
        void onEmployeePending();
        void onManagerApproved(String companyId, String companyCode);
        void onError(String message);
    }

    /** Callback used by older/compatibility ViewModel method signature. */
    public interface CompleteProfileCallback {
        void onSuccessEmployeePending();
        void onError(String message);
    }

    // =========================================================
    // Employee flow (Google sign-in - join existing company)
    // =========================================================
    public void completeAsEmployee(@NonNull String fullName,
                                   @NonNull String companyCode,
                                   @NonNull CompleteCallback callback) {

        // Must be authenticated with Google before completing profile
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError("Not authenticated with Google");
            return;
        }

        String uid = user.getUid();
        String email = user.getEmail() == null ? "" : user.getEmail();

        // Validate company code
        db.collection("companies")
                .whereEqualTo("code", companyCode.trim().toUpperCase())
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (qs == null || qs.isEmpty()) {
                        callback.onError("Company code not found");
                        return;
                    }

                    // Normalize name and split to first/last for profile fields
                    String cleanedFullName = fullName.trim().replaceAll("\\s+", " ");
                    String[] parts = cleanedFullName.split(" ", 2);
                    String firstName = parts[0];
                    String lastName = parts.length > 1 ? parts[1] : "";

                    String companyId = qs.getDocuments().get(0).getId();

                    // Load managers in company to create notifications
                    db.collection("users")
                            .whereEqualTo("companyId", companyId)
                            .whereEqualTo("role", "MANAGER")
                            .get()
                            .addOnSuccessListener(managers -> {

                                // Batch: create employee user doc + notifications in one commit
                                WriteBatch batch = db.batch();

                                // Create employee profile (starts as PENDING)
                                Map<String, Object> userData = new HashMap<>();
                                userData.put("uid", uid);
                                userData.put("firstName", firstName);
                                userData.put("lastName", lastName);
                                userData.put("fullName", cleanedFullName);
                                userData.put("email", email);
                                userData.put("role", "EMPLOYEE");
                                userData.put("companyId", companyId);
                                userData.put("status", "PENDING");
                                userData.put("createdAt", Timestamp.now());

                                batch.set(db.collection("users").document(uid), userData);

                                // Add notifications for each manager (if exists)
                                if (managers != null && !managers.isEmpty()) {
                                    for (DocumentSnapshot m : managers.getDocuments()) {
                                        String managerId = m.getId();
                                        NotificationService.addEmployeePendingApprovalForManager(
                                                batch,
                                                managerId,
                                                uid,
                                                fullName.trim(),
                                                companyId
                                        );
                                    }
                                }

                                // Single commit
                                batch.commit()
                                        .addOnSuccessListener(v -> callback.onEmployeePending())
                                        .addOnFailureListener(e -> callback.onError(msg(e, "Failed to complete registration")));
                            })
                            .addOnFailureListener(e -> callback.onError(msg(e, "Failed to load managers")));
                })
                .addOnFailureListener(e -> callback.onError(msg(e, "Failed to validate company code")));
    }

    // =========================================================
    // Manager flow (Google sign-in - create new company)
    // =========================================================
    public void completeAsManagerCreateCompany(@NonNull String fullName,
                                               @NonNull String companyName,
                                               @NonNull CompleteCallback callback) {

        // Must be authenticated with Google before creating company
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError("Not authenticated with Google");
            return;
        }

        String uid = user.getUid();
        String email = user.getEmail() == null ? "" : user.getEmail();

        // Generate companyId and derive short join code
        String companyId = db.collection("companies").document().getId();
        String companyCode = companyId.substring(0, 6).toUpperCase();

        // Company document payload
        Map<String, Object> companyData = new HashMap<>();
        companyData.put("id", companyId);
        companyData.put("name", companyName.trim());
        companyData.put("managerId", uid);
        companyData.put("code", companyCode);
        companyData.put("createdAt", Timestamp.now());

        // Create company doc, then create manager user doc
        db.collection("companies").document(companyId)
                .set(companyData)
                .addOnSuccessListener(v -> {

                    // Manager profile (already approved, profileCompleted later)
                    Map<String, Object> managerData = new HashMap<>();
                    managerData.put("uid", uid);
                    managerData.put("fullName", fullName.trim());
                    managerData.put("email", email);
                    managerData.put("role", "MANAGER");
                    managerData.put("companyId", companyId);
                    managerData.put("status", "APPROVED");
                    managerData.put("createdAt", Timestamp.now());
                    managerData.put("profileCompleted", false);

                    db.collection("users").document(uid)
                            .set(managerData)
                            .addOnSuccessListener(v2 -> callback.onManagerApproved(companyId, companyCode))
                            .addOnFailureListener(e -> callback.onError(msg(e, "Failed to save manager profile")));

                })
                .addOnFailureListener(e -> callback.onError(msg(e, "Failed to create company")));
    }

    /** Returns exception message if exists, otherwise a fallback. */
    private String msg(Exception e, String fallback) {
        return (e == null || e.getMessage() == null) ? fallback : e.getMessage();
    }

}