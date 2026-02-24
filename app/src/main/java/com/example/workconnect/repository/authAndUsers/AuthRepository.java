package com.example.workconnect.repository.authAndUsers;

import androidx.annotation.NonNull;

import com.example.workconnect.models.enums.RegisterStatus;
import com.example.workconnect.models.enums.Roles;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Handles authentication logic (Email/Password + Google login).
 * Also validates that a matching user profile exists in Firestore.
 */
public class AuthRepository {

    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Callback for Google login flow.
     * Used both for real Google login and internal validation reuse.
     */
    public interface GoogleLoginCallback {
        void onExistingUserSuccess(String role);
        void onNewUserNeedsRegistration(); // User authenticated but no users/{uid} document
        void onError(String message);
    }

    /**
     * Callback for email/password login.
     */
    public interface LoginCallback {
        void onSuccess(String role);
        void onError(String message);
    }

    /**
     * Login using email and password.
     * After FirebaseAuth success, verifies user profile in Firestore.
     */
    public void login(@NonNull String email,
                      @NonNull String password,
                      @NonNull LoginCallback callback) {

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {

                    // Safety check
                    if (mAuth.getCurrentUser() == null) {
                        callback.onError("Login succeeded but user is null");
                        return;
                    }

                    final String uid = mAuth.getCurrentUser().getUid();

                    // Fetch user profile from Firestore
                    db.collection("users").document(uid)
                            .get()
                            .addOnSuccessListener(snapshot -> {

                                // If no profile exists -> treat as error
                                if (snapshot == null || !snapshot.exists()) {
                                    mAuth.signOut();
                                    callback.onError("User data not found");
                                    return;
                                }

                                // Reuse same validation logic as Google login
                                validateSnapshotAndReturnRole(snapshot, new GoogleLoginCallback() {
                                    @Override
                                    public void onExistingUserSuccess(String role) {
                                        callback.onSuccess(role);
                                    }

                                    @Override
                                    public void onNewUserNeedsRegistration() {
                                        // Email/password users are expected to have a profile
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
                                callback.onError(
                                        e.getMessage() == null ?
                                                "Error loading user data" :
                                                e.getMessage()
                                );
                            });
                })
                .addOnFailureListener(e ->
                        callback.onError(
                                e.getMessage() == null ?
                                        "Login failed" :
                                        e.getMessage()
                        )
                );
    }

    /**
     * Login using Google ID token.
     * If user exists in Firestore -> continue.
     * If not -> user must complete registration.
     */
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

                    // Check if profile already exists
                    db.collection("users").document(uid)
                            .get()
                            .addOnSuccessListener(snapshot -> {

                                if (snapshot == null || !snapshot.exists()) {
                                    // Authenticated with Google, but no profile in app yet
                                    callback.onNewUserNeedsRegistration();
                                    return;
                                }

                                validateSnapshotAndReturnRole(snapshot, callback);
                            })
                            .addOnFailureListener(e ->
                                    callback.onError(
                                            e.getMessage() == null ?
                                                    "Error loading user data" :
                                                    e.getMessage()
                                    )
                            );
                })
                .addOnFailureListener(e ->
                        callback.onError(
                                e.getMessage() == null ?
                                        "Google login failed" :
                                        e.getMessage()
                        )
                );
    }

    /**
     * Validates role and status from Firestore snapshot.
     * Ensures role is valid and employee accounts are approved.
     */
    private void validateSnapshotAndReturnRole(@NonNull DocumentSnapshot snapshot,
                                               @NonNull GoogleLoginCallback callback) {

        String roleStr = snapshot.getString("role");
        String statusStr = snapshot.getString("status");

        Roles role;

        // Parse role safely
        try {
            if (roleStr == null || roleStr.trim().isEmpty())
                throw new IllegalArgumentException("Missing role");

            role = Roles.valueOf(roleStr.trim().toUpperCase());

        } catch (IllegalArgumentException e) {
            mAuth.signOut();
            callback.onError("Invalid user role");
            return;
        }

        RegisterStatus status;

        // Parse register status safely
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

        // Business rule: Employees must be approved before login
        if (role == Roles.EMPLOYEE && status != RegisterStatus.APPROVED) {
            mAuth.signOut();
            callback.onError("User account is not approved yet");
            return;
        }

        // Everything valid → return role
        callback.onExistingUserSuccess(role.name());
    }
}