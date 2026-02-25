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

public class EmployeeRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();

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
                                            notifyManagersEmployeePending(companyId, uid, fullName, () -> {
                                                mAuth.signOut();
                                                callback.onSuccess();
                                            });
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
                                .addOnFailureListener(e -> onDone.run());
                    } else {
                        onDone.run();
                    }
                })
                .addOnFailureListener(e -> onDone.run());
    }

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

        if (email.isEmpty()) {
            approveEmployeeWithDetails(
                    employeeUid, role, null,
                    vacationDaysPerMonth, department, jobTitle,
                    selectedTeamId, employmentType,
                    callback
            );
            return;
        }

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

        updates.put("status", RegisterStatus.APPROVED.name());
        updates.put("role", role.name());

        updates.put("directManagerId", directManagerId);
        updates.put("managerChain", managerChain);

        updates.put("department", department == null ? "" : department);
        updates.put("jobTitle", jobTitle == null ? "" : jobTitle);

        updates.put("employmentType",
                (employmentType == null || employmentType.trim().isEmpty()) ? null : employmentType
        );

        updates.put("vacationDaysPerMonth", vacationDaysPerMonth);

        updates.put("joinDate", new Date());

        updates.put("vacationBalance", 0.0);
        updates.put("lastAccrualDate", LocalDate.now().toString());

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