package com.example.workconnect.repository.authAndUsers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Repository for employee-related operations.
 * Responsibilities:
 * - Employee registration (Auth + Firestore user profile)
 * - Listen for pending employees in a company
 * - Approve employee (status/role + hierarchy + optional team assignment)
 * - Manager profile completion
 */
public class EmployeeRepository {

    // Firestore entry point for users/companies collections
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // Auth instance is used for employee registration only (create user)
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();

    // Callback interfaces keep repository UI-agnostic
    public interface RegisterEmployeeCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface PendingEmployeesCallback {
        void onSuccess(List<User> employees);
        void onError(String message);
    }

    public interface SimpleCallback {
        void onComplete(boolean success, String message);
    }

    /**
     * Registration flow for a new employee:
     * 1) Find company by join code
     * 2) Create Auth account (email/password)
     * 3) Create Firestore user document with PENDING status
     * 4) Notify relevant managers and sign out newly created user
     */
    public void registerEmployee(
            @NonNull String firstName,
            @NonNull String lastName,
            @NonNull String email,
            @NonNull String password,
            @NonNull String companyCode,
            @NonNull RegisterEmployeeCallback callback
    ) {
        // Store derived values to avoid recomputing and to keep writes normalized
        final String fullName = (firstName + " " + lastName).trim();
        final String normalizedCode = companyCode.trim();

        // Lookup company by public join code (expected to be unique / first match)
        db.collection("companies")
                .whereEqualTo("code", normalizedCode)
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (qs == null || qs.isEmpty()) {
                        callback.onError("Company not found for code: " + normalizedCode);
                        return;
                    }

                    // Use document id as companyId
                    DocumentSnapshot companyDoc = qs.getDocuments().get(0);
                    final String companyId = companyDoc.getId();

                    // Create Auth account for employee (separate from Firestore profile)
                    mAuth.createUserWithEmailAndPassword(email, password)
                            .addOnSuccessListener(authResult -> {

                                // Safety check: after auth success, current user should exist
                                if (mAuth.getCurrentUser() == null) {
                                    callback.onError("Registration succeeded but user is null");
                                    return;
                                }

                                final String uid = mAuth.getCurrentUser().getUid();

                                // Build initial user profile document (defaults are intentionally explicit)
                                HashMap<String, Object> userData = new HashMap<>();
                                userData.put("uid", uid);
                                userData.put("firstName", firstName);
                                userData.put("lastName", lastName);
                                userData.put("fullName", fullName);
                                userData.put("email", email.trim().toLowerCase());
                                userData.put("companyId", companyId);

                                // New employees start as PENDING until manager approval
                                userData.put("status", RegisterStatus.PENDING.name());
                                userData.put("role", Roles.EMPLOYEE.name());

                                // Hierarchy fields (filled on approval)
                                userData.put("directManagerId", null);
                                userData.put("managerChain", new ArrayList<String>());

                                // Vacation fields are initialized to 0 until approval config is applied
                                userData.put("vacationDaysPerMonth", 0.0);
                                userData.put("vacationBalance", 0.0);
                                userData.put("lastAccrualDate", null);

                                // Optional profile fields
                                userData.put("department", "");
                                userData.put("jobTitle", "");

                                // Membership fields
                                userData.put("teamIds", new ArrayList<String>());
                                userData.put("employmentType", null);

                                // Join date is set upon approval
                                userData.put("joinDate", null);

                                // Write Firestore user profile
                                db.collection("users")
                                        .document(uid)
                                        .set(userData)
                                        .addOnSuccessListener(unused -> {
                                            // Notify managers asynchronously; regardless of notify outcome we sign out
                                            notifyManagersEmployeePending(companyId, uid, fullName, () -> {
                                                // Employee should not remain logged in right after registration
                                                mAuth.signOut();
                                                callback.onSuccess();
                                            });
                                        })
                                        .addOnFailureListener(e -> {
                                            // If Firestore write failed after Auth user created, ה attempt to delete Auth user
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
     * Real-time listener for pending employees in a company.
     * Returns ListenerRegistration so caller can remove listener in onStop/onCleared.
     */
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

                    // If snapshot is null, treat as empty list
                    if (value == null) {
                        callback.onSuccess(new ArrayList<>());
                        return;
                    }

                    // Map documents into User models
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
     * Updates employee status only.
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
     * Notifies all managers in the company that an employee is pending approval.
     */
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
                            // Delegate to NotificationService to keep repo focused on data flow
                            NotificationService.addEmployeePendingApprovalForManager(
                                    batch, managerId, employeeUid, employeeName, companyId
                            );
                        }
                        batch.commit()
                                .addOnSuccessListener(v -> onDone.run())
                                .addOnFailureListener(e -> onDone.run());
                    } else {
                        onDone.run();
                    }
                })
                .addOnFailureListener(e -> onDone.run());
    }

    /**
     * Approves employee while resolving direct manager by email
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

        // Empty email means "no direct manager" (top-level under company)
        if (email.isEmpty()) {
            approveEmployeeWithDetails(
                    employeeUid, role, null,
                    vacationDaysPerMonth, department, jobTitle,
                    selectedTeamId, employmentType,
                    callback
            );
            return;
        }

        // Find manager by email (expected unique in users collection)
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
     * Approves employee and sets additional HR fields + hierarchy:
     * - status APPROVED
     * - role assignment
     * - directManagerId and managerChain (for approvals routing)
     * - optional team membership updates (employee + team document)
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

        // Load employee document first to extract companyId
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

            // If employee has a direct manager, build the chain from manager's document
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
                // No manager means empty chain
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
     * Builds manager chain for employee.
     */
    private List<String> buildManagerChain(
            @NonNull String directManagerId,
            @NonNull DocumentSnapshot managerDoc
    ) {
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
     * Applies approval updates to employee document and optionally updates team membership.
     * If selectedTeamId is provided, uses WriteBatch to update:
     * - employee fields + teamIds array
     * - team.memberIds array
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

        // Core approval fields
        updates.put("status", RegisterStatus.APPROVED.name());
        updates.put("role", role.name());

        // Hierarchy fields
        updates.put("directManagerId", directManagerId);
        updates.put("managerChain", managerChain);

        // Normalize nullable strings
        updates.put("department", department == null ? "" : department);
        updates.put("jobTitle", jobTitle == null ? "" : jobTitle);

        // Keep employmentType nullable
        updates.put("employmentType",
                (employmentType == null || employmentType.trim().isEmpty()) ? null : employmentType
        );

        // Vacation configuration set by manager on approval
        updates.put("vacationDaysPerMonth", vacationDaysPerMonth);

        // Join date is set at approval time
        updates.put("joinDate", new Date());

        // Initialize accrual state (balance starts at 0)
        updates.put("vacationBalance", 0.0);
        updates.put("lastAccrualDate", LocalDate.now().toString());

        boolean hasTeam = selectedTeamId != null && !selectedTeamId.trim().isEmpty();

        // If no team selected, a single update is enough
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

        // Team membership requires updating both employee and team documents
        WriteBatch batch = db.batch();

        batch.update(employeeRef, updates);
        batch.update(employeeRef, "teamIds", FieldValue.arrayUnion(selectedTeamId.trim()));

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
     * Completes manager profile after initial signup:
     * - sets department/jobTitle
     * - sets vacation config and accrual initialization
     * - marks profileCompleted=true
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

    /**
     * LiveData listener for approved employees in a specific team.
     */
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
     * Backwards-compatible overload (team parameter is ignored).
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