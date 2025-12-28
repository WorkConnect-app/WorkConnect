package com.example.workconnect.repository;

import com.example.workconnect.models.VacationRequest;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.Query;

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

    public Task<Void> updateRequestStatus(String requestId, String newStatus) {
        return db.collection("vacation_requests")
                .document(requestId)
                .update("status", newStatus);
    }

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
}
