package com.example.workconnect.viewModels.auth;

import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.repository.authAndUsers.GoogleRegistrationRepository;

public class CompleteGoogleProfileViewModel extends ViewModel {

    public enum RoleChoice { EMPLOYEE, MANAGER }

    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private final MutableLiveData<Boolean> employeePending = new MutableLiveData<>(false);
    private final MutableLiveData<String> managerCompanyId = new MutableLiveData<>();
    private final MutableLiveData<String> managerCompanyCode = new MutableLiveData<>();

    private final GoogleRegistrationRepository repo = new GoogleRegistrationRepository();

    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getEmployeePending() { return employeePending; }
    public LiveData<String> getManagerCompanyId() { return managerCompanyId; }
    public LiveData<String> getManagerCompanyCode() { return managerCompanyCode; }

    public void complete(RoleChoice choice, String fullName, String companyCode, String companyName) {

        if (TextUtils.isEmpty(fullName) || fullName.trim().split("\\s+").length < 2) {
            errorMessage.setValue("Please enter first and last name");
            return;
        }

        isLoading.setValue(true);

        if (choice == RoleChoice.EMPLOYEE) {
            if (TextUtils.isEmpty(companyCode) || companyCode.trim().length() != 6) {
                isLoading.setValue(false);
                errorMessage.setValue("Company code must be 6 characters");
                return;
            }

            repo.completeAsEmployee(fullName, companyCode, new GoogleRegistrationRepository.CompleteCallback() {
                @Override public void onEmployeePending() {
                    isLoading.postValue(false);
                    employeePending.postValue(true);
                }
                @Override public void onManagerApproved(String companyId, String companyCode) {}
                @Override public void onError(String message) {
                    isLoading.postValue(false);
                    errorMessage.postValue(message);
                }
            });

        } else { // MANAGER
            if (TextUtils.isEmpty(companyName)) {
                isLoading.setValue(false);
                errorMessage.setValue("Company name is required");
                return;
            }

            repo.completeAsManagerCreateCompany(fullName, companyName, new GoogleRegistrationRepository.CompleteCallback() {
                @Override public void onEmployeePending() {}
                @Override public void onManagerApproved(String companyId, String code) {
                    isLoading.postValue(false);
                    managerCompanyId.postValue(companyId);
                    managerCompanyCode.postValue(code);
                }
                @Override public void onError(String message) {
                    isLoading.postValue(false);
                    errorMessage.postValue(message);
                }
            });
        }
    }
}
