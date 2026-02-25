package com.example.workconnect.viewModels.auth;

import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.repository.authAndUsers.GoogleRegistrationRepository;

/**
 * ViewModel for completing Google sign-in registration flow.
 * Decides between:
 * - EMPLOYEE: join existing company by code (PENDING)
 * - MANAGER: create new company (APPROVED but profile may be incomplete)
 */
public class CompleteGoogleProfileViewModel extends ViewModel {

    // UI choice on the screen
    public enum RoleChoice { EMPLOYEE, MANAGER }

    // UI state
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    // Navigation/flow outputs
    private final MutableLiveData<Boolean> employeePending = new MutableLiveData<>(false);
    private final MutableLiveData<String> managerCompanyId = new MutableLiveData<>();
    private final MutableLiveData<String> managerCompanyCode = new MutableLiveData<>();

    // Repository handles Firestore/Auth writes
    private final GoogleRegistrationRepository repo = new GoogleRegistrationRepository();

    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getEmployeePending() { return employeePending; }
    public LiveData<String> getManagerCompanyId() { return managerCompanyId; }
    public LiveData<String> getManagerCompanyCode() { return managerCompanyCode; }

    /**
     * Completes registration after Google authentication.
     * Validates input locally, then delegates to repository.
     */
    public void complete(RoleChoice choice, String fullName, String companyCode, String companyName) {

        // Basic validation: require at least two name parts
        if (TextUtils.isEmpty(fullName) || fullName.trim().split("\\s+").length < 2) {
            errorMessage.setValue("Please enter first and last name");
            return;
        }

        isLoading.setValue(true);

        if (choice == RoleChoice.EMPLOYEE) {

            // Company join code is expected to be 6 chars
            if (TextUtils.isEmpty(companyCode) || companyCode.trim().length() != 6) {
                isLoading.setValue(false);
                errorMessage.setValue("Company code must be 6 characters");
                return;
            }

            // Employee flow: create user doc as PENDING + notify managers
            repo.completeAsEmployee(fullName, companyCode, new GoogleRegistrationRepository.CompleteCallback() {
                @Override public void onEmployeePending() {
                    isLoading.postValue(false);
                    employeePending.postValue(true); // UI should navigate to "waiting for approval"
                }
                @Override public void onManagerApproved(String companyId, String companyCode) {
                    // Not used in employee flow
                }
                @Override public void onError(String message) {
                    isLoading.postValue(false);
                    errorMessage.postValue(message);
                }
            });

        } else { // MANAGER

            // Manager must provide a company name for creation
            if (TextUtils.isEmpty(companyName)) {
                isLoading.setValue(false);
                errorMessage.setValue("Company name is required");
                return;
            }

            // Manager flow: create company + manager profile, return company id/code
            repo.completeAsManagerCreateCompany(fullName, companyName, new GoogleRegistrationRepository.CompleteCallback() {
                @Override public void onEmployeePending() {
                    // Not used in manager flow
                }
                @Override public void onManagerApproved(String companyId, String code) {
                    isLoading.postValue(false);
                    managerCompanyId.postValue(companyId);
                    managerCompanyCode.postValue(code); // UI may show join code / continue setup
                }
                @Override public void onError(String message) {
                    isLoading.postValue(false);
                    errorMessage.postValue(message);
                }
            });
        }
    }
}