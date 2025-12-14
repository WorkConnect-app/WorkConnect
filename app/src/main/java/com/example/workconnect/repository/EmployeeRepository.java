package com.example.workconnect.repository;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Repository for employee-related operations.
 * - Registering a new employee (sign-up)
 * - Listening for pending employees for a company
 * - Approving/rejecting employees
 * - Setting role, direct manager, hierarchy, vacation accrual, etc.
 */
public class EmployeeRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();

    /* -----------------------------------------------------------------------
     *  Callback interfaces
     * --------------------------------------------------------------------- */

    // For registering a new employee (sign-up)
    public interface RegisterEmployeeCallback {
        void onSuccess();
        void onError(String message);
    }

    // For listening to pending employees
    public interface PendingEmployeesCallback {
        void onSuccess(List<com.example.workconnect.models.User> employees);
        void onError(String message);
    }

    // Simple callback for status updates / approve / reject
    public interface SimpleCallback {
        void onComplete(boolean success, String message);
    }

    /* -----------------------------------------------------------------------
     *  Employee registration (sign-up)
     * --------------------------------------------------------------------- */

    /**
     * Register a new employee:
     * 1. Find company by code.
     * 2. Create Firebase Auth user.
     * 3. Create Firestore document in "users" with status = "pending".
     *
     * This matches your RegisterEmployeeViewModel usage
     * (firstName, lastName, email, password, companyCode).
     */
    public void registerEmployee(
            String firstName,
            String lastName,
            String email,
            String password,
            String companyCode,
            RegisterEmployeeCallback callback
    ) {
        // Build full name from first & last
        String fullName = (firstName + " " + lastName).trim();

        // 1) Find company by code
        db.collection("companies")
                .whereEqualTo("code", companyCode)
                .get()
                .addOnCompleteListener(companyTask -> {
                    if (!companyTask.isSuccessful() || companyTask.getResult() == null
                            || companyTask.getResult().isEmpty()) {
                        callback.onError("Company not found for code: " + companyCode);
                        return;
                    }

                    // Take the first matching company
                    DocumentSnapshot companyDoc = companyTask.getResult().getDocuments().get(0);
                    String companyId = companyDoc.getId();

                    // 2) Create Firebase Auth user
                    mAuth.createUserWithEmailAndPassword(email, password)
                            .addOnCompleteListener(authTask -> {
                                if (!authTask.isSuccessful() || authTask.getResult() == null) {
                                    String message = authTask.getException() != null
                                            ? authTask.getException().getMessage()
                                            : "Failed to create user";
                                    callback.onError(message);
                                    return;
                                }

                                String uid = authTask.getResult().getUser().getUid();

                                // 3) Create Firestore document in "users"
                                HashMap<String, Object> userData = new HashMap<>();
                                userData.put("uid", uid);
                                userData.put("firstName", firstName);
                                userData.put("lastName", lastName);
                                userData.put("fullName", fullName);
                                userData.put("email", email);
                                userData.put("companyId", companyId);

                                // Initial status and role
                                userData.put("status", "pending");    // waiting for manager approval
                                userData.put("role", "EMPLOYEE");     // default role

                                // Hierarchy fields – will be set later by manager
                                userData.put("directManagerId", null);
                                userData.put("managerChain", new ArrayList<String>());

                                // Vacation accrual – zero by default, manager can set later
                                userData.put("vacationDaysPerMonth", 0.0);

                                // Org fields – empty for now, manager can set later
                                userData.put("department", null);
                                userData.put("team", null);
                                userData.put("jobTitle", null);

                                // joinDate will be set when employee is approved
                                userData.put("joinDate", null);

                                db.collection("users")
                                        .document(uid)
                                        .set(userData)
                                        .addOnCompleteListener(setTask -> {
                                            if (setTask.isSuccessful()) {
                                                callback.onSuccess();
                                            } else {
                                                callback.onError("Failed to save user data");
                                            }
                                        });
                            });
                });
    }

    /* -----------------------------------------------------------------------
     *  Listening for pending employees
     * --------------------------------------------------------------------- */

    /**
     * Listen for employees with status = "pending" for a specific company.
     */
    public ListenerRegistration listenForPendingEmployees(
            String companyId,
            PendingEmployeesCallback callback
    ) {
        return db.collection("users")
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("status", "pending")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        callback.onError(error.getMessage());
                        return;
                    }
                    if (value == null) {
                        callback.onSuccess(new ArrayList<>());
                        return;
                    }

                    List<com.example.workconnect.models.User> list = new ArrayList<>();
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        com.example.workconnect.models.User user =
                                doc.toObject(com.example.workconnect.models.User.class);
                        if (user != null) {
                            user.setUid(doc.getId());
                            list.add(user);
                        }
                    }
                    callback.onSuccess(list);
                });
    }

    /* -----------------------------------------------------------------------
     *  Simple status update (used for reject)
     * --------------------------------------------------------------------- */

    public void updateEmployeeStatus(
            String uid,
            String status,
            SimpleCallback callback
    ) {
        db.collection("users")
                .document(uid)
                .update("status", status)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onComplete(true, "Status updated");
                    } else {
                        callback.onComplete(false, "Failed to update status");
                    }
                });
    }

    /* -----------------------------------------------------------------------
     *  Approve employee with role, hierarchy, vacation accrual, etc.
     * --------------------------------------------------------------------- */

    /**
     * Approve an employee and set:
     * - role (EMPLOYEE / MANAGER)
     * - direct manager (directManagerId)
     * - managerChain (hierarchy)
     * - vacationDaysPerMonth
     * - department / team / jobTitle
     * - joinDate (now)
     */
    public void approveEmployeeWithDetails(
            String employeeUid,
            String role,                        // "EMPLOYEE" or "MANAGER"
            @Nullable String directManagerId,   // null if top-level manager
            Double vacationDaysPerMonth,        // how many vacation days per month
            String department,
            String team,
            String jobTitle,
            SimpleCallback callback
    ) {
        DocumentReference employeeRef = db.collection("users").document(employeeUid);

        // If there is a direct manager, fetch them to build managerChain
        if (directManagerId != null) {
            DocumentReference managerRef = db.collection("users").document(directManagerId);

            managerRef.get().addOnCompleteListener(task -> {
                if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                    callback.onComplete(false, "Failed to load direct manager data");
                    return;
                }

                DocumentSnapshot managerDoc = task.getResult();
                List<String> managerChain = buildManagerChain(directManagerId, managerDoc);

                updateEmployeeDocument(
                        employeeRef,
                        role,
                        directManagerId,
                        managerChain,
                        vacationDaysPerMonth,
                        department,
                        team,
                        jobTitle,
                        callback
                );
            });
        } else {
            // Top-level manager (no direct manager, empty hierarchy)
            List<String> managerChain = new ArrayList<>();
            updateEmployeeDocument(
                    employeeRef,
                    role,
                    null,
                    managerChain,
                    vacationDaysPerMonth,
                    department,
                    team,
                    jobTitle,
                    callback
            );
        }
    }

    private List<String> buildManagerChain(String directManagerId, DocumentSnapshot managerDoc) {
        List<String> chain = new ArrayList<>();
        chain.add(directManagerId); // first element is direct manager

        List<String> managersOfManager =
                (List<String>) managerDoc.get("managerChain");
        if (managersOfManager != null) {
            chain.addAll(managersOfManager);
        }

        return chain;
    }

    private void updateEmployeeDocument(
            DocumentReference employeeRef,
            String role,
            @Nullable String directManagerId,
            List<String> managerChain,
            Double vacationDaysPerMonth,
            String department,
            String team,
            String jobTitle,
            SimpleCallback callback
    ) {
        HashMap<String, Object> updates = new HashMap<>();
        updates.put("status", "approved");
        updates.put("role", role);
        updates.put("directManagerId", directManagerId);
        updates.put("managerChain", managerChain);
        updates.put("vacationDaysPerMonth", vacationDaysPerMonth);
        updates.put("department", department);
        updates.put("team", team);
        updates.put("jobTitle", jobTitle);

        // Set join date when employee is approved
        updates.put("joinDate", new Date());

        employeeRef.update(updates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onComplete(true, "Employee approved");
                    } else {
                        callback.onComplete(false, "Failed to approve employee");
                    }
                });
    }
}
