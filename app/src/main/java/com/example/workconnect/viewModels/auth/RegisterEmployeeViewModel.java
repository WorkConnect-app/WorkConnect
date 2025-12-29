package com.example.workconnect.viewModels.auth;

import android.text.TextUtils;
import android.util.Patterns;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.repository.EmployeeRepository;

/**
 * ViewModel responsible for registering a new employee.
 * Holds UI state (loading / error / success) using LiveData and delegates
 * Firebase Auth + Firestore operations to EmployeeRepository.
 */
public class RegisterEmployeeViewModel extends ViewModel {

    // True while the registration request is running
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    // Error messages to be displayed by the UI
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    // True when registration succeeds but the employee is still pending manager approval
    private final MutableLiveData<Boolean> registrationPending = new MutableLiveData<>(false);

    // Repository layer – handles Firebase/Auth/Firestore logic
    private final EmployeeRepository repository = new EmployeeRepository();

    // Expose LiveData to the UI (read-only)
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<Boolean> getRegistrationPending() {
        return registrationPending;
    }

    /**
     * Starts the employee registration flow.
     * Performs input validation and updates LiveData according to success/failure.
     */
    public void registerEmployee(String firstName,
                                 String lastName,
                                 String email,
                                 String password,
                                 String companyCode) {

        if (TextUtils.isEmpty(firstName) || TextUtils.isEmpty(lastName)) {
            errorMessage.setValue("Please enter first and last name");
            return;
        }

        if (TextUtils.isEmpty(email)) {
            errorMessage.setValue("Email is required");
            return;
        }

        // Email format validation
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errorMessage.setValue("Please enter a valid email address");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            errorMessage.setValue("Password is required");
            return;
        }

        // Firebase email/password requires at least 6 characters
        if (password.length() < 6) {
            errorMessage.setValue("Password must be at least 6 characters");
            return;
        }

        if (TextUtils.isEmpty(companyCode)) {
            errorMessage.setValue("Company code is required");
            return;
        }

        // Company code format validation
        if (companyCode.length() != 6) {
            errorMessage.setValue("Company code must be 6 characters");
            return;
        }

        // ===== Start registration =====

        // Notify UI that the request has started
        isLoading.setValue(true);

        // Forward Firebase/Auth/Firestore logic to repository
        repository.registerEmployee(
                firstName,
                lastName,
                email,
                password,
                companyCode,
                new EmployeeRepository.RegisterEmployeeCallback() {

                    @Override
                    public void onSuccess() {
                        // Stop loading state
                        isLoading.postValue(false);

                        // Registration succeeded but employee still needs manager approval
                        registrationPending.postValue(true);
                    }

                    @Override
                    public void onError(String message) {
                        // Stop loading state
                        isLoading.postValue(false);

                        // Propagate error message to the UI
                        errorMessage.postValue(message);
                    }
                }
        );
    }
}
