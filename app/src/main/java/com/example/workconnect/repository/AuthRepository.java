package com.example.workconnect.repository;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class AuthRepository {

    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface LoginCallback {
        void onSuccess(String role);
        void onError(String message);
    }

    public void login(String email, String password, LoginCallback callback) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || mAuth.getCurrentUser() == null) {
                        String msg = "Login failed";
                        if (task.getException() != null) {
                            msg += ": " + task.getException().getMessage();
                        }
                        callback.onError(msg);
                        return;
                    }

                    String uid = mAuth.getCurrentUser().getUid();

                    db.collection("users").document(uid)
                            .get()
                            .addOnSuccessListener(snapshot -> {
                                if (snapshot.exists()) {
                                    String role = snapshot.getString("role");
                                    callback.onSuccess(role);
                                } else {
                                    callback.onError("User data not found");
                                }
                            })
                            .addOnFailureListener(e ->
                                    callback.onError("Error loading user data: " + e.getMessage())
                            );
                });
    }
}
