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
 * Repository responsible for employee-related operations.
 *
 * Responsibilities:
 * - Employee registration (Auth account + Firestore profile)
 * - Listening for pending employees
 * - Approving employees with hierarchy and organizational data
 * - Initializing vacation accrual data
 * - Managing team membership during approval
 */
public class EmployeeRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();

    /** Callback used when registering a new employee. */
    public interface RegisterEmployeeCallback {
        void onSuccess();
        void onError(String message);
    }

    /** Callback used when listening to pending employees. */
    public interface PendingEmployeesCallback {
        void onSuccess(List<User> employees);
        void onError(String message);
    }

    /** Generic callback for simple operations. */
    public interface SimpleCallback {
        void onComplete(boolean success, String message);
    }

    /**
     * Registers a new employee:
     * 1) Finds company by join code.
     * 2) Creates FirebaseAuth account.
     * 3) Creates Firestore user document with status = PENDING.
     *
     * After successful registration, user is signed out until approved.
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
                        callback.onError("Company not found for provided code");
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
                                                        .addOnCompleteListener(t ->
                                                                callback.onError("Failed to save user data")
                                                        );
                                            } else {
                                                callback.onError("Failed to save user data");
                                            }
                                        });

                            })
                            .addOnFailureListener(e ->
                                    callback.onError("Failed to create user")
                            );
                })
                .addOnFailureListener(e ->
                        callback.onError("Failed to lookup company")
                );
    }

    /**
     * Realtime listener for pending employees within a company.
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
                        callback.onError("Listener error");
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
     * Updates only the employee status field.
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
     * Sends notifications to all managers in the company
     * when a new employee registers and is pending approval.
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
     * Builds manager hierarchy chain:
     * [directManagerId, managerOfManagerId, ...]
     */
    private List<String> buildManagerChain(
            @NonNull String directManagerId,
            @NonNull DocumentSnapshot managerDoc
    ) {
        List<String> chain = new ArrayList<>();
        chain.add(directManagerId);

        @SuppressWarnings("unchecked")
        List<String> parentChain = (List<String>) managerDoc.get("managerChain");

        if (parentChain != null) {
            chain.addAll(parentChain);
        }

        return chain;
    }

    /**
     * Realtime listener for approved employees belonging to a specific team.
     */
    public LiveData<List<User>> listenApprovedEmployeesForTeam(
            String companyId,
            String teamId
    ) {
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
}