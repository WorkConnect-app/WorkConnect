package com.example.workconnect.repository.vacations;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.workconnect.models.VacationRequest;
import com.example.workconnect.models.enums.VacationStatus;
import com.example.workconnect.services.NotificationService;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;

public class VacationRepository {

    private final FirebaseAuth mAuth;
    private final FirebaseFirestore db;

    public VacationRepository() {
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    /** @return current signed-in user's uid, or null if not signed in. */
    public String getCurrentUserId() {
        if (mAuth.getCurrentUser() == null) return null;
        return mAuth.getCurrentUser().getUid();
    }

    /**
     * Fetch current user's Firestore document.
     * Returns null if user is not logged in (caller should handle).
     */
    public Task<DocumentSnapshot> getCurrentUserTask() {
        String uid = getCurrentUserId();
        if (uid == null) return null;

        return db.collection("users")
                .document(uid)
                .get();
    }

    /**
     * Fetch company document by id.
     * Returns null if companyId is empty (caller should handle).
     */
    public Task<DocumentSnapshot> getCompanyTask(String companyId) {
        if (companyId == null || companyId.trim().isEmpty()) return null;
        return db.collection("companies").document(companyId).get();
    }

    /** Generates a new document id for a vacation request (client-side). */
    public String generateVacationRequestId() {
        return db.collection("vacation_requests").document().getId();
    }

    /**
     * Creates a vacation request document using request.getId() as the document id.
     * Also adds a notification to the manager (if managerId exists).
     */
    public Task<Void> createVacationRequest(VacationRequest request) {
        if (request == null || request.getId() == null) return null;

        String managerId = request.getManagerId();
        String employeeId = request.getEmployeeId();
        String requestId = request.getId();

        com.google.firebase.firestore.WriteBatch batch = db.batch();

        // Create the request document
        DocumentReference reqRef = db.collection("vacation_requests").document(requestId);
        batch.set(reqRef, request);

        // Notify manager (only when we have both managerId and employeeId)
        if (managerId != null && !managerId.trim().isEmpty()
                && employeeId != null && !employeeId.trim().isEmpty()) {

            NotificationService.addVacationNewRequestForManager(
                    batch,
                    managerId,
                    requestId,
                    employeeId
            );
        }

        return batch.commit();
    }

    /**
     * Realtime listener: returns LiveData of all PENDING requests for a specific manager.
     */
    public LiveData<List<VacationRequest>> getPendingRequestsForManager(String managerId) {
        MutableLiveData<List<VacationRequest>> live = new MutableLiveData<>(new ArrayList<>());

        db.collection("vacation_requests")
                .whereEqualTo("managerId", managerId)
                .whereEqualTo("status", VacationStatus.PENDING.name())
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
                        live.postValue(new ArrayList<>());
                        return;
                    }

                    List<VacationRequest> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        VacationRequest r = d.toObject(VacationRequest.class);
                        if (r != null) {
                            // Keep model id consistent with Firestore document id
                            r.setId(d.getId());
                            list.add(r);
                        }
                    }
                    live.postValue(list);
                });

        return live;
    }

    /**
     * Realtime listener: returns LiveData of all vacation requests for a specific employee.
     */
    public LiveData<List<VacationRequest>> getRequestsForEmployee(String employeeId) {
        MutableLiveData<List<VacationRequest>> live = new MutableLiveData<>(new ArrayList<>());

        db.collection("vacation_requests")
                .whereEqualTo("employeeId", employeeId)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
                        Log.e("VacationRepo", "employee query failed", e);
                        live.postValue(new ArrayList<>());
                        return;
                    }

                    List<VacationRequest> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        VacationRequest r = d.toObject(VacationRequest.class);
                        if (r != null) {
                            r.setId(d.getId());
                            list.add(r);
                        }
                    }
                    live.postValue(list);
                });

        return live;
    }

    /**
     * Updates current user's vacation balance and last accrual date.
     * NOTE: returns null if user is not logged in (caller should handle).
     */
    public Task<Void> updateCurrentUserVacationAccrualDaily(double newBalance, String lastAccrualDate) {
        String uid = getCurrentUserId();
        if (uid == null) return null;

        return db.collection("users")
                .document(uid)
                .update(
                        "vacationBalance", newBalance,
                        "lastAccrualDate", lastAccrualDate
                );
    }

    /**
     * Approves a request and deducts employee's vacation balance in a single transaction.
     * Transaction prevents race conditions (two approvals at the same time).
     */
    public Task<Void> approveRequestAndDeductBalance(String requestId) {
        DocumentReference reqRef = db.collection("vacation_requests").document(requestId);

        return db.runTransaction(transaction -> {
            DocumentSnapshot reqSnap = transaction.get(reqRef);
            if (reqSnap == null || !reqSnap.exists()) {
                throw new FirebaseFirestoreException("Request not found",
                        FirebaseFirestoreException.Code.NOT_FOUND);
            }

            // Prevent double-processing
            String currentStatus = reqSnap.getString("status");
            if (VacationStatus.APPROVED.name().equals(currentStatus)
                    || VacationStatus.REJECTED.name().equals(currentStatus)) {
                return null;
            }

            String employeeId = reqSnap.getString("employeeId");
            Long daysL = reqSnap.getLong("daysRequested");

            if (employeeId == null || employeeId.trim().isEmpty() || daysL == null) {
                throw new FirebaseFirestoreException("Invalid request fields",
                        FirebaseFirestoreException.Code.INVALID_ARGUMENT);
            }

            int daysRequested = daysL.intValue();

            DocumentReference userRef = db.collection("users").document(employeeId);

            DocumentSnapshot userSnap = transaction.get(userRef);
            if (userSnap == null || !userSnap.exists()) {
                throw new FirebaseFirestoreException("Employee not found",
                        FirebaseFirestoreException.Code.NOT_FOUND);
            }

            Double balD = userSnap.getDouble("vacationBalance");
            double balance = (balD == null) ? 0.0 : balD;

            double newBalance = balance - daysRequested;

            // Prevent approving if balance would become negative
            if (newBalance < 0) {
                throw new FirebaseFirestoreException(
                        "Not enough balance",
                        FirebaseFirestoreException.Code.INVALID_ARGUMENT
                );
            }

            // Approve request + store decision time
            transaction.update(reqRef,
                    "status", VacationStatus.APPROVED.name(),
                    "decisionAt", new Date()
            );

            // Deduct balance
            transaction.update(userRef,
                    "vacationBalance", newBalance
            );

            // Add notification inside transaction
            NotificationService.addVacationApproved(
                    transaction,
                    employeeId,
                    requestId,
                    daysRequested
            );

            return null;
        });
    }

    /**
     * Rejects a request atomically (single transaction).
     */
    public Task<Void> rejectRequest(String requestId) {
        DocumentReference reqRef = db.collection("vacation_requests").document(requestId);

        return db.runTransaction(transaction -> {
            DocumentSnapshot reqSnap = transaction.get(reqRef);
            if (reqSnap == null || !reqSnap.exists()) {
                throw new FirebaseFirestoreException("Request not found",
                        FirebaseFirestoreException.Code.NOT_FOUND);
            }

            // Prevent double-processing
            String currentStatus = reqSnap.getString("status");
            if (VacationStatus.APPROVED.name().equals(currentStatus)
                    || VacationStatus.REJECTED.name().equals(currentStatus)) {
                return null;
            }

            String employeeId = reqSnap.getString("employeeId");
            if (employeeId == null || employeeId.trim().isEmpty()) {
                throw new FirebaseFirestoreException("Invalid request fields",
                        FirebaseFirestoreException.Code.INVALID_ARGUMENT);
            }

            transaction.update(reqRef,
                    "status", VacationStatus.REJECTED.name(),
                    "decisionAt", new Date()
            );

            NotificationService.addVacationRejected(
                    transaction,
                    employeeId,
                    requestId
            );

            return null;
        });
    }

    /**
     * Realtime listener for a single user document.
     * Caller should keep the returned ListenerRegistration and remove it when done.
     */
    public ListenerRegistration listenToUser(String uid, EventListener<DocumentSnapshot> listener) {
        return db.collection("users").document(uid).addSnapshotListener(listener);
    }

    /** Fetch a user document once. */
    public Task<DocumentSnapshot> getUserTask(@NonNull String uid) {
        return FirebaseFirestore.getInstance().collection("users").document(uid).get();
    }
}