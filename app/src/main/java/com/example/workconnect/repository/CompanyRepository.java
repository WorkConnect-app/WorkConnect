package com.example.workconnect.repository;

import androidx.annotation.NonNull;

import com.example.workconnect.models.enums.RegisterStatus;
import com.example.workconnect.models.enums.Roles;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Repository responsible for registering a new company and its manager.
 *
 * Flow:
 * 1) Create manager user in FirebaseAuth (email/password)
 * 2) Create company + manager user profile in Firestore using a single batch (atomic in Firestore)
 * 3) If Firestore write fails, delete the Auth user to avoid orphan accounts
 */
public class CompanyRepository {

    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface RegisterCompanyCallback {
        void onSuccess(String companyId, String companyCode);
        void onError(String message);
    }

    public void registerCompanyAndManager(
            @NonNull String companyName,
            @NonNull String managerName,
            @NonNull String email,
            @NonNull String password,
            RegisterCompanyCallback callback
    ) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {

                    if (mAuth.getCurrentUser() == null) {
                        if (callback != null) callback.onError("Registration succeeded but user is null");
                        return;
                    }

                    final String uid = mAuth.getCurrentUser().getUid();

                    //I want a new document in collection companies, automatically generates a unique ID
                    final DocumentReference companyRef = db.collection("companies").document();

                    // Get the ID of the new document
                    final String companyId = companyRef.getId();

                    // Shorten the company ID to 6 characters
                    final String companyCode = companyId.length() >= 6 ? companyId.substring(0, 6) : companyId;

                    // Create a reference to the user document
                    final DocumentReference userRef = db.collection("users").document(uid);

                    // Build company data
                    Map<String, Object> companyData = new HashMap<>();
                    companyData.put("id", companyId);
                    companyData.put("name", companyName);
                    companyData.put("managerId", uid);
                    companyData.put("createdAt", Timestamp.now());
                    companyData.put("code", companyCode);

                    // Split managerName into first/last
                    String firstName = managerName == null ? "" : managerName.trim();
                    String lastName = "";
                    if (firstName.contains(" ")) {
                        String[] parts = firstName.split("\\s+", 2);
                        firstName = parts[0];
                        lastName = parts[1];
                    }

                    // Build manager user data (consistent with your User model / AuthRepository)
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("uid", uid);
                    userData.put("firstName", firstName);
                    userData.put("lastName", lastName);
                    userData.put("fullName", (firstName + " " + lastName).trim());
                    userData.put("email", email);

                    userData.put("role", Roles.MANAGER.name());                 // "MANAGER"
                    userData.put("status", RegisterStatus.APPROVED.name());    // "APPROVED"
                    userData.put("companyId", companyId);

                    // Manager is approved immediately but can still complete profile
                    userData.put("profileCompleted", false);

                    // more details
                    userData.put("department", "");
                    userData.put("team", "");
                    userData.put("jobTitle", "");

                    // Vacation defaults
                    userData.put("vacationDaysPerMonth", 0.0);
                    userData.put("vacationBalance", 0.0);
                    userData.put("lastAccrualDate", null);
                    userData.put("joinDate", null);

                    // Hierarchy defaults (top-level manager)
                    userData.put("directManagerId", null);
                    userData.put("managerChain", new ArrayList<String>());

                    // --- Atomic Firestore write: company + user together ---
                    WriteBatch batch = db.batch();
                    batch.set(companyRef, companyData);
                    batch.set(userRef, userData);

                    // Firestore does all the writes together. if one fails, all fail. If all succeed, all succeed.
                    batch.commit()
                            .addOnSuccessListener(unused -> {
                                if (callback != null) callback.onSuccess(companyId, companyCode);
                            })
                            .addOnFailureListener(e -> {
                                // Firestore write failed => make sure we don't leave an Auth-only manager
                                if (mAuth.getCurrentUser() != null) {
                                    mAuth.getCurrentUser().delete() // Asynchronous operation

                                            // Notify me when the action is complete, regardless of the outcome.
                                            .addOnCompleteListener(t -> {
                                                String msg = "Error saving company/user data: "
                                                        + (e.getMessage() == null ? "Unknown error" : e.getMessage());
                                                if (callback != null) callback.onError(msg);
                                            });
                                } else {
                                    String msg = "Error saving company/user data: "
                                            + (e.getMessage() == null ? "Unknown error" : e.getMessage());
                                    if (callback != null) callback.onError(msg);
                                }
                            });

                })
                .addOnFailureListener(e -> {
                    String msg = "Registration failed: " + (e.getMessage() == null ? "Unknown error" : e.getMessage());
                    if (callback != null) callback.onError(msg);
                });
    }
}
