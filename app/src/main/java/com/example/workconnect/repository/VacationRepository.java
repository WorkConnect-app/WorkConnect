package com.example.workconnect.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.workconnect.models.VacationRequest;
import com.example.workconnect.models.enums.VacationStatus;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

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

    /** @return current FirebaseAuth user's uid, or null if not signed in. */
    public String getCurrentUserId() {
        if (mAuth.getCurrentUser() == null) {
            return null;
        }
        return mAuth.getCurrentUser().getUid();
    }

    /**
     * bring the current user's Firestore document.
     * returns null if user is not logged in (caller must handle).
     */
    public Task<DocumentSnapshot> getCurrentUserTask() {
        String uid = getCurrentUserId();
        if (uid == null) {
            return null;
        }

        return db.collection("users")
                .document(uid)
                .get();
    }

    /**
     * bring a company document by id.
     * returns null if companyId is empty (caller must handle).
     */
    public Task<DocumentSnapshot> getCompanyTask(String companyId) {
        if (companyId == null || companyId.trim().isEmpty()) return null;
        return db.collection("companies").document(companyId).get();
    }

    /** Generates a new document id for a vacation request (client-side). */
    public String generateVacationRequestId() {
        DocumentReference ref = db.collection("vacation_requests").document();
        return ref.getId();
    }

    /**
     * Creates a vacation request document using request.getId() as the document id.
     * Assumes request and request.getId() are not null.
     */
    public Task<Void> createVacationRequest(VacationRequest request) {
        return db.collection("vacation_requests")
                .document(request.getId())
                .set(request);
    }

    /**
     * Realtime listener: returns LiveData of all PENDING requests for a manager.
     */
    public LiveData<List<VacationRequest>> getPendingRequestsForManager(String managerId) {

        // Creates a MutableLiveData object and initial value is an empty list
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
                    for (DocumentSnapshot d : snap.getDocuments()) { // runung on the list of all documents that meet the query conditions.
                        VacationRequest r = d.toObject(VacationRequest.class); // Automatically converts the document into VacationRequest fields.
                        if (r != null) {
                            r.setId(d.getId());  // Ensure model id matches the Firestore document id
                            list.add(r); // Adds the vacation request to the results list.
                        }
                    }
                    live.postValue(list); // Updating the LiveData in the new list
                });

        return live;
    }

    /**
     * Realtime listener: returns LiveData of all requests for an employee.
     */
    public LiveData<List<VacationRequest>> getRequestsForEmployee(String employeeId) {
        MutableLiveData<List<VacationRequest>> live = new MutableLiveData<>(new ArrayList<>());

        db.collection("vacation_requests")
                .whereEqualTo("employeeId", employeeId)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        Log.e("VacationRepo", "employee query failed", e);
                        live.postValue(new ArrayList<>());
                        return;
                    }
                    Log.d("VacationRepo", "employee query docs=" + snap.size());

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
     * Daily accrual update: stores the new balance and the last accrued date (yyyy-MM-dd).
     * NOTE: returns null if user is not logged in (caller must handle).
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
     * Approves a request and deducts the employee's vacation balance atomically.
     *
     *
     */
    public Task<Void> approveRequestAndDeductBalance(String requestId) {
        DocumentReference reqRef = db.collection("vacation_requests").document(requestId);

        return db.runTransaction(transaction -> {
            DocumentSnapshot reqSnap = transaction.get(reqRef);
            if (reqSnap == null || !reqSnap.exists()) {
                throw new FirebaseFirestoreException("Request not found",
                        FirebaseFirestoreException.Code.NOT_FOUND);
            }

            // If the request has already been processed
            String currentStatus = reqSnap.getString("status");
            if (VacationStatus.APPROVED.name().equals(currentStatus)
                    || VacationStatus.REJECTED.name().equals(currentStatus)) {
                return null;
            }

            String employeeId = reqSnap.getString("employeeId");
            Long daysL = reqSnap.getLong("daysRequested"); // Reading the number of vacation days

            if (employeeId == null || employeeId.trim().isEmpty() || daysL == null) {
                throw new FirebaseFirestoreException("Invalid request fields",
                        FirebaseFirestoreException.Code.INVALID_ARGUMENT);
            }

            int daysRequested = daysL.intValue();

            DocumentReference userRef = db.collection("users").document(employeeId);

            // Reading within a transaction ensures that no one else has changed the balance at the same time
            DocumentSnapshot userSnap = transaction.get(userRef);
            if (userSnap == null || !userSnap.exists()) {
                throw new FirebaseFirestoreException("Employee not found",
                        FirebaseFirestoreException.Code.NOT_FOUND);
            }

            Double balD = userSnap.getDouble("vacationBalance");
            double balance = balD == null ? 0.0 : balD;

            double newBalance = balance - daysRequested;

            // 1) Approve request + store decision time (client device time)
            transaction.update(reqRef,
                    "status", VacationStatus.APPROVED.name(),
                    "decisionAt", new Date()
            );

            // 2) Deduct balance
            transaction.update(userRef,
                    "vacationBalance", newBalance
            );

            return null;
        });
    }

    /**
     * Rejects a request atomically.
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

            transaction.update(reqRef,
                    "status", VacationStatus.REJECTED.name(),
                    "decisionAt", new Date()
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

    public Task<DocumentSnapshot> getUserTask(@NonNull String uid) {
        return FirebaseFirestore.getInstance().collection("users").document(uid).get();
    }

}
