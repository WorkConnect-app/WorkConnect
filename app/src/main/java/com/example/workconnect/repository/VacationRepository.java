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

    // Returns the UID of the currently authenticated user (or null if not logged in)
    public String getCurrentUserId() {
        if (mAuth.getCurrentUser() == null) {
            return null;
        }
        return mAuth.getCurrentUser().getUid();
    }

    // Returns a Task that fetches the user's Firestore document (or null if no user is logged in)
    public Task<DocumentSnapshot> getCurrentUserTask() {
        String uid = getCurrentUserId();
        if (uid == null) {
            return null;
        }

        return db.collection("users")
                .document(uid)
                .get();
    }

    // Generates a new unique ID for a vacation request
    public String generateVacationRequestId() {
        DocumentReference ref = db.collection("vacation_requests").document();
        return ref.getId();
    }

    // Saves a vacation request to Firestore and returns a Task representing the operation
    public Task<Void> createVacationRequest(VacationRequest request) {
        // Ensure that VacationRequest has a getId() method.
        // If not, generateVacationRequestId() should be used instead.
        return db.collection("vacation_requests")
                .document(request.getId())
                .set(request);
    }

    // Fetches all pending vacation requests for a specific company (real-time updates)
    public LiveData<List<VacationRequest>> getPendingRequests(String companyId) {
        MutableLiveData<List<VacationRequest>> live = new MutableLiveData<>(new ArrayList<>());

        db.collection("vacation_requests")
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("status", "pending")
                .orderBy("startDate", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
                        live.postValue(new ArrayList<>());
                        return;
                    }

                    List<VacationRequest> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        VacationRequest r = d.toObject(VacationRequest.class);
                        if (r != null) {
                            // IMPORTANT: set request id from document id (for approve/reject)
                            r.setId(d.getId());
                            list.add(r);
                        }
                    }
                    live.postValue(list);
                });

        return live;
    }

    // Updates request status ("approved" / "rejected")
    public Task<Void> updateRequestStatus(String requestId, String newStatus) {
        return db.collection("vacation_requests")
                .document(requestId)
                .update("status", newStatus);
    }


}
