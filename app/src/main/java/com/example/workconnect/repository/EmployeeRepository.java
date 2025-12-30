package com.example.workconnect.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.workconnect.models.User;
import com.example.workconnect.models.enums.RegisterStatus;
import com.example.workconnect.models.enums.Roles;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Repository for employee-related operations.
 *
 * Responsibilities:
 * - Employee registration (sign-up): Auth user + Firestore user profile
 * - Listening for pending employees in a company
 * - Approving / rejecting employees (status changes + profile fields)
 * - Hierarchy (direct manager + manager chain)
 * - Vacation accrual initialization after approval
 */
public class EmployeeRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();

    /** Callback for registering a new employee (sign-up). */
    public interface RegisterEmployeeCallback {
        void onSuccess();
        void onError(String message);
    }

    /** Callback for listening to pending employees list changes. */
    public interface PendingEmployeesCallback {
        void onSuccess(List<User> employees);
        void onError(String message);
    }

    /** Generic callback for simple operations (approve/reject/status update). */
    public interface SimpleCallback {
        void onComplete(boolean success, String message);
    }

    /**
     * Registers a new employee:
     * 1) Finds the company by its join code.
     * 2) Creates a FirebaseAuth account (email/password).
     * 3) Creates a Firestore user document with status = PENDING.
     *
     * - After successful sign-up, we sign out because employees must be approved first.
     */
    public void registerEmployee(
            @NonNull String firstName,
            @NonNull String lastName,
            @NonNull String email,
            @NonNull String password,
            @NonNull String companyCode,
            @NonNull RegisterEmployeeCallback callback
    ) {
        final String fullName = (firstName + " " + lastName).trim();
        final String normalizedCode = companyCode.trim();

        // 1) Find company by code.
        db.collection("companies")
                .whereEqualTo("code", normalizedCode) // Search by membership code
                .limit(1) // even if there is more than one company with the same code (which shouldn't happen), You return only one document.
                .get()
                .addOnSuccessListener(qs -> {
                    if (qs == null || qs.isEmpty()) {
                        callback.onError("Company not found for code: " + normalizedCode);
                        return;
                    }

                    // Contains a list of documents that matched the query. but we limit the query for 1, so its save the doc.
                    DocumentSnapshot companyDoc = qs.getDocuments().get(0);

                    final String companyId = companyDoc.getId();

                    // 2) Create Firebase Auth user.
                    mAuth.createUserWithEmailAndPassword(email, password)
                            .addOnSuccessListener(authResult -> {

                                if (mAuth.getCurrentUser() == null) {
                                    callback.onError("Registration succeeded but user is null");
                                    return;
                                }

                                final String uid = mAuth.getCurrentUser().getUid();

                                // 3) Create Firestore user document (users/{uid}).
                                HashMap<String, Object> userData = new HashMap<>();
                                userData.put("uid", uid);
                                userData.put("firstName", firstName);
                                userData.put("lastName", lastName);
                                userData.put("fullName", fullName);
                                userData.put("email", email);
                                userData.put("companyId", companyId);

                                // Initial status & role (must be consistent with enums).
                                userData.put("status", RegisterStatus.PENDING.name()); // "PENDING"
                                userData.put("role", Roles.EMPLOYEE.name());           // "EMPLOYEE"

                                // Hierarchy defaults (set later by manager).
                                userData.put("directManagerId", null);
                                userData.put("managerChain", new ArrayList<String>());

                                // Vacation defaults.
                                userData.put("vacationDaysPerMonth", 0.0);
                                userData.put("vacationBalance", 0.0);
                                userData.put("lastAccrualDate", null);

                                // Optional org fields.
                                userData.put("department", "");
                                userData.put("team", "");
                                userData.put("jobTitle", "");

                                // joinDate is set on approval.
                                userData.put("joinDate", null);

                                db.collection("users")
                                        .document(uid)
                                        .set(userData)
                                        .addOnSuccessListener(unused -> {
                                            // Sign out: employee cannot access app until approved.
                                            mAuth.signOut();
                                            callback.onSuccess();
                                        })
                                        .addOnFailureListener(e -> {
                                            // Firestore failed AFTER Auth succeeded => delete Auth user to avoid orphan account.
                                            if (mAuth.getCurrentUser() != null) {
                                                mAuth.getCurrentUser().delete()
                                                        .addOnCompleteListener(t -> {
                                                            String msg = "Failed to save user data: "
                                                                    + (e.getMessage() == null ? "Unknown error" : e.getMessage());
                                                            callback.onError(msg);
                                                        });
                                            } else {
                                                String msg = "Failed to save user data: "
                                                        + (e.getMessage() == null ? "Unknown error" : e.getMessage());
                                                callback.onError(msg);
                                            }
                                        });

                            })
                            .addOnFailureListener(e -> {
                                String msg = (e.getMessage() == null) ? "Failed to create user" : e.getMessage();
                                callback.onError(msg);
                            });
                })
                .addOnFailureListener(e -> {
                    String msg = (e.getMessage() == null) ? "Failed to lookup company" : e.getMessage();
                    callback.onError(msg);
                });
    }


    /**
     * Real-time listener for employees with status = PENDING in a given company.
     * Caller should store the returned ListenerRegistration and remove it onStop/onDestroy.
     */
    public ListenerRegistration listenForPendingEmployees(
            @NonNull String companyId,
            @NonNull PendingEmployeesCallback callback
    ) {
        return db.collection("users")
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("status", RegisterStatus.PENDING.name())

                // Listening that starts immediately with the first data, and starts again with every change (add / delete / update)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        callback.onError(error.getMessage() == null ? "Listener error" : error.getMessage());
                        return;
                    }
                    if (value == null) {
                        callback.onSuccess(new ArrayList<>());
                        return;
                    }

                    List<User> list = new ArrayList<>();
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        User user = doc.toObject(User.class);
                        if (user != null) {
                            // Ensure uid matches document ID.
                            user.setUid(doc.getId());
                            list.add(user);
                        }
                    }
                    callback.onSuccess(list);
                });
    }


    /**
     * Updates only the employee's status field.
     * Prefer passing RegisterStatus.name() to keep values consistent in Firestore.
     */
    public void updateEmployeeStatus(
            @NonNull String uid,
            @NonNull RegisterStatus status,
            @NonNull SimpleCallback callback
    ) {
        db.collection("users")
                .document(uid)
                .update("status", status.name())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onComplete(true, "Status updated");
                    } else {
                        callback.onComplete(false, "Failed to update status");
                    }
                });
    }

    /**
     * Manager enters a direct manager EMAIL in the UI, but we store directManagerId as UID in Firestore.
     *
     * If directManagerEmail is empty => top-level manager (directManagerId = null).
     */
    public void approveEmployeeWithDetailsByManagerEmail(
            @NonNull String employeeUid,
            @NonNull Roles role,
            @Nullable String directManagerEmail,
            @NonNull Double vacationDaysPerMonth,
            @Nullable String department,
            @Nullable String team,
            @Nullable String jobTitle,
            @NonNull SimpleCallback callback
    ) {
        String email = directManagerEmail == null ? "" : directManagerEmail.trim().toLowerCase();

        // Empty email => no direct manager.
        if (email.isEmpty()) {
            approveEmployeeWithDetails(employeeUid, role, null,
                    vacationDaysPerMonth, department, team, jobTitle, callback);
            return;
        }

        // Find manager user by email.
        db.collection("users")
                .whereEqualTo("email", email)
                .whereEqualTo("role", Roles.MANAGER.name())
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (qs == null || qs.isEmpty()) {
                        callback.onComplete(false, "No manager found with this email");
                        return;
                    }

                    // Document ID is the manager UID.
                    String managerUid = qs.getDocuments().get(0).getId();

                    approveEmployeeWithDetails(employeeUid, role, managerUid,
                            vacationDaysPerMonth, department, team, jobTitle, callback);
                })
                .addOnFailureListener(e -> {
                    String msg = (e.getMessage() == null) ? "Failed to lookup manager by email" : e.getMessage();
                    callback.onComplete(false, msg);
                });
    }


    /**
     * Approves an employee:
     * - Sets status to APPROVED
     * - Stores role, hierarchy fields, vacation policy and org metadata
     * - Initializes accrual tracking fields
     *
     * directManagerId is UID or null for top-level.
     */
    public void approveEmployeeWithDetails(
            @NonNull String employeeUid,
            @NonNull Roles role,
            @Nullable String directManagerId,
            @NonNull Double vacationDaysPerMonth,
            @Nullable String department,
            @Nullable String team,
            @Nullable String jobTitle,
            @NonNull SimpleCallback callback
    ) {
        DocumentReference employeeRef = db.collection("users").document(employeeUid);

        // If there is a direct manager, fetch them to build managerChain.
        if (directManagerId != null) {
            DocumentReference managerRef = db.collection("users").document(directManagerId);

            managerRef.get().addOnCompleteListener(task -> {
                if (!task.isSuccessful()
                        || task.getResult() == null
                        || !task.getResult().exists()) {

                    callback.onComplete(false, "Failed to load direct manager data");
                    return;
                }

                // build the managerChain
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
            // Top-level manager (no direct manager).
            updateEmployeeDocument(
                    employeeRef,
                    role,
                    null,
                    new ArrayList<>(),
                    vacationDaysPerMonth,
                    department,
                    team,
                    jobTitle,
                    callback
            );
        }
    }

    /**
     * Builds manager chain: [directManagerId, managerOfManagerId, ...]
     */
    private List<String> buildManagerChain(@NonNull String directManagerId, @NonNull DocumentSnapshot managerDoc) {
        List<String> chain = new ArrayList<>();
        chain.add(directManagerId);

        // Pulling out the manager's own chain
        @SuppressWarnings("unchecked")
        List<String> managersOfManager = (List<String>) managerDoc.get("managerChain");

        if (managersOfManager != null) {
            chain.addAll(managersOfManager);
        }

        return chain;
    }

    /**
     * Central method to apply approval updates to employee document.
     */
    private void updateEmployeeDocument(
            @NonNull DocumentReference employeeRef,
            @NonNull Roles role,
            @Nullable String directManagerId,
            @NonNull List<String> managerChain,
            @NonNull Double vacationDaysPerMonth,
            @Nullable String department,
            @Nullable String team,
            @Nullable String jobTitle,
            @NonNull SimpleCallback callback
    ) {
        HashMap<String, Object> updates = new HashMap<>();

        // Approval state
        updates.put("status", RegisterStatus.APPROVED.name());
        updates.put("role", role.name());

        // Hierarchy
        updates.put("directManagerId", directManagerId);
        updates.put("managerChain", managerChain);

        // Org metadata
        updates.put("department", department == null ? "" : department);
        updates.put("team", team == null ? "" : team);
        updates.put("jobTitle", jobTitle == null ? "" : jobTitle);

        // Vacation policy
        updates.put("vacationDaysPerMonth", vacationDaysPerMonth);

        // Join date set at approval time
        updates.put("joinDate", new Date());

        // Accrual tracking initialization
        updates.put("vacationBalance", 0.0);
        updates.put("lastAccrualDate", LocalDate.now().toString()); // yyyy-MM-dd

        employeeRef.update(updates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onComplete(true, "Employee approved");
                    } else {
                        callback.onComplete(false, "Failed to approve employee");
                    }
                });
    }

    /**
     * Completes manager profile fields after registration.
     * Sets profileCompleted = true and initializes accrual fields.
     */
    public void completeManagerProfile(
            @NonNull String managerUid,
            @NonNull Double vacationDaysPerMonth,
            @Nullable String department,
            @Nullable String team,
            @Nullable String jobTitle,
            @NonNull SimpleCallback callback
    ) {
        HashMap<String, Object> updates = new HashMap<>();

        updates.put("vacationDaysPerMonth", vacationDaysPerMonth);

        updates.put("department", department == null ? "" : department);
        updates.put("team", team == null ? "" : team);
        updates.put("jobTitle", jobTitle == null ? "" : jobTitle);

        // Initialize accrual tracking
        updates.put("joinDate", new Date());
        updates.put("vacationBalance", 0.0);
        updates.put("lastAccrualDate", LocalDate.now().toString());

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
