package com.example.workconnect.repository;

import com.example.workconnect.models.Company;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

public class CompanyRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ===============================
    // Register company + manager
    // ===============================
    public interface RegisterCompanyCallback {
        void onSuccess(String companyId, String companyCode);
        void onError(String message);
    }

    public void registerCompanyAndManager(
            String companyName,
            String managerFullName,
            String email,
            String password,
            RegisterCompanyCallback callback
    ) {
        FirebaseAuth auth = FirebaseAuth.getInstance();

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        callback.onError("Failed to create user");
                        return;
                    }

                    String managerId = user.getUid();
                    String companyId = db.collection("companies").document().getId();
                    String companyCode = generateCompanyCode();

                    Map<String, Object> companyData = new HashMap<>();
                    companyData.put("id", companyId);
                    companyData.put("name", companyName);
                    companyData.put("managerId", managerId);
                    companyData.put("code", companyCode);
                    companyData.put("createdAt", Timestamp.now());

                    // NOTE: attendanceLocation is optional, we don't set it here (null => GPS disabled)

                    db.collection("companies").document(companyId)
                            .set(companyData)
                            .addOnSuccessListener(v -> {

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
                                        .addOnSuccessListener(v2 -> callback.onSuccess(companyId, companyCode))
                                        .addOnFailureListener(e -> callback.onError(e.getMessage()));

                            })
                            .addOnFailureListener(e -> callback.onError(e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    private String generateCompanyCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }

    // ===============================
    // Get company by ID
    // ===============================
    public interface CompanyCallback {
        void onSuccess(Company company);
        void onError(Exception e);
    }

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
    // Pass null to DISABLE GPS attendance
    // ===============================
    public void updateAttendanceLocation(
            String companyId,
            Company.AttendanceLocation location,
            Runnable onSuccess,
            Consumer<Exception> onError
    ) {
        if (location == null) {
            db.collection("companies")
                    .document(companyId)
                    .update("attendanceLocation", null)
                    .addOnSuccessListener(v -> onSuccess.run())
                    .addOnFailureListener(e -> onError.accept((Exception) e));
        } else {
            db.collection("companies")
                    .document(companyId)
                    .update("attendanceLocation", location)
                    .addOnSuccessListener(v -> onSuccess.run())
                    .addOnFailureListener(e -> onError.accept((Exception) e));
        }
    }
}
