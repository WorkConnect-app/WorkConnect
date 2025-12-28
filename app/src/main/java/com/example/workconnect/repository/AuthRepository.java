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
                .addOnSuccessListener(authResult -> {
                    String uid = mAuth.getCurrentUser().getUid();

                    db.collection("users").document(uid)
                            .get()
                            .addOnSuccessListener(snapshot -> {
                                if (!snapshot.exists()) {
                                    mAuth.signOut();
                                    callback.onError("User data not found");
                                    return;
                                }

                                String role = snapshot.getString("role");
                                String status = snapshot.getString("status");

                                if (role == null) {
                                    mAuth.signOut();
                                    callback.onError("Missing user role");
                                    return;
                                }

                                if ("employee".equalsIgnoreCase(role)) {
                                    if (status == null || !"approved".equalsIgnoreCase(status)) {
                                        mAuth.signOut();
                                        callback.onError("User account is not approved yet");
                                        return;
                                    }
                                }

                                callback.onSuccess(role);
                            })
                            .addOnFailureListener(e -> {
                                e.printStackTrace();
                                mAuth.signOut();
                                callback.onError("Error loading user data: " + e.toString());
                            });
                })
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                    callback.onError("Login failed: " + e.toString());
                });
    }


}
