package com.example.workconnect.repository;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class CompanyRepository {

    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface RegisterCompanyCallback {
        void onSuccess(String companyId, String companyCode);
        void onError(String message);
    }

    public void registerCompanyAndManager(
            String companyName,
            String managerName,
            String email,
            String password,
            RegisterCompanyCallback callback
    ) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {

                        if (!task.isSuccessful() || mAuth.getCurrentUser() == null) {
                            String msg = "Registration failed";
                            if (task.getException() != null) {
                                msg += ": " + task.getException().getMessage();
                            }
                            if (callback != null) callback.onError(msg);
                            return;
                        }

                        final String uid = mAuth.getCurrentUser().getUid();

                        // Create company document
                        final DocumentReference companyRef = db.collection("companies").document();
                        final String companyId = companyRef.getId();
                        final String companyCode = companyId.length() >= 6 ? companyId.substring(0, 6) : companyId;

                        Map<String, Object> companyData = new HashMap<>();
                        companyData.put("id", companyId);
                        companyData.put("name", companyName);
                        companyData.put("managerId", uid);
                        companyData.put("createdAt", Timestamp.now());
                        companyData.put("code", companyCode);

                        companyRef.set(companyData)
                                .addOnSuccessListener(unused -> {

                                    // Manager user document (consistent with employee fields)
                                    Map<String, Object> userData = new HashMap<>();
                                    userData.put("uid", uid);

                                    // Split managerName into first/last if possible (optional but helpful)
                                    String firstName = managerName == null ? "" : managerName.trim();
                                    String lastName = "";
                                    if (firstName.contains(" ")) {
                                        String[] parts = firstName.split("\\s+", 2);
                                        firstName = parts[0];
                                        lastName = parts[1];
                                    }

                                    userData.put("firstName", firstName);
                                    userData.put("lastName", lastName);
                                    userData.put("fullName", (firstName + " " + lastName).trim());

                                    userData.put("email", email);
                                    userData.put("role", "manager");
                                    userData.put("companyId", companyId);

                                    // Manager is approved immediately but must complete profile
                                    userData.put("status", "APPROVED");
                                    userData.put("profileCompleted", false);

                                    // Profile fields to be completed
                                    userData.put("department", null);
                                    userData.put("team", null);
                                    userData.put("jobTitle", null);
                                    userData.put("vacationDaysPerMonth", 0.0);

                                    // Hierarchy defaults (top-level manager)
                                    userData.put("directManagerId", null);
                                    userData.put("managerChain", new ArrayList<String>());

                                    // Vacation defaults (keep clean and safe)
                                    userData.put("vacationBalance", 0.0);
                                    userData.put("lastAccrualDate", null);

                                    // Join date can be set after completing profile
                                    userData.put("joinDate", null);

                                    db.collection("users").document(uid)
                                            .set(userData)
                                            .addOnSuccessListener(unused2 -> {
                                                if (callback != null) {
                                                    callback.onSuccess(companyId, companyCode);
                                                }
                                            })
                                            .addOnFailureListener(e -> {
                                                if (callback != null) {
                                                    callback.onError("Error saving user data: " + e.getMessage());
                                                }
                                            });

                                })
                                .addOnFailureListener(e -> {
                                    if (callback != null) {
                                        callback.onError("Error creating company: " + e.getMessage());
                                    }
                                });
                    }
                });
    }
}
