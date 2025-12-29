package com.example.workconnect.viewModels.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import android.text.TextUtils;

import com.example.workconnect.repository.CompanyRepository;

/**
 * ViewModel responsible for the logic of registering a new company
 * and its manager. It validates input, exposes loading/error/success
 * states to the UI using LiveData, and delegates Firebase operations
 * to the CompanyRepository.
 */
public class RegisterCompanyViewModel extends ViewModel {

    // Indicates whether the registration process is currently running
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    // Contains error messages to display in the UI
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    // Holds the newly created company's ID (upon success)
    private final MutableLiveData<String> successCompanyId = new MutableLiveData<>();

    // Holds the auto-generated short company code (shown to the manager)
    private final MutableLiveData<String> successCompanyCode = new MutableLiveData<>();

    // Repository layer – handles all Firebase operations
    private final CompanyRepository repository = new CompanyRepository();

    // Getters for exposing LiveData to the Activity (UI observes these)
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<String> getSuccessCompanyId() {
        return successCompanyId;
    }


    /**
     * Validates the registration input and triggers the repository call.
     * Updates LiveData states according to success/failure.
     */
    public void registerCompany(String companyName,
                                String managerName,
                                String email,
                                String password) {

        // === Input Validation ===
        if (TextUtils.isEmpty(companyName) ||
                TextUtils.isEmpty(managerName) ||
                TextUtils.isEmpty(email) ||
                TextUtils.isEmpty(password)) {

            // Notify UI about missing fields
            errorMessage.setValue("Please fill all fields");
            return;
        }

        // Manager full name required (at least two words)
        if (TextUtils.isEmpty(managerName) || managerName.trim().split("\\s+").length < 2) {
            errorMessage.setValue("Please enter first and last name");
            return;
        }

        if (password.length() < 6) {
            errorMessage.setValue("Password must be at least 6 characters");
            return;
        }

        // Notify UI that processing has started
        isLoading.setValue(true);

        // === forward Firebase logic to Repository ===
        repository.registerCompanyAndManager(
                companyName,
                managerName,
                email,
                password,
                new CompanyRepository.RegisterCompanyCallback() {

                    @Override
                    public void onSuccess(String companyId, String companyCode) {
                        // Stop loading animation
                        isLoading.postValue(false);

                        // Supply results to UI layer
                        successCompanyId.postValue(companyId);
                        successCompanyCode.postValue(companyCode);
                    }

                    @Override
                    public void onError(String message) {
                        // Stop loading animation
                        isLoading.postValue(false);

                        // Propagate error to UI
                        errorMessage.postValue(message);
                    }
                }
        );
    }
}
