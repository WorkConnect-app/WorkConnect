package com.example.workconnect.viewModels.auth;

import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.repository.authAndUsers.AuthRepository;
import com.google.firebase.auth.FirebaseAuth;

/**
 * ViewModel responsible for handling the login flow.
 * Handles:
 * - Email login
 * - Google login
 * - Navigation decisions based on role/status/profileCompleted
 */
public class LoginViewModel extends ViewModel {

    // UI loading state
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    // Error messages to display in Activity
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    // Navigation target for the Activity
    // Possible values: COMPLETE_GOOGLE, MANAGER_COMPLETE, HOME
    private final MutableLiveData<String> navigationTarget = new MutableLiveData<>();

    // Used when Google user authenticated but has no Firestore user document
    private final MutableLiveData<Boolean> needsRegistration = new MutableLiveData<>(false);

    private final AuthRepository repository = new AuthRepository();

    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<String> getNavigationTarget() { return navigationTarget; }
    public LiveData<Boolean> getNeedsRegistration() { return needsRegistration; }

    // Clear methods to prevent re-triggering old LiveData values
    public void clearError() { errorMessage.postValue(null); }
    public void clearNavigation() { navigationTarget.postValue(null); }
    public void clearNeedsRegistration() { needsRegistration.postValue(false); }

    // =========================
    // Email login
    // =========================
    public void login(String email, String password) {

        // Basic validation before calling repository
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            errorMessage.setValue("Email or Password required.");
            return;
        }

        isLoading.setValue(true);

        repository.login(email, password, new AuthRepository.LoginCallback() {
            @Override
            public void onSuccess(String role, String status, boolean profileCompleted) {
                isLoading.postValue(false);
                decideNavigation(role, status, profileCompleted);
            }

            @Override
            public void onError(String message) {
                isLoading.postValue(false);
                errorMessage.postValue(message);
            }
        });
    }

    // =========================
    // Google login
    // =========================
    public void loginWithGoogleIdToken(String idToken) {

        // ID token must exist
        if (TextUtils.isEmpty(idToken)) {
            errorMessage.setValue("Google ID token is missing.");
            return;
        }

        isLoading.setValue(true);

        repository.loginWithGoogleIdToken(idToken, new AuthRepository.GoogleLoginCallback() {
            @Override
            public void onExistingUserSuccess(String role, String status, boolean profileCompleted) {
                isLoading.postValue(false);
                decideNavigation(role, status, profileCompleted);
            }

            @Override
            public void onNewUserNeedsRegistration() {
                isLoading.postValue(false);
                needsRegistration.postValue(true); // Navigate to complete profile screen
            }

            @Override
            public void onError(String message) {
                isLoading.postValue(false);
                errorMessage.postValue(message);
            }
        });
    }

    // =========================
    // Navigation rules
    // =========================
    private void decideNavigation(String role, String status, boolean profileCompleted) {

        // Safety check: role must exist
        if (role == null) {
            FirebaseAuth.getInstance().signOut();
            errorMessage.postValue("Missing role");
            return;
        }

        String r = role.trim().toUpperCase();
        String s = (status == null) ? "" : status.trim().toUpperCase();

        // -------------------------
        // Manager logic
        // -------------------------
        if ("MANAGER".equals(r)) {

            // Manager completes profile only once
            if (profileCompleted) {
                navigationTarget.postValue("HOME");
            } else {
                navigationTarget.postValue("MANAGER_COMPLETE");
            }
            return;
        }

        // -------------------------
        // Employee logic
        // -------------------------
        if ("EMPLOYEE".equals(r)) {

            if ("APPROVED".equals(s)) {
                navigationTarget.postValue("HOME");
                return;
            }

            // If employee is PENDING or REJECTED - deny login
            FirebaseAuth.getInstance().signOut();

            if ("REJECTED".equals(s)) {
                errorMessage.postValue("Your registration was rejected by the manager");
            } else {
                errorMessage.postValue("Waiting for manager approval");
            }
            return;
        }

        // Fallback for unexpected roles
        FirebaseAuth.getInstance().signOut();
        errorMessage.postValue("Invalid role");
    }
}