package com.example.workconnect.repository;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

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
                                userData.put("status", "PENDING");    // waiting for manager approval
                                userData.put("role", "employee");     // default role

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
                                                mAuth.signOut();
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
                .whereEqualTo("status", "PENDING")
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
     *  NEW: Approve by manager EMAIL (UI) -> store manager UID (DB)
     * --------------------------------------------------------------------- */

    /**
     * Manager enters direct manager EMAIL in the UI,
     * but we store directManagerId as UID in Firestore.
     *
     * If directManagerEmail is empty -> top-level manager (directManagerId = null)
     */
    public void approveEmployeeWithDetailsByManagerEmail(
            String employeeUid,
            String role,
            @Nullable String directManagerEmail, // email from UI
            Double vacationDaysPerMonth,
            String department,
            String team,
            String jobTitle,
            SimpleCallback callback
    ) {
        String email = directManagerEmail == null ? "" : directManagerEmail.trim().toLowerCase();

        // If empty -> same behavior as top-level manager
        if (email.isEmpty()) {
            approveEmployeeWithDetails(employeeUid, role, null,
                    vacationDaysPerMonth, department, team, jobTitle, callback);
            return;
        }

        // Find manager user by email (in the SAME users collection)
        db.collection("users")
                .whereEqualTo("email", email)
                .whereEqualTo("role", "manager")
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (qs == null || qs.isEmpty()) {
                        callback.onComplete(false, "No manager found with this email");
                        return;
                    }

                    // In your app, user docs are saved under document(uid),
                    // so doc.getId() is the manager UID.
                    String managerUid = qs.getDocuments().get(0).getId();

                    // Reuse your existing approval logic (UID-based)
                    approveEmployeeWithDetails(employeeUid, role, managerUid,
                            vacationDaysPerMonth, department, team, jobTitle, callback);
                })
                .addOnFailureListener(e ->
                        callback.onComplete(false, "Failed to lookup manager by email: " + e.getMessage())
                );
    }

    /* -----------------------------------------------------------------------
     *  Approve employee with role, hierarchy, vacation accrual, etc. (UID-based)
     * --------------------------------------------------------------------- */

    public void approveEmployeeWithDetails(
            String employeeUid,
            String role,                        // "employee" or "manager"
            @Nullable String directManagerId,   // UID (null if top-level manager)
            Double vacationDaysPerMonth,
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
        updates.put("status", "APPROVED");
        updates.put("role", role);
        updates.put("directManagerId", directManagerId);
        updates.put("managerChain", managerChain);
        updates.put("vacationDaysPerMonth", vacationDaysPerMonth);
        updates.put("department", department);
        updates.put("team", team);
        updates.put("jobTitle", jobTitle);

        // Set join date when employee is approved
        updates.put("joinDate", new Date());

        // Initialize vacation accrual fields (daily accrual)
        updates.put("vacationBalance", 0.0);
        updates.put("lastAccrualDate", java.time.LocalDate.now().toString()); // yyyy-MM-dd

        employeeRef.update(updates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onComplete(true, "Employee approved");
                    } else {
                        callback.onComplete(false, "Failed to approve employee");
                    }
                });
    }
    public void completeManagerProfile(
            String managerUid,
            Double vacationDaysPerMonth,
            String department,
            String team,
            String jobTitle,
            SimpleCallback callback
    ) {
        HashMap<String, Object> updates = new HashMap<>();
        updates.put("status", "APPROVED");
        updates.put("vacationDaysPerMonth", vacationDaysPerMonth);

        updates.put("department", department);
        updates.put("team", team);
        updates.put("jobTitle", jobTitle);

        updates.put("directManagerId", null);
        updates.put("managerChain", new ArrayList<String>());

        updates.put("joinDate", new Date());
        updates.put("vacationBalance", 0.0);
        updates.put("lastAccrualDate", java.time.LocalDate.now().toString());

        updates.put("profileCompleted", true);

        db.collection("users")
                .document(managerUid)
                .update(updates)
                .addOnCompleteListener(t -> {
                    if (t.isSuccessful()) {
                        callback.onComplete(true, "Profile updated");
                    } else {
                        callback.onComplete(false, "Failed to update profile");
                    }
                });
    }

}
