package com.example.workconnect.repository.authAndUsers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.workconnect.models.User;
import com.example.workconnect.models.enums.RegisterStatus;
import com.example.workconnect.models.enums.Roles;
import com.example.workconnect.services.NotificationService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

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

        db.collection("companies")
                .whereEqualTo("code", normalizedCode)
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (qs == null || qs.isEmpty()) {
                        callback.onError("Company not found for code: " + normalizedCode);
                        return;
                    }

                    DocumentSnapshot companyDoc = qs.getDocuments().get(0);
                    final String companyId = companyDoc.getId();

                    mAuth.createUserWithEmailAndPassword(email, password)
                            .addOnSuccessListener(authResult -> {

                                if (mAuth.getCurrentUser() == null) {
                                    callback.onError("Registration succeeded but user is null");
                                    return;
                                }

                                final String uid = mAuth.getCurrentUser().getUid();

                                HashMap<String, Object> userData = new HashMap<>();
                                userData.put("uid", uid);
                                userData.put("firstName", firstName);
                                userData.put("lastName", lastName);
                                userData.put("fullName", fullName);
                                userData.put("email", email);
                                userData.put("companyId", companyId);

                                userData.put("status", RegisterStatus.PENDING.name());
                                userData.put("role", Roles.EMPLOYEE.name());

                                userData.put("directManagerId", null);
                                userData.put("managerChain", new ArrayList<String>());

                                userData.put("vacationDaysPerMonth", 0.0);
                                userData.put("vacationBalance", 0.0);
                                userData.put("lastAccrualDate", null);

                                userData.put("department", "");
                                userData.put("jobTitle", "");

                                userData.put("teamIds", new ArrayList<String>());
                                userData.put("employmentType", null);

                                userData.put("joinDate", null);

                                db.collection("users")
                                        .document(uid)
                                        .set(userData)
                                        .addOnSuccessListener(unused -> {
                                            android.util.Log.d("Notif", "✅ user saved. calling notifyManagersEmployeePending. companyId=" + companyId);

                                            notifyManagersEmployeePending(companyId, uid, fullName, () -> {
                                                android.util.Log.d("Notif", "✅ notifyManagersEmployeePending finished. signing out.");
                                                mAuth.signOut();
                                                callback.onSuccess();
                                            });

                                            android.util.Log.d("Notif", "✅ notifyManagersEmployeePending called (async)");
                                        })
                                        .addOnFailureListener(e -> {
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

    public ListenerRegistration listenForPendingEmployees(
            @NonNull String companyId,
            @NonNull PendingEmployeesCallback callback
    ) {
        return db.collection("users")
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("status", RegisterStatus.PENDING.name())
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
                            user.setUid(doc.getId());
                            list.add(user);
                        }
                    }
                    callback.onSuccess(list);
                });
    }


    /**
     * Updates only the employee's status field.
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

    private void notifyManagersEmployeePending(
            @NonNull String companyId,
            @NonNull String employeeUid,
            @NonNull String employeeName,
            @NonNull Runnable onDone
    ) {
        db.collection("users")
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("role", Roles.MANAGER.name())
                .get()
                .addOnSuccessListener(qs -> {
                    if (qs != null && !qs.isEmpty()) {
                        WriteBatch batch = db.batch();
                        for (DocumentSnapshot m : qs.getDocuments()) {
                            String managerId = m.getId();
                            NotificationService.addEmployeePendingApprovalForManager(
                                    batch, managerId, employeeUid, employeeName, companyId
                            );
                        }
                        batch.commit()
                                .addOnSuccessListener(v -> onDone.run())
                                .addOnFailureListener(e -> {
                                    android.util.Log.e("Notif", "❌ notifyManagersEmployeePending failed", e);
                                    onDone.run();
                                });
                    } else {
                        onDone.run();
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("Notif", "❌ failed to find managers", e);
                    onDone.run();
                });
    }


    /**
     * Manager enters a direct manager EMAIL in the UI, but we store directManagerId as UID in Firestore.
     *
     * If directManagerEmail is empty => top-level manager (directManagerId = null).
     *
     * NOTE:
     * - selectedTeamId is optional (can be null/empty)
     * - employmentType is optional (can be null/empty)
     */
    public void approveEmployeeWithDetailsByManagerEmail(
            @NonNull String employeeUid,
            @NonNull Roles role,
            @Nullable String directManagerEmail,
            @NonNull Double vacationDaysPerMonth,
            @Nullable String department,
            @Nullable String jobTitle,
            @Nullable String selectedTeamId,
            @Nullable String employmentType,
            @NonNull SimpleCallback callback
    ) {
        String email = directManagerEmail == null ? "" : directManagerEmail.trim().toLowerCase();

        // Empty email => no direct manager.
        if (email.isEmpty()) {
            approveEmployeeWithDetails(
                    employeeUid, role, null,
                    vacationDaysPerMonth, department, jobTitle,
                    selectedTeamId, employmentType,
                    callback
            );
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

                    String managerUid = qs.getDocuments().get(0).getId();

                    approveEmployeeWithDetails(
                            employeeUid, role, managerUid,
                            vacationDaysPerMonth, department, jobTitle,
                            selectedTeamId, employmentType,
                            callback
                    );
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
     * - Optionally adds employee to a team (user.teamIds + team.memberIds)
     *
     * directManagerId is UID or null for top-level.
     */
    public void approveEmployeeWithDetails(
            @NonNull String employeeUid,
            @NonNull Roles role,
            @Nullable String directManagerId,
            @NonNull Double vacationDaysPerMonth,
            @Nullable String department,
            @Nullable String jobTitle,
            @Nullable String selectedTeamId,
            @Nullable String employmentType,
            @NonNull SimpleCallback callback
    ) {
        DocumentReference employeeRef = db.collection("users").document(employeeUid);

        // Need companyId for team path updates.
        employeeRef.get().addOnCompleteListener(empTask -> {
            if (!empTask.isSuccessful()
                    || empTask.getResult() == null
                    || !empTask.getResult().exists()) {
                callback.onComplete(false, "Failed to load employee data");
                return;
            }

            String companyId = empTask.getResult().getString("companyId");
            if (companyId == null || companyId.trim().isEmpty()) {
                callback.onComplete(false, "Employee has no companyId");
                return;
            }

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

                    DocumentSnapshot managerDoc = task.getResult();
                    List<String> managerChain = buildManagerChain(directManagerId, managerDoc);

                    updateEmployeeDocument(
                            companyId,
                            employeeRef,
                            role,
                            directManagerId,
                            managerChain,
                            vacationDaysPerMonth,
                            department,
                            jobTitle,
                            selectedTeamId,
                            employmentType,
                            callback
                    );
                });
            } else {
                // Top-level manager (no direct manager).
                updateEmployeeDocument(
                        companyId,
                        employeeRef,
                        role,
                        null,
                        new ArrayList<>(),
                        vacationDaysPerMonth,
                        department,
                        jobTitle,
                        selectedTeamId,
                        employmentType,
                        callback
                );
            }
        });
    }

    /**
     * Builds manager chain: [directManagerId, managerOfManagerId, ...]
     */
    private List<String> buildManagerChain(@NonNull String directManagerId, @NonNull DocumentSnapshot managerDoc) {
        List<String> chain = new ArrayList<>();
        chain.add(directManagerId);

        @SuppressWarnings("unchecked")
        List<String> managersOfManager = (List<String>) managerDoc.get("managerChain");

        if (managersOfManager != null) {
            chain.addAll(managersOfManager);
        }

        return chain;
    }

    /**
     * Central method to apply approval updates to employee document.
     * Uses a batch when we also need to touch team membership.
     */
    private void updateEmployeeDocument(
            @NonNull String companyId,
            @NonNull DocumentReference employeeRef,
            @NonNull Roles role,
            @Nullable String directManagerId,
            @NonNull List<String> managerChain,
            @NonNull Double vacationDaysPerMonth,
            @Nullable String department,
            @Nullable String jobTitle,
            @Nullable String selectedTeamId,
            @Nullable String employmentType,
            @NonNull SimpleCallback callback
    ) {
        HashMap<String, Object> updates = new HashMap<>();

        // Approval state
        updates.put("status", RegisterStatus.APPROVED.name());
        updates.put("role", role.name());

        // Hierarchy
        updates.put("directManagerId", directManagerId);
        updates.put("managerChain", managerChain);

        // Org metadata (NO "team")
        updates.put("department", department == null ? "" : department);
        updates.put("jobTitle", jobTitle == null ? "" : jobTitle);

        // NEW
        updates.put("employmentType", (employmentType == null || employmentType.trim().isEmpty()) ? null : employmentType);

        // Vacation policy
        updates.put("vacationDaysPerMonth", vacationDaysPerMonth);

        // Join date set at approval time
        updates.put("joinDate", new Date());

        // Accrual tracking initialization
        updates.put("vacationBalance", 0.0);
        updates.put("lastAccrualDate", LocalDate.now().toString()); // yyyy-MM-dd

        boolean hasTeam = selectedTeamId != null && !selectedTeamId.trim().isEmpty();

        if (!hasTeam) {
            employeeRef.update(updates)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            callback.onComplete(true, "Employee approved");
                        } else {
                            callback.onComplete(false, "Failed to approve employee");
                        }
                    });
            return;
        }

        // Batch: user.teamIds += teamId, team.memberIds += userId, and user fields update
        WriteBatch batch = db.batch();

        // Update user fields
        batch.update(employeeRef, updates);
        batch.update(employeeRef, "teamIds", FieldValue.arrayUnion(selectedTeamId.trim()));

        // Update team membership
        DocumentReference teamRef = db.collection("companies")
                .document(companyId)
                .collection("teams")
                .document(selectedTeamId.trim());

        batch.update(teamRef, "memberIds", FieldValue.arrayUnion(employeeRef.getId()));

        batch.commit().addOnCompleteListener(task -> {
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
     *
     * NOTE: no "team" field anymore.
     */
    public void completeManagerProfile(
            @NonNull String managerUid,
            @NonNull Double vacationDaysPerMonth,
            @Nullable String department,
            @Nullable String jobTitle,
            @NonNull SimpleCallback callback
    ) {
        HashMap<String, Object> updates = new HashMap<>();

        updates.put("vacationDaysPerMonth", vacationDaysPerMonth);

        updates.put("department", department == null ? "" : department);
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

    public LiveData<List<User>> listenApprovedEmployeesForTeam(String companyId, String teamId) {
        MutableLiveData<List<User>> live = new MutableLiveData<>(new ArrayList<>());

        db.collection("users")
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("status", RegisterStatus.APPROVED.name())
                .whereArrayContains("teamIds", teamId)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
                        live.postValue(new ArrayList<>());
                        return;
                    }

                    List<User> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        User u = doc.toObject(User.class);
                        if (u != null) {
                            u.setUid(doc.getId());
                            list.add(u);
                        }
                    }
                    live.postValue(list);
                });

        return live;
    }

    /**
     * Backwards-compatible overload (in case older screens still call it with "team").
     * We DO NOT write team anywhere. This just forwards.
     */
    public void completeManagerProfile(
            @NonNull String managerUid,
            @NonNull Double vacationDaysPerMonth,
            @Nullable String department,
            @Nullable String team_IGNORED,
            @Nullable String jobTitle,
            @NonNull SimpleCallback callback
    ) {
        completeManagerProfile(managerUid, vacationDaysPerMonth, department, jobTitle, callback);
    }
}
