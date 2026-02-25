package com.example.workconnect.viewModels.auth;

import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.repository.authAndUsers.EmployeeRepository;

/**
 * ViewModel responsible for employee registration flow.
 * Handles validation + delegates creation to EmployeeRepository.
 * After registration, employee status is PENDING (waiting for manager approval).
 */
public class RegisterEmployeeViewModel extends ViewModel {

    // UI state
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> registrationPending = new MutableLiveData<>(false);

    // Repository that handles Firebase Auth + Firestore writes
    private final EmployeeRepository repository = new EmployeeRepository();

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
     * Validates input and registers a new employee.
     * If successful → employee is created with PENDING status.
     */
    public void registerEmployee(String firstName,
                                 String lastName,
                                 String email,
                                 String password,
                                 String companyCode) {

        // Basic validation for empty fields
        if (TextUtils.isEmpty(firstName) ||
                TextUtils.isEmpty(lastName) ||
                TextUtils.isEmpty(email) ||
                TextUtils.isEmpty(password) ||
                TextUtils.isEmpty(companyCode)) {

            errorMessage.setValue("Please fill all fields");
            return;
        }

        // Password rule
        if (password.length() < 6) {
            errorMessage.setValue("Password must be at least 6 characters");
            return;
        }

        isLoading.setValue(true);

        // Delegate actual registration logic to repository
        repository.registerEmployee(firstName, lastName, email, password, companyCode,
                new EmployeeRepository.RegisterEmployeeCallback() {
                    @Override
                    public void onSuccess() {
                        isLoading.postValue(false);
                        // Notify UI that registration succeeded but awaits approval
                        registrationPending.postValue(true);
                    }

                    @Override
                    public void onError(String message) {
                        isLoading.postValue(false);
                        errorMessage.postValue(message);
                    }
                });
    }
}