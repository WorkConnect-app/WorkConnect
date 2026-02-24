package com.example.workconnect.repository.authAndUsers;

import com.example.workconnect.models.Company;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Handles company-related Firestore operations:
 * - Creating a new company and its manager
 * - Fetching company data
 * - Updating attendance GPS configuration
 */
public class CompanyRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ===============================
    // Register company + manager
    // ===============================

    /**
     * Callback used when creating a new company and manager account.
     */
    public interface RegisterCompanyCallback {
        void onSuccess(String companyId, String companyCode);
        void onError(String message);
    }

    /**
     * Creates:
     * 1) FirebaseAuth account for the manager
     * 2) Company document in Firestore
     * 3) User document for the manager
     */
    public void registerCompanyAndManager(
            String companyName,
            String managerFullName,
            String email,
            String password,
            RegisterCompanyCallback callback
    ) {
        FirebaseAuth auth = FirebaseAuth.getInstance();

        // Step 1: Create authentication account
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {

                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        callback.onError("Failed to create user");
                        return;
                    }

                    String managerId = user.getUid();

                    // Generate company document id
                    String companyId = db.collection("companies").document().getId();

                    // Simple join code (first 6 chars of id)
                    String companyCode = companyId.substring(0, 6).toUpperCase();

                    // Build company data
                    Map<String, Object> companyData = new HashMap<>();
                    companyData.put("id", companyId);
                    companyData.put("name", companyName);
                    companyData.put("managerId", managerId);
                    companyData.put("code", companyCode);
                    companyData.put("createdAt", Timestamp.now());

                    // attendanceLocation not set here (null = GPS disabled)

                    // Step 2: Create company document
                    db.collection("companies").document(companyId)
                            .set(companyData)
                            .addOnSuccessListener(v -> {

                                // Step 3: Create manager user document
                                Map<String, Object> managerData = new HashMap<>();
                                managerData.put("uid", managerId);
                                managerData.put("fullName", managerFullName);
                                managerData.put("email", email);
                                managerData.put("role", "MANAGER");
                                managerData.put("companyId", companyId);
                                managerData.put("status", "APPROVED");
                                managerData.put("createdAt", Timestamp.now());

                                db.collection("users").document(managerId)
                                        .set(managerData)
                                        .addOnSuccessListener(v2 ->
                                                callback.onSuccess(companyId, companyCode)
                                        )
                                        .addOnFailureListener(e ->
                                                callback.onError(e.getMessage())
                                        );

                            })
                            .addOnFailureListener(e ->
                                    callback.onError(e.getMessage())
                            );
                })
                .addOnFailureListener(e ->
                        callback.onError(e.getMessage())
                );
    }

    // ===============================
    // Get company by ID
    // ===============================

    /**
     * Callback for fetching a single company.
     */
    public interface CompanyCallback {
        void onSuccess(Company company);
        void onError(Exception e);
    }

    /**
     * Fetch a company document and convert it to Company model.
     */
    public void getCompanyById(String companyId, CompanyCallback callback) {
        db.collection("companies")
                .document(companyId)
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (!snapshot.exists()) {
                        callback.onError(new Exception("Company not found"));
                        return;
                    }

                    Company company = snapshot.toObject(Company.class);
                    callback.onSuccess(company);
                })
                .addOnFailureListener(callback::onError);
    }

    // ===============================
    // Update attendance GPS config
    // ===============================

    /**
     * Updates company's attendance GPS configuration.
     * If location == null → disables GPS attendance.
     */
    public void updateAttendanceLocation(
            String companyId,
            Company.AttendanceLocation location,
            Runnable onSuccess,
            Consumer<Exception> onError
    ) {

        // If null → explicitly disable GPS attendance
        if (location == null) {
            db.collection("companies")
                    .document(companyId)
                    .update("attendanceLocation", null)
                    .addOnSuccessListener(v -> onSuccess.run())
                    .addOnFailureListener(e -> onError.accept((Exception) e));

        } else {
            // Otherwise update with new config
            db.collection("companies")
                    .document(companyId)
                    .update("attendanceLocation", location)
                    .addOnSuccessListener(v -> onSuccess.run())
                    .addOnFailureListener(e -> onError.accept((Exception) e));
        }
    }
}