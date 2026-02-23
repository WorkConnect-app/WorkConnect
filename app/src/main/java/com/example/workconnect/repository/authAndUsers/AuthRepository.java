package com.example.workconnect.repository.authAndUsers;

import androidx.annotation.NonNull;

import com.example.workconnect.models.enums.RegisterStatus;
import com.example.workconnect.models.enums.Roles;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class AuthRepository {

    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface GoogleLoginCallback {
        void onExistingUserSuccess(String role);
        void onNewUserNeedsRegistration(); // אין users/{uid}
        void onError(String message);
    }

    public interface LoginCallback {
        void onSuccess(String role);
        void onError(String message);
    }

    public void login(@NonNull String email,
                      @NonNull String password,
                      @NonNull LoginCallback callback) {

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {

                    if (mAuth.getCurrentUser() == null) {
                        callback.onError("Login succeeded but user is null");
                        return;
                    }

                    final String uid = mAuth.getCurrentUser().getUid();

                    db.collection("users").document(uid)
                            .get()
                            .addOnSuccessListener(snapshot -> {

                                if (snapshot == null || !snapshot.exists()) {
                                    mAuth.signOut();
                                    callback.onError("User data not found");
                                    return;
                                }

                                // reuse the same validation logic you already have
                                validateSnapshotAndReturnRole(snapshot, new GoogleLoginCallback() {
                                    @Override
                                    public void onExistingUserSuccess(String role) {
                                        callback.onSuccess(role);
                                    }

                                    @Override
                                    public void onNewUserNeedsRegistration() {
                                        // Email/password users are supposed to have user profiles,
                                        // so treat this as an error.
                                        mAuth.signOut();
                                        callback.onError("User profile missing");
                                    }

                                    @Override
                                    public void onError(String message) {
                                        callback.onError(message);
                                    }
                                });
                            })
                            .addOnFailureListener(e -> {
                                mAuth.signOut();
                                callback.onError(e.getMessage() == null ? "Error loading user data" : e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    callback.onError(e.getMessage() == null ? "Login failed" : e.getMessage());
                });
    }

    public void loginWithGoogleIdToken(@NonNull String idToken,
                                       @NonNull GoogleLoginCallback callback) {

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);

        mAuth.signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    if (mAuth.getCurrentUser() == null) {
                        callback.onError("Google login succeeded but user is null");
                        return;
                    }

                    String uid = mAuth.getCurrentUser().getUid();

                    db.collection("users").document(uid)
                            .get()
                            .addOnSuccessListener(snapshot -> {
                                if (snapshot == null || !snapshot.exists()) {
                                    // מחוברים עם גוגל, אבל אין פרופיל באפליקציה -> השלמת פרטים
                                    callback.onNewUserNeedsRegistration();
                                    return;
                                }

                                validateSnapshotAndReturnRole(snapshot, callback);
                            })
                            .addOnFailureListener(e ->
                                    callback.onError(e.getMessage() == null ? "Error loading user data" : e.getMessage())
                            );
                })
                .addOnFailureListener(e ->
                        callback.onError(e.getMessage() == null ? "Google login failed" : e.getMessage())
                );
    }

    private void validateSnapshotAndReturnRole(@NonNull DocumentSnapshot snapshot,
                                               @NonNull GoogleLoginCallback callback) {

        String roleStr = snapshot.getString("role");
        String statusStr = snapshot.getString("status");

        Roles role;
        try {
            if (roleStr == null || roleStr.trim().isEmpty()) throw new IllegalArgumentException("Missing role");
            role = Roles.valueOf(roleStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            mAuth.signOut();
            callback.onError("Invalid user role");
            return;
        }

        RegisterStatus status;
        try {
            if (statusStr == null || statusStr.trim().isEmpty()) {
                status = null;
            } else {
                status = RegisterStatus.valueOf(statusStr.trim().toUpperCase());
            }
        } catch (IllegalArgumentException e) {
            mAuth.signOut();
            callback.onError("Invalid user status");
            return;
        }

        if (role == Roles.EMPLOYEE && status != RegisterStatus.APPROVED) {
            mAuth.signOut();
            callback.onError("User account is not approved yet");
            return;
        }

        callback.onExistingUserSuccess(role.name());
    }
}
