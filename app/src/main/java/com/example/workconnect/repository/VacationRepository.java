package com.example.workconnect.repository;

import com.example.workconnect.models.VacationRequest;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

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
}
