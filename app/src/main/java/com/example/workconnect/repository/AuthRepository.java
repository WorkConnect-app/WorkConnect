package com.example.workconnect.repository;

import androidx.annotation.NonNull;

import com.example.workconnect.models.enums.RegisterStatus;
import com.example.workconnect.models.enums.Roles;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Repository responsible for authentication-related operations (login).
 *
 * Flow:
 * 1) Authenticate using FirebaseAuth (email/password).
 * 2) Load the user profile document from Firestore ("users/{uid}").
 * 3) Validate role and approval status (employees must be APPROVED).
 */
public class AuthRepository {

    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Callback used to return login result back to the UI layer.
     */
    public interface LoginCallback {
        void onSuccess(String role);
        void onError(String message);
    }

    /**
     * Logs in with email/password, then verifies the user's role and approval status in Firestore.
     * - Employees must be APPROVED to access the app.
     * - Missing/invalid role/status will reject the login and sign out the user.
     */
    public void login(@NonNull String email,
                      @NonNull String password,
                      @NonNull LoginCallback callback) {

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {

                    // Safety check (prevents rare crashes).
                    if (mAuth.getCurrentUser() == null) {
                        callback.onError("Login succeeded but user is null");
                        return;
                    }

                    final String uid = mAuth.getCurrentUser().getUid();

                    // Load user profile from Firestore to validate role and status.
                    db.collection("users").document(uid)
                            .get()
                            .addOnSuccessListener(snapshot -> {

                                // If the user profile document doesn't exist, reject login.
                                if (snapshot == null || !snapshot.exists()) {
                                    mAuth.signOut();
                                    callback.onError("User data not found");
                                    return;
                                }

                                String roleStr = snapshot.getString("role");
                                String statusStr = snapshot.getString("status");

                                // Role is required to continue the user to the correct home screen.
                                Roles role;
                                try {
                                    if (roleStr == null || roleStr.trim().isEmpty()) {
                                        throw new IllegalArgumentException("Missing role");
                                    }
                                    role = Roles.valueOf(roleStr.trim().toUpperCase());
                                } catch (IllegalArgumentException e) {
                                    mAuth.signOut();
                                    callback.onError("Invalid user role");
                                    return;
                                }

                                // Status is required for employee approval logic.
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

                                // Employees must be approved by a manager before they can access the app.
                                if (role == Roles.EMPLOYEE) {
                                    if (status != RegisterStatus.APPROVED) {
                                        mAuth.signOut();
                                        callback.onError("User account is not approved yet");
                                        return;
                                    }
                                }

                                // Login is valid. Return the role (as string) to the UI layer.
                                callback.onSuccess(role.name()); // "EMPLOYEE" / "MANAGER"
                            })
                            .addOnFailureListener(e -> {
                                mAuth.signOut();
                                String msg = (e.getMessage() == null) ? "Error loading user data" : e.getMessage();
                                callback.onError(msg);
                            });
                })
                .addOnFailureListener(e -> {
                    String msg = (e.getMessage() == null) ? "Login failed" : e.getMessage();
                    callback.onError(msg);
                });
    }
}
