package com.example.workconnect.viewModels.auth;

import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.repository.AuthRepository;

/**
 * ViewModel responsible for handling the login flow.
 * It validates input, exposes loading/error/success states via LiveData,
 * and delegates the actual authentication logic to AuthRepository.
 */
public class LoginViewModel extends ViewModel {

    // Indicates whether the login request is currently in progress
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    // Holds error messages to be displayed by the UI
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    // Holds the logged-in user's role ("manager" / "employee")
    // Used by the UI to navigate to the correct home screen
    private final MutableLiveData<String> loginRole = new MutableLiveData<>();

    // Repository that handles Firebase Auth / Firestore login logic
    private final AuthRepository repository = new AuthRepository();

    // Expose LiveData to the UI (read-only)
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<String> getLoginRole() {
        return loginRole;
    }

    private final MutableLiveData<Boolean> needsRegistration = new MutableLiveData<>(false);
    public LiveData<Boolean> getNeedsRegistration() { return needsRegistration; }


    /**
     * Triggers the login process.
     * Performs basic input validation and updates LiveData according to the result.
     */
    public void login(String email, String password) {

        // Basic validation before calling the repository
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            errorMessage.setValue("Email or Password required.");
            return;
        }

        // Notify UI that login has started
        isLoading.setValue(true);

        // Forward the actual authentication logic to the repository
        repository.login(email, password, new AuthRepository.LoginCallback() {

            @Override
            public void   onSuccess(String role) {
                // Stop loading state
                isLoading.postValue(false);

                // Supply the user's role to the UI
                if (role != null) {
                    loginRole.postValue(role.toLowerCase());
                }
            }

            @Override
            public void onError(String message) {
                // Stop loading state
                isLoading.postValue(false);

                // Propagate error message to the UI
                errorMessage.postValue(message);
            }
        });
    }

    public void loginWithGoogleIdToken(String idToken) {
        if (TextUtils.isEmpty(idToken)) {
            errorMessage.setValue("Google ID token is missing.");
            return;
        }

        isLoading.setValue(true);

        repository.loginWithGoogleIdToken(idToken, new AuthRepository.GoogleLoginCallback() {
            @Override
            public void onExistingUserSuccess(String role) {
                isLoading.postValue(false);
                loginRole.postValue(role.toLowerCase());
            }

            @Override
            public void onNewUserNeedsRegistration() {
                isLoading.postValue(false);
                needsRegistration.postValue(true);
            }

            @Override
            public void onError(String message) {
                isLoading.postValue(false);
                errorMessage.postValue(message);
            }
        });
    }

}
