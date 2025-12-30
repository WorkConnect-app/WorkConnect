package com.example.workconnect.viewModels.auth;

import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.repository.AuthRepository;

public class LoginViewModel extends ViewModel {

    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<String> loginRole = new MutableLiveData<>(); // "manager"/"employee"

    private final AuthRepository repository = new AuthRepository();

    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<String> getLoginRole() { return loginRole; }

    public void login(String email, String password) {
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            errorMessage.setValue("Email and Password required.");
            return;
        }

        isLoading.setValue(true);

        repository.login(email, password, new AuthRepository.LoginCallback() {
            @Override
            public void onSuccess(String role) {
                isLoading.postValue(false);
                loginRole.postValue(role);
            }


            @Override
            public void onError(String message) {
                isLoading.postValue(false);
                errorMessage.postValue(message);
            }
        });
    }
}
