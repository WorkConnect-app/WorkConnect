package com.example.workconnect.viewModels.auth;

import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.repository.EmployeeRepository;
import com.google.firebase.auth.FirebaseAuth;

public class CompleteManagerProfileViewModel extends ViewModel {

    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> done = new MutableLiveData<>(false);

    private final EmployeeRepository repo = new EmployeeRepository();

    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }
    public LiveData<Boolean> getDone() { return done; }

    // ✅ Teams removed from this flow
    public void save(String department, String jobTitle, String vacationDaysPerMonthStr) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (uid == null) {
            error.setValue("No logged-in user");
            return;
        }

        if (TextUtils.isEmpty(jobTitle) || TextUtils.isEmpty(department) || TextUtils.isEmpty(vacationDaysPerMonthStr)) {
            error.setValue("Please fill all fields");
            return;
        }

        double v;
        try {
            v = Double.parseDouble(vacationDaysPerMonthStr.trim());
        } catch (Exception e) {
            error.setValue("Invalid vacation days value");
            return;
        }

        if (v < 0) {
            error.setValue("Vacation days must be 0 or higher");
            return;
        }

        loading.setValue(true);

        // ✅ We keep repo signature for now, but pass empty team (or null)
        repo.completeManagerProfile(
                uid,
                v,
                department.trim(),
                "", // team removed from this screen
                jobTitle.trim(),
                (success, message) -> {
                    loading.postValue(false);
                    if (success) {
                        done.postValue(true);
                    } else {
                        error.postValue(message == null ? "Save failed" : message);
                    }
                }
        );
    }
}
