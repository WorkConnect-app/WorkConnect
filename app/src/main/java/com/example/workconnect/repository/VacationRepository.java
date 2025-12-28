package com.example.workconnect.repository;
import com.google.firebase.firestore.FirebaseFirestoreException;
import java.util.Date;

import com.example.workconnect.models.VacationRequest;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.Query;

import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class VacationRepository {

    private final FirebaseAuth mAuth;
    private final FirebaseFirestore db;

    public VacationRepository() {
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public String getCurrentUserId() {
        if (mAuth.getCurrentUser() == null) {
            return null;
        }
        return mAuth.getCurrentUser().getUid();
    }

    public Task<DocumentSnapshot> getCurrentUserTask() {
        String uid = getCurrentUserId();
        if (uid == null) {
            return null;
        }

        return db.collection("users")
                .document(uid)
                .get();
    }

    public Task<DocumentSnapshot> getCompanyTask(String companyId) {
        if (companyId == null || companyId.trim().isEmpty()) return null;
        return db.collection("companies").document(companyId).get();
    }

    public String generateVacationRequestId() {
        DocumentReference ref = db.collection("vacation_requests").document();
        return ref.getId();
    }

    public Task<Void> createVacationRequest(VacationRequest request) {
        return db.collection("vacation_requests")
                .document(request.getId())
                .set(request);
    }

    public LiveData<List<VacationRequest>> getPendingRequestsForManager(String managerId) {
        MutableLiveData<List<VacationRequest>> live = new MutableLiveData<>(new ArrayList<>());

        db.collection("vacation_requests")
                .whereEqualTo("managerId", managerId)
                .whereEqualTo("status", "PENDING")
                //.orderBy("startDate", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
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

    public LiveData<List<VacationRequest>> getRequestsForEmployee(String employeeId) {
        MutableLiveData<List<VacationRequest>> live = new MutableLiveData<>(new ArrayList<>());

        db.collection("vacation_requests")
                .whereEqualTo("employeeId", employeeId)
                //.orderBy("startDate", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
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

//    public Task<Void> updateRequestStatus(String requestId, String newStatus) {
//        return db.collection("vacation_requests")
//                .document(requestId)
//                .update("status", newStatus);
//    }

    // Daily accrual update: stores the new balance and the last accrued date (yyyy-MM-dd)
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
            if ("APPROVED".equals(currentStatus) || "REJECTED".equals(currentStatus)) {
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
            double balance = balD == null ? 0.0 : balD;

            double newBalance = balance - daysRequested;

            // 1) Approve request + store decision time
            transaction.update(reqRef,
                    "status", "APPROVED",
                    "decisionAt", new Date()
            );

            // 2) Deduct balance
            transaction.update(userRef,
                    "vacationBalance", newBalance
            );

            return null;
        });
    }

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
            if ("APPROVED".equals(currentStatus) || "REJECTED".equals(currentStatus)) {
                return null;
            }

            transaction.update(reqRef,
                    "status", "REJECTED",
                    "decisionAt", new Date()
            );

            return null;
        });
    }

    public ListenerRegistration listenToUser(String uid, EventListener<DocumentSnapshot> listener) {
        return db.collection("users").document(uid).addSnapshotListener(listener);
    }

}
