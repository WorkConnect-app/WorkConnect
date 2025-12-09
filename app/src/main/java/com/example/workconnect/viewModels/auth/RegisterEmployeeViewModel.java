package com.example.workconnect.viewModels.auth;

import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.repository.EmployeeRepository;

public class RegisterEmployeeViewModel extends ViewModel {

    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> registrationPending = new MutableLiveData<>(false);

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

    public void registerEmployee(String fullName,
                                 String email,
                                 String password,
                                 String companyCode) {

        // ולידציות בסיסיות
        if (TextUtils.isEmpty(fullName) ||
                TextUtils.isEmpty(email) ||
                TextUtils.isEmpty(password) ||
                TextUtils.isEmpty(companyCode)) {

            errorMessage.setValue("Please fill all fields");
            return;
        }

        if (password.length() < 6) {
            errorMessage.setValue("Password must be at least 6 characters");
            return;
        }

        isLoading.setValue(true);

        repository.registerEmployee(fullName, email, password, companyCode,
                new EmployeeRepository.RegisterEmployeeCallback() {
                    @Override
                    public void onSuccess() {
                        isLoading.postValue(false);
                        // הרשמה הצליחה – במצב שלך זה "ממתין לאישור"
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
