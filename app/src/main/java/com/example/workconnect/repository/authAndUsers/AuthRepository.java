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
 * Repository responsible for authentication operations.
 * Supports Email/Password and Google authentication.
 * After authentication, validates that a corresponding user
 * document exists in Firestore.
 */
public class AuthRepository {

    // FirebaseAuth instance used for authentication operations
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();

    // Firestore instance used to retrieve user profile data
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Callback for Google login flow.
     * Separates between existing user and new user cases.
     */
    public interface GoogleLoginCallback {
        void onExistingUserSuccess(String role, String status, boolean profileCompleted);
        void onNewUserNeedsRegistration();
        void onError(String message);
    }

    /**
     * Callback for Email/Password login.
     */
    public interface LoginCallback {
        void onSuccess(String role, String status, boolean profileCompleted);
        void onError(String message);
    }

    // =========================
    // Email login
    // =========================

    /**
     * Attempts login using email and password.
     * After successful authentication, validates user document in Firestore.
     */
    public void login(@NonNull String email,
                      @NonNull String password,
                      @NonNull LoginCallback callback) {

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {

                    // Extra safety check – theoretically shouldn't be null after success
                    if (mAuth.getCurrentUser() == null) {
                        callback.onError("Login succeeded but user is null");
                        return;
                    }

                    final String uid = mAuth.getCurrentUser().getUid();

                    // Retrieve corresponding user document from Firestore
                    db.collection("users").document(uid)
                            .get()
                            .addOnSuccessListener(snapshot -> {

                                // If user document does not exist - force sign out
                                if (snapshot == null || !snapshot.exists()) {
                                    mAuth.signOut();
                                    callback.onError("User data not found");
                                    return;
                                }

                                // Reuse common validation logic
                                validateSnapshotAndReturnRoleAndStatus(snapshot, new GoogleLoginCallback() {
                                    @Override
                                    public void onExistingUserSuccess(String role, String status, boolean profileCompleted) {
                                        callback.onSuccess(role, status, profileCompleted);
                                    }

                                    @Override
                                    public void onNewUserNeedsRegistration() {
                                        // Email login should not reach this state
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
                                // On Firestore failure, invalidate session
                                mAuth.signOut();
                                callback.onError(e.getMessage() == null ? "Error loading user data" : e.getMessage());
                            });
                })
                .addOnFailureListener(e ->
                        callback.onError(e.getMessage() == null ? "Login failed" : e.getMessage())
                );
    }

    // =========================
    // Google login
    // =========================

    /**
     * Authenticates user using Google ID token.
     * If Firestore document does not exist - considered new user.
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

                    db.collection("users").document(uid)
                            .get()
                            .addOnSuccessListener(snapshot -> {

                                // No Firestore document - user must complete registration
                                if (snapshot == null || !snapshot.exists()) {
                                    callback.onNewUserNeedsRegistration();
                                    return;
                                }

                                validateSnapshotAndReturnRoleAndStatus(snapshot, callback);
                            })
                            .addOnFailureListener(e ->
                                    callback.onError(e.getMessage() == null ? "Error loading user data" : e.getMessage())
                            );
                })
                .addOnFailureListener(e ->
                        callback.onError(e.getMessage() == null ? "Google login failed" : e.getMessage())
                );
    }

    // =========================
    // Snapshot parsing
    // =========================

    /**
     * Extracts role and status from Firestore snapshot.
     * Performs enum validation to prevent invalid values.
     */
    private void validateSnapshotAndReturnRoleAndStatus(@NonNull DocumentSnapshot snapshot,
                                                        @NonNull GoogleLoginCallback callback) {

        String roleStr = snapshot.getString("role");
        String statusStr = snapshot.getString("status");

        Roles role;
        RegisterStatus status = null;

        try {
            // Validate role existence and enum compatibility
            if (roleStr == null || roleStr.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing role");
            }
            role = Roles.valueOf(roleStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            mAuth.signOut(); // Prevent inconsistent session
            callback.onError("Invalid user role");
            return;
        }

        try {
            // Status may be null (e.g., managers without approval flow)
            if (statusStr != null && !statusStr.trim().isEmpty()) {
                status = RegisterStatus.valueOf(statusStr.trim().toUpperCase());
            }
        } catch (IllegalArgumentException e) {
            mAuth.signOut();
            callback.onError("Invalid user status");
            return;
        }

        // Default to false if field is missing
        Boolean pc = snapshot.getBoolean("profileCompleted");
        boolean profileCompleted = pc != null && pc;

        callback.onExistingUserSuccess(
                role.name(),
                status == null ? null : status.name(),
                profileCompleted
        );
    }
}