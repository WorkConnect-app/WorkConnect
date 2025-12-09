package com.example.workconnect.repository;

import androidx.annotation.NonNull;

import com.example.workconnect.models.User;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository responsible for all employee-related operations:
 * registration, loading pending employees, approving/rejecting, etc.
 */
public class EmployeeRepository {

    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface RegisterEmployeeCallback {
        void onSuccess();                 // register success
        void onError(String message);     // error
    }

    public interface PendingEmployeesCallback {
        void onSuccess(List<User> employees);
        void onError(String message);
    }

    public interface SimpleCallback {
        void onComplete(boolean success, String message);
    }

    // ==================== Register Employee ====================

    public void registerEmployee(
            String fullName,
            String email,
            String password,
            String companyCode,
            RegisterEmployeeCallback callback
    ) {

        // 1. check that the company code exists
        db.collection("companies")
                .whereEqualTo("code", companyCode)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        // There is no company with this code.
                        if (callback != null) {
                            callback.onError("Company code is invalid or does not exist");
                        }
                        return;
                    }

                    // There is a company – we use its ID
                    DocumentSnapshot companyDoc = querySnapshot.getDocuments().get(0);
                    String companyId = companyDoc.getId();

                    // 2. create user in Firebase Authentication
                    mAuth.createUserWithEmailAndPassword(email, password)
                            .addOnCompleteListener(task ->
                                    handleAuthResult(task, fullName, email, companyId, callback)
                            );

                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        callback.onError("Error checking company code: " + e.getMessage());
                    }
                });
    }

    private void handleAuthResult(
            @NonNull Task<AuthResult> task,
            String fullName,
            String email,
            String companyId,
            RegisterEmployeeCallback callback
    ) {
        if (!task.isSuccessful() || mAuth.getCurrentUser() == null) {
            String msg = "Registration failed";
            if (task.getException() != null) {
                msg += ": " + task.getException().getMessage();
            }
            if (callback != null) callback.onError(msg);
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();

        // Split fullName into first + last (very simple split, can be improved)
        String firstName = fullName;
        String lastName = "";
        if (fullName.contains(" ")) {
            int index = fullName.indexOf(" ");
            firstName = fullName.substring(0, index);
            lastName = fullName.substring(index + 1).trim();
        }

        // 3. save the user data in the "users" collection
        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", uid);
        userData.put("firstName", firstName);
        userData.put("lastName", lastName);
        userData.put("email", email);
        userData.put("phoneNumber", null);
        userData.put("dateOfStartWork", Timestamp.now()); // or null, depends on your logic

        userData.put("totalVacationDaysPerYear", 0);
        userData.put("remainingVacationDays", 0);
        userData.put("usedVacationDays", 0);

        userData.put("companyId", companyId);
        userData.put("role", "employee");
        userData.put("status", "pending");        // user waits for manager's approval
        userData.put("createdAt", Timestamp.now());

        db.collection("users").document(uid)
                .set(userData)
                .addOnSuccessListener(unused -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        callback.onError("Error saving user data: " + e.getMessage());
                    }
                });
    }

    // ==================== Pending Employees ====================

    /**
     * Listen for all pending employees of a specific company.
     * Returns a ListenerRegistration so the ViewModel can stop listening if needed.
     */
    public ListenerRegistration listenForPendingEmployees(
            String companyId,
            PendingEmployeesCallback callback
    ) {
        return db.collection("users")
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("role", "employee")
                .whereEqualTo("status", "pending")
                .addSnapshotListener((querySnapshot, e) -> {
                    if (e != null) {
                        if (callback != null) {
                            callback.onError("Error loading pending employees: " + e.getMessage());
                        }
                        return;
                    }
                    if (querySnapshot == null) {
                        if (callback != null) {
                            callback.onSuccess(new ArrayList<>());
                        }
                        return;
                    }

                    List<User> list = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        User user = doc.toObject(User.class);
                        if (user != null) {
                            list.add(user);
                        }
                    }

                    if (callback != null) {
                        callback.onSuccess(list);
                    }
                });
    }

    /**
     * Update employee status: "approved" or "rejected".
     */
    public void updateEmployeeStatus(String uid, String newStatus, SimpleCallback callback) {
        db.collection("users").document(uid)
                .update("status", newStatus)
                .addOnSuccessListener(unused -> {
                    if (callback != null) {
                        callback.onComplete(true, null);
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        callback.onComplete(false, "Error updating status: " + e.getMessage());
                    }
                });
    }
}
